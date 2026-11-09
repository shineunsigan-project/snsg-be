## 배경

- Linear SHI-174. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-09 "다른 도메인용 프로필 조회 port/in (user)"다(DTL-01·06).
- 명세 범위: port/in `FindUserProfilesUseCase`(여러 ID → nickname, profileImageUrl, gender, ageGroup, withdrawn). 이름·이메일·생년월일은 담지 않는다. 삭제된 행도 읽는 repository 메서드(이름에 드러낸다)와 POL-U12 마스킹(is_deleted 기준: 닉네임 "탈퇴한 사용자", 기본 이미지, ageGroup 없음, 성별 유지).
- 명세 완료 조건: 한 번의 쿼리로 N명 조회 / 없는 ID는 결과에서 빠지지 않고 withdrawn 처리 / 탈퇴자 마스킹 / Result에 이름·이메일·생년월일 필드 없음.
- 이 UseCase를 쓸 곳: T-12 일정 상세(`GetScheduleDetailFacade`가 `UserPort.findProfiles`로 개설자와 참여자를 한 번에 조회, 연령대 범위에서 탈퇴자 제외)와 T-14 참여자 명단(nickname·profileImageUrl·gender·ageGroup·withdrawn, 탈퇴자 ageGroup null).
- 결정 (코드·명세 근거, 사용자 확인 완료)
  1. **호출자가 아직 없지만 port/in을 만든다.** 명세의 도메인 간 의존 방향에 schedule → user `FindUserProfilesUseCase`가 신규 계약으로 정해져 있고 T-12가 쓴다. 선례: place `FindRegionUseCase`(T-06)·`FindPlaceUseCase`(T-07)도 schedule이 쓰기 전에 만들었다. 기존 `FindUserUseCase`에 더하지 않고 새 인터페이스로 둔다(행위별 UseCase, 명세 이름). 구현은 `UserQueryService`(명세 3.4 표), 새 의존 없음.
  2. **행이 없는 ID**: 탈퇴자와 같이 마스킹하고 `withdrawn = true`, `gender = null`이다. 행이 없으니 성별을 알 수 없다. 사용자는 hard delete되지 않고 schedule은 저장 전 활성 사용자를 확인하므로 데이터 손상 때만 생긴다. T-12는 이 사람을 어느 성별에도 세지 않는다.
  3. **기본 이미지**는 `Profile.DEFAULT_PROFILE_IMAGE_URL`(`/images/default-profile.png`)이다. 가입 시 사진 없음과 현재 `Profile.anonymized()`가 같은 상수를 쓴다.
  4. **마스킹은 `isDeleted`만 보고 정한다.** 지금 `User.withdraw()`는 즉시 비식별(닉네임 "탈퇴한 사용자", 생년월일 1900-01-01, 기본 이미지)하지만 T-05가 이를 나눠 원본을 90일 보관한다. 저장된 값에 기대지 않아야 T-05 전후 모두 맞다. 삭제된 사용자는 연령대를 계산하지 않는다.
  5. **연령대 기준일**은 내 프로필(`UserQueryService.getMyProfile`)과 같이 서비스가 `LocalDate.now()`로 정해 넘기고 `AgeGroup.of(birthDate, today)`로 계산한다. `AgeGroup`은 domain VO라 Result에 담지 않고, Result에는 nullable 중첩 record(decade + `AgePhase`)로 담는다. `AgePhase`·`Gender`는 `domain/constants`라 계약에 실을 수 있다.
  6. **입력·결과 형태**: 입력 `Collection<UUID>`, 결과 `Map<UUID, 프로필 Result>`. 입력의 서로 다른 ID마다 항목이 정확히 하나 있다(중복 ID는 한 번). 빈 입력이면 쿼리 없이 빈 맵. 근거: package-structure.md "포트는 요청당 한 번 불러 맵으로 붙인다". 순서(개설자 먼저, 참여 순)는 schedule이 정한다.
  7. **한 번의 쿼리**: 삭제된 행까지 읽는 repository 메서드 하나로 읽는다. 이름에 삭제 행 포함을 드러낸다(`UserRepository` 주석 규칙). `User`에는 지연 로딩 컬렉션이 없어 `id in (...)` 한 번이다.
  8. **API·문서 변경 없음.** FE API가 없으므로 `docs/openapi.yaml`·`docs/api-guide.md`는 바뀌지 않는다. 마이그레이션도 없다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-174` (브랜치 `feat/SHI-174-user-profiles-lookup`, 베이스 `75a02d4` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리는 읽기만 한다.
- `snsg/.env`는 워크트리에 복사돼 있어야 한다. 없으면 멈추고 보고한다.
- develop의 마이그레이션은 V1~V8이다. 이번 작업은 더하지 않는다.
- 셸은 PowerShell(5.1)이 기본이고 Bash도 쓸 수 있다. PowerShell에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL `snsg-postgres`)·Redis(`snsg-redis`)·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`·`SnsgApplicationTests`·`OpenApiSpecTest`가 로컬 DB에 붙는다. 컨테이너가 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다. 테스트는 다른 워크트리와 동시에 돌리지 않는다.
- 테스트의 Hikari 풀은 3이다(`snsg/build.gradle`).
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 테스트 클래스는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 하고, 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.

1. user `application/port/in`에 `FindUserProfilesUseCase`가 있고, 입력은 사용자 ID 컬렉션(`Collection<UUID>`), 결과는 사용자 ID → 프로필 Result의 `Map`이다. `UserQueryService`가 구현한다.
   - 검증: `grep -rn "interface FindUserProfilesUseCase" snsg/src/main/java/com/shineunsigan/snsg/user/application/port/in` → 1건, 메서드 반환 타입이 `Map<UUID, ...>`, 인자가 `Collection<UUID>`
   - 검증: `grep -n "class UserQueryService" snsg/src/main/java/com/shineunsigan/snsg/user/application/UserQueryService.java` → `implements`에 `FindUserProfilesUseCase` 포함

2. 프로필 Result는 user `application/dto/result`의 record이고, 필드는 닉네임·프로필 사진 경로·`Gender`·연령대·탈퇴 여부(`boolean withdrawn`) 다섯 개뿐이다. 연령대는 decade(`int`, 십의 자리 × 10)와 `AgePhase`를 가진 중첩 record이고 nullable이다. 이름·이메일·생년월일 필드는 없다. record javadoc에 `gender`는 행이 없는 ID면 null, 연령대는 탈퇴자·행이 없는 ID면 null이라고 적혀 있다.
   - 검증: 새 Result 파일을 읽는다 → 위 다섯 필드만 있음, 중첩 연령대 record의 필드는 `int decade`, `AgePhase phase`(이름은 달라도 됨), `@param` 또는 본문 javadoc에 gender·연령대의 null 조건이 적혀 있음
   - 검증: `grep -niE "name|email|birth" <새 Result 파일>` → 필드 선언에 걸리는 줄 없음(`nickname`은 제외하고 본다)
   - 검증: Result가 `user.domain.model`(`AgeGroup`, `User`, `Profile`)을 필드 타입으로 쓰지 않는다 → 필드 타입은 `String`, `Gender`, `boolean`, 중첩 record뿐

3. 삭제된 행까지 읽는 `UserRepository` 메서드가 있고 이름에 삭제 행 포함을 드러낸다(예: `IncludingDeleted`). 주어진 ID들을 한 번의 쿼리로 읽는다. 실제 PostgreSQL에서 삭제되지 않은 행과 삭제된 행이 모두 읽히고, 없는 ID는 결과에 없다.
   - 검증: `grep -n "IncludingDeleted\|WithDeleted" snsg/src/main/java/com/shineunsigan/snsg/user/domain/repository/UserRepository.java` → 1건 이상
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*UserRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0. 새 테스트가 정상 사용자·`delete()`한 사용자·무작위 없는 ID를 함께 넘겨 앞의 둘만 읽히는지 단정한다

4. `UserQueryService`의 프로필 조회가 경우별로 아래와 같이 돌려준다. 연령대 기준일은 `LocalDate.now()`다.
   - 정상 행: 저장된 닉네임·사진 경로·성별, 연령대 = `AgeGroup.of(생년월일, 오늘)`의 decade·phase, `withdrawn = false`
   - 삭제된 행(`isDeleted = true`): 닉네임 `"탈퇴한 사용자"`, 사진 `Profile.DEFAULT_PROFILE_IMAGE_URL`, 저장된 성별 유지, 연령대 null, `withdrawn = true`. 테스트는 `withdraw()`가 아니라 `delete()`만 불러 원본 닉네임·사진·생년월일이 남은 사용자로 확인한다(T-05 이후 상태)
   - 행이 없는 ID: 결과에 빠지지 않고 닉네임 `"탈퇴한 사용자"`, 사진 `Profile.DEFAULT_PROFILE_IMAGE_URL`, `gender = null`, 연령대 null, `withdrawn = true`
   - 중복 ID: 결과 맵 크기는 서로 다른 ID 수이고, repository에 넘긴 ID에도 중복이 없다
   - 빈 입력: 빈 맵, repository를 부르지 않는다
   - 모든 경우(빈 입력 제외) 3번의 메서드를 정확히 한 번 부르고 `findById`는 부르지 않는다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*UserQueryServiceTest'` → BUILD SUCCESSFUL, 실패 0. 위 다섯 경우마다 테스트 메서드가 있고, 호출 횟수는 Mockito `verify(..., times(1))`·`never()`로 단정한다

5. 패키지 의존 규칙을 지킨다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*Architecture*'` → BUILD SUCCESSFUL, 실패 0

6. API·문서·마이그레이션·다른 도메인을 바꾸지 않는다.
   - 검증: `git status --short` → 모든 경로가 `snsg/src/main/java/com/shineunsigan/snsg/user/`, `snsg/src/test/java/com/shineunsigan/snsg/user/`, `.claude/tasks/SHI-174/` 아래. `docs/`, `db/migration/`, `presentation/`, user 외 도메인 경로 없음

7. 전체 빌드가 통과한다.
   - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0 (`OpenApiSpecTest` 포함)

## 비목표

- 일정 쪽 사용처(`schedule`의 `UserPort.findProfiles`, adapter, 연령대 min·max 비교)는 만들지 않는다. T-12 몫이다. 명세 3.4의 "`AgeGroup` 연령대 범위 비교(min·max)"도 이번에 만들지 않는다.
- `User.withdraw()`·`Profile.anonymized()`의 즉시 비식별 동작은 그대로 둔다. 분리는 T-05 몫이다.
- 기존 `FindUserUseCase`(`exists`, `findIdBySocialAccount`)와 그 구현, `MyProfileResult`·`getMyProfile`은 고치지 않는다.
- 컨트롤러·Response·`docs/openapi.yaml`·`docs/api-guide.md`는 만들거나 고치지 않는다.
- 기존 테스트의 단정을 고치지 않는다.
- 입력에 `null` ID가 섞이는 경우는 다루지 않는다(호출하는 schedule이 저장된 ID만 넘긴다). 방어 코드를 더하지 않는다.
- 결과 맵의 순서를 보장하지 않는다. 순서는 호출하는 쪽이 정한다.

## 제약

- 패키지 구조·DTO·도메인 간 참조는 `.claude/rules/package-structure.md`를 따른다(Port 절, DTO 절, 도메인 간 참조 절, domain 절).
- 테스트는 `.claude/rules/testing.md`를 따른다. repository 테스트는 실제 PostgreSQL(`@AutoConfigureTestDatabase(replace = NONE)`), 서비스 테스트는 Mockito다. 테스트는 원래 코드와 별도 커밋 대상이므로 스테이징만 하고 커밋하지 않는다.
- 임시 파일은 세션 스크래치패드에 두고 스테이징 전에 지운다(`.claude/rules/temp-files.md`).
- 들여쓰기 4칸 공백, 커밋은 하지 않는다(`.claude/rules/git-workflow.md` 3번).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 이름 제안(바꿔도 된다): Result `UserProfileResult`(중첩 `AgeGroupResult(int decade, AgePhase phase)`), repository `findAllByIdIncludingDeleted(Collection<UUID> ids)`, JPA 쪽은 Spring Data `findAllByIdIn` 또는 `findAllById`(`JpaRepository` 기본 메서드는 삭제 조건이 없어 삭제 행도 읽는다).
- 마스킹 위치 제안: Result 팩토리 두 개(`of(User, LocalDate today)`가 `user.isDeleted()`로 갈림, `missing()`이 행 없는 ID)나 엔티티 쪽 메서드. "탈퇴한 사용자" 문구는 지금 `Profile.WITHDRAWN_NICKNAME`(private)에 있다. 중복 상수를 만들지 말고 공개 범위를 넓혀 함께 쓰는 쪽을 권한다.
- 단위 테스트에서 `User.create`로 만든 엔티티는 id가 없다(JPA가 생성). 맵 키를 확인하려면 `ReflectionTestUtils.setField(user, "id", ...)` 등으로 넣는다.
- 기존 `UserQueryServiceTest`는 `@InjectMocks`로 `UserQueryService`를 만든다. 생성자 의존이 늘지 않으므로 그대로 쓸 수 있다.
- `UserRepositoryImplTest`는 공유 DB를 쓴다. 닉네임 유니크 인덱스(`uix_users_nickname`)가 삭제되지 않은 행에 걸려 있으니 테스트 닉네임은 무작위로 만든다(기존 테스트의 방식을 따른다).
- 탈퇴 판정을 `isDeleted`로만 하는 것은 erd.md "탈퇴 여부는 `is_deleted`다. 상태 컬럼을 두지 않는다"와 같다.

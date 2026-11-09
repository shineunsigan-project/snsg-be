## 배경

- Linear SHI-176 "일정 상세". 기능 명세(저장소 밖 `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-12를 구현한다. 관련 명세 항목은 API-S07, 화면 표시 상태(displayStatus), 보는 사람의 역할과 버튼(viewerRole·availableActions), 연령대 계산 규칙(D13), 탈퇴 표시(POL-U12), 삭제된 일정(POL-S16)이다.
- 이미 머지된 선행 작업
  - T-09 (#58): user `FindUserProfilesUseCase.findProfiles(Collection<UUID>)` → 불변 `Map<UUID, UserProfileResult>`. 탈퇴자는 닉네임 "탈퇴한 사용자"·기본 이미지·ageGroup null·성별 유지, 행이 없는 ID는 탈퇴자처럼 가리고 gender null.
  - T-16 (#56): `ListSchedulesFacade`, `RegionPort.findAll`. T-11에서 `RegionPort.find`.
  - T-13 (#54): 참여·참여 취소. T-17 (#57): 상태 전환 스케줄러.
- 사용자와 합의한 결정
  - 연령대 비교(min·max)와 참여자 요약은 **schedule이 갖는다.** `ScheduleUserAdapter`가 user `AgeGroupResult`를 schedule 소유 값으로 바꾸고, user `AgePhase`는 `default` 없는 switch로 schedule 소유 enum으로 옮긴다. 명세의 "user AgeGroup VO에서 범위 비교" 문구는 오케스트레이터가 고친다(이 작업에서 명세 파일을 건드리지 않는다).
  - 응답의 `cancellation`은 T-15 범위다. 이번에는 응답에 넣지 않는다.
  - HOST의 `CANCEL_SCHEDULE`은 명세 표대로 지금 넣는다(전체 취소 API는 T-15에서 생긴다는 것을 api-guide에 적는다).
  - regionName은 `RegionPort.find(code)`로 채운다.
  - SecurityConfig는 `/api/v1/schedules/participating`·`/hosted`를 authenticated로 먼저 두고 상세는 GET 정확 패턴으로 연다(정규식은 필요할 때만).

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-176` (브랜치 `feat/SHI-176-schedule-detail`, 베이스 `736c6d7`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다
- `snsg/.env`는 복사돼 있다
- DB·Redis 테스트는 로컬 Docker 컨테이너(`snsg-postgres`, `snsg-redis`)에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 다른 워크트리가 테스트 중일 수 있으니 테스트는 오케스트레이터가 정한 시점에만 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의")
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. `&&`는 PowerShell에서 동작하지 않는다. 아래 검증 명령은 Bash 기준이다
- 이번 작업은 마이그레이션을 더하지 않는다(V1~V9 그대로)
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다

## GOAL

1. `GET /api/v1/schedules/{scheduleId}`가 상세를 돌려준다. 응답 `data` 필드: `id, title, description, startAt, createdAt, durationType, category{id, name}, host{nickname, profileImageUrl, withdrawn}, confirmedCount, capacity, remainingSeats(= capacity − confirmedCount), participantSummary{count, maleCount, femaleCount, minAgeGroup, maxAgeGroup}, place{name, roadAddress, addressDetail, latitude, longitude, regionName}, displayStatus, viewerRole, availableActions`. `cancellation` 필드는 없다. place는 일정에 저장된 사본에서 읽고 place 도메인을 부르지 않는다. 연령대 값은 user의 `AgeGroupResult`와 같은 정보(십의 자리 decade, 초반·중반·후반 phase)를 담는다.
   - 검증: `cd snsg && grep -n "cancellation" ../docs/openapi.yaml` → 상세 응답 스키마에 `cancellation`이 없다(다른 곳에도 없으면 출력 없음)
   - 검증: 8번 `OpenApiSpecTest` 통과로 응답 스키마가 docs와 일치함을 확인한다

2. viewerRole·availableActions 판정은 schedule 도메인 메서드에 있고, domain 단위 테스트(Spring·mock 없이)가 아래를 단정한다.
   - viewerRole: 보는 사람 id가 null → `GUEST`, 개설자 → `HOST`, 확정(CONFIRMED) MEMBER 행이 있음 → `PARTICIPANT`, 그 밖(참여 취소한 사람 포함) → `NON_PARTICIPANT`
   - availableActions: 네 역할 × 네 표시 상태(`RECRUITING`, `RECRUITMENT_CLOSED`, `COMPLETED`, `CANCELED`) 조합의 표 테스트
     - GUEST: `RECRUITING`일 때만 `[LOGIN_TO_JOIN]`, 그 밖은 빈 목록
     - NON_PARTICIPANT: `RECRUITING`일 때만 `[JOIN]`
     - PARTICIPANT: `RECRUITING`·`RECRUITMENT_CLOSED`일 때 `[CANCEL_PARTICIPATION]`
     - HOST: status = SCHEDULED이고 startAt > now일 때만 `[CANCEL_SCHEDULE]`. `RECRUITMENT_CLOSED`는 두 경우를 모두 본다. 정원이 차 마감된 시작 전 일정은 `[CANCEL_SCHEDULE]`이고, 시작 시각이 지난(스케줄러 전 포함) 일정은 빈 목록이다
   - 시작 시각이 지났지만 저장된 모집 상태가 아직 RECRUITING(스케줄러 전)이면 displayStatus는 `RECRUITMENT_CLOSED`이고 GUEST·NON_PARTICIPANT의 동작은 빈 목록이다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.domain*'` → BUILD SUCCESSFUL, 실패 0, 위 조합을 다루는 테스트가 실행됨(테스트 리포트 또는 `--info` 출력에서 확인)

3. 참여자 요약 계산은 schedule 도메인에 있고, domain 단위 테스트가 아래를 단정한다.
   - count는 확정 참여자 수(개설자 포함)다
   - 탈퇴자는 성별을 그대로 남자/여자 수에 센다. gender가 null인 항목(행이 없는 ID)은 어느 쪽에도 세지 않는다
   - minAgeGroup·maxAgeGroup은 연령대가 없는 항목(탈퇴자·행이 없는 ID)을 빼고 고른다. 비교는 decade, 같으면 초반 < 중반 < 후반 순서다(예: 20대 후반 < 30대 초반)
   - 연령대가 있는 항목이 하나도 없으면(전원 탈퇴) min·max 모두 null
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.domain*'` → BUILD SUCCESSFUL, 실패 0

4. 상세 흐름(facade)을 Mockito 테스트가 단정한다.
   - 개설자와 확정 참여자 ID를 모아 `UserPort`의 프로필 일괄 조회를 **요청당 한 번** 부른다(참여자마다 부르지 않는다)
   - 탈퇴한 개설자는 `host`가 nickname "탈퇴한 사용자"·기본 이미지·`withdrawn = true`다(user가 준 마스킹 값을 그대로 쓴다)
   - regionName은 `RegionPort.find(code)` 결과의 이름이고, 지역이 없으면 `""`다
   - 일정이 없으면 `BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND)`가 나고 user·region 포트를 부르지 않는다
   - facade에는 `@Transactional`이 없다(ArchUnit이 검사한다)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.application*'` → BUILD SUCCESSFUL, 실패 0

5. 없는 일정과 삭제된(`is_deleted = true`) 일정은 404 `SCHEDULE_NOT_FOUND`다. 일정을 잠금 없이 읽는 조회가 삭제된 행을 돌려주지 않는다는 것을 실제 PostgreSQL 테스트(`@DataJpaTest`, `replace = NONE`)가 단정한다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0, 삭제된 일정 조회 테스트 포함

6. 상세는 선택 인증 API다. 토큰 없이도 200이고, 토큰이 없으면(principal null) viewerRole 판정에 null이 넘어가 GUEST가 된다. 토큰이 있으면 그 userId가 넘어간다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest'` → BUILD SUCCESSFUL, 실패 0. 인증 없이 부를 때 facade에 null이 넘어가는 것과 인증했을 때 userId가 넘어가는 것을 단정하는 테스트 포함

7. SecurityConfig가 상세만 공개하고 두 목록은 계속 인증을 요구한다. `SecurityConfigTest`가 아래를 단정한다.
   - `GET /api/v1/schedules/{무작위 UUID}`를 토큰 없이 부르면 401이 아니다
   - `GET /api/v1/schedules/participating`, `GET /api/v1/schedules/hosted`를 토큰 없이 부르면 401(기존 단정 유지)
   - `GET /api/v1/schedules`(공개), `POST /api/v1/schedules`(401) 기존 단정 유지
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `cd snsg && grep -n 'schedules/\*' src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java` → 출력 없음

8. 문서가 코드와 맞는다.
   - `docs/openapi.yaml`을 `OPENAPI_UPDATE=true`로 다시 만들어 상세 API가 들어 있다. 상세 description에는 토큰이 선택이고, 토큰이 있으면 viewerRole이 달라지고, 틀린 토큰은 익명(GUEST)으로 처리한다고 적는다. 상세 메서드에는 `@SecurityRequirements`를 달지 않는다
   - 참여·참여 취소 API description의 "(상세 API는 아직 없다)" 문구가 없다
   - `docs/api-guide.md`에 "일정 상세" 절이 있다. api.md 문서 절의 항목(부르는 화면, 응답 필드마다 의미·null 여부, displayStatus·viewerRole·availableActions 값마다의 뜻과 버튼, 404 처리, 인증 선택, 재시도 안전)을 담고, `CANCEL_SCHEDULE`이 부를 전체 취소 API는 아직 없다(T-15)는 것과 `cancellation`이 아직 없다는 것을 적는다. 참여 절의 "일정 상세 API는 아직 없다. 생기면 그 API로 다시 그린다." 문구를 상세 API로 다시 그린다는 내용으로 바꾼다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `grep -rn "상세 API는 아직 없다\|일정 상세 API는 아직 없다" snsg/src/main docs/` (워크트리 루트에서) → 출력 없음
   - 검증: `grep -n "### 일정 상세" docs/api-guide.md` → 한 줄

9. 패키지 규칙과 전체 빌드를 통과한다.
   - schedule의 application·domain·presentation이 user 패키지를 import하지 않는다(user 타입은 `schedule/infrastructure/client` 어댑터에서만)
   - 검증: `grep -rln "com.shineunsigan.snsg.user" snsg/src/main/java/com/shineunsigan/snsg/schedule` (워크트리 루트에서) → `schedule/infrastructure/client/` 아래 파일만
   - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0(`ArchitectureTest` 포함)

10. 워크트리 밖과 무관한 파일을 고치지 않는다. 변경은 스테이징까지만 하고 커밋하지 않는다.
    - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/wt-SHI-176 status --short` → 모든 경로가 `snsg/src/`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/` 아래. `snsg/src/main/resources/db/migration/` 아래 변경 없음
    - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/snsg-be status --short` → 출력 없음(메인 디렉터리 무변경)

- 정지 조건: 위 검증이 모두 기대 결과일 때 끝낸다
- 끝내지 않는 경우: 테스트를 지우거나 기존 단정을 완화해 통과시켰을 때, 컴파일이 깨져 테스트가 돌지 않았을 때, `OpenApiSpecTest`를 `OPENAPI_UPDATE` 없이 돌려 확인하지 않았을 때, 실패가 줄었지만 0이 아닐 때

## 비목표

- `cancellation` 필드, 전체 취소 API(API-S11), `schedule_cancellations` 마이그레이션: T-15
- 참여자 명단 API(API-S08, `ListParticipantsFacade`): T-14. 이번에 만드는 프로필 조회 포트·DTO는 T-14가 다시 쓸 수 있지만 명단용 필드(isHost, isMe 등)를 미리 만들지 않는다
- 탈퇴 가능 검사(T-18), auth 흐름: 건드리지 않는다
- user 도메인 코드(`AgeGroup`, `FindUserProfilesUseCase`, `UserProfileResult` 등)를 고치지 않는다. 연령대 비교는 schedule에 둔다
- 보는 사람의 활성 여부(`UserPort.isActive`)를 상세에서 확인하지 않는다. 토큰이 유효하면 그 userId로 역할을 정한다
- 기존 `Schedule.displayStatus`·`join`·`leave`의 동작과 기존 테스트의 단정을 바꾸지 않는다
- 명세 파일(`planning/be-spec-mvp.html`)을 고치지 않는다(오케스트레이터가 고친다)
- 목록 API(`ListSchedulesFacade`, `ScheduleQueryService`의 목록 메서드)는 동작을 바꾸지 않는다
- 삭제된 활동이면 category.name은 목록 API와 같이 `""`다. 별도 오류로 만들지 않는다
- 지역이 없으면 regionName은 `""`다. 오류로 만들지 않는다

## 제약

- 패키지 구조·Service와 Facade·Port·도메인 간 참조(enum 변환은 어댑터에서 `default` 없는 switch)·삭제 조회 규칙은 `.claude/rules/package-structure.md`를 따른다
- API·인증 주체·공개 경로·문서 작성은 `.claude/rules/api.md`를 따른다
- 테스트 계층·방식·DB 공유 주의(무작위 키, `replace = NONE`)는 `.claude/rules/testing.md`를 따른다
- 테스트는 원래 코드와 별도 커밋이 되도록 스테이징 계획을 보고한다(`.claude/rules/git-workflow.md`, `testing.md`). `docs/openapi.yaml`은 API 코드와 같은 커밋에 둔다(api.md)
- 임시 파일은 세션 스크래치패드에 두고 워크트리에 남기지 않는다(`.claude/rules/temp-files.md`)
- 들여쓰기 4칸, Jackson 3(CLAUDE.md)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 형태에 대한 제안이다. 결과(GOAL)를 지키면 바꿔도 된다.

- 흐름: `ScheduleController` → `GetScheduleDetailFacade`(트랜잭션 없음) → `ScheduleQueryService`(readOnly 트랜잭션 안에서 일정·확정 참여 행·활동 이름을 읽고 now 한 번으로 displayStatus·viewerRole·availableActions를 판정해 Result로 바꾼다. 참여 행은 지연 로딩이라 트랜잭션 밖으로 엔티티를 넘기지 않는다) → `UserPort.findProfiles`(개설자 + 확정 참여자 ID 한 번) → `RegionPort.find(regionCode)` → 도메인 요약 계산 → Result.
- 잠금 없는 조회: `ScheduleRepository`에 삭제되지 않은 일정을 읽는 메서드(예: `findById`)를 더하고 JPQL에 `isDeleted = false`를 명시한다. 기존 `findByIdForUpdate`(잠금)를 재사용하지 않는다.
- 확정 참여 행 판정은 기존 `ScheduleParticipant.isConfirmedBy`(CONFIRMED이고 삭제되지 않음)와 같은 기준을 쓴다.
- displayStatus는 기존 `Schedule.displayStatus(now, completionAfter)`를 그대로 쓴다. `completionAfter`는 `ScheduleProperties`에서 읽는다.
- 이름 제안: `ViewerRole`·`ScheduleAction` enum(schedule `domain/constants`), schedule 소유 연령대 값(예: `ParticipantAgeGroup(decade, AgePhase)` + schedule `domain/constants/AgePhase`), 요약 VO(예: `ParticipantSummary`), `UserPort.findProfiles(Collection<UUID>)` → `Map<UUID, ScheduleUserProfileResult>`, `ScheduleDetailResult`, `ScheduleDetailResponse`.
- 어댑터 빈 이름 충돌 주의: schedule 어댑터는 기존 `ScheduleUserAdapter`·`ScheduleRegionAdapter`에 메서드를 더한다.
- SecurityConfig 예시 순서: `requestMatchers(GET, "/api/v1/schedules/participating", "/api/v1/schedules/hosted").authenticated()`를 먼저 두고 그 뒤에 `requestMatchers(GET, "/api/v1/schedules/{scheduleId}").permitAll()`. `{scheduleId}` 패턴은 `participating`·`hosted`와도 맞으므로 순서가 중요하다는 주석을 남긴다. 정규식 매처는 이 방식으로 안 될 때만 쓴다. 상세의 하위 경로(`/participants` 등)는 이 패턴에 걸리지 않는다.
- `SecurityConfigTest`·`ScheduleControllerTest`는 컨트롤러 의존이 늘면 `@MockitoBean`(새 facade)을 함께 더해야 컨텍스트가 뜬다(testing.md).
- `ScheduleController`의 다른 컨트롤러 테스트는 `addFilters = false` + `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(userId, null, List.of())`을 넣는 방식이다. 인증 없음은 컨텍스트를 비워 principal이 null로 들어오는지 본다.
- 상세 description에 넣을 것: DTL 화면(상세 진입, 참여·참여 취소·전체 취소 뒤 재조회), 404 처리("찾을 수 없는 일정이에요." 토스트 후 목록으로), 비활성 버튼 문구는 displayStatus로 FE가 정한다, 조회만 하므로 재시도 안전.
- `ScheduleUserAdapter`의 변환은 필드를 옮기는 것이라 별도 어댑터 테스트는 필수가 아니다(testing.md "DTO 변환"). enum 변환 누락은 `default` 없는 switch가 컴파일로 잡는다.
- 상세 시각 경계(스케줄러 지연)는 명세의 "스케줄러 지연 대비" 규칙과 같다. 판정은 `Schedule.displayStatus` 한 곳에서만 한다.

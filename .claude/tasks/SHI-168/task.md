## 배경

- Linear SHI-168. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖·읽기 전용)의 T-02 "닉네임 보호 이력과 중복 확인 API (user)"를 구현한다. 화면은 AUTH-03·04(가입 닉네임 입력), MY-02(프로필 수정)다. API 번호는 API-U01이다.
- 명세 범위: 새 마이그레이션, `user_nickname_histories` 테이블, POL-U3(닉네임 중복) 판정을 `UserRepository.isNicknameTaken(nickname, now)` 하나로 모으기, API-U01 `GET /api/v1/users/nickname-availability?nickname=`, 가입 검증에도 같은 판정 적용. 이력을 쓰는 곳(닉네임 변경 T-03, 탈퇴 T-05)은 범위 밖이다.
- 명세 완료 조건: reserved_until이 지나지 않은 이력의 닉네임은 `available=false`·`reason=DUPLICATED` / 지난 이력은 `true` / 형식 오류는 `INVALID_FORMAT` / 토큰 있는 본인의 현재 닉네임은 `true` / 가입 시 보호 중 닉네임은 `DUPLICATED_NICKNAME`.
- POL-U3(명세): 대소문자 무시. ① 탈퇴하지 않은 회원의 닉네임 ② `reserved_until > now`인 이력(변경 전 14일, 탈퇴 90일) 중 하나라도 있으면 쓸 수 없다. 본인의 현재 닉네임은 중복으로 보지 않는다.
- API-U01(명세): 공개(가입 중에는 토큰이 없다). 토큰이 있으면 본인의 현재 닉네임은 사용 가능으로 본다. 응답 `available`(boolean), `reason`(`INVALID_FORMAT`(POL-U2) / `DUPLICATED`(사용 중·보호 중을 구분하지 않는다. 보호 중임을 알리면 탈퇴·변경 여부가 드러난다), available이면 null). 가입·수정 API가 같은 검사를 다시 하므로 이 결과는 안내용이다.
- 사용자와 합의한 결정(사용자가 "권장안대로 진행"을 위임했다)
  1. **선택 인증.** 이 API는 `SecurityConfig`에서 `GET /api/v1/users/nickname-availability`만 정확히 지정해 permitAll한다. `JwtAuthenticationFilter`는 토큰이 없거나 틀리거나 만료돼도 익명으로 통과시키므로, 컨트롤러의 `@AuthenticationPrincipal UUID userId`는 유효 토큰이면 UUID, 아니면 null이다. 틀린 토큰은 401이 아니라 익명으로 처리한다(본인 예외 없음).
  2. **본인 예외.** 토큰의 사용자가 탈퇴하지 않은 회원이고 그 현재 닉네임과 요청 닉네임이 대소문자 무시로 같으면 `available=true`. 사용자를 못 찾거나 탈퇴했으면 익명과 같다. 이 판정은 QueryService에서 하고 `isNicknameTaken`의 시그니처는 `(nickname, now)` 그대로 둔다.
  3. **판정 순서.** 형식(POL-U2) 실패 → `INVALID_FORMAT`. 그다음 본인 예외 → `true`. 그다음 `isNicknameTaken` → `DUPLICATED`. 아니면 `true`. available이면 reason은 null. reason은 user 도메인 enum(`INVALID_FORMAT`, `DUPLICATED`)이다. 형식 규칙은 `Profile`에 이미 있는 것을 재사용하고 정규식을 새로 적지 않는다.
  4. `nickname` 파라미터 누락은 기존 공통 처리(`GlobalExceptionHandler`가 `MissingServletRequestParameterException`을 400 `INVALID_REQUEST`로 감싼다)에 맡긴다. 빈 문자열(`?nickname=`)은 200 `available=false`·`INVALID_FORMAT`.
  5. **T-02에서는 이력 JPA 엔티티를 만들지 않는다.** 이력 쓰기 경로가 없으므로(`.claude/rules/package-structure.md` domain 절 "쓰기 경로가 없는 엔티티에는 정적 팩토리를 두지 않는다", CLAUDE.md Simplicity First) `isNicknameTaken`은 `users`와 `user_nickname_histories`를 함께 보는 native 쿼리로 구현한다. 엔티티·팩토리는 이력을 쓰는 T-03에서 만든다. 테스트 데이터는 native INSERT로 넣는다. `ddl-auto: validate`는 매핑된 엔티티만 대조하므로 매핑 없는 테이블과 부딪히지 않는다.
  6. DDL은 명세 그대로: `user_id` UUID FK → users, `nickname` VARCHAR(10), `reason` VARCHAR(20)(`CHANGED`/`WITHDRAWN`), `reserved_until` TIMESTAMP, BaseEntity 컬럼(`id`, `created_at`, `updated_at`, `is_deleted`), 인덱스 둘:
     ```sql
     CREATE INDEX ix_nickname_histories_nickname ON user_nickname_histories (LOWER(nickname), reserved_until);
     CREATE INDEX ix_nickname_histories_user ON user_nickname_histories (user_id, reason, created_at);
     ```
     기존 마이그레이션 스타일을 따른다: 모든 컬럼 NOT NULL, 기본값 없음, `CONSTRAINT pk_... PRIMARY KEY`, `CONSTRAINT fk_..._user FOREIGN KEY`, enum 컬럼에 CHECK를 걸지 않는다(V1·V7은 CHECK를 불변식에만 쓴다). `is_deleted=true`인 이력은 판정에서 제외한다.
  7. POL-U3 ①은 현재 `existsByNicknameIgnoreCase`의 범위(`users.is_deleted = false`인 행의 `LOWER(nickname)` 비교)를 그대로 유지하고 이력 조건만 더한다. 이력은 작성자 회원의 탈퇴 여부와 무관하게 본다(탈퇴 이력이 바로 탈퇴 회원의 것이다). 가입(`UserCommandService.validateNicknameNotTaken`)도 같은 메서드를 쓴다. `now`는 기존 서비스 패턴대로 서비스가 `LocalDateTime.now()`로 얻는다(Clock 빈이 없다. 선례 `ScheduleCommandService`, `PlaceCommandService`).
  8. 기존 `UserRepository.existsByNickname`(와 `UserJpaRepository.existsByNicknameIgnoreCase`)는 `isNicknameTaken`으로 대체하고 지운다.
  9. 응답 DTO는 `available`(boolean), `reason`(enum, nullable) 두 필드다. `docs/openapi.yaml`을 다시 만들어 API 코드와 같은 커밋 대상으로 둔다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-168` (브랜치 `feat/SHI-168-nickname-availability`, 베이스 `68adb84`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 워크트리 밖 파일은 고치지 않는다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V7이다. 이번 작업은 V8을 더한다. 다른 워크트리에서 진행 중인 마이그레이션 작업은 없다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 로컬 DB에 붙는다.
- **spec 작성 시점(2026-11-09)에 Docker 데몬이 꺼져 있었다**(`docker ps` → `failed to connect to the docker API`). DB·Redis 컨테이너가 떠 있지 않으면 DB 테스트가 실패하고 Redis Testcontainers 테스트는 skip된다. 테스트 전에 Docker Desktop과 로컬 DB·Redis 컨테이너가 떠 있는지 확인한다. 띄울 수 없으면 구현을 멈추고 그 사실을 보고한다(DB 실패를 코드로 고치려 하지 않는다).
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 그 클래스에 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다. 경로의 `$WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-168`이다.

1. 마이그레이션 `V8__<설명>.sql` 하나가 더해져 `user_nickname_histories` 테이블과 인덱스 둘(`ix_nickname_histories_nickname`, `ix_nickname_histories_user`)을 배경 결정 6의 DDL대로 만든다. V1~V7은 바뀌지 않는다.
   - 검증: `ls $WT/snsg/src/main/resources/db/migration | grep '^V8__'` → 한 줄
   - 검증: `cd $WT && git diff --name-status 68adb84 -- snsg/src/main/resources/db/migration` → `A` 한 줄(V8)만
   - 검증: `grep -niE "user_id|nickname|reason|reserved_until|created_at|updated_at|is_deleted|REFERENCES users|ix_nickname_histories_nickname|ix_nickname_histories_user|LOWER\(nickname\)" $WT/snsg/src/main/resources/db/migration/V8__*.sql` → 열거한 컬럼·FK·두 인덱스가 모두 보이고, 컬럼은 모두 `NOT NULL`, `DEFAULT`·`CHECK` 없음

2. `UserRepository.isNicknameTaken(String nickname, LocalDateTime now)`가 POL-U3 판정을 한 곳에서 한다. 참이 되는 경우: 탈퇴하지 않은 회원(`users.is_deleted=false`)의 닉네임과 대소문자 무시로 같거나, 삭제되지 않은 이력 중 `reserved_until > now`이고 닉네임이 대소문자 무시로 같은 행이 있을 때. 기존 `existsByNickname`·`existsByNicknameIgnoreCase`는 남지 않는다.
   - 검증: `grep -rn "existsByNickname" $WT/snsg/src` → 출력 없음
   - 검증: `grep -n "isNicknameTaken" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/domain/repository/UserRepository.java` → 한 줄, 파라미터가 `String`과 `LocalDateTime`
   - 검증: `UserRepositoryImplTest`(실제 PostgreSQL, 이력은 native INSERT)가 다음을 단정한다. 기존 닉네임 테스트는 `isNicknameTaken`으로 바꿔 부르되 단정(참·거짓)은 유지한다.
     - 탈퇴하지 않은 회원의 닉네임: 같은 값·대소문자만 다른 값 모두 참, 다른 닉네임은 거짓
     - 탈퇴한 회원(`withdraw()`)의 기존 닉네임: 이력이 없으면 거짓(현재 규칙 유지)
     - `reserved_until`이 `now` 이후인 `CHANGED` 이력의 닉네임: 참(대소문자만 달라도 참)
     - `reserved_until`이 `now` 이후인 `WITHDRAWN` 이력의 닉네임: 참
     - `reserved_until`이 `now` 이전인 이력의 닉네임: 거짓
     - `reserved_until`이 `now` 이후지만 `is_deleted=true`인 이력의 닉네임: 거짓
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0

3. 가입(`UserCommandService.register`)이 `isNicknameTaken(닉네임, 현재 시각)`으로 판정하고, 참이면 저장하지 않고 `BusinessException(UserErrorCode.DUPLICATED_NICKNAME)`을 던진다(보호 중 닉네임 포함).
   - 검증: `grep -n "isNicknameTaken" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/application/UserCommandService.java` → 한 줄 이상
   - 검증: `UserCommandServiceTest`가 `isNicknameTaken`이 참이면 `DUPLICATED_NICKNAME`이고 `save`가 호출되지 않음을 단정한다(기존 `registerRejectsDuplicatedNickname`을 바꿔도 된다). 기존 다른 테스트의 단정은 바꾸지 않는다.
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserCommandServiceTest'` → BUILD SUCCESSFUL, 실패 0

4. user `UserQueryService`에 닉네임 사용 가능 여부 판정이 더해진다. 입력은 닉네임과 요청자 userId(null 가능), 결과는 available과 reason(user 도메인 enum, `INVALID_FORMAT`·`DUPLICATED` 두 값, available이면 null). 판정 순서는 배경 결정 3, 본인 예외는 결정 2를 따른다.
   - 검증: `grep -rn "INVALID_FORMAT" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/domain/constants` → enum 파일 하나에서 `INVALID_FORMAT`과 `DUPLICATED`가 보이고 다른 상수는 없다
   - 검증: `UserQueryServiceTest`(Mockito, repository mock)가 다음을 단정한다.
     - 형식 위반(허용하지 않는 문자, 1자, 11자, 빈 문자열): `available=false`·`INVALID_FORMAT`. repository가 호출되지 않는다
     - `isNicknameTaken`이 참(사용 중 또는 보호 중): `available=false`·`DUPLICATED`
     - `isNicknameTaken`이 거짓(만료 이력만 있는 경우 포함): `available=true`·reason null
     - userId가 있고 그 사용자의 현재 닉네임과 대소문자 무시로 같음: `available=true`·reason null, `isNicknameTaken`이 호출되지 않는다
     - userId가 있지만 사용자를 찾지 못함(탈퇴 포함, `findById`가 빈 값): `isNicknameTaken` 결과대로 판정한다
     - userId가 null: `isNicknameTaken` 결과대로 판정한다
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserQueryServiceTest'` → BUILD SUCCESSFUL, 실패 0

5. 형식 판정은 `Profile`의 기존 닉네임 규칙을 재사용한다. 닉네임 정규식 리터럴은 베이스와 같은 두 곳(`Profile.java`, auth `SignUpRequest.java`)에만 있다. `Profile.of`의 기존 동작(형식 위반 시 `INVALID_NICKNAME`)은 그대로다.
   - 검증: `grep -rln "가-힣a-zA-Z0-9._" $WT/snsg/src/main` → `Profile.java`, `SignUpRequest.java` 두 줄
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*ProfileTest'` → BUILD SUCCESSFUL, 실패 0

6. `GET /api/v1/users/nickname-availability?nickname=<값>`가 user `UserController`에 생긴다. 응답은 `ResponseEntity<ApiResponse<응답 DTO>>`, `data`는 `{available, reason}`이다. 인증 주체는 `@AuthenticationPrincipal UUID userId`로 받아 그대로 넘긴다(null 가능). `SecurityConfig`가 이 경로를 `HttpMethod.GET`으로만 정확히 지정해 연다.
   - 검증: `grep -n "nickname-availability" $WT/snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java` → 한 줄, 같은 줄에 `HttpMethod.GET`
   - 검증: `UserControllerTest`(`@WebMvcTest`, auth 미import, `addFilters = false`, `UserQueryService` mock)가 다음을 단정한다.
     - 서비스가 `available=true`를 돌려주면 200, `data.available=true`, `data.reason`이 없거나 null
     - 서비스가 `DUPLICATED`를 돌려주면 200, `data.available=false`, `data.reason="DUPLICATED"`
     - `SecurityContextHolder`에 userId를 넣고 부르면 서비스가 그 userId로 호출되고, 넣지 않으면 null로 호출된다
   - 검증: 실제 `SecurityConfig`·`JwtAuthenticationFilter`·`JwtAuthenticationEntryPoint`를 올리고 `TokenPort`를 mock(파싱 시 `BusinessException`)으로 둔 테스트가 다음을 단정한다(위치는 `## 참고` 제안).
     - 토큰 없이 부르면 401이 아니라 200
     - `Authorization: Bearer <틀린 토큰>`으로 부르면 401이 아니라 200이고, 서비스에 userId null이 넘어간다
     - 같은 경로를 `POST` 등 다른 메서드로 토큰 없이 부르면 401(경로만이 아니라 메서드까지 지정해 열었는지)
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserControllerTest'` 및 위 보안 테스트 클래스 → BUILD SUCCESSFUL, 실패 0

7. 문서화: 컨트롤러 메서드에 `@Operation(summary, description)`, `@Parameter`(nickname), 응답 코드별 `@ApiResponses`(200, 400 `INVALID_REQUEST` 파라미터 누락)가 있고, description이 토큰이 선택이며 있으면 본인 현재 닉네임을 사용 가능으로 본다는 것, 틀린 토큰은 익명으로 처리한다는 것, 결과가 안내용이라 가입·수정이 다시 검사한다는 것, `DUPLICATED`가 사용 중·보호 중을 구분하지 않는다는 것을 적는다. 응답 DTO 필드에 `@Schema(description, example)`이 있다. `docs/openapi.yaml`을 다시 만든다.
   - 검증: `cd $WT/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'` 실행 후 `cd $WT/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `grep -n "nickname-availability" $WT/docs/openapi.yaml` → 한 줄 이상
   - 검증: `cd $WT && git diff 68adb84 -- docs/openapi.yaml | grep '^-[^-]'` → 출력 없음 또는 이번 API로 생긴 줄 정렬 변화만(기존 엔드포인트 정의가 지워지지 않는다)

8. 전체 빌드와 아키텍처 규칙이 통과한다.
   - 검증: `cd $WT/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0. `ArchitectureTest` 포함
   - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다
   - 끝내지 않는 경우: DB가 꺼져 DB 테스트가 실패하거나 컨텍스트가 안 떴을 때(환경 문제로 보고), 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컴파일이 깨져 테스트가 돌지 않았을 때

9. 범위 밖 파일을 고치지 않는다.
   - 검증: `cd $WT && git status --short` → 모든 경로가 워크트리 안이고, 바뀐 main 코드는 `snsg/src/main/java/com/shineunsigan/snsg/user/**`, `snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java`, `snsg/src/main/resources/db/migration/V8__*.sql` 뿐이다. 그 밖에는 `snsg/src/test/**`, `docs/openapi.yaml`, `.claude/tasks/SHI-168/task.md`만

## 비목표

- 이력을 쓰는 코드(닉네임 변경 T-03, 탈퇴 T-05)와 이력 JPA 엔티티·정적 팩토리는 만들지 않는다. `UserCommandService.withdraw`·`User.withdraw`는 건드리지 않는다.
- `nickname-protect-after-change`·`nickname-protect-after-withdrawal` 설정(명세 설정 절)은 이력을 쓰는 T-03·T-05에서 더한다. 이번에 설정 record·`.env`를 고치지 않는다.
- 닉네임 수정 API(T-03)와 `GET /api/v1/users/me`(API-U02)는 만들지 않는다.
- auth `SignUpRequest`의 닉네임 정규식·문구, `CompleteSignUpFacade`는 고치지 않는다. 가입 경로의 중복 판정은 user `UserCommandService` 안에서만 바꾼다.
- `nickname` 파라미터 누락용 새 에러 코드를 만들지 않는다(기존 400 `INVALID_REQUEST`). 이 프레임워크 동작은 테스트하지 않는다(`.claude/rules/testing.md`).
- `GlobalExceptionHandler`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`는 고치지 않는다.
- `.claude/rules/api.md`의 "공개 경로에서는 `@AuthenticationPrincipal`을 쓰지 않는다" 문장과 이번 선택 인증의 충돌은 코드로 해결하지 않는다. refine-docs 단계가 보완한다(`## 참고`). `docs/api-guide.md` 갱신도 refine-docs 단계 몫이다.
- 기존 테스트의 단정을 바꾸지 않는다. 단 `existsByNickname` 호출을 `isNicknameTaken`으로 바꾸는 것은 허용한다(GOAL 2·3).
- 만약 V8 번호가 다른 브랜치의 머지로 겹치면 이 브랜치의 번호를 올린다(`.claude/rules/package-structure.md` 마이그레이션 절). 지금은 겹치지 않는다.

## 제약

- 패키지 구조·의존 방향·DTO·마이그레이션은 `.claude/rules/package-structure.md`를 따른다.
- API·문서화·SecurityConfig 공개 경로는 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 읽는다. 테스트는 원래 코드와 별도 커밋 대상이다.
- 브랜치·커밋은 `.claude/rules/git-workflow.md`를 따른다. 커밋하지 않고 스테이징까지만 한다. `docs/openapi.yaml`은 API 코드와 같은 커밋 대상이다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 워크트리 안에 만들지 않는다.
- Spring Boot 4·Jackson 3 함정은 CLAUDE.md를 본다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 제안(바꿔도 된다. 결과가 GOAL을 만족하면 된다)

- 마이그레이션 이름 예: `V8__create_user_nickname_history.sql`. 첫 줄 주석으로 용도(변경 전 14일·탈퇴 90일 보호, 마지막 변경 시각 판정)를 적는 기존 스타일.
- native 쿼리 예(UserJpaRepository):
  ```sql
  SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(nickname) = LOWER(:nickname) AND is_deleted = FALSE)
      OR EXISTS (SELECT 1 FROM user_nickname_histories
                 WHERE LOWER(nickname) = LOWER(:nickname) AND reserved_until > :now AND is_deleted = FALSE)
  ```
- 형식 재사용 예: `Profile`에 `public static boolean isValidNickname(String)`을 두고 private `validateNickname`이 그것을 쓰게 한다. 기존 `NICKNAME` Pattern을 그대로 쓴다.
- 이름 예: enum `NicknameUnavailableReason`(user `domain/constants`), 결과 `NicknameAvailabilityResult`(user `application/dto/result`), 응답 `NicknameAvailabilityResponse`(user `presentation/dto/response`), 서비스 메서드 `checkNicknameAvailability(String nickname, UUID userId)`. 파라미터가 하나이고 제약이 없으므로 Request DTO 없이 `@RequestParam String nickname`(필수)으로 받는다(api.md Request DTO 기준).
- `UserQueryService`는 이미 `UserRepository`를 의존한다. 새 의존은 필요 없다. 클래스 레벨 `@Transactional(readOnly = true)`가 이미 있다.
- 보안 테스트 위치 제안: auth 패키지의 `SecurityConfigTest`(`auth/infrastructure/security`). `@WebMvcTest(controllers = UserController.class)` + `@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, <CorsConfigurationSource 제공 설정>})`, `@MockitoBean TokenPort`·`UserQueryService`. testing.md "인가 규칙과 401 형식은 auth에서 `@Import`로 `SecurityConfig`와 인증 진입점을 넣고, 토큰 검증 adapter를 `@MockitoBean`으로 대체해 검증한다"를 따른 것이다. 테스트 코드는 ArchUnit 검사 대상이 아니라 user 컨트롤러를 import해도 된다. 지금까지 이 방식의 테스트가 저장소에 없으므로 처음 만드는 것이다.
- springdoc 보안 표시: 이 API는 토큰이 선택이다. `@SecurityRequirements`(빈 값)로 전역 bearer 요구를 지우면 swagger-ui가 토큰을 보내지 않아 본인 예외를 시험할 수 없다. 전역 요구를 그대로 두고 description에 "토큰은 선택"이라 적는 쪽을 제안한다. 어느 쪽이든 description에 선택 인증을 적는 것이 GOAL 7이다.
- `@AuthenticationPrincipal UUID userId`는 익명일 때 principal이 `"anonymousUser"` 문자열이라 타입이 맞지 않아 예외 없이 null이 들어온다(api.md 문장의 근거). 이번 API는 이 null을 "익명"으로 그대로 쓴다.
- 탈퇴한 회원의 닉네임은 `Profile.anonymized()`로 `탈퇴한 사용자`가 되고 `is_deleted=true`다. 그래서 탈퇴 회원의 원래 닉네임 보호는 T-05가 쓸 `WITHDRAWN` 이력이 맡는다. 이번 테스트의 WITHDRAWN 이력은 native INSERT로 흉내 낸다. 이력 `user_id`는 FK라 실제 users 행(탈퇴 처리된 행이어도 된다)을 가리켜야 한다.
- 이력 테스트의 `now`는 테스트가 정한 고정 시각을 넘기고 `reserved_until`을 그 앞뒤로 넣으면 시계에 흔들리지 않는다.
- refine-docs 단계에 넘길 것: `.claude/rules/api.md`의 "공개 경로에서는 principal이 null이 들어오므로 쓰지 않는다" 문장을 선택 인증(공개지만 토큰이 있으면 응답이 달라지는 API) 예외로 보완한다. `docs/api-guide.md`에 가입·프로필 수정 화면에서 닉네임 칸을 벗어날 때 이 API를 부르는 흐름과 결과가 안내용이라는 것을 더한다.

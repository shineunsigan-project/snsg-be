## 배경

- Linear SHI-177 "탈퇴 가능 검사와 탈퇴 흐름 이동". 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖)의 T-18(auth, schedule, user)이다. 화면은 MY-04(회원 탈퇴).
- 명세 근거
  - POL-A5 탈퇴 가능 조건: 내가 확정 참여(개설 포함)한 일정 중 `status ≠ CANCELED`이고 `start_at + 24h > now`인 것이 하나라도 있으면 탈퇴할 수 없다. 탈퇴 가능 시각 = 그런 일정 중 가장 늦은 `start_at + 24h`. 판정과 계산은 schedule(`CheckWithdrawableUseCase`)이 한다.
  - POL-A6 탈퇴 처리: POL-A5 확인 → user 탈퇴(구현됨) → 커밋 후 이벤트로 전체 세션 삭제·카카오 연결 끊기(구현됨). 확인과 탈퇴 사이에 참여하는 경합은 허용한다.
  - POL-A7: 카카오 연결 끊기 통지(`/webhooks/kakao/unlink`)로 오는 탈퇴는 POL-A5를 검사하지 않는다. 남은 일정은 그대로 둔다(C8).
  - 도메인 의존(명세 3.2): 탈퇴는 user의 일인데 확인할 데이터는 schedule에 있다. user가 schedule을 부르면 schedule → user와 순환이 된다. 그래서 데이터가 없는 auth가 흐름을 조합한다(auth → schedule 신규, auth → user 기존). 경로 `DELETE /api/v1/users/me`는 FE 문서에 나가 있으므로 그대로 두고 핸들러만 auth의 새 컨트롤러로 옮긴다. `UserController.withdraw`는 지운다.
  - API-A07 `GET /api/v1/users/me/withdrawal-eligibility`: 인증 필요. 응답 `withdrawable`(boolean), `availableAt`(탈퇴 가능해지는 시각, withdrawable이면 null). MY-04에서 [회원 탈퇴]를 눌렀을 때 불가 팝업과 재확인 팝업 중 무엇을 띄울지 정한다.
  - API-A08 `DELETE /api/v1/users/me`: 인증 필요. POL-A5 위반 시 409 `WITHDRAWAL_BLOCKED`. 성공 시 refresh 쿠키 만료(쿠키 Path가 `/api/v1/auth`라 이 경로로는 쿠키가 오지 않지만, 같은 Path로 수명 0 쿠키를 내려 지울 수 있다).
  - 오류 코드: auth `WITHDRAWAL_BLOCKED`, `CONFLICT`, 메시지는 MY-04 문구.
  - 설정: `schedule.withdrawal-block-after-start: 24h`. schedule이 계산해 auth에 "가능 시각"으로 돌려주므로 schedule 설정에 둔다(명세 설정 절).
  - 명세의 완료 조건
    - 시작 24시간 이내 일정이 있으면 409, availableAt 정확(가장 늦은 start_at + 24h)
    - 취소된 일정만 있으면 탈퇴 가능
    - 참여 취소한 일정은 무시
    - 개설한 일정도 검사
    - 완료 12~24시간 사이 일정은 거부
    - 탈퇴 성공 시 refresh 쿠키 만료 헤더
    - 웹훅 탈퇴는 일정이 있어도 진행
- 코드에서 확인한 현재 동작(베이스 `736c6d7`)
  - `UserController.withdraw`(`DELETE /api/v1/users/me`)가 `UserWithdrawalCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED)`를 직접 부른다. 쿠키는 건드리지 않고, 문서는 "탈퇴 직후 로그아웃 API를 한 번 호출해 쿠키를 비운다"고 안내한다.
  - `WithdrawUserUseCase`(user `port/in`)에는 `withdrawBySocialAccount`만 있다. 구현은 `UserWithdrawalCommandService`다.
  - auth에는 `UserPort`·`UserAdapter`(`auth/infrastructure/client`)가 있다. 웹훅 탈퇴는 `HandleSocialUnlinkFacade` → `UserPort.withdrawBySocialAccount`다.
  - schedule에는 `application/port/in` 패키지가 아직 없다. 일정을 개설하면 개설자도 참여 행(`HOST`, `CONFIRMED`)을 가진다(`Schedule`의 `ScheduleParticipant.host`). 참여 행 상태는 `CONFIRMED`·`CANCELED`, 일정 상태는 `SCHEDULED`·`CANCELED`·`COMPLETED`다.
  - `ScheduleProperties`(record, prefix `schedule`)를 `ScheduleQueryServiceTest`·`ScheduleCommandServiceTest`가 생성자로 만든다.
  - `SecurityConfig`는 `anyRequest().authenticated()`라 두 경로는 지금 설정 그대로 인증이 필요하다. 공개 경로를 더하지 않는다.
  - `AuthCookieFactory.expired(AuthCookieFactory.REFRESH_TOKEN)`이 같은 이름·Path(`/api/v1/auth`)·수명 0 쿠키를 만든다. 로그아웃(`AuthController.logout`)이 이것을 `Set-Cookie`로 내린다.
  - **명세와 다른 점: 두 번째 탈퇴 요청은 지금 `ALREADY_WITHDRAWN`(409)이 아니라 `USER_NOT_FOUND`(404)다.** `UserRepositoryImpl.findById`가 삭제되지 않은 행만 읽어 `User.withdraw()`의 `ALREADY_WITHDRAWN`에 닿지 않는다.
- 결정(권장안 적용. 사용자 승인 단계에서 확인받는다)
  1. **`WITHDRAWAL_BLOCKED` 메시지는 "모든 일정 취소 혹은 진행 중 이후 24시간이 지나야 탈퇴할 수 있어요."다.** 화면 명세 MY-04 문구("모든 일정 취소 혹은 진행 중 이후 24시간이 지나야 가능", `planning/requirements.md` 10절)를 그대로 쓰고, 기존 메시지 말투(`~어요.`)로 문장을 맺었다.
  2. **두 번째 탈퇴 요청은 지금 동작대로 404 `USER_NOT_FOUND`로 둔다.** user 코드를 바꾸지 않는다. 다른 `/api/v1/users/me` API(조회·수정)도 탈퇴한 사용자에게 같은 코드를 내고, FE는 둘 다 로그인 화면으로 보낸다. 문서에는 실제 코드(404 `USER_NOT_FOUND`)를 적는다. 명세는 저장소 밖이라 이 작업에서 고치지 않는다.
  3. **탈퇴 흐름은 명세 순서대로 "탈퇴 가능 확인 → user 탈퇴"이고, 그 앞에 사용자 활성 확인을 따로 두지 않는다.** 그래서 이미 탈퇴한 사용자가 남은 access token(최대 30분)으로 부르면, 막는 일정이 남아 있을 때(웹훅 탈퇴는 일정을 남긴다) 409 `WITHDRAWAL_BLOCKED`, 없으면 404 `USER_NOT_FOUND`다. 탈퇴 가능 확인 API는 탈퇴한 사용자에게도 계산 결과를 200으로 준다. 활성 확인을 더하려면 auth에 오류 코드를 새로 만들어야 하는데 이 경우는 30분 동안 남은 토큰뿐이라 두지 않는다.
  4. **409 응답에 `availableAt`을 싣지 않는다.** 오류 봉투에는 `data`가 없다(`.claude/rules/api.md` 응답과 예외 절). FE는 탈퇴 가능 확인 API로 시각을 받는다.
  5. 설정 키는 명세대로 `schedule.withdrawal-block-after-start: 24h`이고 `ScheduleProperties`에 더한다. `schedule`은 흔한 OS 환경변수 이름이 아니다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-177` (브랜치 `feat/SHI-177-withdrawal-eligibility`, 베이스 `736c6d7` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리(`wt-SHI-176`, 일정 상세) 파일은 고치지 않는다.
- `snsg/.env`는 이미 복사돼 있다. 새 설정은 `application.yaml`에 값으로 두므로 `.env`를 고치지 않는다.
- **마이그레이션이 없는 작업이다.** develop의 마이그레이션은 V1~V9다. 새 파일을 더하지 않는다.
- 셸은 PowerShell(5.1)이 기본이고 Bash도 쓸 수 있다. PowerShell에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").
  - **지금 `wt-SHI-176`이 공유 DB로 작업 중이다. 테스트는 오케스트레이터가 실행해도 된다고 알린 뒤에만 돌린다.** 두 워크트리에서 테스트를 동시에 돌리지 않는다.
  - DB·Redis가 꺼져 있으면 테스트가 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다.
  - 다른 워크트리가 머지 전 마이그레이션을 공유 DB에 적용해 Flyway 검증이 어긋나면 멈춰서 보고한다.
- 테스트의 Hikari 풀은 3이다(`.claude/rules/testing.md` DB 연결 수 항목). DB 슬라이스 구성(`@Import` 조합)을 새로 더했으면 `./gradlew cleanTest build`로 전체를 다시 돌린다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통 규칙:
- "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`).
- 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.
- 아래 검증 명령의 `WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-177`다.

1. **schedule이 POL-A5 판정을 `port/in` `CheckWithdrawableUseCase`로 제공한다.**
   - `schedule/application/port/in`에 `CheckWithdrawableUseCase`가 있고 `ScheduleQueryService`가 구현한다. userId를 받아 탈퇴 가능 여부와 탈퇴 가능 시각을 돌려준다(Result record).
   - 막는 일정: 그 사용자의 참여 행이 `CONFIRMED`이고 삭제되지 않았으며(개설자의 `HOST` 행 포함), 일정이 삭제되지 않았고 `status ≠ CANCELED`(`SCHEDULED`·`COMPLETED` 모두)이며 `start_at + 기간 > now`인 것. 기간은 `schedule.withdrawal-block-after-start`다.
   - 막는 일정이 없으면 탈퇴 가능, 가능 시각은 null. 있으면 탈퇴 불가, 가능 시각 = 막는 일정 중 가장 늦은 `start_at` + 기간.
   - `start_at + 기간 = now`(정확히 경계)는 막지 않는다.
   - 검증: `grep -n "withdrawal-block-after-start" WT/snsg/src/main/resources/application.yaml` → `withdrawal-block-after-start: 24h` 한 줄(`schedule:` 블록 아래)
   - 검증: `ls WT/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/port/in/` → `CheckWithdrawableUseCase.java`가 있다. `grep -n "implements" WT/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/ScheduleQueryService.java` → `CheckWithdrawableUseCase`가 들어 있다
   - 검증: 실제 PostgreSQL 테스트(`ScheduleRepositoryImplTest` 등 repository 테스트). 테스트마다 무작위 사용자 id로 행을 넣고 그 사용자로 조회하며, `now`는 인자로 넘긴 고정 시각이다. 아래 경우를 각각 단정한다.
     - 참여(MEMBER)한 `SCHEDULED` 일정, `start_at` = now + 1일 → 막는다
     - 개설(HOST)만 한 일정 → 막는다
     - `CANCELED` 일정만 있음 → 막는 일정 없음
     - 참여 행이 `CANCELED`(참여 취소)인 일정만 있음 → 막는 일정 없음
     - `COMPLETED` 일정, `start_at` = now − 18시간(완료 12~24시간 사이) → 막는다
     - `start_at` = now − 24시간(경계) → 막지 않는다. now − 24시간 + 1분 → 막는다
     - 막는 일정 둘 이상 → 가장 늦은 `start_at`을 기준으로 한다
   - 검증: `ScheduleQueryServiceTest`(Mockito)가 막는 일정이 없을 때 `withdrawable = true`·`availableAt = null`, 있을 때 `withdrawable = false`·`availableAt` = 가장 늦은 `start_at` + 설정 기간을 단정한다.

2. **user `WithdrawUserUseCase`에 사용자 요청 탈퇴 `withdraw(userId)`가 있다.**
   - 이유는 `USER_REQUESTED`다. 기존 탈퇴 처리(보관형 탈퇴, 이벤트)를 그대로 탄다.
   - 검증: `grep -n "void withdraw" WT/snsg/src/main/java/com/shineunsigan/snsg/user/application/port/in/WithdrawUserUseCase.java` → `withdraw(UUID userId)`와 `withdrawBySocialAccount` 두 줄
   - 검증: `UserWithdrawalCommandServiceTest`가 `withdraw(userId)`로 탈퇴한 회원의 탈퇴 기록 사유가 `USER_REQUESTED`임을 단정한다.

3. **auth가 탈퇴 흐름을 조합한다.**
   - auth `port/out`에 schedule 탈퇴 가능 확인을 부르는 포트가 있고, `infrastructure`의 adapter만 schedule `CheckWithdrawableUseCase`를 import한다. auth `UserPort`에 사용자 요청 탈퇴가 더해진다.
   - `CheckWithdrawalFacade`: 탈퇴 가능 여부와 가능 시각을 돌려준다.
   - `WithdrawFacade`: 탈퇴 불가면 `BusinessException(AuthErrorCode.WITHDRAWAL_BLOCKED)`를 던지고 user 탈퇴를 부르지 않는다. 가능하면 user 탈퇴를 부른다. 트랜잭션을 걸지 않는다.
   - `AuthErrorCode.WITHDRAWAL_BLOCKED`는 `ErrorType.CONFLICT`, 메시지 "모든 일정 취소 혹은 진행 중 이후 24시간이 지나야 탈퇴할 수 있어요."다(배경 결정 1).
   - 검증: `grep -n "WITHDRAWAL_BLOCKED" WT/snsg/src/main/java/com/shineunsigan/snsg/auth/domain/exception/AuthErrorCode.java` → `ErrorType.CONFLICT`와 위 메시지가 있는 한 줄
   - 검증: `grep -rln "schedule\." WT/snsg/src/main/java/com/shineunsigan/snsg/auth/` → `auth/infrastructure/` 아래 파일만 나온다
   - 검증: `WithdrawFacadeTest`(Mockito)가 (a) 탈퇴 불가면 `WITHDRAWAL_BLOCKED`이고 user 탈퇴 포트가 호출되지 않음, (b) 가능하면 user 탈퇴 포트가 그 userId로 한 번 호출됨을 단정한다.

4. **웹훅 탈퇴(POL-A7)는 탈퇴 가능 확인을 하지 않는다.**
   - 검증: `cd WT && git diff --staged --name-only -- snsg/src/main/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacade.java` → 출력 없음
   - 검증: `grep -n "Schedule" WT/snsg/src/main/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacade.java` → 출력 없음

5. **API-A07·A08 핸들러가 auth 컨트롤러에 있고, `UserController.withdraw`는 없다.**
   - `GET /api/v1/users/me/withdrawal-eligibility` → 200, `data`는 `{withdrawable, availableAt}`. 탈퇴 가능하면 `availableAt`은 `null`이다(JSON에 `"availableAt":null`로 나간다).
   - `DELETE /api/v1/users/me` → 성공 200 `{"isSuccess":true}`와 refresh 쿠키 만료 `Set-Cookie`(이름 `refreshToken`, `Path=/api/v1/auth`, `Max-Age=0`). 탈퇴 불가면 409 `WITHDRAWAL_BLOCKED`이고 `Set-Cookie`가 없다.
   - 검증: `grep -rn "DeleteMapping" WT/snsg/src/main/java/com/shineunsigan/snsg/user/presentation/` → 출력 없음
   - 검증: `grep -rln "withdrawal-eligibility" WT/snsg/src/main/java/com/shineunsigan/snsg/auth/presentation/` → 컨트롤러 파일 하나
   - 검증: auth 탈퇴 컨트롤러의 `@WebMvcTest` 테스트가 위 세 응답(탈퇴 가능 확인 두 경우 중 하나 이상의 `availableAt` 값, 탈퇴 성공의 `Set-Cookie` 이름·`Path`·`Max-Age=0`, 탈퇴 불가의 409와 code)을 단정한다.

6. **두 엔드포인트는 토큰 없이 부르면 401이다.**
   - 검증: `SecurityConfigTest`가 토큰 없는 `GET /api/v1/users/me/withdrawal-eligibility`와 `DELETE /api/v1/users/me`의 401을 단정한다.

7. **문서가 바뀐 API와 맞는다.**
   - `docs/openapi.yaml`을 `OPENAPI_UPDATE=true`로 다시 만든다. 두 엔드포인트의 설명에 `.claude/rules/api.md` 문서 절의 항목(화면·호출 시점, 응답 필드와 null의 뜻, 에러별 상태·code·조건·화면에서 할 일, 인증, 재시도)이 있다. 탈퇴 에러에는 409 `WITHDRAWAL_BLOCKED`, 404 `USER_NOT_FOUND`(이미 탈퇴함, 배경 결정 2), 401이 있다.
   - `docs/api-guide.md`의 탈퇴 절과 화면별 API 표가 새 흐름(탈퇴 가능 확인 → 재확인 → 탈퇴)과 맞고, "탈퇴 직후 로그아웃을 호출해 쿠키를 비운다" 안내가 없다.
   - 검증: `cd WT/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `grep -n "withdrawal-eligibility\|WITHDRAWAL_BLOCKED" WT/docs/openapi.yaml WT/docs/api-guide.md` → 두 파일 모두에서 나온다
   - 검증: `grep -n "탈퇴 직후" WT/docs/api-guide.md WT/docs/openapi.yaml` → 로그아웃 호출 안내가 나오지 않는다

8. **패키지 규칙과 전체 빌드가 통과한다.**
   - 검증: `cd WT/snsg && ./gradlew cleanTest test --tests '*ArchitectureTest'` → BUILD SUCCESSFUL
   - 검증: `cd WT/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0

9. **범위 밖 파일을 고치지 않는다.**
   - 검증: `cd WT && git diff --staged --name-only` → 모든 경로가 `snsg/src/main/java/com/shineunsigan/snsg/{auth,schedule,user}/`, `snsg/src/test/java/com/shineunsigan/snsg/{auth,schedule,user}/`, `snsg/src/main/resources/application.yaml`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/tasks/SHI-177/task.md` 중 하나다(task.md는 오케스트레이터가 허용 목록에 더했다. 파이프라인이 커밋하는 파일이다). `db/migration`, `place/`, `common/`, `.env`는 없다

## 비목표

- 마이그레이션을 더하지 않는다. 기존 인덱스로 조회한다.
- user 탈퇴 처리(`UserWithdrawalCommandService.withdraw(UUID, WithdrawReason)`의 본문, 이벤트, 비식별)를 바꾸지 않는다. `UserRepositoryImpl.findById`를 바꿔 두 번째 요청을 `ALREADY_WITHDRAWN`으로 만들지 않는다(배경 결정 2).
- `HandleSocialUnlinkFacade`, `KakaoWebhookController`, `UserWithdrawnEventListener`를 고치지 않는다.
- `SecurityConfig`의 규칙을 바꾸지 않는다. 두 경로는 지금 설정으로 인증이 필요하다. `SecurityConfigTest`에 대상 컨트롤러와 `@MockitoBean`을 더하는 것은 GOAL 6에 필요한 변경이다.
- C8(웹훅 탈퇴 사용자의 남은 일정 정리)은 하지 않는다. 일정은 그대로 남는다.
- 탈퇴한 사용자를 막는 활성 확인을 탈퇴 흐름 앞에 더하지 않는다(배경 결정 3). 409 응답에 `availableAt`을 싣지 않는다(배경 결정 4).
- `docs/api-guide.md`의 공통 에러 표에 있는 `ALREADY_WITHDRAWN` 행은 이번에 고치지 않는다. 탈퇴 절과 화면별 표만 고친다.
- 기존 테스트의 단정을 바꾸지 않는다. 예외:
  - `ScheduleProperties`에 값이 더해져 생성자로 만드는 테스트(`ScheduleQueryServiceTest`·`ScheduleCommandServiceTest`)는 인자만 더한다.
  - `UserController`가 `UserWithdrawalCommandService`를 더 이상 쓰지 않으면, 그 때문에 쓸모없어진 `@MockitoBean`(`UserControllerTest`·`SecurityConfigTest`)은 지운다.
- `wt-SHI-176`과 겹치는 파일(`ScheduleQueryService`, `ScheduleRepository`·`Impl`·`JpaRepository`, `SecurityConfigTest`, 문서)이 있을 수 있다. 앞 작업이 먼저 머지되면 최신 origin/develop 위로 rebase하고 양쪽 변경을 모두 살려 충돌을 푼 뒤 GOAL 8을 다시 돌린다.

## 제약

- 패키지 구조·facade·포트·도메인 간 참조·설정은 `.claude/rules/package-structure.md`를 따른다(특히 Service와 Facade 절, Port 절의 adapter 빈 이름 충돌, 도메인 간 참조 절, 설정 절).
- 컨트롤러·응답·문서는 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 테스트는 원래 코드와 별도 커밋 대상이다(스테이징까지만 한다).
- `docs/openapi.yaml`은 API를 바꾼 코드와 같은 커밋에 들어가야 한다(`.claude/rules/api.md` 문서 절).
- 임시 파일은 세션 스크래치패드에 두고 워크트리에 만들지 않는다(`.claude/rules/temp-files.md`).
- 커밋·push·PR은 하지 않는다. 스테이징까지만 한다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 구현 제안(바꿔도 된다)
  - schedule: repository에 "막는 일정 중 가장 늦은 `start_at`"을 돌려주는 메서드(`Optional<LocalDateTime>`, 인자 userId·now·기간). JPQL은 `existsConfirmedScheduledBetween`처럼 참여 행(`user_id, status` 인덱스)에서 출발해 일정과 조인하고 `max(s.startAt)`을 읽는다. 조건은 `s.startAt > now − 기간`. 가능 시각 계산(+ 기간)은 `ScheduleQueryService`에서 한다.
  - Result: `schedule/application/dto/result/WithdrawalEligibilityResult(boolean withdrawable, LocalDateTime availableAt)`. auth adapter에서 auth 쪽 Result로 바꾼다.
  - auth: `port/out/SchedulePort`, adapter `infrastructure/client/AuthScheduleAdapter` 또는 `ScheduleAdapter`. adapter는 Spring 빈이므로 단순 클래스명이 다른 도메인 클래스와 겹치면 기동이 실패한다(package-structure Port 절). schedule에는 지금 `ScheduleAdapter`라는 클래스가 없다.
  - `UserPort.withdraw(UUID userId)` → `UserAdapter`가 `WithdrawUserUseCase.withdraw(userId)`를 부른다. `UserWithdrawalCommandService`는 기존 `withdraw(userId, reason)`에 `USER_REQUESTED`로 넘기면 된다. `WithdrawUserUseCase`의 클래스 javadoc("소셜 쪽에서 연동을 끊었다는 통지를 auth가 받아 호출한다")도 두 입구에 맞게 고친다.
  - 컨트롤러: `auth/presentation/WithdrawalController`(`@RequestMapping("/api/v1/users/me")`). 경로가 `/api/v1/users` 아래라 FE가 찾기 쉽도록 `@Tag(name = "사용자")`를 그대로 쓰는 것을 제안한다. 응답 DTO는 `auth/presentation/dto/response/WithdrawalEligibilityResponse`. 쿠키는 `AuthCookieFactory.expired(AuthCookieFactory.REFRESH_TOKEN)`.
  - 테스트 이름: `CheckWithdrawalFacade`는 결과를 그대로 돌려주기만 하면 테스트하지 않는다(testing.md "단순 위임"). auth schedule adapter도 필드를 옮기기만 하면 테스트하지 않는다.
- 탈퇴 가능 확인과 탈퇴 사이에 참여하는 경합은 허용한다(POL-A6). 탈퇴 뒤 그 참여는 "탈퇴한 사용자"로 보이고, 남은 access token으로 하는 참여·개설은 POL-S9(`USER_NOT_ACTIVE`)가 막는다. 잠금을 더하지 않는다.
- 시간 값은 오프셋 없는 `LocalDateTime`(Asia/Seoul)이다(CLAUDE.md).
- 탈퇴 성공 뒤의 세션 전체 삭제·카카오 연결 끊기는 기존 `UserWithdrawnEvent` 리스너가 그대로 한다.
- 이 작업이 끝나면 refine-docs 단계가 `.claude/rules/package-structure.md`의 도메인 간 의존 예(auth → schedule)와 `.claude/rules/testing.md`의 `SecurityConfigTest` 대상 컨트롤러 목록을 갱신할 수 있다. 구현 단계에서 rules를 고치지 않는다.

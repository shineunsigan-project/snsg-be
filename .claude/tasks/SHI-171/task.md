## 배경

- Linear SHI-171. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-13 "참여와 참여 취소 (schedule)"를 구현한다. 화면은 DTL-03([참여하기]), DTL-07(참여 취소 [확인]), DTL-08(모집 마감). API 번호는 API-S09(참여), API-S10(참여 취소)이다.
- 선행 T-10(일정 개설, PR #47: `Schedule`·`ScheduleParticipant`, `ScheduleCommandService.create`, `CreateScheduleFacade`, 사용자 advisory lock `ScheduleRepository.lockUser`, 겹침 쿼리 `existsOverlapping`, V7)과 T-11(PR #49)이 develop에 머지돼 있다. 이번 작업은 그 위에 참여·참여 취소를 더한다. 이미 있는 잠금·겹침 쿼리·설정(`ScheduleProperties.completionAfter`·`overlapWindow`)·테스트 구성을 다시 만들지 않고 쓴다.
- 명세 완료 조건: 정원 도달 시 CLOSED / 마지막 자리 동시 요청 2개 중 1개만 성공(동시성 테스트) / 취소 후 재참여 시 새 행, 두 번 확정은 인덱스가 거부 / 시작 전 취소로 RECRUITING 복귀, 시작 후 취소는 CLOSED 유지 / 완료 후 취소 PARTICIPATION_NOT_CANCELABLE / 개설자 참여·취소 거부 / 시작 지났는데 아직 RECRUITING이면 RECRUIT_CLOSED.
- 근거 규칙(명세): POL-S8(겹침), POL-S9(활성 사용자), POL-S10(참여 조건), POL-S11(① 사용자 advisory lock, ② 일정 행 `SELECT … FOR UPDATE`, 잠금 순서 사용자 → 일정), POL-S12(참여 취소), POL-S16(삭제된 일정 404), 4.2 상태 전이, 4.5 버튼 규칙(JOIN은 displayStatus = RECRUITING, CANCEL_PARTICIPATION은 displayStatus ∈ {RECRUITING, RECRUITMENT_CLOSED}), 9장 schedule 오류 코드, 3.4(참여 응답이 상세를 돌려주지 않는 이유: facade는 다른 facade를 부르지 않는다).

### 결정

사용자 위임("권장안대로 진행")에 따라 명세·코드로 답이 나오지 않는 지점은 아래 권장안을 채택했다.

1. **참여 오류 판정 순서**: (facade) `USER_NOT_ACTIVE` → (서비스) `SCHEDULE_NOT_FOUND` → `HOST_CANNOT_JOIN` → `ALREADY_JOINED` → `RECRUIT_CLOSED` → `SCHEDULE_TIME_CONFLICT`. (권장안 채택)
   - 근거: 명세 API-S09 "두 번 눌렸을 때 두 번째는 ALREADY_JOINED". 첫 요청이 마지막 자리를 채워 CLOSED가 된 뒤에도 두 번째는 ALREADY_JOINED여야 하므로 ALREADY_JOINED가 RECRUIT_CLOSED보다 앞이다. 겹침 쿼리는 내 확정 행의 일정을 보므로 개설자·이미 참여한 사용자는 대상 일정 자신과 겹친다. 그래서 개설자·이미 참여 판정이 겹침보다 앞이다. 모집이 끝난 일정에 "시간이 겹친다"고 안내하지 않도록 RECRUIT_CLOSED도 겹침보다 앞이다. POL-S9는 명세대로 facade에서 트랜잭션 밖에 먼저 본다.
2. **참여 취소 오류 판정 순서**: `SCHEDULE_NOT_FOUND` → `HOST_CANNOT_LEAVE` → `NOT_PARTICIPANT` → `PARTICIPATION_NOT_CANCELABLE`. (권장안 채택)
   - 근거: 명세 API-S10 "NOT_PARTICIPANT(이미 취소함 포함, 재시도 안전)". 확정 참여가 아닌 사람은 일정 상태와 관계없이 같은 코드를 받아야 재시도 판단이 한 가지로 끝난다. 참여와 같이 "누구인가"를 "일정 상태"보다 먼저 본다.
3. **참여 취소는 사용자 잠금·활성 사용자 확인을 하지 않는다.** 일정 행 잠금(POL-S11 ②)만 건다. Controller → `ScheduleCommandService` 직접 호출이고 facade를 만들지 않는다. (명세 API-S10 "Controller → CommandService(POL-S11 ②·S12)", 3.4 표. 취소는 겹침을 만들지 않으므로 사용자 직렬화가 필요 없고, 일정 잠금 하나만 잡으므로 "사용자 → 일정" 잠금 순서와 충돌하지 않는다)
4. **확정 참여 부분 유니크 인덱스(`uix_participants_confirmed`) 위반을 오류 코드로 바꾸지 않는다.** (권장안 채택)
   - 근거: user의 닉네임 인덱스(`UserRepositoryImpl.save`)는 잠금 없이 경쟁이 실제로 일어나 409로 바꾼다. 참여는 같은 일정의 요청이 일정 행 잠금으로 직렬화되고, 잠근 뒤 읽은 참여 행으로 `ALREADY_JOINED`를 판정하므로 앱 경로로는 인덱스 위반이 나지 않는다. 인덱스는 잠금이 빠지는 버그에 대한 마지막 방어선이고, 그때 500으로 드러나는 편이 409로 가려지는 것보다 낫다(CLAUDE.md "불가능한 경우의 오류 처리 금지"). 명세의 "두 번 확정은 인덱스가 거부"는 기존 `ScheduleRepositoryImplTest`의 인덱스 테스트가 확인한다.
5. **시각 판정**: 참여는 `start_at > now`일 때만 가능하다(같으면 마감). 참여 취소는 `start_at + completionAfter > now`일 때만 가능하다(같으면 완료). `completionAfter`는 기존 설정 `schedule.completion-after`(12h, `displayStatus`·JOB-02와 같은 값)를 쓴다. 새 설정을 만들지 않는다. 스케줄러(T-17) 전이라 저장된 모집 상태가 RECRUITING이어도 시각으로 판정한다(명세 4.4).
6. **시작 후 취소는 모집 상태를 바꾸지 않는다.** 시작 전이고 CLOSED였을 때만 RECRUITING으로 되돌린다(POL-S12). 시작 후인데 아직 RECRUITING(스케줄러 전)이면 그대로 둔다. 화면 상태는 `displayStatus`가 시각으로 마감을 보여주고, 저장값은 JOB-01이 마감한다.
7. **삭제된 일정(`is_deleted = true`)은 참여·참여 취소 모두 `SCHEDULE_NOT_FOUND`(404)**다(POL-S16).
8. 응답은 둘 다 **200, `data` 없음**이다(명세 API-S09·S10). 오류 문구는 `SCHEDULE_NOT_FOUND`만 명세에 있다("찾을 수 없는 일정이에요"). 나머지는 기존 문구 톤(~요)으로 짓는다.
9. 마이그레이션은 없다. `schedule_participants`와 부분 유니크 인덱스는 V7에 이미 있다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-171` (브랜치 `feat/SHI-171-schedule-participation`, 베이스 `d67041d`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다.
- 다른 워크트리가 진행 중이다: `wt-SHI-169`(user 프로필 수정), `wt-SHI-170`(auth 기기별 세션). **건드리지 않는다.** 두 작업 모두 `docs/api-guide.md`·`docs/openapi.yaml`을 고칠 수 있어 머지 순서에 따라 충돌이 날 수 있다. 이번 작업은 마이그레이션을 더하지 않으므로 공유 DB의 Flyway 충돌은 없다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V8이다. 이번 작업은 더하지 않는다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). **테스트는 한 번에 한 워크트리에서만 돌린다.** 다른 워크트리가 테스트·앱을 돌리는 중이면 기다린다(오케스트레이터에게 확인). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 로컬 DB에 붙는다. DB가 꺼져 있으면 이 테스트들이 실패한다.
- 테스트의 Hikari 풀은 3이다(`snsg/build.gradle`, `.claude/rules/testing.md` DB 연결 수 항목).
- 로컬 DB 초기 데이터: 활동 `01a19cc6-3d80-7000-8000-000000000001` BOARD_GAME(→ BOARD_GAME_CAFE). 기존 `ScheduleCommandServiceTest`가 이 고정 id를 쓴다.
- 이 단계(spec)에서는 테스트를 돌리지 않았다. 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다. 구현 전에 베이스 상태에서 실패가 보이면 고치지 말고 보고한다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 그 클래스에 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지(어떤 ErrorCode인지, 상태·인원·행이 어떻게 바뀌는지) 확인한다. 기존 테스트 메서드의 단정은 바꾸지 않는다(생성자 인자 변경처럼 컴파일을 위한 수정만 허용).

1. `ScheduleErrorCode`에 다음 코드가 더해진다. 기존 코드의 값·문구는 바뀌지 않는다.
   - `SCHEDULE_NOT_FOUND`(`ErrorType.NOT_FOUND`, 문구 "찾을 수 없는 일정이에요."), `RECRUIT_CLOSED`, `ALREADY_JOINED`, `HOST_CANNOT_JOIN`, `HOST_CANNOT_LEAVE`, `NOT_PARTICIPANT`, `PARTICIPATION_NOT_CANCELABLE`(모두 `ErrorType.CONFLICT`)
   - 검증: `grep -nE "^\s+(SCHEDULE_NOT_FOUND|RECRUIT_CLOSED|ALREADY_JOINED|HOST_CANNOT_JOIN|HOST_CANNOT_LEAVE|NOT_PARTICIPANT|PARTICIPATION_NOT_CANCELABLE)\(" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → 7줄. `SCHEDULE_NOT_FOUND` 줄에 `ErrorType.NOT_FOUND`와 "찾을 수 없는 일정이에요", 나머지 6줄에 `ErrorType.CONFLICT`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171 && git diff d67041d -- snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java | grep '^-[^-]'` → 출력 없음, 또는 마지막 기존 상수(`USER_NOT_ACTIVE`) 줄의 `;` → `,` 변경 한 줄뿐

2. 참여 규칙(POL-S10, 4.2)이 도메인(`Schedule`)에 있고 순수 JUnit으로 검증된다. 판정 순서는 배경 "결정" 1(개설자 → 이미 참여 → 모집 중 아님)이다.
   - 검증: `ScheduleTest`(Spring·mock 없음)가 다음을 단정한다.
     - 참여하면 그 사용자의 `MEMBER`·`CONFIRMED` 행이 더해지고 확정 인원이 1 늘며, 정원 전이면 RECRUITING 그대로다.
     - 참여로 확정 인원이 정원에 닿으면 모집 상태가 CLOSED가 된다.
     - 개설자가 참여하면 `HOST_CANNOT_JOIN`. 모집이 마감된 일정이어도 `HOST_CANNOT_JOIN`이다.
     - 이미 확정 참여한 사용자가 다시 참여하면 `ALREADY_JOINED`. 그 참여로 정원이 차서 CLOSED가 된 뒤에도 `ALREADY_JOINED`다(더블클릭).
     - 모집 상태가 CLOSED면 `RECRUIT_CLOSED`.
     - 저장된 모집 상태가 RECRUITING이어도 `now`가 시작 시각과 같거나 지났으면 `RECRUIT_CLOSED`(스케줄러 전).
     - 일정 상태가 CANCELED, COMPLETED면 `RECRUIT_CLOSED`.
     - 위 거부 경우 모두 행·확정 인원·모집 상태가 바뀌지 않는다.
     - 참여 → 참여 취소 → 재참여하면 그 사용자의 행이 둘이다(이전 행 CANCELED, 새 행 CONFIRMED). 확정 인원은 참여 한 번과 같다.

3. 참여 취소 규칙(POL-S12, 4.2)이 도메인(`Schedule`)에 있고 순수 JUnit으로 검증된다. 판정 순서는 배경 "결정" 2(개설자 → 확정 참여 아님 → 취소 불가 일정)다.
   - 검증: `ScheduleTest`가 다음을 단정한다.
     - 시작 전에 취소하면 행이 CANCELED, 확정 인원 −1이고, CLOSED였으면 RECRUITING으로 돌아간다.
     - 시작 후(시작 ≤ now < 시작 + 12h)에 취소하면 행이 CANCELED, 확정 인원 −1이고, CLOSED는 그대로다.
     - `now`가 시작 + 12시간과 같거나 지났으면 `PARTICIPATION_NOT_CANCELABLE`(스케줄러 전).
     - 일정 상태가 COMPLETED, CANCELED면 `PARTICIPATION_NOT_CANCELABLE`.
     - 개설자가 취소하면 `HOST_CANNOT_LEAVE`.
     - 참여한 적 없는 사용자, 이미 취소한 사용자는 `NOT_PARTICIPANT`(이미 취소한 사용자는 일정이 완료됐어도 `NOT_PARTICIPANT`).
     - 위 거부 경우 모두 행·확정 인원·모집 상태가 바뀌지 않는다.

4. `ScheduleCommandService`에 참여와 참여 취소가 더해진다(각 `@Transactional`). 의존은 기존 그대로(repository·설정)이고 `port/out`을 의존하지 않는다.
   - 참여: 사용자 잠금(`lockUser`) → 일정 행 잠금 조회(삭제되지 않은 일정, `SELECT … FOR UPDATE`) → 도메인 판정 → 겹침 검사(`existsOverlapping`, 기존 쿼리·`overlapWindow`) → 반영. 잠금 순서는 사용자 → 일정이다.
   - 참여 취소: 일정 행 잠금 조회 → 도메인 판정 → 반영. 사용자 잠금을 걸지 않는다.
   - 검증: `ScheduleCommandServiceTest`의 Mockito 흐름 테스트가 다음을 단정한다.
     - 참여: `lockUser`가 일정 잠금 조회보다 먼저, 겹침 검사가 그 뒤에 불린다(InOrder).
     - 참여: 일정이 없으면(잠금 조회가 비면) `SCHEDULE_NOT_FOUND`이고 겹침 검사를 하지 않는다.
     - 참여: 12시간 안에 확정 참여한 일정이 있으면 `SCHEDULE_TIME_CONFLICT`이고 참여 행이 더해지지 않는다.
     - 참여: 개설자·이미 참여·모집 중 아님으로 거부될 때 겹침 검사를 부르지 않는다(셋 중 하나 이상으로 단정).
     - 참여 취소: 일정이 없으면 `SCHEDULE_NOT_FOUND`. `lockUser`를 부르지 않는다.
   - 검증: `ScheduleRepositoryImplTest`(실제 PostgreSQL, 기존 구성)가 일정 잠금 조회에 대해 다음을 단정한다.
     - 삭제되지 않은 일정은 찾고, `is_deleted = true`인 일정은 비어 있다.
   - 검증: `grep -n "Port" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/ScheduleCommandService.java` → 출력 없음

5. 마지막 한 자리에 서로 다른 두 사용자가 동시에 참여하면 한 명만 성공한다(POL-S11 ②, 명세 완료 조건). 실제 PostgreSQL에서 커밋까지 거쳐 확인한다.
   - 정원 4, 확정 3(개설자 + 멤버 2)인 일정을 커밋해 두고, 두 스레드가 latch로 시작을 맞춰 서로 다른 사용자로 `ScheduleCommandService` 참여를 동시에 부른다.
   - 기대: 성공 1, 실패 1이고 실패는 `BusinessException(RECRUIT_CLOSED)`. DB의 그 일정은 `confirmed_count = 4`, `recruitment_status = 'CLOSED'`, `status = 'CONFIRMED'`인 참여 행 4개.
   - 같은 DB 구성에서 참여 → 참여 취소 → 재참여를 서비스로 거치면 그 사용자의 행이 둘(CANCELED 1, CONFIRMED 1)로 저장되고 `confirmed_count`가 맞다(명세 "취소 후 재참여 시 새 행").
   - 테스트가 커밋한 행(일정·참여 행)은 테스트가 끝나면 지운다.
   - 새 DB 슬라이스 구성을 늘리지 않는다. 기존 `ScheduleCommandServiceTest`의 `Concurrency` 중첩 클래스와 같은 애너테이션·`@Import` 구성을 쓴다(그 클래스에 메서드를 더하는 것을 권장).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg && ./gradlew cleanTest test --tests '*ScheduleCommandServiceTest' --tests '*ScheduleTest' --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: 위 명령을 연속 3회 실행해 3회 모두 통과한다(`cleanTest`가 있어 매번 실제로 돈다).
   - 검증: `grep -c 'skipped="0"' /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg/build/test-results/test/TEST-com.shineunsigan.snsg.schedule.application.ScheduleCommandServiceTest\$Concurrency.xml` → 1 (동시성 테스트가 건너뛰어지지 않았다)

6. `JoinScheduleFacade`(schedule application, 트랜잭션 없음)가 `UserPort.isActive`로 활성 사용자를 확인한 뒤 `ScheduleCommandService` 참여를 부른다.
   - 검증: `JoinScheduleFacadeTest`(Mockito)가 다음을 단정한다.
     - 탈퇴(비활성) 사용자면 `USER_NOT_ACTIVE`이고 `ScheduleCommandService`가 호출되지 않는다.
     - 활성 사용자면 `ScheduleCommandService` 참여가 요청한 일정 id·사용자 id로 호출된다.
   - 검증: `grep -n "Transactional" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/JoinScheduleFacade.java` → 출력 없음

7. API 두 개(인증 필요, 응답 200 `{"isSuccess":true}`, `data` 없음)
   - `POST /api/v1/schedules/{scheduleId}/participants`(API-S09) → `JoinScheduleFacade`
   - `DELETE /api/v1/schedules/{scheduleId}/participants/me`(API-S10) → `ScheduleCommandService`(facade 없음)
   - 인증 주체는 `@AuthenticationPrincipal UUID userId`. 요청 본문은 없다(Request DTO를 만들지 않는다).
   - `SecurityConfig`는 바꾸지 않는다(`anyRequest().authenticated()`가 두 API를 막는다).
   - 검증: `ScheduleControllerTest`(기존 설정: auth 스캔 제외, `addFilters = false`, `GlobalExceptionHandler` `@Import`)가 다음을 단정한다.
     - 참여: 200, `$.isSuccess` true, `$.data`가 없다. facade가 경로의 일정 id와 인증 주체로 호출된다.
     - 참여 취소: 200, `$.isSuccess` true, `$.data`가 없다. 서비스가 경로의 일정 id와 인증 주체로 호출된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171 && git diff d67041d --stat -- snsg/src/main/java/com/shineunsigan/snsg/auth` → 출력 없음

8. FE용 문서를 갱신한다.
   - `docs/openapi.yaml`에 두 경로가 들어가고 코드와 일치한다. 컨트롤러 메서드에 `@Operation`(부르는 화면, 인증 필요, 두 번 눌렸을 때의 결과, 성공·실패 모두 상세를 다시 불러 그린다)과 응답 코드별 `@ApiResponses`(200, 401 `UNAUTHENTICATED`·`USER_NOT_ACTIVE`(참여만), 404 `SCHEDULE_NOT_FOUND`, 409 각 코드와 조건·화면에서 할 일)가 있다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `grep -n "/api/v1/schedules/{scheduleId}/participants" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/docs/openapi.yaml` → `/participants:`와 `/participants/me:` 두 경로
   - `docs/api-guide.md`의 "일정" 절에 "참여"와 "참여 취소" 절이 더해지고 `.claude/rules/api.md` 문서 절의 항목을 담는다.
     - 부르는 화면(상세의 [참여하기], 참여 취소 확인 팝업의 [확인]), 인증 필요, 요청 본문 없음
     - 성공 200·`data` 없음. 성공·실패 모두 상세를 다시 불러 화면을 그린다(상세 API는 아직 없다는 사실을 함께 적는다)
     - 오류마다 상태·code·조건·화면에서 할 일과 예시 JSON. 참여: 401 `USER_NOT_ACTIVE`, 404 `SCHEDULE_NOT_FOUND`, 409 `HOST_CANNOT_JOIN`·`ALREADY_JOINED`·`RECRUIT_CLOSED`(마감·시작·취소·완료 포함)·`SCHEDULE_TIME_CONFLICT`. 참여 취소: 404 `SCHEDULE_NOT_FOUND`, 409 `HOST_CANNOT_LEAVE`·`NOT_PARTICIPANT`(이미 취소함 포함)·`PARTICIPATION_NOT_CANCELABLE`(취소·완료된 일정). 판정 순서(배경 "결정" 1·2)를 적어, 여러 조건에 걸릴 때 어느 코드가 오는지 FE가 알게 한다.
     - 404 `SCHEDULE_NOT_FOUND`는 없는 경로의 404(`INVALID_REQUEST`)와 code로 구분한다는 것
     - 두 번 눌렸을 때: 참여는 두 번째가 409 `ALREADY_JOINED`(상태는 한 번 참여한 것과 같다), 참여 취소는 두 번째가 409 `NOT_PARTICIPANT`(재시도해도 안전)
     - 시작 후에도 완료(시작 + 12시간) 전까지 참여 취소가 되고, 시작 후 취소로 자리가 나도 모집이 다시 열리지 않는다는 것
     - 취소 후 다시 참여할 수 있다는 것
   - "화면별로 쓰는 API" 표에 일정 상세 [참여하기]와 참여 취소 [확인] 행이 더해진다. 표 아래 "아직 없다" 문장에서 참여가 빠진다.
   - 검증: `grep -n "participants" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/docs/api-guide.md` → 참여·참여 취소 절과 화면별 표에서 한 줄 이상씩
   - 검증: `grep -n "아직 없다" /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/docs/api-guide.md` → "아직 없다" 일정 API 목록 문장에 "참여"가 없다

9. 전체 빌드와 테스트가 통과한다. ArchUnit(facade의 `@Transactional` 금지, Command 서비스의 `port/out` 의존 금지, 도메인 간 참조)도 포함된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 테스트 실패 0
   - 검증: `grep -l 'skipped="[1-9]' /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg/build/test-results/test/TEST-com.shineunsigan.snsg.schedule.*.xml` → 출력 없음
   - 정지 조건: 위 두 검증이 모두 기대 결과면 끝낸다.
   - 끝내지 않는 경우: 실패가 줄었지만 0이 아닐 때, 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컴파일이 깨져 테스트가 돌지 않았을 때, 동시성 테스트가 3회 중 한 번이라도 실패했을 때.

10. 범위 밖 파일을 고치지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171 && git status --short`. 기대 결과는 다음과 같다.
      - `snsg/src/main/resources/db/migration/` 아래 변경·추가가 없다.
      - `auth/`·`user/`·`place/`·`common/` 패키지의 main 파일 변경이 없다.
      - `snsg/.env`, `snsg/build.gradle`, `snsg/src/main/resources/application*.yaml`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md` 변경이 없다(이 단계에서는. 문서 단계는 별도다).
      - 임시 파일(로그·스크래치)이 없다.

## 비목표

- 일정 상세(API-S07, T-12), 명단(T-14), 전체 취소(T-15), 스케줄러 JOB-01·02(T-17), `UserPort.findProfiles`·`RegionPort.findAll`은 만들지 않는다. 참여 API가 상세를 돌려주지 않는다(명세 3.4).
- 개설자 내보내기, 승인제 참여, 대기열, 알림·이벤트 발행은 만들지 않는다. 참여·취소 시 이벤트를 발행하지 않는다.
- 참여 취소에 활성 사용자 확인(`UserPort`)·사용자 잠금을 넣지 않는다(배경 "결정" 3).
- 확정 참여 인덱스 위반을 `ALREADY_JOINED` 등으로 바꾸는 처리를 넣지 않는다(배경 "결정" 4). `ScheduleRepositoryImplTest`의 기존 인덱스·CHECK 테스트는 그대로 둔다.
- 시작 후 취소 때 저장된 모집 상태를 CLOSED로 바꾸지 않는다. 시작 후 참여 거부 때도 모집 상태를 쓰지 않는다(배경 "결정" 6. 마감 저장은 JOB-01 몫).
- 마이그레이션·설정(`application*.yaml`, `.env`, `build.gradle`)을 바꾸지 않는다. Hikari 풀 3을 올리지 않는다. 동시성 테스트가 연결이 모자라 실패하면 풀을 올리지 말고 원인을 보고한다.
- `CreateScheduleFacade`, 개설 흐름(`ScheduleCommandService.create`), `ListPlaceCandidatesFacade`, `PlaceCandidateController`, 기존 오류 코드·문구는 바꾸지 않는다. 생성자 주입이 늘어 기존 테스트의 생성 코드가 컴파일되지 않을 때만 그 줄을 고친다.
- 다른 도메인(auth·user·place·common) 코드와 `SecurityConfig`는 바꾸지 않는다.
- `docs/api-guide.md`의 기존 절(인증·지역·활동·장소 후보·일정 개설)은 고치지 않는다. 화면별 표와 "아직 없다" 문장만 고치고 참여·참여 취소 절을 더한다.
- 동시성 테스트가 간헐적으로 실패하면 sleep·재시도로 덮지 않는다. 잠금 위치(트랜잭션 안, 판정보다 먼저, 일정 행이 그 트랜잭션에서 처음 읽히는 조회에 잠금이 걸렸는지)를 먼저 확인하고 원인을 보고한다.
- 로컬 DB를 사용자 확인 없이 초기화하지 않는다. Flyway 검증 오류를 만나면 그 자리에서 멈추고 오류 메시지 원문을 보고한다.
- 다른 워크트리(`wt-SHI-169`, `wt-SHI-170`)의 파일을 읽기 외로 건드리지 않는다.

## 제약

- 패키지 구조·의존 방향·Service와 Facade(참여는 facade, 참여 취소는 Controller → CommandService)·코드 작성(메서드 단일 책임, private 메서드로 단계 분리)·soft delete와 개수 계산 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API·Controller·인증 주체·응답 봉투·springdoc 문서화·`docs/` 갱신은 `.claude/rules/api.md`를 따른다. `docs/openapi.yaml`은 API를 바꾼 코드와 같은 커밋에 넣는다.
- 테스트 범위와 방식(domain은 순수 JUnit, application은 Mockito, DB 쿼리는 실제 PostgreSQL, DB 연결 수, `@Nested`로 나누기, `cleanTest`)은 `.claude/rules/testing.md`를 따른다.
- 커밋은 하지 않는다. 스테이징까지만 하고(`.claude/rules/git-workflow.md` 3번), 커밋 분리 계획을 함께 보고한다. 권장: ① 오류 코드·도메인·서비스·facade·컨트롤러·`docs/openapi.yaml` ② 테스트 ③ `docs/api-guide.md`.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 문서 근거 (`be-spec-mvp.html`)
- 10장 T-13 범위·완료 조건, 7장 API-S09·S10(처리 경로, 오류, 두 번 눌렸을 때), 6.3 POL-S8~S12·S16, 4.2 전이 표, 4.4 스케줄러 지연 대비("참여는 start_at > now를 따로 확인한다"), 4.5 버튼 규칙, 5장 `schedule_participants`(참여할 때마다 새 행, 참여 일시 = created_at, 취소 일시 = updated_at, Audit 안 씀), 9장 schedule 오류 코드, 3.4 schedule 표(`JoinScheduleFacade`: `UserPort.isActive` → CommandService, 참여 취소는 Controller가 CommandService 직접).
- 명세 결정 D1: 시작 후에도 완료 전까지 취소 가능, 시작 후 취소로 자리가 나도 모집을 다시 열지 않는다.

### 구현 형태 제안 (바꿔도 됨. 단 GOAL에 고정한 이름 `JoinScheduleFacade`, `ScheduleCommandService`, `ScheduleErrorCode`의 코드 이름, 두 경로는 유지)
- 도메인: `Schedule.join(UUID userId, LocalDateTime now)`, `Schedule.leave(UUID userId, LocalDateTime now, Duration completionAfter)`. 참여 행 생성은 `ScheduleParticipant.member(schedule, userId)`(기존 `host`와 같은 패키지 전용 팩토리), 취소는 `ScheduleParticipant.cancel()`.
  - 모집 중 판정은 기존 `displayStatus(now, completionAfter) == RECRUITING`, 취소 가능 판정은 `displayStatus ∈ {RECRUITING, RECRUITMENT_CLOSED}`로 쓰면 4.5의 버튼 규칙(JOIN·CANCEL_PARTICIPATION)과 같은 판정 한 곳을 공유한다. 이 경우 `join`도 `completionAfter`를 받는다.
  - 겹침 검사가 개설자·이미 참여 판정과 모집 판정 사이에 와야 하므로(결정 1), 서비스가 "참여 가능 확인(도메인) → 겹침 검사(repository) → 참여 반영(도메인)" 순으로 부르는 형태가 자연스럽다. 예: `schedule.validateJoinable(userId, now, completionAfter)` → `existsOverlapping` → `schedule.join(...)`. `join` 안에서도 같은 확인을 다시 해 불변식을 지킨다.
  - 이미 참여 판정은 루트가 가진 참여 행(`participants`)에서 `userId`가 같고 `CONFIRMED`이며 삭제되지 않은 행을 찾는다. 정원 ≤ 24라 행 수가 작다.
- repository: `ScheduleRepository.findByIdForUpdate(UUID id)` → `Optional<Schedule>`. JPA는 `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `findByIdAndIsDeletedFalse`. 주의:
  - **참여 행을 fetch join하면서 잠그지 않는다.** PostgreSQL은 outer join의 nullable 쪽에 `FOR UPDATE`를 허용하지 않고, 잠금 대상은 일정 행 하나면 된다. 참여 행은 잠근 뒤 지연 로딩으로 읽는다. READ COMMITTED라 잠금을 얻은 뒤의 조회는 앞 트랜잭션이 커밋한 행을 본다.
  - **잠금 조회가 그 트랜잭션에서 일정을 처음 읽는 조회여야 한다.** 먼저 잠금 없이 읽어 영속성 컨텍스트에 올려두면 잠금 뒤에도 옛 값(확정 인원·모집 상태)을 쓴다.
  - 반영은 변경 감지로 충분하다. 새 참여 행은 기존 `CascadeType.PERSIST`로 flush 때 저장된다. 명시적 `save`가 필요하면 기존 `save`를 쓴다.
- 서비스 메서드 예: `join(UUID scheduleId, UUID userId)`, `leave(UUID scheduleId, UUID userId)`(반환 void). `LocalDateTime.now()`는 서비스에서 한 번 구해 도메인에 넘긴다(기존 `create`와 같다). Command record는 필드가 둘뿐이라 만들지 않아도 된다(판단은 구현에 맡긴다).
- 컨트롤러: 기존 `ScheduleController`(`/api/v1/schedules`)에 `@PostMapping("/{scheduleId}/participants")`, `@DeleteMapping("/{scheduleId}/participants/me")`. `@PathVariable UUID scheduleId`(형식 오류는 기존 공통 처리로 400 `INVALID_REQUEST`). 반환 `ResponseEntity<ApiResponse<Void>>` + `ApiResponse.success()`. `ScheduleControllerTest`에 `JoinScheduleFacade`·`ScheduleCommandService` `@MockitoBean`을 더한다.
- 오류 문구 예(명세에 없는 것, 기존 톤): `RECRUIT_CLOSED` "모집이 끝난 일정이에요.", `ALREADY_JOINED` "이미 참여한 일정이에요.", `HOST_CANNOT_JOIN` "내가 연 일정에는 참여할 수 없어요.", `HOST_CANNOT_LEAVE` "내가 연 일정은 참여를 취소할 수 없어요. 일정 전체 취소를 이용해주세요.", `NOT_PARTICIPANT` "참여 중인 일정이 아니에요.", `PARTICIPATION_NOT_CANCELABLE` "취소되었거나 끝난 일정은 참여를 취소할 수 없어요."
- 동시성 테스트(`ScheduleCommandServiceTest.Concurrency`)
  - 기존 `@AfterEach`는 `host_user_id = userId`인 일정과 그 참여 행을 지운다. 일정을 그 클래스의 `userId`를 개설자로 `scheduleCommandService.create`로 만들면 정리가 그대로 맞는다. 정원은 설정 최소값 4로 두고, 멤버 2명은 무작위 id로 `join`해 확정 3을 만든 뒤 무작위 id 두 명이 동시에 `join`한다.
  - 연결 수: 준비는 순차라 연결을 오래 잡지 않고, 동시 구간은 두 스레드의 연결 둘이다. 풀 3 안에 든다(`.claude/rules/testing.md`).
  - 기존 `@DisplayName("동시 개설")`은 범위가 넓어지면 "동시 요청" 등으로 바꿔도 된다(단정은 그대로).
- 도메인 테스트에서 CANCELED·COMPLETED 상태는 `ScheduleTest`가 이미 쓰는 `ReflectionTestUtils`로 만든다(상태를 바꾸는 도메인 메서드는 T-15·T-17에서 생긴다). 시각 조건은 `Schedule.create`에 이른 `now`를 주고 `join`·`leave`에 늦은 `now`를 주면 된다.
- 스펙 재생성: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-171/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`
- api-guide에서 고칠 곳: "일정" 절 끝(일정 개설 뒤)에 참여·참여 취소 절, "화면별로 쓰는 API" 표, 표 아래 "장소 후보·일정 개설 외의 일정 API(목록·상세·참여 등)와 알림 API는 아직 없다" 문장.

### refine-docs(4단계)가 점검할 후보
- `.claude/rules/package-structure.md`: 지금 잠금 규약이 rules에 없다. 이번에 확정된 사실(사용자 advisory lock → 일정 행 `FOR UPDATE` 순서, 잠금 조회는 트랜잭션에서 그 행을 처음 읽는 조회여야 한다는 것, outer join fetch와 `FOR UPDATE`를 섞지 않는다는 것)을 인프라 규약 절에 둘지. 부분 유니크 인덱스 위반을 오류 코드로 바꾸는 기준(잠금 없이 경쟁이 실제로 나는 곳만. 닉네임은 바꾸고 참여는 바꾸지 않는다)도 후보.
- `.claude/rules/testing.md`: DB 연결 수 항목의 "지금 가장 많이 쓰는 곳은 `ScheduleCommandServiceTest`의 동시 개설(둘)" 문장을 동시 참여까지 반영해 고칠지. 커밋하는 동시성 테스트의 정리 방식(개설자 id 기준 삭제) 관례.
- `.claude/rules/api.md`: 본문 없는 성공 응답(`ApiResponse<Void>` + `success()`)과 경로의 `me` 하위 리소스(`/participants/me`) 관례를 적을지.
- `docs/api-guide.md`: 참여·참여 취소 절, 화면별 표, "아직 없다" 문장, 공통 에러 표의 404 설명(경로 404 `INVALID_REQUEST`와 업무 404 `SCHEDULE_NOT_FOUND`의 구분)이 맞는지.
- `.claude/skills/`: 동시성 테스트 절차가 T-10·T-13 두 번 반복됐다. skill로 뺄 만한지.

### 머지 시 충돌 예상
- `wt-SHI-169`(user), `wt-SHI-170`(auth)이 `docs/api-guide.md`·`docs/openapi.yaml`을 고칠 수 있다. 먼저 머지된 쪽 뒤에 develop을 받아 `openapi.yaml`은 재생성으로 맞추고, `api-guide.md`의 화면별 표는 손으로 합친다.

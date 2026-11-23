## 배경
- Linear SHI-215. 일정 상태 전환 스케줄러(`ScheduleStatusScheduler`: 모집 마감 `closeStartedRecruitments`, 완료 `completeFinishedSchedules`)는 지금 `@Scheduled(fixedDelayString = "${schedule.status-transition-interval}")`(`1m`)로 1분마다 돈다.
- 일정 시작 시각은 :00/:30만 허용한다(`Schedule.isHalfHour`, `schedule/domain/model/Schedule.java:393`). 완료 기준은 시작 + `schedule.completion-after: 12h`라 완료 시점도 :00/:30이다. 전환 쿼리 경계가 `<=`라 정각에 돌면 바로 전환된다. 그래서 1분 주기가 필요 없다.
- 사용자와 합의한 결정: 주기를 cron `0 0,30 * * * *`(매시 0분·30분 0초)으로 바꾼다. `fixedDelay: 30m`은 기동 시각 기준이라 최대 30분 늦으므로 쓰지 않는다. 전환 쿼리·판정 로직은 바꾸지 않는다.
- 작업 유형: 동작 변경(실행 시각). 바뀌는 기존 단정은 `SnsgApplicationTests.schedulingIsDisabledInTests`의 설정 키 단정 하나다(GOAL 4).

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-215` (브랜치 `refactor/SHI-215-schedule-status-cron`, 베이스 `af16b32`). Git Bash 경로로는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-215`
- 메인 작업 디렉터리 `snsg-be`는 읽기만 한다
- `snsg-postgres`·`snsg-redis` 컨테이너는 작성 시점에 Up이다. 꺼져 있으면 `@SpringBootTest`·DB 테스트가 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다(켜는 방법은 `.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의")
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. `bootRun`(GOAL 6·7) 전에 다른 워크트리에서 앱이 떠 있지 않은지 본다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다
- 마이그레이션 변경 없음

## GOAL
1. 두 전환 메서드가 cron 자리표시자로 돈다. `fixedDelayString`은 남지 않는다.
   - 검증: `grep -n "@Scheduled" /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/scheduler/ScheduleStatusScheduler.java` → 정확히 2줄, 둘 다 `@Scheduled(cron = "${schedule.status-transition-cron}")`
   - 검증: `grep -c "fixedDelay" /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/scheduler/ScheduleStatusScheduler.java` → `0` (javadoc의 fixedDelay 설명도 cron에 맞게 바뀌어 있다)
2. `application.yaml`의 `schedule:` 블록에 cron 키가 있고 옛 키는 없다. 바로 위에 record(`ScheduleProperties`)에 넣지 않는 이유를 적은 주석이 있다.
   - 검증: `grep -n -B1 "status-transition" /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg/src/main/resources/application.yaml` → `  status-transition-cron: "0 0,30 * * * *"` 한 줄과 그 바로 위 `#` 주석 줄(`ScheduleProperties`라는 말이 들어 있다). `status-transition-interval`은 나오지 않는다
3. `snsg/` 아래 어디에서도 옛 키를 참조하지 않는다(main·test 코드, 모든 프로파일 yaml, 테스트 설정, `.env.example`).
   - 검증: `grep -rn "status-transition-interval" /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg --include=*.java --include=*.yaml --include=*.yml --include=*.properties --include=.env.example` → 출력 없음
4. `SnsgApplicationTests.schedulingIsDisabledInTests`의 설정 단정이 바뀌고 통과한다. 바뀌기 전: `environment.getProperty("schedule.status-transition-interval")` → `"1m"`. 바뀐 뒤: `environment.getProperty("schedule.status-transition-cron")` → `"0 0,30 * * * *"`. 같은 메서드의 나머지 두 단정(`SchedulingConfig`·`ScheduledAnnotationBeanPostProcessor` 빈 없음)은 그대로다.
   - 검증: `grep -n "getProperty" /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg/src/test/java/com/shineunsigan/snsg/SnsgApplicationTests.java` → 1줄, `schedule.status-transition-cron`과 `"0 0,30 * * * *"`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg && ./gradlew cleanTest test --tests '*SnsgApplicationTests'` → BUILD SUCCESSFUL, 실패 0
5. 전체 빌드가 통과한다(ArchitectureTest 포함).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
6. 기본 설정(`0 0,30 * * * *`)으로 앱이 뜬다. cron 문자열을 Spring이 기동 때 해석하므로 형식이 틀리면 여기서 실패한다(테스트에서는 스케줄링이 꺼져 있어 해석되지 않는다).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg && ./gradlew bootRun > <세션 스크래치패드>/bootrun-default.log 2>&1`을 백그라운드로 띄우고, 기동 로그가 찍힐 때까지 기다린 뒤 `grep -c "Started SnsgApplication" <세션 스크래치패드>/bootrun-default.log` → `1`, `grep -ci "IllegalStateException\|Cron expression must consist" <세션 스크래치패드>/bootrun-default.log` → `0`. 확인 후 앱을 멈춘다
7. cron 키로 스케줄러가 실제로 돈다(Boot 4에서 "에러 없이 조용히 동작하지 않는" 경우를 잡는다, CLAUDE.md). 30분을 기다리지 않도록 환경변수로 20초 주기를 넣는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-215/snsg && SCHEDULE_STATUS_TRANSITION_CRON='0/20 * * * * *' ./gradlew bootRun > <세션 스크래치패드>/bootrun-cron.log 2>&1`을 백그라운드로 띄운다. `Started SnsgApplication`이 찍히고 70초 이상 지난 뒤 `L=<세션 스크래치패드>/bootrun-cron.log`로 두고 아래 넷을 본다. local 프로파일이 `org.hibernate.SQL: debug`라 0건 갱신도 SQL이 찍힌다. `format_sql: true`라 SQL 한 문장이 `update` / `schedules s1_0` / `set` / `<컬럼>=...` 네 줄로 나뉘므로, `update` 단독 줄과 그 뒤 3줄에서 찾는다.
     - `grep -A3 -E '^\s*update\s*$' "$L" | grep -c "recruitment_status='CLOSED'"` → 3 이상(모집 마감 UPDATE가 세 번 이상 실행됨)
     - `grep -A3 -E '^\s*update\s*$' "$L" | grep -cE "^\s*status='COMPLETED'"` → 3 이상(완료 UPDATE가 세 번 이상 실행됨)
     - `grep "scheduling-1.*org.hibernate.SQL" "$L" | grep -oE 'T[0-9]{2}:[0-9]{2}:[0-9]{2}' | cut -d: -f3 | sort -u` → `00`·`20`·`40`만 나온다. 그 밖의 초는 없다(cron 주기대로 0·20·40초에만 돈다)
     - `grep "scheduling-1.*org.hibernate.SQL" "$L" | grep -oE 'T[0-9]{2}:[0-9]{2}:[0-9]{2}' | sort -u | wc -l` → 3 이상(서로 다른 실행 시각이 세 번 이상)
     - spec이 같은 명령으로 실측한 값(기동 뒤 75초): 5 / 5 / `00 20 40` / 5. 실행 한 번마다 두 UPDATE가 `scheduling-1` 스레드에서 같은 초에 찍혔다
   - 확인 후 앱을 멈추고(8080을 잡은 java 프로세스 종료) GOAL 6·7의 로그 파일을 지운다(`.claude/rules/temp-files.md`)
8. 이 단계(implement)의 변경 경로는 아래 넷뿐이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-215 && git diff --staged --name-only` → 다음 경로만(순서 무관): `.claude/tasks/SHI-215/task.md`, `snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/scheduler/ScheduleStatusScheduler.java`, `snsg/src/main/resources/application.yaml`, `snsg/src/test/java/com/shineunsigan/snsg/SnsgApplicationTests.java`

## 비목표
- 전환 쿼리(`ScheduleJpaRepository.closeRecruitmentStarted`·`completeStartedBy`), `ScheduleCommandService`의 전환 메서드, 판정 로직, 시각 경계(`<=`)는 바꾸지 않는다
- 놓친 실행 보완(기동 직후 1회 실행 등)은 하지 않는다. 재기동이 :00/:30과 겹쳐 실행을 놓치면 다음 30분 실행에서 반영되는 것을 받아들인다(사용자 합의)
- 스케줄러 스레드 풀 크기(`spring.task.scheduling.pool.size`)를 설정하지 않는다(참고의 겹침 메모)
- `ScheduleStatusScheduler`에 새 테스트를 쓰지 않는다. 서비스를 부르기만 하는 스케줄러라 `.claude/rules/testing.md` 계층별 범위상 테스트 대상이 아니고, 실행 확인은 GOAL 7이 맡는다
- 다른 스케줄러(`UserAnonymizationScheduler`, `PlaceImportScheduler`)와 그 cron 키, `SchedulingConfig`는 건드리지 않는다
- 기존 테스트의 단정은 GOAL 4의 한 줄 외에 고치지 않는다. `ScheduleRepositoryImplTest`의 "1분 뒤" 경계 테스트는 쿼리 경계 테스트라 주기와 무관하니 그대로 둔다
- 문서(`.claude/rules/*.md`, `docs/`, README)는 고치지 않는다. refine-docs 단계가 맡는다(참고의 목록)
- `.claude/tasks/SHI-173` 등 지난 task.md는 기록이라 고치지 않는다
- 워크트리 밖, 메인 디렉터리의 `snsg/.env`는 건드리지 않는다. 새 환경변수는 생기지 않는다(키는 yaml에 값이 있다)

## 제약
- 패키지 구조·설정 규칙은 `.claude/rules/package-structure.md`(스케줄러 절, 설정 절의 애노테이션 자리표시자 예외)를 따른다
- 테스트는 `.claude/rules/testing.md`를 따른다
- 임시 로그는 세션 스크래치패드에 두고 확인 후 지운다(`.claude/rules/temp-files.md`)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 키 이름 `schedule.status-transition-cron`은 관례(`withdrawal.anonymize-cron`, `place.import-cron`: `<도메인 prefix>.<용도>-cron`)를 따라 spec이 정했다. 옛 키의 용도 부분(`status-transition`)을 그대로 두고 접미사만 `-cron`으로 바꿨다.
- 따라 할 기존 코드
  - 흐름(cron 스케줄러): `UserAnonymizationScheduler`(`user/infrastructure/scheduler/UserAnonymizationScheduler.java:33`) — `@Scheduled(cron = "${withdrawal.anonymize-cron}")`. 단 이 클래스의 SecurityContext 설정은 엔티티를 바꾸는 스케줄러용이라 따라 하지 않는다. 상태 전환은 일괄 UPDATE라 감사 값을 쿼리에 직접 넣는다(package-structure 일괄 UPDATE 절)
  - 이름·yaml 주석: `application.yaml:64-67`(`withdrawal.anonymize-cron` 위 주석 "@Scheduled가 자리표시자로 읽어 WithdrawalProperties에는 두지 않는다"), `application.yaml:91-94`(`place.import-cron`, 실행 시각 근거도 주석에 적었다). 새 주석에 실행 시각 근거(시작 시각이 :00/:30만 허용, 완료도 :00/:30)를 짧게 적어도 된다
  - 테스트: `SnsgApplicationTests.java:33` — 단정 한 줄만 바꾼다
- `ScheduleStatusScheduler`의 javadoc 둘째 줄("fixedDelay라 앞 실행이 끝난 뒤 간격을 재서 같은 서버에서 실행이 겹치지 않는다")은 cron에 맞게 고친다. 사실 확인: Spring의 cron 실행은 한 실행이 끝난 뒤 다음 시각을 계산하므로 같은 메서드가 같은 서버에서 겹치지 않고, Boot 기본 스케줄러 스레드 풀은 1개다(설정에 `spring.task.scheduling.pool.size` 없음).
- 겹침 메모(범위 밖, 판단용): 스레드 풀이 1개라 03:00(`place.import-cron`, 분기 1회)·04:00(`withdrawal.anonymize-cron`, 매일)과 같은 시각의 전환은 앞 작업이 끝난 뒤에 돈다. 기준 시각이 실행 시점의 `now`이고 경계가 `<=`라 늦게 돌아도 그 사이 대상까지 함께 전환된다. 1분 주기일 때도 같은 스레드를 공유했다.
- GOAL 7의 환경변수 `SCHEDULE_STATUS_TRANSITION_CRON`은 `${schedule.status-transition-cron}` 자리표시자를 덮는다(환경변수가 yaml보다 우선). `--args`로 넘기면 cron의 공백 때문에 인자가 쪼개질 수 있어 환경변수를 쓴다(SHI-181 메모).
- refine-docs 단계가 볼, 옛 키·fixedDelay를 인용하는 문서
  - `.claude/rules/package-structure.md:285` 스케줄러 절 "주기는 `@Scheduled(fixedDelayString = "${...}")`… (`schedule.status-transition-interval: 1m`, …)"
  - `.claude/rules/package-structure.md:361` 설정 절 예외 예시 `@Scheduled(fixedDelayString = "${schedule.status-transition-interval}")`
  - `.claude/rules/package-structure.md:282` 스케줄러 절 예시(`ScheduleStatusScheduler` → 전환 메서드). 키 이름은 없지만 같은 절이다
  - `docs/`·README에는 이 키나 1분 주기를 언급한 곳이 없다(작성 시점 grep). FE 문서에 "상태가 언제 바뀌는지"가 있는지는 refine-docs가 다시 본다
  - `.claude/tasks/SHI-173/task.md` 등 지난 task.md의 언급은 기록이라 고치지 않는다

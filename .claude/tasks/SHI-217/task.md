## 배경

- Linear SHI-217 "다가오는 내 일정 배너에 진행 중인 일정 포함".
- 사용자 원문: "30분마다 일정 상태를 변경하는 스케줄러를 추가중이다. 정확한 d-day배너의 요구사항은 다음과 같다. [요구사항]= 1. 종료 상태가 아닌 일정 중 현재와 가장 근접한 일정을 보여준다."
- 대상 API는 SHI-202에서 만든 `GET /api/v1/schedules/next`(홈 배너)다. 지금은 시작 전 일정만 준다(`ScheduleJpaRepository.findConfirmedScheduledStartingAfter`: `s.status = SCHEDULED and s.startAt > :now`). 진행 중인 일정은 빠진다.
- 작업 유형: **동작 변경**. 바뀌는 단정만 고치고, 나머지 단정은 그대로 둔다.

### 사용자 결정
- "가장 근접"은 종료되지 않은 일정 중 **시작이 가장 이른 것**이다. 정렬은 지금처럼 (start_at, id) 오름차순 첫 건이다. 예: 1시간 전 시작해 진행 중인 A와 10분 뒤 시작하는 B가 있으면 A가 온다.
- "종료되지 않음"의 조건은 둘 다 만족해야 한다.
  - 상태: `status not in (CANCELED, COMPLETED)`.
    - 다른 담당자가 상태를 더할지 몰라 `= SCHEDULED`가 아니라 종료 상태를 빼는 형태로 쓴다.
    - `ScheduleStatus`에 값이 더해지면 이 조건을 다시 봐야 한다는 javadoc을 남긴다.
  - 시각: `startAt > now - schedule.completion-after`. 기존 완료 기준(`ScheduleQueryService.completionBoundary`)을 그대로 쓴다.
    - 다가오는 일정 탭(`period=UPCOMING`)과 `Schedule.displayStatus`가 쓰는 종료 정의와 같다. 그래서 배너에 `displayStatus = COMPLETED`가 오지 않는다.
    - 경계는 다가오는 일정 탭과 같다. 시작 + 12시간이 정확히 된 일정은 빠진다.
- `schedule.completion-after`는 읽기만 하고 값을 바꾸지 않는다.
- 응답 필드는 더하지 않는다.
- 30분 주기 상태 스케줄러는 다른 담당자가 따로 진행한다. 이번 작업에서 스케줄러·상태 전환·설정은 건드리지 않는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-217` (브랜치 `feat/SHI-217-next-schedule-in-progress`, 베이스 `af16b32`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 워크트리 밖 파일은 고치지 않는다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다. `&&`는 PowerShell 5.1에서 동작하지 않는다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").
  - 다른 워크트리(wt-SHI-211)가 테스트를 돌릴 수 있다. 테스트는 한 번에 한 워크트리에서만 돌리도록 오케스트레이터와 조율한다.
  - `snsg-postgres`·`snsg-redis` 컨테이너가 꺼져 있으면 DB 테스트와 `@SpringBootTest` 계열이 실패한다(skip이 아니다). 테스트 전에 `docker ps`로 확인한다. 꺼져 있으면 `docker start snsg-postgres snsg-redis`로 켜고, 그래도 안 되면 코드를 고치지 말고 멈춰서 보고한다.
- `snsg/.env`가 이 워크트리에 있어야 테스트 컨텍스트가 뜬다.
- 이 작업은 마이그레이션을 더하지 않는다.
- 베이스 커밋에서 이미 실패하는 테스트는 확인하지 않았다. spec 단계에서 gradle을 돌리지 않았기 때문이다. 이번 변경과 무관한 실패가 나오면 고치지 말고 보고한다.

## GOAL

1. 배너 조회가 사용자 결정대로 동작한다. 대상은 내가 확정 참여(개설자 포함)했고, 삭제되지 않았고, 상태가 `CANCELED`·`COMPLETED`가 아니고, `startAt > now - completion-after`인 일정 중 (start_at, id)가 가장 앞선 1건이다.
   - `ScheduleRepositoryImplTest`에서 바뀌는 단정은 아래뿐이다.
     - `nextUpcomingExcluded`(다가오는 내 일정에서 빠지는 일정): 지금은 `startAt = NOW`인 행과 `startAt = NOW.minusMinutes(30)`인 행을 **제외**로 단정한다. 바뀐 뒤에는 이 두 행을 이 테스트에서 뺀다. 남는 제외 대상(취소, 완료, 참여 취소, 삭제된 일정, 삭제된 참여 행, 남의 일정)은 그대로 빈 결과로 단정한다. `@DisplayName`에서 "시작 시각이 기준 시각과 같거나 이전인" 문구도 함께 바로잡는다.
     - `nextUpcomingNone`·`nextUpcomingHosted`·`nextUpcomingParticipating`·`nextUpcomingFull`·`nextUpcomingEarliest`의 단정은 그대로 둔다. 시그니처가 바뀌면 호출부(`nextUpcomingId` 헬퍼 등)만 맞춘다.
   - 새 테스트(같은 클래스, 기준 시각 `NOW`, 완료 기준 12시간):
     - (a) 진행 중인 일정이 온다. `startAt = NOW.minusMinutes(30)`, `status = SCHEDULED`, `recruitmentStatus = CLOSED`인 내 확정 일정이 결과다.
     - (b) 진행 중인 일정이 더 늦게 시작하는 일정보다 먼저 온다. 1시간 전에 시작한 A와 10분 뒤에 시작하는 B가 있으면 A가 결과다.
     - (c) 완료 기준 경계를 지킨다. `startAt = NOW.minusHours(12)`인 `SCHEDULED` 일정만 있으면 빈 결과다. `startAt = NOW.minusHours(12).plusMinutes(1)`인 일정은 결과로 온다. 둘을 따로 단정해도 되고 한 테스트에서 함께 단정해도 된다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0
2. schedule 도메인의 다른 테스트와 아키텍처 규칙이 깨지지 않는다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.schedule.*' --tests '*ArchitectureTest' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
3. 코드와 문서에 "시작 전 일정만" 대상이라는 설명이 남지 않는다. 아래 자리를 바뀐 대상(종료되지 않은 일정: 진행 중 포함, 시작 + 12시간 전까지)으로 고친다.
   - `ScheduleJpaRepository`의 대상 쿼리 메서드 이름에서 `StartingAfter`를 뺀다. 새 이름은 구현이 정한다.
   - 다음 javadoc:
     - `ScheduleRepository.findNextUpcoming`
     - `ScheduleJpaRepository`의 그 메서드
     - `ScheduleQueryService.findNext`
     - 셋 중 상태 조건을 설명하는 곳에 "`ScheduleStatus`에 값이 더해지면 이 조건을 다시 본다"를 남긴다.
   - `ScheduleController`의 `/next` `@Operation` description.
   - `NextScheduleResponse.displayStatus`의 `@Schema` description. "시작 전 일정만"을 고치고, 진행 중인 일정은 정원과 상관없이 `RECRUITMENT_CLOSED`라고 적는다. 오는 값은 여전히 `RECRUITING`·`RECRUITMENT_CLOSED` 둘이다.
   - `docs/api-guide.md` "다가오는 내 일정" 절의 대상·제외 목록. 아래 FE 안내를 함께 적는다.
     - 진행 중인 일정도 온다. 그때 `startAt`은 지금보다 과거다. D-DAY를 `startAt`으로 계산하면 0이거나, 자정을 넘긴 일정이면 하루 지난 값이 된다(최대 시작 + 12시간).
     - 진행 중인 일정의 `displayStatus`는 `RECRUITMENT_CLOSED`다.
   - 검증: `cd snsg && grep -rn "StartingAfter\|시작 전 일정만 주므로\|는 시작 전 일정만 오므로" src/main/java ../docs/api-guide.md ../docs/openapi.yaml` → 출력 없음
     - 베이스에서 걸리는 곳은 넷이다: `ScheduleJpaRepository.java:80`, `ScheduleRepositoryImpl.java:73`, `NextScheduleResponse.java:44`, `docs/api-guide.md:1070`(배너 절의 displayStatus 설명). openapi.yaml은 재생성으로 따라온다.
     - `docs/api-guide.md:934`(모집 중 목록의 "시작 전 일정만 오므로 빈 결과다")는 이 작업과 무관해 패턴에서 뺐다. 고치지 않는다.
   - 검증: `cd snsg && grep -n "ScheduleStatus" src/main/java/com/shineunsigan/snsg/schedule/domain/repository/ScheduleRepository.java src/main/java/com/shineunsigan/snsg/schedule/infrastructure/persistence/ScheduleJpaRepository.java src/main/java/com/shineunsigan/snsg/schedule/application/ScheduleQueryService.java` → 세 파일 중 하나 이상의 javadoc 줄에 상태가 더해지면 다시 본다는 문구가 있다. import 줄만 걸리면 FAIL로 본다.
4. `docs/openapi.yaml`이 코드의 설명과 같다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
5. 변경 범위가 허용 목록 안이다.
   - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/wt-SHI-217 diff --staged --name-only` → 모든 경로가 아래 중 하나다.
     - `snsg/src/main/java/com/shineunsigan/snsg/schedule/` 아래
     - `snsg/src/test/java/com/shineunsigan/snsg/schedule/` 아래
     - `docs/api-guide.md`
     - `docs/openapi.yaml`
     - `.claude/tasks/SHI-217/task.md`
   - `snsg/src/main/resources/`(설정·마이그레이션)와 `docs/openapi-admin.yaml`은 목록에 없다. 이 둘이 바뀌면 FAIL이다.
   - `docs/openapi-admin.yaml`은 재생성 명령이 함께 다시 만들지만 어드민 경로와 무관하다. 내용이 바뀌지 않아야 한다.

## 비목표

- 스케줄러(`ScheduleStatusScheduler`)·상태 전환(`completeStartedBy`·`closeRecruitmentStarted`)·`schedule.completion-after`·`status-transition-interval` 값은 건드리지 않는다. 다른 담당자의 작업이다.
- 응답 필드(진행 중 여부 등)를 더하지 않는다.
- `Schedule.displayStatus` 판정 로직은 바꾸지 않는다.
- 다가오는 일정 탭(`appendPeriod`)·모집 중 목록·다른 목록 쿼리는 바꾸지 않는다.
- 인덱스·마이그레이션을 더하지 않는다.
  - 쿼리는 지금처럼 `ix_participants_user (user_id, status)`에서 출발해 일정과 조인한다.
  - `start_at`은 원래 이 인덱스에 없어서 조건이 바뀌어도 접근 경로가 같다.
- `GetNextScheduleFacade`·`GetNextScheduleFacadeTest`·`ScheduleControllerTest`·`SecurityConfigTest`의 단정을 바꾸지 않는다. 시그니처 변경에 따른 컴파일 맞춤만 허용한다.
- 저장소 밖 기획 문서는 고치지 않는다. 어긋나는 곳은 아래 참고에만 적고 PR background에서 보고한다.
- 위 GOAL 1에 적은 단정 외의 기존 단정은 고치지 않는다. 테스트가 실패하면 단정을 고치지 말고 구현을 고치거나 멈춰서 보고한다.

## 제약

- 패키지 구조·계층·repository 쿼리 규칙은 `.claude/rules/package-structure.md`를 따른다. 목록 쿼리 절과 삭제 절(`isDeleted = false`는 참여 행과 일정 모두)을 본다.
- API 문서·OpenAPI는 `.claude/rules/api.md`를 따른다.
  - 스펙 재생성은 구현이 한다: `cd snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다.
- 기준 시각은 서비스가 `LocalDateTime.now()`로 한 번 정한다. 같은 값을 조회 조건과 `displayStatus` 판정에 함께 쓴다. 지금 `findNext`가 그렇게 한다.
- 임시 파일은 세션 스크래치패드에만 둔다(`.claude/rules/temp-files.md`).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 따라 할 기존 코드
- 데이터 접근: `ScheduleRepositoryImpl.appendPeriod`(`snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/persistence/ScheduleRepositoryImpl.java:176`). UPCOMING의 `s.startAt > :completionBoundary`와 같은 경계를 쓴다. 단 배너는 `CANCELED`도 뺀다(UPCOMING은 취소 일정을 남긴다).
- 흐름: `ScheduleQueryService.findParticipating`·`findHosted`(`ScheduleQueryService.java:70-83`). `completionBoundary(now)`를 계산해 repository에 넘긴다. `findNextUpcoming`도 같은 방식으로 경계를 인자로 받는 형태를 제안한다(이름·시그니처는 구현이 정한다).
- 쿼리: `ScheduleJpaRepository.findConfirmedScheduledStartingAfter`(`ScheduleJpaRepository.java:65-81`). 참여 행에서 출발하는 구조, `Limit`, 정렬 `order by s.startAt, s.id`를 유지한다. 상태 조건은 `s.status not in (...CANCELED, ...COMPLETED)`처럼 enum 전체 경로로 적는다. 이 파일의 다른 쿼리가 그렇게 쓴다.
- 테스트: `ScheduleRepositoryImplTest`(`@DataJpaTest`, `NOW = 2027-03-10 12:00`, `row()` 빌더의 `.startAt()`·`.status()`·`.recruitmentStatus()`, `insertParticipant`). 경계 단정은 같은 파일의 `participatingPastIncluded` 계열(완료 기준 NOW − 12시간)을 본다.

### FE 영향
- 응답 필드는 바뀌지 않는다.
- 배너에 진행 중인 일정이 오면 `startAt`이 과거다. FE가 D-DAY를 계산할 때 이 경우를 다뤄야 한다. 배지는 `RECRUITMENT_CLOSED`(모집 마감)다.
- 이 내용은 `docs/api-guide.md`로만 전달한다(GOAL 3).

### 저장소 밖 기획 문서와 어긋나는 곳 (고치지 않음, 보고만)
- `C:\Users\ks\nss\dev\snsg-be\planning\decisions.md` 홈 탭 절 54~55줄: "취소되지 않았고 시작 전인 가장 빠른 1건", "시작 시각 > 지금(같으면 제외) … 진행 중 일정은 제외".
- `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html` API-S12(709줄 부근): `status = SCHEDULED이고 start_at > now`, "진행 중 일정은 제외", "displayStatus는 RECRUITING·RECRUITMENT_CLOSED만"(두 값은 여전히 맞다).
- 같은 파일 8.1 스케줄러 표의 JOB-02(1분, 시작 + 12시간 → COMPLETED)도, 다른 담당자의 30분 스케줄러가 들어오면 어긋날 수 있다.

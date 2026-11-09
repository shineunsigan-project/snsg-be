## 배경

- Linear SHI-173 "상태 전환 스케줄러". 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-17이다. 범위는 JOB-01(모집 마감)·JOB-02(완료)이고, 일괄 UPDATE 쿼리는 repository, 호출은 schedule `infrastructure/scheduler`에 둔다. `@EnableScheduling`과 주기 설정을 함께 넣는다.
- 명세 완료 조건: 시각 경계별 전환(start_at = now면 CLOSED, start_at + 12h = now면 COMPLETED) / 취소된 일정은 건드리지 않음 / 두 번 실행해도 같음 / updated_by = SYSTEM_SCHEDULER, updated_at 갱신.
- 명세의 근거
  - JOB-01(1분): `status='SCHEDULED' AND recruitment_status='RECRUITING' AND start_at <= now` → `recruitment_status = CLOSED`
  - JOB-02(1분): `status='SCHEDULED' AND start_at <= now - 12h` → `status = COMPLETED`. 12h는 기존 설정 `schedule.completion-after`(displayStatus와 같은 값)다.
  - 한 번 CANCELED·COMPLETED가 된 일정은 되돌리지 않는다. 일정 취소 시 모집 상태는 그대로 두고 쓰지 않는다.
  - "JOB-01·02는 조건부 일괄 UPDATE라 여러 번 돌거나 서버 두 대에서 동시에 돌아도 결과가 같다. 그래서 분산 락(ShedLock)을 두지 않는다. 일괄 UPDATE는 JPA Auditing을 거치지 않으므로 updated_at·updated_by(`Auditor.SYSTEM_SCHEDULER`)를 쿼리에 직접 넣는다."
  - "스케줄링 활성화(`@EnableScheduling`)와 주기는 설정값. 테스트에서는 끈다(`@WebMvcTest`·`@DataJpaTest`는 스케줄러 빈을 올리지 않지만 `@SpringBootTest`는 올린다)."
  - "JOB-01·02는 schedule, JOB-03은 user 도메인의 infrastructure(`infrastructure/scheduler`)에 두고 각 도메인의 CommandService를 부른다."
  - 스케줄러는 최대 1분 늦을 수 있어 화면 상태(`Schedule.displayStatus`)·참여·목록은 시각 조건을 따로 본다(D11). 이번 작업은 저장된 상태만 맞춘다.
- 결정 (사용자 위임 "권장안대로 진행"에 따라 근거와 함께 고른 것)
  1. **삭제된 일정(`is_deleted = true`)은 두 전환 모두에서 제외한다. (권장안 채택)** 명세의 JOB 조건에는 없다. 근거: `.claude/rules/package-structure.md` 삭제 절이 조회·개수 계산 모두 삭제되지 않은 행만 보게 하고, `BaseAuditEntity` 주석대로 삭제한 행의 `updated_by`는 삭제한 주체를 가리킨다. 스케줄러가 덮어쓰면 그 기록이 사라진다. 삭제된 일정은 모든 조회에서 404(POL-S16)라 상태를 바꿀 이유가 없다.
  2. **`updated_at`에는 판정에 쓴 기준 시각(now)을 넣는다. (권장안 채택)** 근거: 같은 실행의 조건과 기록이 같은 시각을 가리키고, 테스트가 고정 시각으로 경계와 감사 값을 함께 단정할 수 있다. 기준 시각은 서비스가 `LocalDateTime.now()`로 정한다(기존 `ScheduleCommandService.create`와 같은 방식, JVM 시간대는 Asia/Seoul로 고정돼 있다).
  3. **두 전환은 각자의 트랜잭션에서 돈다. (권장안 채택)** 스케줄러(infrastructure)가 `ScheduleCommandService`의 전환 메서드(각 `@Transactional`)를 부른다. 한 전환이 실패해도 다른 전환은 다음 주기를 기다리지 않고 돈다. 명세가 JOB-01·02를 별도 작업으로 적었다.
  4. **JOB-02는 `status`만 바꾸고 모집 상태는 건드리지 않는다. (권장안 채택)** 명세 그대로다. 스케줄러가 12시간 넘게 멈춰 JOB-01보다 JOB-02가 먼저 맞으면 `COMPLETED`/`RECRUITING` 조합이 남을 수 있지만, `displayStatus`가 COMPLETED를 먼저 보고 참여는 `status = SCHEDULED`를 요구하므로 무해하다.
  5. **`@EnableScheduling`은 `common/config`의 설정 클래스에 두고 설정값 하나로 켜고 끈다. 기본은 켬, test 태스크에서 끔. (권장안 채택)** 근거: JOB-03(user)·JOB-04(place)도 같은 스위치를 쓴다. schedule 안에 두면 다른 도메인의 스케줄러가 schedule 설정에 기대게 된다. 메인 클래스에 두지 않는 것은 `JpaAuditingConfig`와 같은 이유(설정으로 끌 수 있어야 한다)다. common에 무엇을 넣을 때는 트레이드오프를 보고하고 규칙 문서에 남긴다(package-structure common 절) — 문서 반영은 refine-docs 단계 몫이다(참고 절 후보).
  6. **주기는 설정값이고 기본 1분이다. 다중 인스턴스 대비 잠금은 두지 않는다. (명세 그대로)** 주기 값은 `@Scheduled`의 자리표시자로 읽는다. `@Scheduled` 애너테이션 속성은 record를 주입받을 자리가 없어서다(package-structure "설정값은 `@Value` 대신 record"는 서비스 주입에 대한 규칙). 이 해석은 refine-docs 후보로 적는다.
  7. **새 마이그레이션은 없다.** `ix_schedules_status_start (status, start_at, id)`가 두 조건의 앞머리(status, start_at)를 덮는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-173` (브랜치 `feat/SHI-173-schedule-status-scheduler`, 베이스 `3c84a20` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리 파일은 고치지 않는다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V8이다. 이번 작업은 더하지 않는다.
- 셸은 PowerShell(5.1)이 기본이고 Bash도 쓸 수 있다. PowerShell에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`·`SnsgApplicationTests`·`OpenApiSpecTest`가 로컬 DB에 붙는다. DB가 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다. 테스트와 앱 실행은 다른 워크트리와 동시에 하지 않는다(`.claude/skills/task/SKILL.md`).
- 테스트의 Hikari 풀은 3이다(`snsg/build.gradle`, `.claude/rules/testing.md` DB 연결 수 항목).
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.
- 동시에 진행 중인 schedule 작업 (앞 작업이 먼저 머지된다는 전제로 진행한다)
  - `wt-SHI-171`(T-13 참여·참여 취소): `ScheduleCommandService`, `ScheduleRepository`, `ScheduleJpaRepository`, `ScheduleRepositoryImpl`, `ScheduleRepositoryImplTest`, `ScheduleCommandServiceTest`를 고친다. 일정 행 잠금(`SELECT … FOR UPDATE`)을 더한다.
  - `wt-SHI-172`(T-16 목록, spec 중): `ScheduleRepository`·`ScheduleJpaRepository`·`ScheduleRepositoryImpl`·`ScheduleRepositoryImplTest`, `ScheduleProperties`, `application.yaml`의 `schedule:` 블록(page-size 키)을 고칠 가능성이 높다.
  - 이번 작업이 겹치는 파일: 위 repository 3종과 그 테스트, `ScheduleCommandService`, `snsg/src/main/resources/application.yaml`, `snsg/build.gradle`(test 태스크).
  - 앞 작업이 develop에 머지되면 이 브랜치를 최신 origin/develop 위로 올리고(rebase) 양쪽 메서드·키를 모두 살려 충돌을 푼다. 충돌 해결 후 GOAL 8을 다시 돌린다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 하고, 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다. 아래 "기준 시각"은 전환에 넘기는 now다.

1. 모집 마감 전환(JOB-01)이 삭제되지 않았고 `status = SCHEDULED`, `recruitment_status = RECRUITING`, `start_at <= 기준 시각`인 일정의 `recruitment_status`를 `CLOSED`로 바꾼다. `status`는 바꾸지 않는다.
   - 검증: 기존 `ScheduleRepositoryImplTest`(실제 PostgreSQL, 기존 애너테이션·`@Import` 구성 그대로, 일정은 native INSERT)에 다음 단정이 더해진다. 단정은 넣은 행의 id로 다시 읽어서 한다(공유 DB의 다른 행이 함께 바뀔 수 있으므로 바뀐 건수로 단정하지 않는다).
     - `start_at` = 기준 시각 → `CLOSED`
     - `start_at` = 기준 시각 + 1분 → `RECRUITING` 그대로, `updated_by`·`updated_at` 그대로
     - `status = CANCELED`, `RECRUITING`, 시작 지남 → 두 상태와 `updated_by`·`updated_at` 그대로
     - `is_deleted = true`, `SCHEDULED`·`RECRUITING`, 시작 지남 → 그대로
     - 이미 `CLOSED`(정원 도달)인 시작 지난 일정 → `updated_by`·`updated_at` 그대로

2. 완료 전환(JOB-02)이 삭제되지 않았고 `status = SCHEDULED`, `start_at + schedule.completion-after <= 기준 시각`인 일정의 `status`를 `COMPLETED`로 바꾼다. 모집 상태는 바꾸지 않는다. 경계 값은 기존 설정 `schedule.completion-after`(12h)에서 온다. 새 설정 키를 만들지 않는다.
   - 검증: `ScheduleRepositoryImplTest`에 다음 단정이 더해진다(12시간을 넘겨 전환을 부른다).
     - `start_at + 12h` = 기준 시각 → `COMPLETED`
     - `start_at + 12h` = 기준 시각 + 1분 → `SCHEDULED` 그대로
     - `status = CANCELED`, `start_at + 12h` 지남 → `CANCELED` 그대로, `updated_by`·`updated_at` 그대로
     - `is_deleted = true`, `SCHEDULED`, `start_at + 12h` 지남 → 그대로
     - 전환된 행의 `recruitment_status`는 넣은 값 그대로
   - 검증: `grep -rn "completion-after\|completionAfter" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/resources/application.yaml` → `completion-after: 12h` 한 줄(기존 그대로)
   - 검증: `ScheduleCommandService`(또는 스케줄러)가 완료 전환에 `ScheduleProperties.completionAfter()`를 넘긴다 — `grep -rn "completionAfter()" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java/com/shineunsigan/snsg/schedule` → `Schedule`의 기존 사용 외에 전환 경로에서 한 곳 이상

3. 전환된 행의 `updated_by`는 `Auditor.SYSTEM_SCHEDULER`, `updated_at`은 기준 시각이다. `created_by`·`created_at`은 그대로다.
   - 검증: `ScheduleRepositoryImplTest`가 두 전환 각각에 대해 전환된 행의 `updated_by = 'SYSTEM_SCHEDULER'`, `updated_at = 기준 시각`, `created_by`·`created_at` = 넣은 값을 단정한다.
   - 검증: `grep -rn "SYSTEM_SCHEDULER" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java/com/shineunsigan/snsg/schedule` → 한 줄 이상(문자열 리터럴 `"SYSTEM_SCHEDULER"`가 아니라 `Auditor.SYSTEM_SCHEDULER` 참조)

4. 두 번 실행해도 결과가 같다. 첫 실행에서 전환된 행은 두 번째 실행(더 늦은 기준 시각)에서 다시 갱신되지 않는다.
   - 검증: `ScheduleRepositoryImplTest`가 두 전환 각각에 대해, 기준 시각 T로 한 번, T + 1분으로 한 번 더 부른 뒤 상태가 같고 `updated_at`이 T로 남아 있음을 단정한다.

5. 스케줄러가 schedule `infrastructure/scheduler` 패키지에 있고 `ScheduleCommandService`의 두 전환을 각각 부른다. 전환 메서드는 각자 `@Transactional`이다. 일괄 UPDATE 쿼리는 schedule repository(`domain/repository` 인터페이스 ← `infrastructure/persistence` 구현)에 있다. `ScheduleCommandService`는 `port/out`을 의존하지 않는다.
   - 검증: `ls /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/scheduler/` → 클래스 파일 하나 이상, `grep -n "@Scheduled" <그 파일>` → 두 줄 이상(전환마다 하나)
   - 검증: `grep -n "Port" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/ScheduleCommandService.java` → 출력 없음
   - 검증: `grep -rn "@Modifying" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/persistence/` → 두 줄 이상

6. 스케줄링은 설정값 하나로 켜고 끈다. 기본(애플리케이션 설정)은 켬, Gradle test 태스크는 끔. 전환 주기는 설정값이고 기본 1분이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-173 && git diff --staged -- snsg/build.gradle snsg/src/main/resources/application.yaml` → test 태스크에 스케줄링을 끄는 `systemProperty` 한 줄, `application.yaml`에 같은 키를 켜는 값과 주기 키(1분) 추가. 그 밖의 기존 줄 변경 없음
   - 검증: `grep -rn "EnableScheduling" /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg/src/main/java` → `common/config` 아래 한 곳. 같은 클래스에 위 키를 보는 조건(`@ConditionalOnProperty` 등)이 있다. `SnsgApplication.java`에는 없다.

7. 앱을 띄우면 스케줄러가 실제로 돈다(Boot 4 모듈 분리로 "에러 없이 조용히 동작하지 않는" 경우를 잡는다, CLAUDE.md).
   - 사전 조건: 8080 포트를 다른 워크트리가 쓰고 있지 않다. 쓰고 있으면 이 항목을 건너뛰지 말고 멈춰서 보고한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg && ./gradlew bootRun > <세션 스크래치패드>/bootrun.log 2>&1` 을 백그라운드로 띄우고 기동 후 70초 이상 지나서 `grep -ci "update schedules" <세션 스크래치패드>/bootrun.log` → 4 이상(두 전환 × 두 번 이상. local 프로파일이 `org.hibernate.SQL: debug`라 0건 갱신도 SQL이 찍힌다). 확인 후 앱을 멈추고 로그 파일을 지운다(`.claude/rules/temp-files.md`).

8. 전체 빌드가 실패 0으로 끝나고 API는 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-173/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0 (`ArchitectureTest`, `OpenApiSpecTest` 포함)
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-173 && git diff --staged --stat -- docs/ snsg/src/main/resources/db/` → 출력 없음
   - 끝내지 않는 경우: 실패가 줄었지만 0이 아닐 때, 테스트를 지우거나 `@Disabled`로 실패가 사라졌을 때, 컴파일이 깨져 테스트가 돌지 않았을 때

9. 변경은 워크트리 안이고 presentation·다른 도메인을 건드리지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-173 && git diff --staged --name-only` → 모든 경로가 `snsg/` 또는 `.claude/tasks/SHI-173/` 아래이고, `presentation/`·`auth/`·`user/`·`place/` 경로가 없다

## 비목표

- JOB-03(탈퇴 비식별, user)·JOB-04(장소 적재, place)는 만들지 않는다. 공용 스위치(GOAL 6)만 그들이 나중에 쓸 수 있게 둔다.
- 분산 락(ShedLock, advisory lock)을 넣지 않는다(명세: 멱등 UPDATE라 불필요).
- 스케줄러가 SecurityContext에 `SYSTEM_SCHEDULER`를 넣는 처리를 하지 않는다. 일괄 UPDATE는 JPA Auditing을 거치지 않아 쓸 데가 없다(그 방식은 엔티티를 바꾸는 JOB-03용).
- `Schedule` 엔티티에 상태 전환 메서드를 더하지 않는다. 일괄 UPDATE가 엔티티를 거치지 않는다. `Schedule.displayStatus`는 바꾸지 않는다.
- JOB-02에서 모집 상태를 CLOSED로 함께 바꾸지 않는다(배경 결정 4).
- 전환 결과를 로그로 남기거나 실행 이력을 저장하는 기능을 더하지 않는다.
- 스케줄러 클래스·`ScheduleCommandService`의 전환 메서드처럼 repository를 그대로 부르기만 하는 코드에는 테스트를 쓰지 않는다(`.claude/rules/testing.md` 계층별 범위). 설정만 추가한 부분(GOAL 6)도 테스트로 검증하지 않는다(GOAL 7의 실행 확인으로 대신).
- 새 DB 테스트 구성(새 `@Import` 조합의 `@DataJpaTest`)을 만들지 않는다. 기존 `ScheduleRepositoryImplTest` 구성에 메서드를 더한다. Hikari 풀 3을 올리지 않는다.
- 기존 테스트의 단정, `CreateScheduleFacade`, `ScheduleCommandService.create`, `ScheduleProperties`의 기존 필드, 오류 코드는 바꾸지 않는다.
- 마이그레이션·`docs/`(openapi.yaml, api-guide.md)·`.claude/rules/`·`.claude/skills/`·`CLAUDE.md`는 이 단계에서 고치지 않는다(문서는 refine-docs 단계).
- `snsg/.env`를 고치지 않는다(새 키는 `application.yaml`에 기본값으로 둔다. 환경변수가 아니다).

## 제약

- 패키지 구조·의존 방향·Service 규칙·common 규칙은 `.claude/rules/package-structure.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다. 테스트는 원래 코드와 별도 커밋 대상이다(스테이징까지만 한다).
- 커밋·브랜치 절차는 `.claude/rules/git-workflow.md`, 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 로그는 세션 스크래치패드에 두고 워크트리에 만들지 않는다.
- 들여쓰기 4칸, Jackson 3 등 `CLAUDE.md`를 따른다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 구현 형태 제안 (바꿔도 된다)

- repository: `ScheduleRepository`에 두 메서드. 예: `int closeRecruitmentStarted(LocalDateTime now)`, `int completeFinished(LocalDateTime now, Duration completionAfter)`. 기존 `existsOverlapping(userId, startAt, window)`처럼 Duration을 받아 구현체가 `now.minus(completionAfter)`를 계산하면 12h 경계를 repository 테스트 하나로 확인할 수 있다.
- JPA: `ScheduleJpaRepository`에 JPQL `@Modifying @Query("update Schedule s set s.recruitmentStatus = …CLOSED, s.updatedAt = :now, s.updatedBy = :auditor where s.isDeleted = false and s.status = …SCHEDULED and s.recruitmentStatus = …RECRUITING and s.startAt <= :now")`. 상속 필드(`updatedAt`·`updatedBy`)도 JPQL에서 쓸 수 있다. enum은 기존 쿼리처럼 정규화된 이름 리터럴로 쓴다. `updatedBy`에는 `Auditor.SYSTEM_SCHEDULER`를 파라미터로 넘긴다. 일괄 UPDATE는 `@CreatedDate`/`@LastModifiedBy` 리스너를 거치지 않는다(Hibernate가 엔티티를 로드하지 않고 SQL을 바로 보낸다). 그래서 감사 컬럼을 쿼리에 직접 넣는다.
- 서비스: `ScheduleCommandService`에 `@Transactional public void closeStartedRecruitments()` / `completeFinishedSchedules()` 같은 메서드 두 개. 기준 시각은 메서드 안에서 `LocalDateTime.now()`. 의존은 기존(`ScheduleRepository`, `ActivityCategoryRepository`, `ScheduleProperties`) 그대로다.
- 스케줄러: `schedule/infrastructure/scheduler/ScheduleStatusScheduler`(`@Component`). 메서드 두 개에 각각 `@Scheduled(fixedDelayString = "${schedule.status-transition-interval}")`. `fixedDelay`면 앞 실행이 끝난 뒤 간격을 재서 실행이 겹치지 않는다. 설정 값 `1m`이 `@Scheduled` 문자열에서 파싱되지 않으면 ISO 형식 `PT1M`을 쓴다(GOAL 7 기동에서 드러난다). `@Component` 단순 클래스명이 다른 도메인과 겹치지 않게 짓는다(빈 이름 충돌, package-structure Port 절).
- 스위치: `common/config/SchedulingConfig`에 `@Configuration @EnableScheduling @ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true")`. `application.yaml`에 `scheduling.enabled: true`, `snsg/build.gradle` test 태스크에 `systemProperty 'scheduling.enabled', 'false'`와 이유 주석(공유 DB에 `@SpringBootTest` 컨텍스트가 실제 UPDATE를 커밋하지 않게). `@ConditionalOnProperty`는 `spring-boot-autoconfigure`(`org.springframework.boot.autoconfigure.condition`)에 있다.
- 주기 키를 `schedule.` 아래에 두되 `ScheduleProperties` record에는 넣지 않아도 된다(`@ConfigurationProperties`는 모르는 키를 무시한다). 넣지 않으면 그 키가 record에 없는 이유를 yaml 주석 한 줄로 남긴다.

### 테스트 작성 팁

- 일괄 UPDATE는 테이블 전체가 대상이라 공유 로컬 DB의 다른 행도 테스트 트랜잭션 안에서 함께 바뀐다(롤백되므로 남지는 않는다). 기준 시각을 과거 고정 값(예: `2020-01-01T12:00`)으로 잡고 테스트 행의 `start_at`을 그 근처에 두면, 실제 개설 데이터(미래 시작)는 조건에 걸리지 않아 다른 행을 잠그지 않는다.
- 단정은 넣은 id로 native SELECT(`status`, `recruitment_status`, `updated_by`, `updated_at`, `created_by`, `created_at`)해서 한다. JPQL 일괄 UPDATE 뒤 영속성 컨텍스트에 남은 엔티티는 옛 값이므로 엔티티로 다시 읽지 않는다(`@Modifying(clearAutomatically = true)`를 쓰거나 native로 읽는다).
- 기존 `insertSchedule(row())` 빌더에 `start_at`·`recruitment_status`·`updated_by`·`updated_at`을 지정하는 항목이 없으면 더한다. 기존 테스트의 단정은 바꾸지 않는다.

### 동시 실행과 다른 작업과의 상호작용

- 다중 인스턴스: 명세가 직접 다룬다(ShedLock 없음). 두 서버가 같은 순간 같은 UPDATE를 보내도 PostgreSQL READ COMMITTED에서 뒤 UPDATE는 앞 커밋을 기다린 뒤 WHERE를 다시 평가해 이미 바뀐 행을 건너뛴다. 결과와 `updated_at`이 한 번 실행한 것과 같다.
- SHI-171의 참여·참여 취소는 일정 행을 `FOR UPDATE`로 잠근다. 스케줄러 UPDATE는 그 잠금이 풀리길 기다렸다가 조건을 다시 본다. 시작 직전에 참여 취소가 CLOSED → RECRUITING으로 되돌려도 시작이 지나면 다음 실행이 다시 마감한다.

### refine-docs 후보 (이번 단계에서 고치지 않는다)

- `.claude/rules/package-structure.md`
  - 레이아웃의 infrastructure 기술별 하위 패키지 예시에 `scheduler/`를 더하고, "스케줄러는 infrastructure/scheduler에 두고 같은 도메인의 Command 서비스(또는 facade)를 부른다. 트랜잭션은 서비스 메서드가 건다"를 적는다.
  - common 절에 `common/config`의 `SchedulingConfig`(스케줄링 스위치)와 common에 둔 근거(여러 도메인의 스케줄러가 쓰는 스위치, 비즈니스 로직 없음)를 적는다. common 절이 "넣거나 뺄 때 판단 근거를 이 문서에 남긴다"고 요구한다.
  - Entity/삭제 절 근처에 "일괄 UPDATE(`@Modifying`)는 JPA Auditing을 거치지 않으므로 `updated_at`·`updated_by`를 쿼리에 직접 넣는다. 삭제된 행은 바꾸지 않는다(삭제 주체 `updated_by` 보존)"를 적는다.
  - "설정값은 `@Value` 대신 record" 규칙에 `@Scheduled` 주기처럼 애너테이션 속성이 읽는 값은 자리표시자로 읽는다는 예외를 적을지.
- `.claude/rules/testing.md`: test 태스크가 스케줄링을 끈다는 사실(이유: `@SpringBootTest` 컨텍스트가 공유 DB에 실제 UPDATE를 커밋)과, 일괄 UPDATE 테스트는 과거 고정 기준 시각·id 단위 단정으로 쓴다는 요령.
- `docs/api-guide.md`: 현재 일정 상태·`displayStatus`를 설명하는 절이 없다. 상세·목록 API(T-12·T-16)가 상태를 내보낼 때 "저장 상태는 최대 1분 늦게 바뀌지만 `displayStatus`는 시각을 함께 봐 즉시 맞다"를 안내하는 것이 맞고, 이번 작업은 API 변화가 없으므로 고칠 것이 없을 가능성이 높다. refine-docs가 확인만 한다.
- `CLAUDE.md`: Boot 4 함정 목록에 스케줄링 관련 새 사실이 확인되면(예: `@Scheduled` 주기 문자열 형식) 그때 더한다.

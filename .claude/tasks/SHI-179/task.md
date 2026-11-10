## 배경

- Linear SHI-179 "일정 전체 취소". 기능 명세(저장소 밖, 읽기 전용 `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-15(HOST-01)를 구현한다. 범위는 새 마이그레이션 `schedule_cancellations`, API-S11, POL-S13, 일정 상세(API-S07)의 `cancellation` 필드다.
- 명세 완료 조건: 취소 후 상세가 CANCELED·사유·canceledAt을 보인다 / 시작 후 취소는 거부한다 / 개설자가 아닌 사람은 403 / 참여자 행은 CONFIRMED로 남는다 / 취소 행의 created_by = 개설자 / 두 번째 취소는 `SCHEDULE_NOT_CANCELABLE`.
- 명세의 근거(읽어서 확인한 것)
  - POL-S13: 개설자 본인, status = SCHEDULED, start_at > now, 사유 1~100자(공백 제거 후). 위반 시 `NOT_HOST`(FORBIDDEN), `SCHEDULE_NOT_CANCELABLE`(CONFLICT), `INVALID_CANCEL_REASON`(INVALID).
  - D6(결정 표): 시작 전까지만 취소할 수 있다. 사유는 필수(1~100자)이고 취소된 일정 상세에 보여준다. 푸시·소통이 없으므로 참여자는 상세나 목록의 "일정 취소됨"으로 안다.
  - API-S11: `POST /api/v1/schedules/{scheduleId}/cancellation`. 인증 필요. 요청 `{reason}`(필드 하나지만 검증 제약이 있어 Request record를 둔다). Controller → CommandService. 응답 200, data 없음 → FE가 상세 재조회. 두 번째 요청은 `SCHEDULE_NOT_CANCELABLE`.
  - 4.1·4.2 상태 모델: 개설자 전체 취소는 SCHEDULED이고 start_at > now일 때 `status = CANCELED`. **모집 상태는 그대로 두고 쓰지 않는다.** 일정이 취소돼도 참여 상태는 바꾸지 않는다. CANCELED는 되돌리지 않는다.
  - 5장 `schedule_cancellations`: Schedule 구성요소, FK O, Audit(BaseAuditEntity). 컬럼 `schedule_id`(FK → schedules, 유니크), `reason VARCHAR(100)`. 취소 일시 = created_at, 취소한 사람 = created_by. 유니크의 SQL은 명세에 없다(ERD).
  - API-S07: `cancellation {reason, canceledAt}` — 취소된 일정만, 아니면 null.
  - 8.2 이벤트: "도메인 간 새 이벤트는 만들지 않는다. 일정 취소·완료에 반응할 기능(알림, 후기)이 없기 때문이다. 그 기능이 생길 때 ScheduleCanceledEvent를 만들고 CommandService(취소)에서 발행한다."
- 이미 머지된 관련 작업과 맞물림(코드로 확인함)
  - T-12 일정 상세(#60): `Schedule.availableActions`가 HOST에 `status == SCHEDULED && startAt.isAfter(now)`일 때 `CANCEL_SCHEDULE`을 준다. 취소 판정은 이것과 같은 조건이어야 한다. `ScheduleController.getDetail`의 description과 `docs/api-guide.md`가 "취소 사유는 아직 응답에 없다", "일정 전체 취소 API는 아직 없다(T-15)"라고 적고 있어 이번에 고친다.
  - T-13 참여·참여 취소: `ScheduleCommandService.join`은 사용자 잠금 → `findByIdForUpdate`, `leave`는 `findByIdForUpdate`만. 취소된 일정에 참여하면 `RECRUIT_CLOSED`, 참여 취소하면 `PARTICIPATION_NOT_CANCELABLE`이 이미 난다(`displayStatus` = CANCELED).
  - T-16 목록: 모집 중 목록은 status = SCHEDULED만, 참여한·개설한 목록은 취소 일정도 `displayStatus = CANCELED`로 준다. 바꿀 것 없음.
  - T-17 스케줄러: 두 일괄 UPDATE 모두 `status = SCHEDULED` 조건이라 취소 일정을 건드리지 않는다. 바꿀 것 없음.
  - T-18 탈퇴 가능 검사: `status <> CANCELED`로 취소 일정을 제외한다. 바꿀 것 없음. 겹침 검사(POL-S8)도 SCHEDULED만 보므로 취소 후 참여자는 같은 시간대 다른 일정에 참여할 수 있다.

### 결정 (spec 단계에서 근거로 정함)

1. **사유는 필수 자유 입력 문자열이다(enum 아님).** 앞뒤 공백을 뺀 길이 1~100자(코드 포인트로 센다, 기존 `Schedule.hasLengthBetween`과 같은 방식). 저장 값은 앞뒤 공백을 뺀 값. 근거: POL-S13 "사유 1~100자(공백 제거 후)", D6 "사유는 필수", 컬럼 `VARCHAR(100)`(PostgreSQL은 글자 수로 센다). 줄바꿈은 허용한다(설명 필드와 같이 막을 근거가 없다).
2. **형식 오류는 요청 DTO에서 400 `INVALID_REQUEST`(errors에 `reason`)로 먼저 거르고, 도메인은 같은 규칙을 `INVALID_CANCEL_REASON`(400)으로 한 번 더 지킨다(안전망).** 근거: API-S11 "검증 제약이 있어 Request record를 둔다", `.claude/rules/api.md`의 "형식은 요청 DTO와 domain 양쪽에서 검증한다", 개설 API의 `INVALID_TITLE`·`INVALID_DESCRIPTION`과 같은 구조. 본문 누락·`reason` null도 `INVALID_REQUEST`다.
3. **도메인 판정 순서: `SCHEDULE_NOT_FOUND`(404) → `NOT_HOST`(403) → `SCHEDULE_NOT_CANCELABLE`(409) → `INVALID_CANCEL_REASON`(400).** 권한을 상태보다 먼저 봐 개설자가 아닌 사람에게 일정 상태를 알려주지 않는다. 이미 취소된 일정에 개설자가 다시 보내면 `SCHEDULE_NOT_CANCELABLE`(명세 완료 조건), 개설자가 아닌 사람이 보내면 `NOT_HOST`다.
4. **취소 가능 조건은 `status = SCHEDULED` 그리고 `startAt > now`다. RECRUITING·CLOSED(정원 마감) 모두 취소할 수 있다. `startAt == now`이면 취소할 수 없다.** 근거: 4.2 표, POL-S13, `availableActions`의 `startAt.isAfter(now)`. COMPLETED·CANCELED는 `SCHEDULE_NOT_CANCELABLE`.
5. **잠금: 트랜잭션 안에서 일정 행을 `findByIdForUpdate`로 처음 읽고 판정한다. 사용자 잠금(`lockUser`)은 걸지 않는다.** 근거: 참여(사용자 → 일정 잠금)와 겹치면, 잠그지 않은 취소는 참여가 바꾼 `confirmed_count`를 옛 값으로 덮어쓸 수 있고(같은 행 UPDATE), 동시에 들어온 두 취소는 둘 다 통과해 유니크 인덱스 위반(500)이 난다. 일정 행 잠금이면 두 번째 취소는 CANCELED를 보고 `SCHEDULE_NOT_CANCELABLE`, 취소 뒤 들어온 참여는 `RECRUIT_CLOSED`다. 취소는 겹침을 만들지 않아 사용자 잠금이 필요 없고(참여 취소와 같다), 일정 하나만 잡으므로 잠금 순서 규칙(사용자 → 일정)과 어긋나지 않는다. 스케줄러 일괄 UPDATE는 `start_at <= now`·`SCHEDULED` 조건이라 취소(`start_at > now`)와 같은 행을 바꾸지 않고, 겹쳐도 커밋 뒤 WHERE를 다시 평가해 건너뛴다.
6. **유니크는 부분 유니크 인덱스 `(schedule_id) WHERE is_deleted = FALSE`다.** 근거: `.claude/rules/package-structure.md` 삭제 절("유니크 제약은 삭제되지 않은 행에만"), V9 `uix_user_withdrawals_user`와 같은 형태. 이 인덱스는 잠금으로 직렬화한 경로의 마지막 방어선이라 위반을 오류 코드로 바꾸지 않는다(잠금 절).
7. **취소 알림 이벤트(`ScheduleCanceledEvent`)는 이번에 만들지 않는다.** 근거: 명세 8.2가 명시적으로 미룬다. 수신할 도메인(알림)이 없고, 이벤트를 쓰는 곳이 없는 이벤트 발행 포트는 쓰지 않는 코드다.
8. **`cancellation`은 일정 상세(API-S07)에만 넣는다. 목록 카드(API-S04~S06)에는 넣지 않는다.** 근거: 명세가 상세에만 정의(API-S07, T-15 범위 "상세의 cancellation"), 목록은 `displayStatus = CANCELED`로 "일정 취소됨"을 보인다(D6). 상세의 `cancellation`은 보는 사람 역할과 관계없이(GUEST 포함) 취소된 일정이면 준다(D6 "취소된 일정 상세에 보여준다", 역할 조건 없음).
9. **활성 사용자 확인(POL-S9, user 호출)은 하지 않는다. facade 없이 Controller → `ScheduleCommandService`다.** 근거: API-S11 "Controller → CommandService", 2장 서비스 표 "Controller가 직접 부르는 것: 참여 취소(API-S10), 전체 취소(API-S11)". 다른 도메인을 부르지 않으므로 facade 기준에 들지 않는다.
10. **응답은 200 `{"isSuccess":true}`(`ApiResponse<Void>`, data 없음).** 참여·참여 취소와 같다.
11. **취소해도 바꾸지 않는 것: `recruitment_status`, `confirmed_count`, 참여 행의 status.** 근거: 4.1·4.2.
12. **마이그레이션 번호는 V10**(`V10__create_schedule_cancellation.sql`). develop은 V1~V9이고 명세 표 번호는 가칭이다. T-14(SHI-178)는 지금 마이그레이션을 더하지 않는다. 머지 직전에 develop에 V10이 생겼으면 이 브랜치의 번호를 올린다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-179` (브랜치 `feat/SHI-179-schedule-cancel`, 작성 시점 베이스 `58ff8d6`)
- **구현 시작 전 전제: SHI-178(T-14 참여자 명단)이 develop에 머지된 뒤 이 브랜치를 최신 develop에 맞춘다**(`git fetch origin` 후 `git rebase origin/develop`). 그 뒤의 베이스 커밋을 기준으로 diff를 본다. 이 task.md는 T-14 코드에 기대지 않는다. 두 작업이 함께 건드릴 수 있는 파일: `ScheduleController`, `SecurityConfig`·`SecurityConfigTest`, `ScheduleControllerTest`, `docs/api-guide.md`, `docs/openapi.yaml`. 충돌하면 T-14 쪽 내용을 살리고 이 작업의 변경을 더한다. `docs/openapi.yaml`은 손으로 합치지 말고 최신화 뒤 다시 생성한다
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 기획 폴더 `C:\Users\ks\nss\dev\snsg-be\planning`도 읽기만 한다
- `snsg/.env`는 복사돼 있다
- DB·Redis 테스트는 로컬 Docker 컨테이너(`snsg-postgres`, `snsg-redis`)에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 테스트는 오케스트레이터가 정한 시점에만, 한 워크트리에서만 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). V10을 공유 DB에 적용하면 V10이 없는 다른 워크트리의 Flyway 검증은 그대로 통과한다(그쪽에 없는 마이그레이션이 DB에 있는 경우는 `ignoreMigrationPatterns` 기본값에 따라 future로 무시된다). 문제가 생기면 멈추고 보고한다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. `&&`는 PowerShell에서 동작하지 않는다. 아래 검증 명령은 Bash 기준이고 워크트리 루트에서 시작한다
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다

## GOAL

1. 마이그레이션 `snsg/src/main/resources/db/migration/V10__create_schedule_cancellation.sql`이 `schedule_cancellations`를 만든다. 컬럼: `id UUID`, `schedule_id UUID`, `reason VARCHAR(100)`, `created_at`, `updated_at`, `created_by VARCHAR(50)`, `updated_by VARCHAR(50)`, `is_deleted BOOLEAN`, 모두 NOT NULL. PK, `schedules(id)`로의 FK, `(schedule_id) WHERE is_deleted = FALSE` 부분 유니크 인덱스. 기존 V1~V9 파일은 바뀌지 않는다. 엔티티는 `BaseAuditEntity`를 상속하고 `ddl-auto: validate`를 통과한다.
   - 검증: `ls snsg/src/main/resources/db/migration` → V1~V9와 `V10__create_schedule_cancellation.sql`
   - 검증: `git diff --name-only origin/develop -- snsg/src/main/resources/db/migration` → `V10__create_schedule_cancellation.sql` 한 줄
   - 검증: 10번의 전체 빌드(DB 슬라이스 테스트가 validate로 뜬다) 통과

2. `Schedule`(애그리거트 루트)에 일정 취소 도메인 메서드가 있고, domain 단위 테스트(Spring·mock 없이)가 아래를 단정한다.
   - 개설자가 SCHEDULED·startAt > now 일정을 취소하면 status = CANCELED, 취소 행(사유 = 앞뒤 공백을 뺀 값)이 생긴다. `recruitmentStatus`, `confirmedCount`, 모든 참여 행의 status(CONFIRMED)는 그대로다
   - 모집 상태가 RECRUITING인 일정과 CLOSED(정원 마감, 시작 전)인 일정 모두 취소된다
   - startAt == now, startAt < now → `SCHEDULE_NOT_CANCELABLE`
   - status = CANCELED(이미 취소), status = COMPLETED → `SCHEDULE_NOT_CANCELABLE`
   - 개설자가 아닌 사용자 → `NOT_HOST`. 이미 취소된 일정에 개설자가 아닌 사용자가 보내도 `NOT_HOST`(판정 순서 결정 3)
   - 사유가 null·빈 문자열·공백만·앞뒤 공백을 뺀 길이 101자 → `INVALID_CANCEL_REASON`. 앞뒤 공백을 뺀 길이가 정확히 1자, 100자(이모지 100개 포함)면 통과
   - 사유가 틀린 비개설자 → `NOT_HOST`, 사유가 틀린 시작 후 일정 → `SCHEDULE_NOT_CANCELABLE`(판정 순서)
   - 취소한 일정에서 HOST의 `availableActions`는 빈 목록이고 `displayStatus`는 CANCELED다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.domain*'` → BUILD SUCCESSFUL, 실패 0, 위 경우를 다루는 테스트 포함

3. `ScheduleErrorCode`에 `NOT_HOST`(FORBIDDEN), `SCHEDULE_NOT_CANCELABLE`(CONFLICT), `INVALID_CANCEL_REASON`(INVALID)이 있다. 전역 예외 처리로 각각 403, 409, 400이 된다(기존 ErrorType → HttpStatus 변환을 그대로 쓴다).
   - 검증: `grep -n "NOT_HOST\|SCHEDULE_NOT_CANCELABLE\|INVALID_CANCEL_REASON" snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → 세 줄, ErrorType이 각각 `FORBIDDEN`, `CONFLICT`, `INVALID`

4. `ScheduleCommandService`에 일정 취소 메서드가 있다(`@Transactional`). 일정을 `findByIdForUpdate`로 읽고(없으면 `SCHEDULE_NOT_FOUND`), `lockUser`는 부르지 않는다. Mockito 테스트가 아래를 단정한다.
   - 일정이 없으면 `BusinessException(SCHEDULE_NOT_FOUND)`
   - `findByIdForUpdate`로 읽고 `lockUser`·`findById`(잠금 없는 조회)를 부르지 않는다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleCommandServiceTest'` → BUILD SUCCESSFUL, 실패 0, 위 두 경우 포함

5. 실제 PostgreSQL에 커밋까지 거친 취소를 테스트가 단정한다(`ScheduleCommandServiceTest`의 `@DataJpaTest` 중첩 클래스, `replace = NONE`, `JpaAuditingConfig` 포함).
   - SecurityContext 주체를 개설자 id로 두고 취소하면, native SELECT로 읽은 `schedule_cancellations` 행의 `created_by` = 개설자 id 문자열, `reason` = 앞뒤 공백을 뺀 값이고, `schedules.status` = `CANCELED`, `schedules.recruitment_status`·`confirmed_count`는 취소 전 값, 멤버를 포함한 참여 행의 status는 모두 `CONFIRMED`다
   - 동시성: 같은 일정에 개설자의 취소 두 건을 동시에(`CountDownLatch` 둘) 부르면 하나만 성공하고 다른 하나는 `SCHEDULE_NOT_CANCELABLE`이며, 커밋된 `schedule_cancellations` 행은 하나다. 테스트가 커밋한 행은 `@AfterEach`에서 그 테스트의 개설자 id 기준으로 지운다(`schedule_cancellations` → `schedule_participants` → `schedules` 순서, FK). 기존 `Concurrency` 정리 쿼리에도 `schedule_cancellations` 삭제를 먼저 더한다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleCommandServiceTest'` 를 3회 연속 → 매번 BUILD SUCCESSFUL, 실패 0

6. `POST /api/v1/schedules/{scheduleId}/cancellation`이 있다. 요청 본문 `{"reason": "..."}`, 인증 필요, 성공 200 `{"isSuccess":true}`(data 없음). Controller는 `ScheduleCommandService`를 직접 부르고 인증 주체 userId와 scheduleId, reason을 넘긴다. `@WebMvcTest` 테스트가 아래를 단정한다.
   - 정상 요청 → 200, 서비스에 scheduleId·userId·reason이 넘어간다
   - `reason`이 없음·null·`""`·공백만·앞뒤 공백을 뺀 길이 101자 → 400 `INVALID_REQUEST`, `errors`에 field `reason`, 서비스는 불리지 않는다. 앞뒤 공백을 포함해 보내도 뺀 길이가 100자면 통과
   - 서비스가 `NOT_HOST`·`SCHEDULE_NOT_CANCELABLE`을 던지면 각각 403·409와 그 code
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

7. 토큰 없이 `POST /api/v1/schedules/{scheduleId}/cancellation`을 부르면 401이다(공개 경로로 열지 않는다).
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0, 이 경로의 401을 단정하는 테스트 포함

8. 일정 상세(`GET /api/v1/schedules/{scheduleId}`) 응답에 `cancellation` 필드가 있다. 취소된 일정이면 `{reason, canceledAt}`(`canceledAt` = 취소 행의 created_at, 오프셋 없는 한국 시간 `LocalDateTime`), 취소되지 않은 일정이면 `null`. 보는 사람 역할과 관계없이 같다. 목록 카드 응답(`ScheduleCardResponse`)에는 더하지 않는다.
   - 실제 PostgreSQL 테스트가 단정한다: 일정을 만들고 취소한 뒤 영속성 컨텍스트를 비우고(flush·clear) 상세 조회 경로(`ScheduleQueryService.findDetail` 또는 그것이 쓰는 repository 조회)로 읽으면 `displayStatus = CANCELED`, cancellation의 reason·canceledAt(취소 행 created_at과 같음)이 담긴다. 취소하지 않은 일정은 cancellation이 비어 있다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule*'` → BUILD SUCCESSFUL, 실패 0, 위 경우를 다루는 테스트 포함
   - 검증: `grep -n "cancellation" docs/openapi.yaml` → 상세 응답 스키마(`ScheduleDetailResponse`)에 `cancellation`이 있고 그 description에 취소되지 않은 일정이면 null이라고 적혀 있다, 취소 API 경로 `/api/v1/schedules/{scheduleId}/cancellation`이 있다. `ScheduleCardResponse` 스키마에는 `cancellation`이 없다

9. 문서
   - `docs/openapi.yaml`을 `OPENAPI_UPDATE=true`로 다시 만들었고 코드와 일치한다. 취소 API의 `@Operation`·`@ApiResponses`에 200·400(`INVALID_REQUEST`, `INVALID_CANCEL_REASON`)·401·403(`NOT_HOST`)·404(`SCHEDULE_NOT_FOUND`)·409(`SCHEDULE_NOT_CANCELABLE`), 판정 순서, 두 번 눌렸을 때(두 번째는 409 `SCHEDULE_NOT_CANCELABLE`, 첫 요청이 이미 성공한 것), 성공·실패 후 상세를 다시 부른다는 것이 있다. 요청 `reason`의 `@Schema`에 필수·앞뒤 공백을 뺀 1~100자·저장 값은 뺀 값·예시가 있다. 상세 description의 "일정 취소 사유는 아직 응답에 없다" 문장은 사라진다
     - 검증: `cd snsg && OPENAPI_UPDATE=true ./gradlew cleanTest test --tests '*OpenApiSpecTest'` 실행 후 `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
     - 검증: `grep -n "아직 응답에 없다" docs/openapi.yaml snsg/src/main/java/com/shineunsigan/snsg/schedule/presentation/ScheduleController.java` → 출력 없음
   - `docs/api-guide.md`에 "일정 전체 취소" 절이 있다: 부르는 화면(상세 HOST의 [일정 취소] → 사유 입력 팝업), 요청 필드 규칙, 응답, 에러 표(HTTP·code·조건·화면에서 할 일)와 판정 순서, 예시 JSON, 두 번 눌렸을 때, 취소 후 참여자에게 보이는 것(상세 `CANCELED`와 사유, 참여한 일정 목록의 `CANCELED`, 참여·참여 취소 불가), 알림은 없다는 것. 상세 절의 `cancellation` 필드 설명을 더하고, "`cancellation`은 아직 응답에 없다"·"`CANCEL_SCHEDULE` … 아직 없다(T-15에서 생긴다)"·"일정 전체 취소 등 위 표에 없는 일정 API는 아직 없다" 문구를 현재 상태로 고치며, 화면별 API 표에 일정 취소 행을 더한다
     - 검증: `grep -n "T-15\|아직 응답에 없다\|아직 없다" docs/api-guide.md` → 일정 취소·cancellation에 관한 줄이 없다(알림 API처럼 실제로 아직 없는 것을 말하는 줄은 남아도 된다)
     - 검증: `grep -n "/cancellation\|NOT_HOST\|SCHEDULE_NOT_CANCELABLE\|INVALID_CANCEL_REASON" docs/api-guide.md` → 취소 절과 화면별 API 표에서 나온다

10. 전체 빌드가 통과한다(`ArchitectureTest` 포함. 서비스는 repository 외 `port/out`에 의존하지 않고, facade를 새로 만들지 않는다).
    - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0

11. 범위 밖 파일을 고치지 않는다.
    - 검증: `git diff --name-only origin/develop` → 경로가 모두 `snsg/src/main/java/com/shineunsigan/snsg/schedule/`, `snsg/src/test/java/com/shineunsigan/snsg/schedule/`, `snsg/src/test/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfigTest.java`, `snsg/src/main/resources/db/migration/V10__create_schedule_cancellation.sql`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/`(task.md, 규칙 갱신 단계의 문서) 안이다. `auth`·`user`·`place`·`common`의 main 코드, `SecurityConfig.java`, 기존 마이그레이션 V1~V9는 없다

## 비목표

- `ScheduleCanceledEvent`·이벤트 발행 포트·알림을 만들지 않는다(결정 7)
- 목록 3종(`findRecruiting`·`findParticipating`·`findHosted`)의 쿼리와 카드 응답은 바꾸지 않는다(결정 8)
- 스케줄러(`ScheduleStatusScheduler`, `closeRecruitmentStarted`·`completeStartedBy`), 탈퇴 가능 검사(`findLatestConfirmedNotCanceledStartAtAfter`), 겹침 검사 쿼리는 바꾸지 않는다. 이미 CANCELED를 제외한다
- `Schedule.availableActions`의 `CANCEL_SCHEDULE` 조건은 바꾸지 않는다. 새 취소 판정이 같은 조건을 쓰도록 하는 것은 괜찮다(같은 private 판정을 함께 쓰게 하는 정도). 다른 동작의 판정은 건드리지 않는다
- 활성 사용자 확인(user 호출), facade를 두지 않는다(결정 9)
- `SecurityConfig.java`는 고치지 않는다. 이 경로는 `anyRequest().authenticated()`에 이미 걸린다. 공개 경로 규칙을 고치지 않는다
- 운영자 취소, 취소 철회, 일정 삭제는 만들지 않는다
- 참여 행 status, `recruitment_status`, `confirmed_count`를 취소 때 바꾸지 않는다(결정 11)
- 부분 유니크 인덱스 위반을 오류 코드로 바꾸지 않는다(결정 6)
- 기획 폴더의 명세(`be-spec-mvp.html`, `erd.md`)를 고치지 않는다. 명세 갱신(T-15 완료 표시)은 오케스트레이터 몫이다
- 기존 테스트의 단정을 고치지 않는다. 예외: `ScheduleCommandServiceTest.Concurrency`의 `@AfterEach` 정리 쿼리에 `schedule_cancellations` 삭제를 더하는 것(GOAL 5), 그리고 상세 응답에 필드가 늘어 깨지는 단정이 있으면 `cancellation`을 반영하는 만큼만 고친다
- 판단이 갈리는 경우
  - T-14 머지 뒤 `ScheduleController`·`ScheduleControllerTest`·`SecurityConfigTest`에 T-14의 의존(`@MockitoBean`)이 늘어 있으면 그대로 두고 이 작업의 것을 더한다
  - 머지 직전 develop에 V10이 이미 있으면 이 파일 번호를 다음 번호로 올리고 GOAL 1·11의 파일 이름도 그 번호로 읽는다
  - 동시성 테스트가 간헐 실패하면 sleep·재시도로 덮지 말고 잠금이 첫 조회에 걸렸는지부터 본다(`.claude/rules/testing.md`)

## 제약

- 패키지·계층·잠금·마이그레이션·삭제·Audit 규칙은 `.claude/rules/package-structure.md`를 따른다(특히 "Service와 Facade", "Entity"의 BaseAuditEntity, "삭제", "FK", "마이그레이션", "동시 요청과 잠금")
- API·요청 검증·응답·문서 규칙은 `.claude/rules/api.md`를 따른다
- 테스트는 `.claude/rules/testing.md`를 따른다. 테스트는 원래 코드와 별도 커밋이다. 동시성 테스트는 기존 `Concurrency` 중첩 클래스에 메서드로 더한다(DB 슬라이스 구성을 늘리지 않는다)
- `docs/openapi.yaml`은 API를 바꾼 코드와 같은 커밋에 넣는다(`.claude/rules/api.md` 문서 절)
- **목록 조회에서 일정마다 취소 행을 읽는 쿼리가 추가로 나가면 안 된다.** `Schedule`에 역방향 `@OneToOne(mappedBy)`를 두면 Hibernate가 지연 로딩하지 못해 목록·잠금 조회마다 일정 수만큼 SELECT가 나간다. 취소 행은 상세에서만 필요하다
- 잠금 조회(`findByIdForUpdate`)에 취소 행이나 참여 행을 fetch join하지 않는다
- 엔티티 컬럼에 `@Column(name = ...)`을 쓰지 않는다. 모든 컬럼 NOT NULL
- 임시 파일은 세션 스크래치패드에 둔다(`.claude/rules/temp-files.md`). 워크트리에 남기지 않는다
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 제안(구현이 바꿔도 된다)
  - 엔티티 이름 `ScheduleCancellation`(`schedule/domain/model`, `BaseAuditEntity`, `@ManyToOne(fetch = LAZY)` 또는 `@OneToOne(fetch = LAZY)` 소유 측으로 `schedule_id` 매핑, 생성은 `Schedule`이 만드는 정적 팩토리). 루트를 통해 만들고 저장은 `Schedule`의 cascade(PERSIST) 또는 같은 서비스가 쓰는 repository로 한다. 참고 사례: user `UserWithdrawal`(소유 측 `@OneToOne(fetch = LAZY)`, 별도 repository)
  - 상세에서 취소 행을 읽는 방법: `ScheduleRepository`에 scheduleId로 삭제되지 않은 취소 행을 읽는 메서드를 두고 `ScheduleQueryService.findDetail`이 CANCELED일 때만 부른다. 또는 `Schedule`에 지연 로딩 `@OneToMany(mappedBy)` 컬렉션(0~1건)을 두고 상세 트랜잭션 안에서 읽는다. 어느 쪽이든 목록에서는 읽지 않는다(제약)
  - 결과 전달: `ScheduleDetailQueryResult`에 `Cancellation(reason, canceledAt)` 중첩 record(없으면 null)를 더하고 `ScheduleDetailResponse`에 `@Schema(nullable = true)`인 `Cancellation` 중첩 record(`@Schema(name = "ScheduleDetailCancellation")`)
  - 서비스 메서드 이름 `cancel(UUID scheduleId, UUID userId, String reason)`, 도메인 메서드 `Schedule.cancel(UUID userId, String reason, LocalDateTime now)`. Command record는 필드가 셋이라 만들어도 되고 인자로 넘겨도 된다(기존 `join`·`leave`는 인자)
  - 요청 DTO `CancelScheduleRequest(reason)`. 검증은 `CreateScheduleRequest`처럼 `@NotNull` + `@Pattern`(앞뒤 공백을 뺀 1~100자, 줄바꿈 포함: `^\s*\S(?:[\s\S]{0,98}\S)?\s*$`). Java 정규식은 보조 문자(이모지)를 한 글자로 센다
  - 오류 문구 제안: `NOT_HOST` "내가 연 일정만 취소할 수 있어요.", `SCHEDULE_NOT_CANCELABLE` "이미 취소됐거나 시작한 일정은 취소할 수 없어요.", `INVALID_CANCEL_REASON` "취소 사유는 1~100자로 적어주세요."(요청 DTO 문구와 같게)
  - 마이그레이션 상단 주석: 일정 구성요소라 FK를 건다, 취소 일시 = created_at, 취소한 사람 = created_by, 운영자 취소도 같은 테이블을 쓴다. 인덱스 이름 예: `uix_schedule_cancellations_schedule`
- created_by는 JPA Auditing(`SecurityAuditorAware`)이 SecurityContext의 principal(요청의 userId)에서 채운다. 컨트롤러 경로에서는 인증 필터가 넣는다. DB 테스트에서는 `UsernamePasswordAuthenticationToken(hostId, null, List.of())`를 넣고 `@AfterEach`에서 `clearContext()`(`ScheduleCommandServiceTest.SavedSchedule` 사례). `schedules.updated_by`도 개설자 id가 된다
- 명세 C3: 사유 입력 팝업은 FE 몫이다. api-guide에 팝업이 필요하다는 것과 사유 규칙을 적는다
- 남은 access token(최대 30분)으로 탈퇴한 개설자가 취소를 부르는 경우는 막지 않는다(결정 9). 탈퇴 검사(T-18)가 시작 전 개설 일정이 있으면 탈퇴를 막으므로, 사용자 요청 탈퇴 경로에서는 생기지 않는다. 카카오 연결 끊기 웹훅 탈퇴(검사 없음)에서만 가능하다
- 취소 후 영향(기존 코드로 이미 처리됨): 취소된 일정에 참여 → `RECRUIT_CLOSED`, 참여 취소 → `PARTICIPATION_NOT_CANCELABLE`, 모집 중 목록에서 빠짐, 참여한·개설한 목록에는 완료 시각 전까지 `CANCELED`, 탈퇴를 막지 않음, 겹침 검사에서 빠짐. api-guide 취소 절에 적을 내용이다
- PR background에 적을 것: 명세 POL-S13에 판정 순서가 없어 결정 3으로 정했다는 것, 잠금 결정 5의 근거

## 진행 중 고친 GOAL

- GOAL 8 검증 문구(오케스트레이터, verify 1회차 뒤): "cancellation이 nullable" → "description에 null이라고 적혀 있다". springdoc이 OpenAPI 3.1.0으로 생성하며 `$ref` 필드의 `@Schema(nullable = true)`를 출력하지 않는다(`docs/openapi.yaml` 전체에 `nullable` 0건, 기존 `minAgeGroup`·`maxAgeGroup`도 description으로 null을 알린다). `types = {"object","null"}`은 `$ref`와 AND로 묶여 null을 무효로 만드는 잘못된 스키마라 쓰지 않는다. 응답 동작(취소 안 된 일정은 null)은 바뀌지 않는다.

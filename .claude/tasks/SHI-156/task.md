## 배경

- Linear SHI-156. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-10 "일정 개설 (schedule)"을 구현한다. 화면은 CRT-06 [일정 열기]·CRT-07이다.
- 사용자 요구: "T-10 일정 개설 진행해줘. 부족한 요구사항이 있는지 먼저 확인하고, 진행에 문제가 없을지 면밀히 검토 후 진행."
- 선행 T-07(place, `FindPlaceUseCase`, PR #44)과 T-08(schedule 패키지·`ActivityCategory`·`ActivityPlaceType`, PR #45)은 develop에 머지돼 있다.
- 사용자와 합의한 결정
  - RegionPort는 T-10에서 만들지 않는다. 처음 쓰는 곳인 T-11(INVALID_REGION)로 넘긴다. 명세의 T-10 범위 문구는 오케스트레이터가 고친다. 활성 지역 판정은 place `FindPlaceUseCase.find`가 이미 한다(노출 대상만 돌려준다).
  - place `PlaceType` → schedule `ActivityPlaceType` 변환은 adapter의 `default` 없는 switch로 하고, `UNCLASSIFIED`는 "노출 대상 아님"으로 보아 빈 결과로 바꾼다. 사용자는 400 `INVALID_PLACE`를 받는다.
  - 장소 사본 값 객체의 이름은 `PlaceSnapshot`이다. place의 `@Entity Place`와 이름을 겹치지 않는다.
  - schedule의 adapter 이름은 `ScheduleUserAdapter`(→ user), `SchedulePlaceAdapter`(→ place)다. auth의 `UserAdapter`(빈 이름 `userAdapter`)와 겹치면 기동 시 `ConflictingBeanDefinitionException`이 난다.
  - 마이그레이션 번호는 develop의 다음 번호 V7이다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-156` (브랜치 `feat/SHI-156-schedule-create`, 베이스 `845f085`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리는 없다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V6이다: `V1__create_user.sql`, `V2__create_region.sql`, `V3__extend_user_profile.sql`, `V4__create_place.sql`, `V5__insert_reviewed_places.sql`, `V6__create_activity_category.sql`.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 로컬 DB에 Flyway를 적용한다. DB가 꺼져 있으면 이 테스트들이 실패한다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 그 클래스에 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.

1. 마이그레이션 `V7__create_schedule.sql`이 `schedules`와 `schedule_participants`를 만든다.
   - `schedules`: BaseAuditEntity 공통 컬럼(`id`, `created_at`, `updated_at`, `is_deleted`, `created_by`, `updated_by`), `host_user_id` UUID, `host_type` VARCHAR(20), `category_id` UUID, `title` VARCHAR(20), `description` VARCHAR(500), `duration_type` VARCHAR(10), `start_at` TIMESTAMP, `capacity` INT, `confirmed_count` INT, `status` VARCHAR(20), `recruitment_status` VARCHAR(20), `place_id` UUID, `place_name` VARCHAR(100), `place_road_address` VARCHAR(200), `place_address_detail` VARCHAR(100), `place_latitude` NUMERIC(10,7), `place_longitude` NUMERIC(10,7), `region_code` CHAR(5). 모두 NOT NULL이다. `host_user_id`·`category_id`·`place_id`·`region_code`에는 FK가 없다.
   - `schedule_participants`: BaseEntity 공통 컬럼(`id`, `created_at`, `updated_at`, `is_deleted`), `schedule_id` UUID(FK → schedules), `user_id` UUID(FK 없음), `role` VARCHAR(10), `status` VARCHAR(20). 모두 NOT NULL이다.
   - 제약·인덱스: `ck_schedules_count CHECK (confirmed_count >= 0 AND confirmed_count <= capacity)`, `ix_schedules_status_start (status, start_at, id)`, `ix_schedules_host (host_user_id, start_at)`, `uix_participants_confirmed (schedule_id, user_id) WHERE status = 'CONFIRMED' AND is_deleted = FALSE`, `ix_participants_user (user_id, status)`.
   - 검증: `ls /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/resources/db/migration` → V1~V6 여섯 개와 `V7__create_schedule.sql`, 모두 일곱 개
   - 검증: `grep -ciE "ck_schedules_count|ix_schedules_status_start|ix_schedules_host|uix_participants_confirmed|ix_participants_user" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/resources/db/migration/V7__create_schedule.sql` → 5 이상
   - 검증: 앱 기동과 `@DataJpaTest`가 `ddl-auto: validate`로 엔티티와 스키마를 대조해 통과한다(GOAL 12의 build).
   - 검증: `ScheduleRepositoryImplTest`(실제 PostgreSQL)가 다음을 확인한다.
     - 같은 일정·같은 사용자의 CONFIRMED 행을 하나 더 넣으면 DB가 거부한다.
     - 기존 행이 CANCELED면 같은 일정·같은 사용자의 CONFIRMED 행이 허용된다.
     - `confirmed_count > capacity`인 행은 CHECK가 거부한다.

2. 일정 애그리거트 `Schedule`(BaseAuditEntity 상속)이 다음을 만족한다.
   - 개설로 만든 일정의 상태: `status` SCHEDULED, `recruitment_status` RECRUITING, `host_type` USER, `confirmed_count` 1, 참여 행은 개설자의 HOST·CONFIRMED 행 하나뿐이다.
   - 장소 사본은 `@Embeddable` 값 객체 `PlaceSnapshot`(name, roadAddress, addressDetail, latitude, longitude, regionCode)이 가진다. `place_id`는 일정이 따로 가진다.
   - enum 값은 다음과 같다.
     - `ScheduleStatus`: SCHEDULED, CANCELED, COMPLETED
     - `RecruitmentStatus`: RECRUITING, CLOSED
     - `ParticipationStatus`: CONFIRMED, CANCELED
     - 참여 역할: HOST, MEMBER
     - 개설 주체: USER, OWNER
     - `DurationType`: SHORT(60분), NORMAL(120분), LONG(180분). 분은 enum이 가진다.
   - 일정 내용(제목·설명·시작·인원·장소)을 바꾸는 메서드와 setter가 없다(POL-S15).
   - 검증: `grep -rn "class PlaceSnapshot" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/model` → 한 줄
   - 검증: `grep -rn "@Setter\|public void set" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain` → 출력 없음
   - 검증: `ScheduleTest`(순수 JUnit)가 개설 직후의 상태·`confirmed_count`·HOST 행을 단정한다.

3. 형식 규칙은 도메인이 불변식으로 지킨다(POL-S3·S5·S6·S7).
   - 제목: 앞뒤 공백을 뺀 값이 1~20자. 저장되는 값은 공백을 뺀 값이다. 위반하면 `INVALID_TITLE`
   - 설명: 앞뒤 공백을 뺀 값이 10~500자. 저장되는 값은 공백을 뺀 값이다. 위반하면 `INVALID_DESCRIPTION`
   - 시작 일시: 분이 00 또는 30이고 초·나노초가 0. `now + 1시간 ≤ startAt`(같으면 허용). `startAt`의 날짜 ≤ 오늘 + 30일(30일째 날 23:30은 허용, 31일째 날 00:00은 거부). 위반하면 `INVALID_START_AT`
   - 인원: 설정값 `schedule.capacity-min`(4) 이상, `schedule.capacity-max`(24) 이하. 위반하면 `INVALID_CAPACITY`
   - 검증: `ScheduleTest`가 다음 경계값을 단정한다.
     - 제목: 0자(공백만), 1자, 20자, 21자, 앞뒤 공백 포함 20자
     - 설명: 9자, 10자, 500자, 501자
     - 시작: 분 15, 초 1, now = 10:00:00일 때 11:00 허용·10:30 거부, now = 10:00:01일 때 11:00 거부, 30일째 날 23:30 허용, 31일째 날 00:00 거부
     - 인원: 3, 4, 24, 25

4. 표시 상태 `Schedule.displayStatus(now)`가 명세 4.3을 위에서부터 판정한다.
   - CANCELED(status = CANCELED) → COMPLETED(status = COMPLETED 또는 `start_at + schedule.completion-after(12h) ≤ now`) → RECRUITMENT_CLOSED(recruitment_status = CLOSED 또는 `start_at ≤ now`) → RECRUITING
   - 검증: `ScheduleTest`가 네 값 각각과 경계를 단정한다.
     - 시작 직전 RECRUITING
     - `start_at = now`면 RECRUITMENT_CLOSED(스케줄러 전)
     - `start_at + 12h = now`면 COMPLETED
     - `start_at + 12h − 1분`이면 RECRUITMENT_CLOSED
     - CANCELED는 시각과 무관하게 CANCELED
   - 테스트에서 status·recruitment_status를 바꾸려고 프로덕션 setter를 만들지 않는다. CANCELED·COMPLETED·CLOSED 상태는 리플렉션(`ReflectionTestUtils` 등)이나 테스트 전용 경로로 만든다.

5. `CreateScheduleUseCase`(트랜잭션 없음)는 다음 순서로 처리하고 실패 시 해당 코드를 던진다(POL-S1·S2·S9).
   - 활성 사용자 확인: `UserPort.isActive(userId)`가 false면 `USER_NOT_ACTIVE`(ErrorType UNAUTHENTICATED → 401)
   - 활동 조회: 없거나 비활성·삭제된 활동이면 `INVALID_CATEGORY`
   - 장소 조회: `PlacePort.find(placeId)`가 비었으면 `INVALID_PLACE`. 장소 유형이 활동의 `place_type`과 다르면 `INVALID_PLACE`
   - 위를 통과하면 `ScheduleCommandService`에 장소 사본을 넘겨 저장하고 일정 id를 돌려준다.
   - 검증: `CreateScheduleUseCaseTest`(Mockito)가 다음을 단정한다.
     - 다섯 분기(비활성 사용자, 활동 없음·비활성, 장소 없음, 유형 불일치, 정상)
     - 실패 분기에서는 `ScheduleCommandService`가 호출되지 않는다.
     - 비활성 사용자면 활동·장소 조회도 호출되지 않는다.
   - 검증: `grep -n "USER_NOT_ACTIVE" -A0 /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → `ErrorType.UNAUTHENTICATED`와 같은 줄

6. `ScheduleCommandService`의 개설은 한 트랜잭션에서 다음 순서로 처리한다(POL-S1·S8·S11 ①).
   - 순서: 사용자 잠금 → 활동 재확인 → 겹침 검사 → 일정과 HOST 참여 행 저장
   - 사용자 잠금: `pg_advisory_xact_lock(hashtext(<userId 문자열>))`. repository 메서드 하나다.
   - 활동 재확인: 트랜잭션 안에서 활동이 없거나 비활성이면 `INVALID_CATEGORY`
   - 겹침 검사: 내가 CONFIRMED(HOST·MEMBER 모두)이고 삭제되지 않은 참여 행이 걸린 일정 중, 삭제되지 않았고 status = SCHEDULED이며 `|start_at − 새 start_at| < schedule.overlap-window(12h)`인 일정이 있으면 `SCHEDULE_TIME_CONFLICT`. 정확히 12시간 차이는 허용한다.
   - 검증: `grep -rn "pg_advisory_xact_lock" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure` → 한 줄 이상
   - 검증: `ScheduleRepositoryImplTest`(실제 PostgreSQL, 참여 행·일정은 native INSERT)가 겹침 쿼리에 대해 다음을 단정한다.
     - 11시간 59분 차이: 겹침(앞·뒤 양방향 모두)
     - 정확히 12시간 차이: 겹치지 않음
     - MEMBER로 확정 참여한 일정도 겹침으로 잡힌다.
     - 다음은 겹침에서 빠진다: CANCELED 참여 행, CANCELED·COMPLETED 일정, 삭제된 일정, 삭제된 참여 행, 다른 사용자의 일정
   - 검증: `ScheduleCommandServiceTest`(Mockito)가 다음을 단정한다.
     - 잠금이 겹침 검사보다 먼저 호출된다(InOrder).
     - 겹침이 있으면 `SCHEDULE_TIME_CONFLICT`가 나고 저장하지 않는다.
     - 활동 재확인 실패면 `INVALID_CATEGORY`

7. 같은 사용자의 동시 개설 2건 중 1건만 성공한다(POL-S11 ①, 명세 T-10 완료 조건).
   - 실제 PostgreSQL에서 테스트 트랜잭션을 끈 채로 진행한다(예: `@Transactional(propagation = NOT_SUPPORTED)`).
   - 두 스레드가 같은 사용자·같은 `startAt`으로 `ScheduleCommandService` 개설을 동시에 부른다. latch 등으로 시작을 맞춘다.
   - 기대: 1건 성공, 1건 `SCHEDULE_TIME_CONFLICT`. DB에는 그 사용자의 일정 1행과 CONFIRMED HOST 행 1개만 있다.
   - 테스트가 커밋한 행은 테스트가 끝나면 지운다. 사용자 id는 테스트마다 무작위 UUID다(DB를 모든 워크트리가 공유한다).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg && ./gradlew test --tests '*ScheduleCommandService*'` → BUILD SUCCESSFUL. 동시성 테스트가 skipped가 아니다.
   - 검증: 같은 명령을 연속 3회 실행해 3회 모두 통과한다.
   - 검증: 테스트 실행 후 `schedules`에 테스트가 만든 행이 남지 않는다. 테스트 코드의 정리 단계(`@AfterEach` 등)를 읽어 확인한다.

8. 정상 개설의 저장 결과가 명세 완료 조건과 같다.
   - `place_id`와 장소 사본(이름·도로명 주소·상세주소·위도·경도·지역 코드)이 place의 노출 장소 값과 같다.
   - `created_by`가 개설자 userId 문자열이다.
   - 개설 후 places 행을 고치거나(`name`·`road_address` 변경) 폐업(`is_closed = true`) 처리해도 일정의 장소 사본은 그대로다.
   - 검증: `ScheduleCommandServiceTest` 또는 별도 JPA 슬라이스 테스트(실제 PostgreSQL, `JpaAuditingConfig` `@Import`, SecurityContext에 개설자 principal 세팅)가 위 세 가지를 단정한다. 클래스 이름은 구현이 정한다. 단 테스트 이름 규칙(testing.md)을 따른다.

9. `SchedulePlaceAdapter`가 place `FindPlaceUseCase.find`를 부르고, 노출 대상이 아닌 장소는 빈 결과가 된다(POL-S2, D20).
   - place `PlaceType` → `ActivityPlaceType` 변환은 `default` 없는 switch다. `UNCLASSIFIED`는 빈 결과다.
   - 검증: `SchedulePlaceAdapterTest`(실제 PostgreSQL. `SchedulePlaceAdapter`·`PlaceQueryService`·place repository 구현을 `@Import`, places 행은 native INSERT)가 다음을 단정한다.
     - 다음은 빈 결과: 없는 id, CANDIDATE, REJECTED, 폐업(`is_closed = true`), 삭제된 장소, 비활성 지역 장소
     - APPROVED 장소는 유형이 `ActivityPlaceType`으로 바뀌고 이름·주소·상세주소·좌표·지역 코드가 그대로 온다.
     - 활동과 유형이 다른 장소의 `INVALID_PLACE`는 GOAL 5의 UseCase 테스트가 맡는다.
   - 검증: `grep -n "default" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/client/SchedulePlaceAdapter.java` → switch의 `default` 없음
   - 검증: `ls /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/client/` → `SchedulePlaceAdapter.java`, `ScheduleUserAdapter.java` 포함

10. `POST /api/v1/schedules`(API-S03)
    - 인증이 필요하다. 요청 필드는 `categoryId`, `placeId`, `title`, `durationType`, `description`, `startAt`, `capacity` 일곱 개다. 장소명·주소·좌표는 받지 않는다.
    - 성공하면 201 `{isSuccess: true, data: {scheduleId}}`이다.
    - 요청 단계 검증
      - 필드 누락, 제목·설명 형식 위반은 400 `INVALID_REQUEST` + `errors`(해당 field)다. 제목·설명 길이는 앞뒤 공백을 뺀 길이로 본다. 예: 앞뒤 공백 포함 22자이고 뺀 값이 20자인 제목은 요청 단계를 통과한다.
      - `durationType`이 SHORT·NORMAL·LONG 밖이면 400 `INVALID_REQUEST`다.
      - 인원 범위와 시작 일시 규칙은 요청 단계에서 보지 않는다. 설정값·현재 시각이 필요하다. 도메인에서 `INVALID_CAPACITY`·`INVALID_START_AT`로 나간다.
    - 검증: `ScheduleControllerTest`(`@WebMvcTest`, `addFilters = false`, SecurityContext에 userId 세팅, UseCase는 `@MockitoBean`)가 다음을 단정한다.
      - 정상 201과 `data.scheduleId`
      - `title` 누락 시 400 `INVALID_REQUEST`와 `errors[].field = title`
      - 공백 제외 21자 제목 400
      - 앞뒤 공백 포함·공백 제외 20자 제목 통과
      - 공백 제외 9자 설명 400
      - 잘못된 `durationType` 400 `INVALID_REQUEST`
    - 검증: 앱을 띄운 상태(`cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg && ./gradlew bootRun`, 확인 후 반드시 멈춘다)에서 `curl -s -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' http://localhost:8080/api/v1/schedules` → 마지막 줄 `401`, 본문 `code`가 `UNAUTHENTICATED`

11. FE용 문서를 갱신한다.
    - `docs/openapi.yaml`에 `POST /api/v1/schedules`가 들어가고 코드와 일치한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg && ./gradlew test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
    - 검증: `grep -n "/api/v1/schedules" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/docs/openapi.yaml /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/docs/api-guide.md` → 두 파일 모두 한 줄 이상
    - `docs/api-guide.md`의 "일정 개설" 절에 들어갈 내용
      - 인증 필요, 부르는 화면(CRT-06 [일정 열기]), 성공 시 201 `{scheduleId}`
      - 요청 필드마다 의미·형식·범위
        - `startAt`: 오프셋 없는 한국 시간, 30분 단위, 지금+1시간~오늘+30일
        - `capacity`: 개설자 포함 4~24
        - `durationType`: 값마다 뜻
        - `placeId`: 장소 후보 API(T-11, 아직 없음)의 id
      - 오류마다 HTTP 상태·code·발생 조건·화면에서 할 일
        - `INVALID_REQUEST`, `INVALID_CATEGORY`, `INVALID_PLACE`, `INVALID_TITLE`, `INVALID_DESCRIPTION`, `INVALID_START_AT`, `INVALID_CAPACITY`, `SCHEDULE_TIME_CONFLICT`, `USER_NOT_ACTIVE`, 401
      - 두 번 눌렸을 때: 두 번째 요청은 409 `SCHEDULE_TIME_CONFLICT`이고 중복 개설은 생기지 않는다. 첫 응답 전까지 버튼을 막는다.
    - "화면별로 쓰는 API" 표에 일정 개설 행이 더해진다. "일정 개설·조회와 알림 API는 아직 없다" 문장은 개설이 생긴 사실에 맞게 고친다.

12. 전체 빌드와 테스트가 통과한다. ArchUnit 테스트도 포함된다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg && ./gradlew build` → BUILD SUCCESSFUL, 테스트 실패 0
    - 검증: `grep -l 'skipped="[1-9]' /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/build/test-results/test/TEST-com.shineunsigan.snsg.schedule.*.xml` → 출력 없음(schedule 테스트가 하나도 건너뛰어지지 않았다)
    - 검증: `grep -rln "com.shineunsigan.snsg.user\|com.shineunsigan.snsg.place" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src/main/java/com/shineunsigan/snsg/schedule` → `schedule/infrastructure/` 아래 파일만

13. 범위 밖 파일을 고치지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156 && git status --short`. 기대 결과는 다음과 같다.
      - `snsg/.env`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md`, 기존 마이그레이션 V1~V6, `user/`·`place/`·`auth/`·`common/` 패키지의 main 파일이 없다(SecurityConfig도 고치지 않는다. `anyRequest().authenticated()`가 이미 막는다).
      - 임시 파일이 없다.
    - 검증: `grep -rn "RegionPort" /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg/src` → 출력 없음

## 비목표

- RegionPort와 지역 adapter는 만들지 않는다. T-11로 넘긴다(사용자 결정).
- 다음은 만들지 않는다.
  - 장소 후보 API(T-11), `PlacePort.findExposed`
  - 상세(T-12)와 viewerRole·availableActions
  - 참여·참여 취소(T-13)와 일정 단위 `SELECT … FOR UPDATE` 잠금
  - 명단(T-14)
  - 전체 취소와 `schedule_cancellations`(T-15)
  - 목록(T-16)
  - 상태 전환 스케줄러(T-17)
  - 탈퇴 가능 검사(T-18)
  - `ScheduleQueryService`
- 상태 전이 메서드(참여·취소·마감·완료)를 만들지 않는다. 개설과 `displayStatus(now)`만 만든다.
- 설정은 T-10이 쓰는 것만 더한다: `schedule.capacity-min`, `capacity-max`, `min-lead-time`, `max-advance-days`, `overlap-window`, `completion-after`. `withdrawal-block-after-start`, `page-size-*`는 더하지 않는다.
- `host_type`은 응답에 내보내지 않는다(D12).
- `activity_categories` ↔ `schedules` FK를 걸지 않는다(명세 3.3, 개설 시 애플리케이션이 확인).
- place·user·auth·common 코드, `FindPlaceUseCase`·`FindUserUseCase`는 바꾸지 않는다.
- 기존 마이그레이션 V1~V6, `.env`, Flyway 설정은 바꾸지 않는다.
- 로컬 DB를 사용자 확인 없이 초기화하지 않는다. Flyway 검증 오류(체크섬 불일치, "Detected resolved migration not applied to database" 등)를 만나면 그 자리에서 멈추고 오류 메시지 원문을 보고한다.
- 기존 테스트의 단정과 기존 코드의 경고·스타일은 건드리지 않는다. `api-guide.md`의 기존 절(인증·지역·활동)은 고치지 않는다. "화면별로 쓰는 API" 표와 "아직 없다" 문장만 고친다.
- 동시성 테스트가 간헐적으로 실패하면 sleep·재시도로 덮지 않는다. 잠금 위치(겹침 검사보다 먼저, 같은 트랜잭션 안)를 먼저 확인하고 원인을 보고한다.

## 제약

- 패키지 구조·의존 방향·Service와 UseCase·도메인 간 참조·Entity·삭제·마이그레이션·코드 작성(메서드 단일 책임) 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API·Controller·Request 검증·springdoc 문서화·`docs/` 갱신은 `.claude/rules/api.md`를 따른다.
- 테스트 범위와 방식은 `.claude/rules/testing.md`를 따른다. 테스트는 원래 코드와 별도 커밋이 되도록 나눠 스테이징 계획을 보고한다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다.
- 커밋은 하지 않는다. 스테이징까지만 한다(`.claude/rules/git-workflow.md` 3번).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 문서 근거 (`be-spec-mvp.html`)
- 10.2 T-10 범위와 완료 조건(이 문서의 GOAL 1~10이 하나씩 대응한다), 7.4 API-S03 처리 순서, 6.3 POL-S1~S9·S11, 4.1~4.4 상태 모델, 5.3 schedules·schedule_participants, 3.3 enum 변환 원칙, 3.4 application 클래스, 8.4 설정값, 9장 schedule 오류 코드, D2·D10·D12·D20.
- `planning/erd.md`의 schedules·schedule_participants 표가 명세와 같다. `requirements.md`의 설명 300자·7일은 옛 값이고 명세(10~500자, 30일)가 우선한다(문서 머리말).
- 오류 문구는 명세에 `INVALID_DESCRIPTION` "10자 이상 적어주세요"만 있다. 나머지는 구현에서 화면 톤(~요)으로 짓는다. Request의 `message`도 domain ErrorCode와 같은 문구를 쓴다(api.md).

### 구현 형태 제안 (바꿔도 됨. 단 GOAL에 고정한 이름 `Schedule`, `PlaceSnapshot`, `CreateScheduleUseCase`, `ScheduleCommandService`, `ScheduleUserAdapter`, `SchedulePlaceAdapter`, `ScheduleErrorCode`, `ScheduleController`는 유지)
- 패키지 `com.shineunsigan.snsg.schedule`
  - domain/model
    - `Schedule`(루트, BaseAuditEntity)
    - `ScheduleParticipant`(BaseEntity, `@ManyToOne` → schedule, 루트의 cascade로 저장)
    - `PlaceSnapshot`(`@Embeddable`, 컬럼 `place_name` 등에 맞게 필드명 `placeName` 등. `@Column(name=...)`을 쓰지 않는 규칙이 있으므로 필드명 = 컬럼명이 되게 짓는다)
  - domain/constants: `ScheduleStatus`, `RecruitmentStatus`, `ParticipationStatus`, `ParticipantRole`, `HostType`, `DurationType`, `DisplayStatus`
  - domain/exception: `ScheduleErrorCode`
  - domain/repository: `ScheduleRepository`(save, `lockUser(userId)`, `existsOverlapping(userId, startAt, window)`)
  - `ActivityCategoryRepository`에 활성 단건 조회(`findActiveById`)를 더한다.
  - application
    - `CreateScheduleUseCase`(UserPort, PlacePort, `ActivityCategoryQueryService`, `ScheduleCommandService`)
    - `ScheduleCommandService`(`ScheduleRepository`, `ActivityCategoryRepository`, `ScheduleProperties`)
    - `ScheduleProperties`(`@ConfigurationProperties("schedule")` record)
    - port/out: `UserPort`, `PlacePort`
    - dto/command·result
  - infrastructure
    - client: `ScheduleUserAdapter`, `SchedulePlaceAdapter`
    - persistence: `ScheduleJpaRepository`, `ScheduleRepositoryImpl`
  - presentation: `ScheduleController`, `CreateScheduleRequest`, `CreateScheduleResponse`
- 도메인은 설정을 읽을 수 없으므로 인원 범위·리드 타임·30일·12시간을 인자로 받는다. 현재 시각도 인자로 받는다. 서비스가 `LocalDateTime.now()`로 넘긴다(기존 코드에 Clock 빈이 없다).
- 활동 재확인을 위해 CommandService가 `ActivityCategoryRepository`를 직접 쓴다. 같은 도메인의 repository라 규칙에 맞는다. 서비스끼리 주입하지 않는다.
- 겹침 쿼리는 `ix_participants_user`를 타도록 참여 행(user_id, status)에서 출발해 schedules와 조인한다. `start_at > new - window AND start_at < new + window`로 쓰면 정확히 12시간이 빠진다.
- advisory lock은 native query `SELECT pg_advisory_xact_lock(hashtext(:userId))`. 반환이 void라 `getSingleResult()` 등으로 실행만 한다. JOB-04(T-19)의 세션 잠금 키와 공간이 같지만 충돌 확률은 무시할 수 있다.
- 동시성 테스트는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(ScheduleCommandService, ScheduleRepositoryImpl, ActivityCategoryRepositoryImpl, ...)`를 쓴다. `ScheduleProperties`는 `@EnableConfigurationProperties`나 테스트용 빈으로 넣는다(슬라이스에는 `@ConfigurationPropertiesScan`이 적용되지 않는다). 활동 id는 V6의 고정 행(예: BOARD_GAME)을 쓰면 정리할 것이 줄어든다.
- 테스트 DB 행은 엔티티 팩토리를 새로 만들지 말고 native INSERT로 넣는 기존 예를 따른다: `place/infrastructure/persistence/PlaceRepositoryImplTest.java`, `schedule/infrastructure/persistence/ActivityCategoryRepositoryImplTest.java`.
- Request의 제목·설명 길이 제약은 공백을 뺀 길이로 판정해야 한다. 예: `@Pattern(regexp = "^\\s*\\S(?:.{0,18}\\S)?\\s*$")` 형태. `@Size(max = 20)`만 쓰면 앞뒤 공백 포함 입력이 막힌다.
- `startAt`은 `LocalDateTime`(오프셋 없음, CLAUDE.md).
- 컨트롤러는 `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(...))`. 공개 경로가 아니므로 `@SecurityRequirements`를 달지 않는다.
- 스펙 재생성: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-156/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`

### 머지 시 충돌 예상
- T-11이 병렬로 진행되면 `PlacePort`·`SchedulePlaceAdapter`(findExposed 추가), `ScheduleErrorCode`(INVALID_CATEGORY·INVALID_REGION), `ActivityCategoryRepository`, `docs/`를 함께 고친다. 뒤에 머지하는 쪽이 develop을 받아 충돌을 풀고 스펙을 다시 만든다.
- 다른 브랜치가 먼저 V7을 머지하면 머지 직전에 이 브랜치의 번호를 올린다.

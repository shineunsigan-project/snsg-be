## 배경

- Linear SHI-203 "모집 중 일정 목록에 최근 열린 순·마감 임박 순 정렬 추가". 작업 유형: **기능 추가**(기존 동작·단정은 바뀌지 않는다).
- 홈 탭을 새로 넣는다(사용자 합의 2026-11-22, 저장소 밖 `C:\Users\ks\nss\dev\snsg-be\planning\decisions.md` "홈 탭" 절). 홈의 "새로 열린 일정"(활동 칩)·"곧 마감돼요" 칸과 각 전체보기가 **같은 목록 API**를 쓰도록 모집 중 목록 `GET /api/v1/schedules`에 정렬 선택 파라미터를 더한다. 홈 전용 API는 만들지 않는다.
  - 새로 열린 일정: "최근 열린 순"(개설 일시 늦은 순).
  - 곧 마감돼요: "마감 임박 순". 시작이 D-2(모레)까지인 일정만, 정원이 찬 일정은 뺀다. 남은 자리 적은 순 → 같으면 시작 빠른 순.
- 범위 밖: 검색, 다가오는 내 일정 API(SHI-202, 다른 워크트리), 참여한·개설한 목록.

### 결정과 근거 (권장안으로 정했다)

1. **쿼리 파라미터 `sort`를 더한다. 값은 `START_AT`(기본)·`LATEST`·`CLOSING_SOON`.** 생략하거나 빈 값(`sort=`)이면 `START_AT`이다. 대문자 그대로만 받고 그 밖의 값(소문자 포함)은 400 `INVALID_REQUEST`다. 같은 API 묶음의 `period` 관례(`@RequestParam(defaultValue = ...)` enum, 빈 값 = 생략, 잘못된 값은 타입 변환 실패로 400)와 같다.
2. **`START_AT`은 지금 동작 그대로다.** 결과·정렬(시작 빠른 순, 같으면 id 순)·커서 문자열 형식이 바뀌지 않는다. 지금 FE 호출과 이미 받은 커서가 그대로 통한다.
3. **`LATEST`(최근 열린 순)**: 범위는 지금 모집 중 목록과 같다(정원이 찬 일정도 `RECRUITMENT_CLOSED`로 온다). 정렬은 `createdAt` 내림차순, 같으면 id 내림차순. keyset도 내림차순(마지막 항목의 (createdAt, id)보다 앞).
4. **`CLOSING_SOON`(마감 임박 순)**: 범위는 모집 중 목록 중 **시작 날짜(한국 날짜)가 오늘·내일·모레**인 일정, 즉 `startAt < (오늘 + 3일) 00:00`. 시각 기준(지금 + 48시간)이 아니라 날짜 기준이다. "D-2"는 화면의 날짜 표시라 날짜로 읽었다. 시작 전(`startAt > now`) 조건은 그대로 걸린다. 정원이 찬 일정(남은 자리 0, `RECRUITMENT_CLOSED`)은 뺀다. 정렬은 남은 자리(`capacity - confirmedCount`) 오름차순 → 시작 일시 오름차순 → id 오름차순. 오늘 날짜는 요청마다 한 번 잡는 지금 시각(`LocalDateTime.now()`, JVM 기본 시간대 Asia/Seoul)에서 낸다.
5. **기존 필터(`categoryIds`·`regionCodes`·`startDate`·`endDate`)는 세 정렬 모두에서 지금처럼 AND로 걸린다.** `CLOSING_SOON`과 날짜 필터가 함께 오면 교집합이다(예: 모레보다 뒤 날짜만 고르면 빈 결과). 필터 검증 순서(날짜 → 커서)와 오류 코드는 그대로다.
6. **커서는 정렬마다 정렬 키가 다르다.** `START_AT`은 지금 형식 (startAt, id), `LATEST`는 (createdAt, id), `CLOSING_SOON`은 (남은 자리, startAt, id). **다른 정렬에서 받은 커서를 보내면 400 `INVALID_CURSOR`다.** 오류 없이 엉뚱한 위치부터 읽지 않게 하려는 것이다. 그래서 새 두 정렬의 커서에는 어느 정렬의 커서인지가 들어가야 하고, `START_AT` 커서(표식 없는 지금 형식)는 그대로 둔다. FE 처리는 이미 문서화된 `INVALID_CURSOR` 처리(커서 없이 처음부터)와 같다. 정상 흐름에서는 정렬을 바꾸면 처음부터 부르므로 나지 않는다.
   - 참여한·개설한 목록(`period`)의 커서 동작은 바꾸지 않는다(다른 기간의 커서도 오류 없이 읽는 지금 동작 유지).
7. **`CLOSING_SOON`은 바뀌는 값(확정 인원)으로 정렬하므로, 페이지를 넘기는 사이 누가 참여·취소하면 다음 페이지에서 일정이 겹치거나 빠질 수 있다.** 스냅숏을 저장하지 않는 keyset 목록의 한계라 막지 않고 문서(`docs/api-guide.md`)에 적는다. 정원이 다 차면 목록에서 빠지고, 참여 취소로 자리가 나면 다시 들어온다. 홈 칸은 첫 페이지만 쓰므로 영향이 작다.
8. **마이그레이션(인덱스)은 더하지 않는다.** 세 정렬 모두 대상이 "예정(SCHEDULED)이고 시작 전"이며, 일정은 최대 30일 뒤까지만 열 수 있어(`schedule.max-advance-days: 30`) 대상이 앞으로 30일 안의 일정으로 묶인다. 이 범위는 기존 `ix_schedules_status_start (status, start_at, id)`가 좁히고, `CLOSING_SOON`은 3일로 더 좁다. 좁힌 행을 PostgreSQL이 정렬하는 비용은 지금 규모에서 문제가 되지 않는다. 마이그레이션이 없으므로 공유 DB를 건드리지 않아 SHI-202와 병렬로 진행할 수 있다.
9. 응답 형태(`ScheduleCardPageResponse`·`ScheduleCardResponse`)는 바꾸지 않는다. 화면에 필요한 `remainingSeats`·`createdAt`·`startAt`이 이미 카드에 있다.

### 코드에서 확인한 사실

- 조회 경로: `ScheduleController.listRecruiting`(`@RequestParam` 6개 → `ListRecruitingSchedulesCommand`) → `ListSchedulesFacade.listRecruiting`(지역 이름을 붙임) → `ScheduleQueryService.findRecruiting`(필터 → 커서 → size 순으로 해석, `now` 한 번) → `ScheduleRepository.findRecruiting` → `ScheduleRepositoryImpl.findRecruiting`(JPQL 조각 이어 붙이기, 공용 `findPage`가 (startAt, id) keyset과 정렬을 붙인다. `descending`이면 내림차순).
- 모집 중 목록 조건: `s.isDeleted = false and s.status = SCHEDULED and s.startAt > :now`. 정원이 찬 일정은 `recruitmentStatus = CLOSED`(`Schedule.join`이 `confirmedCount == capacity`일 때 바꾼다, 참여 취소로 시작 전에 자리가 나면 다시 `RECRUITING`). 시작한 일정의 마감(`closeRecruitmentStarted`)은 `startAt <= now`라 모집 중 목록에 오지 않는다. 그래서 이 목록 안에서 `RECRUITMENT_CLOSED` ⇔ 남은 자리 0이다.
- `schedules.capacity`·`confirmed_count`는 컬럼이고 CHECK `ck_schedules_count`가 확정 인원 ≤ 정원을 지킨다. `created_at`은 `BaseEntity`의 컬럼이다.
- 인덱스: `ix_schedules_status_start ON schedules (status, start_at, id)`(`V7__create_schedule.sql:37`).
- `ScheduleCursor`는 `startAt.toString() + '|' + id`를 URL-safe Base64(패딩 없음)로 인코딩한다. 구분자가 없거나 날짜·UUID 형식이 아니면 `INVALID_CURSOR`.
- `period`는 `@RequestParam(defaultValue = "UPCOMING") SchedulePeriod period`로 받고, `ScheduleControllerTest.blankPeriodIsUpcoming`·`unknownPeriod`가 빈 값 = 기본, 잘못된 값 400 `INVALID_REQUEST`를 단정한다.
- `docs/openapi.yaml`·`docs/openapi-admin.yaml`에 `name: sort`는 지금 0건이다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-203` (브랜치 `feat/SHI-203-recruiting-list-sort`, 베이스 `bc8a66d`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리(SHI-202 등)는 읽기만 한다.
- `snsg/.env`는 워크트리에 있다. 읽거나 고치지 않는다. 새 환경변수는 없다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 테스트는 다른 워크트리(SHI-202)가 테스트 중이 아닐 때만 돌린다.
- **spec 작성 시점(2026-11-22)에 Docker가 꺼져 있었다.** DB 테스트(`ScheduleRepositoryImplTest`)와 컨텍스트를 띄우는 테스트(`OpenApiSpecTest`, 전체 build)는 로컬 Docker 컨테이너 `snsg-postgres`·`snsg-redis`에 붙고, 꺼져 있으면 실패한다(skip이 아니다). 테스트 전에 `docker ps`로 확인하고, 없으면 `docker start snsg-postgres snsg-redis`로 켠다. 켜지지 않으면 코드를 고치지 말고 멈춰서 보고한다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다(`&&`는 PowerShell 5.1에서 동작하지 않는다).
- 이미 깨져 있는 것: 알려진 실패 없음. 변경 전 `./gradlew build`가 실패하면 이 작업과 무관한지 먼저 확인하고 보고한다.
- 메인 디렉터리의 `bash.exe.stackdump`·`snsg/src/main/bash.exe.stackdump`는 이 작업과 무관하다. 워크트리에 같은 파일이 생기면 스테이징하지 말고 보고한다.

## GOAL

1. `sort`를 생략하거나 `START_AT`(또는 빈 값)으로 부르면 모집 중 목록의 결과·정렬·커서가 지금과 같다. 지금 형식의 커서(`ScheduleCursor`가 만든 문자열)는 그대로 해석된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203/snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest' --tests '*ScheduleCursorTest' --tests '*ScheduleQueryServiceTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && git diff --staged -U0 -- snsg/src/test/java/com/shineunsigan/snsg/schedule/ | grep -E '^-.*(containsExactly|isEmpty\(\)|isEqualTo|hasStackTraceContaining|INVALID_)'` → 출력 없음 (기존 단정 줄을 지우거나 바꾸지 않는다)
2. `LATEST`: 범위는 `START_AT`과 같다(시작 전 예정 일정, 정원이 찬 일정 포함, 시작한·취소·완료·삭제 일정 제외). 순서는 `createdAt` 내림차순, 같으면 id 내림차순이다. `size`보다 많으면 `nextCursor`로 끝까지 넘겨 중복·누락이 없다(같은 `createdAt`이 여러 건이어도).
   - 검증: GOAL 1의 첫 명령 → 이 범위·순서·커서 끝까지 넘기기를 단정하는 실제 PostgreSQL 테스트가 `ScheduleRepositoryImplTest`에 새로 있고 통과한다. 같은 `createdAt` 건들의 기대 순서는 DB가 정한 순서(커서 없이 한 번에 읽은 결과)와 비교하고 Java `UUID.compareTo`로 만들지 않는다. 테스트 데이터는 무작위 활동 id로 범위를 좁힌다(기존 모집 중 테스트 방식).
3. `CLOSING_SOON`: 시작 전 예정 일정 중 시작 날짜가 기준 시각의 날짜 + 2일 이하인 일정만 온다. 경계: 기준 시각이 `2027-03-10 12:00`이면 `2027-03-12 23:59` 시작은 오고 `2027-03-13 00:00` 시작은 오지 않는다(기준 시각이 `23:59`여도 같은 날짜 경계다). 정원이 찬 일정(확정 인원 = 정원, `recruitmentStatus = CLOSED`)은 오지 않는다. 시작한·취소·완료·삭제 일정은 오지 않는다. 순서는 남은 자리 오름차순 → 시작 일시 오름차순 → id 오름차순이고, `nextCursor`로 끝까지 넘겨 중복·누락이 없다(남은 자리·시작 일시가 같은 건이 여러 개여도).
   - 검증: GOAL 1의 첫 명령 → 이 범위·날짜 경계(두 기준 시각 모두)·정원 찬 일정 제외·순서·커서 끝까지 넘기기를 단정하는 테스트가 새로 있고 통과한다. 날짜 경계는 DB 테스트나 순수 JUnit 테스트 어느 쪽으로 확인해도 된다. 같은 키 건들의 기대 순서는 GOAL 2와 같이 DB가 정한 순서와 비교한다.
4. 세 정렬 모두 기존 필터(`categoryIds`·`regionCodes`·`startDate`·`endDate`)가 함께 걸린다. `CLOSING_SOON`과 날짜 필터는 교집합이다.
   - 검증: GOAL 1의 첫 명령 → `LATEST`·`CLOSING_SOON` 각각에 필터가 걸리는지, `CLOSING_SOON` + 모레보다 뒤 날짜만 고른 `startDate`가 빈 결과인지를 단정하는 테스트가 새로 있고 통과한다.
5. 다른 정렬에서 받은 커서를 보내면 400 `INVALID_CURSOR`다. 세 정렬의 모든 짝(`START_AT` 커서 → `LATEST`·`CLOSING_SOON`, `LATEST` 커서 → `START_AT`·`CLOSING_SOON`, `CLOSING_SOON` 커서 → `START_AT`·`LATEST`)에서 그렇다. 새 정렬의 커서도 URL-safe 문자(`[A-Za-z0-9_-]`)만 쓴다. 날짜 범위가 틀리면 커서보다 먼저 `INVALID_DATE_FILTER`다(지금 순서 유지).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203/snsg && ./gradlew cleanTest test --tests '*ScheduleCursorTest' --tests '*ScheduleQueryServiceTest'` → BUILD SUCCESSFUL, 실패 0. 여섯 짝의 `INVALID_CURSOR`와 새 커서의 왕복(인코딩 → 해석 → 같은 값)·URL-safe 문자를 단정하는 테스트가 새로 있다.
6. `GET /api/v1/schedules`가 `sort` 쿼리 파라미터(`START_AT`·`LATEST`·`CLOSING_SOON`)를 받아 조회까지 넘긴다. 생략·빈 값은 `START_AT`이다. 그 밖의 값(예: `sort=latest`, `sort=POPULAR`)은 400 `INVALID_REQUEST`이고 facade를 부르지 않는다. 토큰 없이 부를 수 있는 것은 그대로다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203/snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest' --tests '*ListSchedulesFacadeTest' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0. `ScheduleControllerTest`에 `sort` 전달, 생략·빈 값이면 `START_AT`, 잘못된 값 400 `INVALID_REQUEST`를 단정하는 테스트가 있다.
7. `docs/openapi.yaml`이 코드와 같고 모집 중 목록에 `sort` 파라미터가 있다. `docs/openapi-admin.yaml`은 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && grep -c 'name: sort' docs/openapi.yaml` → `1`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && git diff --staged --name-only | grep -x 'docs/openapi-admin.yaml'` → 출력 없음
8. `docs/api-guide.md`가 바뀐 계약을 FE에 전한다: `sort` 파라미터(세 값의 뜻·기본·빈 값·잘못된 값 400), `LATEST`의 정렬, `CLOSING_SOON`의 범위(한국 날짜로 오늘·내일·모레 시작, 정원이 찬 일정 제외)·정렬, 필터와 함께 쓸 때 교집합, 정렬을 바꾸면 cursor 없이 처음부터 부른다는 것과 다른 정렬의 커서는 400 `INVALID_CURSOR`라는 것, `CLOSING_SOON`은 페이지 사이에 참여·취소가 생기면 겹치거나 빠질 수 있다는 것, 홈의 "새로 열린 일정"·"곧 마감돼요" 칸과 전체보기가 이 API를 어떤 파라미터로 부르는지.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && grep -c 'sort=CLOSING_SOON' docs/api-guide.md` → 1 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && grep -c 'sort=LATEST' docs/api-guide.md` → 1 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && grep -nE '모레|D-2' docs/api-guide.md` → 1줄 이상
9. 마이그레이션 파일을 더하거나 고치지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && git diff --staged --name-only | grep 'db/migration'` → 출력 없음
10. 전체 빌드가 통과한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0 (ArchUnit 포함)
11. 변경 경로가 아래 허용 목록 안에 있다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-203 && git diff --staged --name-only | grep -vE '^snsg/src/(main|test)/java/com/shineunsigan/snsg/schedule/|^docs/openapi\.yaml$|^docs/api-guide\.md$|^\.claude/tasks/SHI-203/task\.md$'` → 출력 없음
    - `.claude/rules/`·CLAUDE.md 갱신은 refine-docs 단계(agent4)가 따로 스테이징하므로 이 검사 대상이 아니다.

## 비목표

- 참여한·개설한 목록(`findParticipating`·`findHosted`, `period`)의 결과·정렬·커서 동작은 바꾸지 않는다. 다른 기간의 커서를 오류 없이 읽는 지금 동작에 검증을 더하지 않는다.
- `START_AT` 커서의 문자열 형식은 바꾸지 않는다(이미 받은 커서가 그대로 통해야 한다).
- 검색, 다가오는 내 일정(SHI-202), 홈 전용 API는 만들지 않는다.
- 응답 형태(`ScheduleCardPageResponse`·`ScheduleCardResponse` 필드), 일정 상세·참여·취소 API, `Schedule.displayStatus`·`availableActions`는 바꾸지 않는다.
- 인덱스·마이그레이션을 더하지 않는다(결정 8). 새 설정 키·환경변수를 더하지 않는다. 마감 임박의 "모레까지"(2일)는 설정이 아니라 코드 상수로 둔다.
- `CLOSING_SOON` 페이지 사이의 겹침·빠짐(결정 7)을 막는 장치(스냅숏, 버전)를 만들지 않는다.
- user·place·auth·admin 도메인 파일과 `common`은 건드리지 않는다. `SecurityConfig`의 매처는 경로가 그대로라 고치지 않는다.
- 기존 테스트의 단정(기대 결과)은 고치지 않는다. 생성자·시그니처 변경(예: `ListRecruitingSchedulesCommand`에 필드 추가)에 따른 호출 인자 맞춤과, 테스트 헬퍼(`ScheduleRow`에 `createdAt` 지정 추가 등) 확장만 허용한다.
- `docs/openapi-admin.yaml`이 재생성으로 바뀌면 이 작업과 무관한 차이이므로 멈추고 보고한다.
- 파라미터 이름·값(`sort`, `START_AT`·`LATEST`·`CLOSING_SOON`)을 다르게 짓고 싶어지면 바꾸지 않는다. FE 계약이라 GOAL에 박았다.
- 기획 문서(저장소 밖 `planning/`)는 고치지 않는다.

## 제약

- 패키지 구조·계층·DIP·목록 쿼리(조건 조각 이어 붙이기, 값은 파라미터 바인딩, 정렬·커서 비교는 DB에서만, 내림차순은 비교와 정렬을 함께 뒤집기)는 `.claude/rules/package-structure.md`를 따른다.
- API·문서(`@Operation`·`@Parameter` 설명, `docs/openapi.yaml` 재생성 명령과 같은 커밋 규칙, `docs/api-guide.md`)는 `.claude/rules/api.md`를 따른다. `docs/openapi.yaml`은 `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 다시 만든다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다. 테스트는 원래 코드와 별도 커밋으로 나눌 수 있게 스테이징 범위를 보고한다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다(워크트리 안에 만들지 않는다).
- 들여쓰기는 4칸 공백. 시간은 오프셋 없는 `LocalDateTime`, 지금 시각은 요청마다 한 번 잡아 쿼리 조건(시작 전, 마감 임박 날짜 경계)과 표시 상태에 함께 쓴다(`ScheduleQueryService` 기존 방식).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 형태는 제안이다. 같은 결과를 다른 형태로 이뤄도 된다.

- 정렬 enum은 schedule `domain/constants`에 둔다(예: `RecruitingScheduleSort { START_AT, LATEST, CLOSING_SOON }`, 값마다 javadoc). presentation은 `@RequestParam(defaultValue = "START_AT") RecruitingScheduleSort sort`로 받으면 `period`와 같게 빈 값 = 기본, 잘못된 값 = 400 `INVALID_REQUEST`가 된다. `ListRecruitingSchedulesCommand`에 정렬을 더한다.
- 쿼리(`ScheduleRepositoryImpl.findRecruiting`):
  - 범위 조각은 지금 그대로 두고, `CLOSING_SOON`이면 `and s.startAt < :closingSoonBefore and s.confirmedCount < s.capacity`(또는 `s.recruitmentStatus = RECRUITING`)를 더한다. `closingSoonBefore = now.toLocalDate().plusDays(3).atStartOfDay()`. 이 계산(날짜 경계)은 순수 JUnit으로 확인할 수 있게 domain(정렬 enum이나 필터)에 두면 GOAL 3의 `23:59` 경계를 DB 없이 단정할 수 있다.
  - `LATEST` keyset: `and (s.createdAt < :c or (s.createdAt = :c and s.id < :id))`, `order by s.createdAt desc, s.id desc`.
  - `CLOSING_SOON` keyset: `r = s.capacity - s.confirmedCount`로 `(r > :r) or (r = :r and (s.startAt > :t or (s.startAt = :t and s.id > :id)))`, `order by (s.capacity - s.confirmedCount), s.startAt, s.id`. Hibernate 6+ JPQL은 order by의 산술식을 받는다.
  - 지금 `findPage(jpql, parameters, after, limit, descending)`는 (startAt, id) 전용이다. 참여한·개설한 목록이 그대로 쓰므로 그 동작은 유지하고, 모집 중 목록의 정렬별 keyset·정렬 조각만 따로 붙이는 방식이 비목표(참여한·개설한 목록 불변)를 지키기 쉽다.
- 커서: `START_AT`은 지금 `ScheduleCursor` 형식 그대로. 새 두 정렬은 정렬 표식을 앞에 붙인 원문(예: `LATEST|<createdAt>|<id>`, `CLOSING_SOON|<remaining>|<startAt>|<id>`)을 같은 Base64로 인코딩하면, 표식 없는 `START_AT` 커서는 새 정렬 해석에서 실패하고 새 커서는 `START_AT` 해석(`LocalDateTime.parse("LATEST")`)에서 실패해 결정 6이 저절로 된다. 커서를 정렬별 record로 나누든 하나로 두든 구현에 맡긴다. `createdAt`은 DB에서 읽은 값(마이크로초)이라 `toString` 왕복으로 같은 값이 된다.
- 따라 할 기존 코드
  - 이름·파라미터: `ScheduleController.listParticipating`의 `@RequestParam(defaultValue = "UPCOMING") SchedulePeriod period`(`schedule/presentation/ScheduleController.java:353`)와 `@Parameter` 설명 형식(값마다 뜻·기본·빈 값·잘못된 값·바꾸면 처음부터). enum 이름·javadoc은 `SchedulePeriod`(`schedule/domain/constants/SchedulePeriod.java`).
  - 오류: 해석할 수 없는 커서는 `ScheduleErrorCode.INVALID_CURSOR`(`schedule/domain/exception/ScheduleErrorCode.java:32`), 날짜 순서는 `INVALID_DATE_FILTER`. 새 ErrorCode를 만들지 않는다.
  - 데이터 접근: `ScheduleRepositoryImpl.findRecruiting`·`findPage`(`schedule/infrastructure/persistence/ScheduleRepositoryImpl.java:67-169`) — 조건 조각을 이어 붙이고 값은 `parameters` 맵으로 바인딩, `descending`으로 비교·정렬을 함께 뒤집는다. `setMaxResults(limit)`는 서비스가 `size + 1`을 넘긴다.
  - 흐름: `ScheduleQueryService.findRecruiting`(`schedule/application/ScheduleQueryService.java:49-56`) — 필터 → 커서 → size 순으로 해석하고 `now`를 한 번 잡는다. `toPage`의 `nextCursor(page.get(size - 1))`가 정렬에 맞는 커서를 만들어야 한다.
  - 커서: `ScheduleCursor`(`schedule/domain/model/ScheduleCursor.java`) — 인코딩·해석과 `INVALID_CURSOR` 변환.
  - 테스트
    - DB: `ScheduleRepositoryImplTest.recruitingScope`·`recruitingOrder`(`schedule/infrastructure/persistence/ScheduleRepositoryImplTest.java:270~`) — 무작위 활동 id로 범위를 좁히고, 같은 키 건들은 `sortedLikePostgres`/DB 순서로 비교한다. native INSERT의 `created_at`은 지금 `INSERTED_AT` 고정이라 `LATEST` 테스트는 `ScheduleRow`에 `createdAt` 지정을 더해야 한다. PAST 커서 끝까지 넘기기 테스트(같은 파일, `period=PAST` 관련)가 커서 루프의 예다.
    - 서비스: `ScheduleQueryServiceTest.invalidCursor`·`cursorIsDecoded`·`dateCheckedBeforeCursor`(`schedule/application/ScheduleQueryServiceTest.java:166-195`) — Mockito로 repository를 mock하고 넘긴 인자를 캡처한다.
    - 컨트롤러: `ScheduleControllerTest.pastPeriodIsPassed`·`blankPeriodIsUpcoming`·`unknownPeriod`(`schedule/presentation/ScheduleControllerTest.java:355-395`) — 같은 형태로 `sort`를 단정한다. `listsRecruiting`의 `listRecruiting(any())`는 그대로 둔다.
    - 커서: `ScheduleCursorTest`(`schedule/domain/model/ScheduleCursorTest.java`) — 왕복과 URL-safe 문자 단정.
- 컨트롤러 `@Operation` 설명의 "정렬은 시작 일시가 빠른 순(같으면 id 순)이다" 문장은 정렬별로 고친다. "정원이 찬 일정도 RECRUITMENT_CLOSED로 함께 온다"는 `CLOSING_SOON`에서는 빠진다고 함께 적는다. 400 설명의 `INVALID_REQUEST`에 `sort` 잘못된 값, `INVALID_CURSOR`에 다른 정렬의 커서를 더한다.
- `docs/api-guide.md`에서 고칠 곳: "일정 목록" 표(모집 중 행), "각 목록에 포함·제외되는 일정"의 모집 중(`CLOSING_SOON` 범위), 요청 파라미터 표(`sort` 행 추가, 대상 "모집 중"), "정렬과 페이지"(정렬별 순서, 정렬을 바꾸면 처음부터, 다른 정렬 커서는 `INVALID_CURSOR`, `CLOSING_SOON`의 겹침·빠짐 주의 — 지금 "끝까지 넘겨도 중복·누락이 없다" 문장에 `CLOSING_SOON` 예외를 단다), 오류 표(`INVALID_CURSOR`·`INVALID_REQUEST` 원인), 화면별 API 표(홈의 "새로 열린 일정" = `sort=LATEST`(+ 활동 칩이면 `categoryIds`), "곧 마감돼요" = `sort=CLOSING_SOON`, 각 전체보기는 같은 파라미터로 페이지를 넘긴다). 홈 화면 ID는 기획 문서가 아직 없으므로 화면 이름으로 적는다.
- `.claude/rules/api.md` 목록 절("정렬은 (정렬 키, id) 오름차순", "커서 형식은 같다", "방향이 다른 구간은 파라미터로 나눈다")과 `package-structure.md` 목록 쿼리 절에 정렬 선택·정렬 표식 커서·바뀌는 값 정렬의 예외가 생긴다. 규칙 문서 갱신은 refine-docs 단계(agent4)가 맡는다.
- 홈 시안: https://claude.ai/artifact/XT63XrCqf1gbMu1GgrRmQm (기획 문서 `decisions.md`가 가리키는 시안. 읽지 않아도 GOAL을 이룰 수 있다).

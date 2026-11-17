## 배경

- Linear SHI-190. 사용자 원문: "종료된 일정들도 보이도록 수정해서 머지해줘"
- 직전 대화: 참여한 일정 목록(`GET /api/v1/schedules/participating`)과 개설한 일정 목록(`GET /api/v1/schedules/hosted`)은 `s.status <> COMPLETED and s.startAt > now - schedule.completion-after(12h)` 조건이라, 종료된 일정(스케줄러가 COMPLETED로 바꾼 일정, 시작 + 12시간이 지난 일정, 시작 + 12시간이 지난 CANCELED 일정)이 어느 목록에도 나오지 않는다. 사용자는 두 목록에서 종료된 일정도 보이게 하라고 했다.
- 기획 명세(저장소 밖, 읽기 전용) `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`
  - D7(합의): "참여한·개설한 일정 탭은 완료 전 일정만 시작 빠른 순 … 지난 일정 보기는 이후 작업."
  - 이후 작업 표: "지난 일정 보기 — API-S05·S06에 **기간 구분 파라미터 추가**(D7)". 이번 작업이 이 항목이다.

### 결정과 근거 (사용자 성향에 따라 권장안으로 정했다)

1. **두 목록에 기간 구분 쿼리 파라미터 `period`를 더한다. 값은 `UPCOMING`(기본)·`PAST`.**
   - 근거: 기획 명세가 지난 일정 보기를 "기간 구분 파라미터 추가"로 미리 정해 두었다.
   - 다가오는·지난 일정을 한 목록에 섞으면 정렬 방향이 둘이라 keyset 커서 하나로 표현할 수 없다. 오름차순 하나로 섞으면 오래된 지난 일정이 맨 앞에 와 다가오는 일정이 뒤 페이지로 밀린다. 파라미터로 나누면 각 목록이 단일 keyset을 유지하고 FE는 "다가오는 / 지난" 구역(또는 하위 탭)을 따로 부른다.
   - 기본값을 `UPCOMING`(지금 동작 그대로)으로 두면 지금 FE 호출이 바뀌지 않는다(하위 호환).
2. **`PAST`의 범위는 `UPCOMING`의 정확한 여집합이다**: `status = COMPLETED` 이거나 `startAt <= now - completion-after`. 두 기간을 합치면 지금 범위 밖에 있던 종료 일정이 빠짐없이, 겹침 없이 들어온다. 그 밖의 조건(확정 MEMBER 참여, 내가 연 일정, 삭제 제외)은 기간과 무관하게 지금과 같다.
   - 종료 후 취소된 일정(시작 + 12시간이 지난 CANCELED)도 `PAST`에 온다. `displayStatus`는 `Schedule.displayStatus`가 그대로 정한다(`CANCELED`가 먼저라 "일정 취소됨", 그 밖은 `COMPLETED`).
3. **`PAST`는 시작 일시 늦은 순(`startAt` 내림차순, 같으면 id 내림차순)이다.** 가장 최근에 끝난 일정이 먼저 와야 화면이 쓸 수 있다. keyset도 반대 방향(마지막 항목의 (startAt, id)보다 앞)이다. 정렬·비교는 DB에서만 한다(`.claude/rules/package-structure.md` 목록 쿼리 절).
4. **커서 형식(`ScheduleCursor`)은 바꾸지 않는다.** 기간을 커서에 싣지 않는다. 문서가 이미 "필터나 탭을 바꾸면 cursor 없이 처음부터 부른다"고 정해 두었고, `period`도 같은 규칙에 든다. 다른 `period`의 커서를 보내도 오류가 아니다(해석만 되면 그 위치부터 읽는다).
5. **잘못된 `period` 값은 400 `INVALID_REQUEST`, 빈 값(`period=`)은 생략과 같다(UPCOMING).** 같은 API의 `cursor=`·`size=` 빈 값이 생략과 같다는 기존 규칙에 맞춘다. 대소문자는 enum 이름 그대로(`UPCOMING`·`PAST`)만 받는다.
6. **마이그레이션(인덱스)은 더하지 않는다.** 개설한 목록은 `ix_schedules_host (host_user_id, start_at)`가 범위를 사용자 한 명으로 좁히고 내림차순은 역방향 스캔으로 쓴다. 참여한 목록은 `ix_participants_user (user_id, status)`로 사용자 한 명의 참여 행만 읽는다. 두 목록 모두 사용자 한 명의 일정 수로 묶여 있어 새 인덱스가 필요한 규모가 아니다. 공유 DB에 마이그레이션을 적용하지 않으므로 다른 워크트리와 병렬로 진행할 수 있다.
7. 모집 중 목록(`GET /api/v1/schedules`)은 바꾸지 않는다. 사용자 요청은 참여한·개설한 두 목록이다.

### 코드에서 확인한 사실

- 조회 경로: `ScheduleController.listParticipating/listHosted` → `ListSchedulesFacade.listParticipating/listHosted`(지역 이름을 붙임) → `ScheduleQueryService.findParticipating/findHosted`(`startAfter(now) = now - completionAfter`) → `ScheduleRepository.findParticipating/findHosted` → `ScheduleRepositoryImpl`(JPQL, 공용 `findPage`가 오름차순 keyset과 `order by s.startAt, s.id`를 붙인다).
- `Schedule.displayStatus`: CANCELED → COMPLETED(저장 상태 COMPLETED 또는 `now >= startAt + completionAfter`) → RECRUITMENT_CLOSED → RECRUITING 순.
- 스케줄러 `completeStartedBy`는 `startAt <= now - completionAfter`인 SCHEDULED를 COMPLETED로 바꾼다. CANCELED는 COMPLETED가 되지 않는다.
- `GlobalExceptionHandler.handleExceptionInternal`이 Spring MVC의 타입 변환 실패를 400 `INVALID_REQUEST`로 감싼다(size가 숫자가 아닐 때와 같은 경로).

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-190` (브랜치 `feat/SHI-190-include-completed-schedules`, 베이스 `d789010`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리는 읽기만 한다.
- `snsg/.env`는 워크트리에 이미 있다. 읽거나 고치지 않는다. 새 환경변수는 없다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 테스트는 다른 워크트리가 테스트 중이 아닐 때만 돌린다.
- **spec 작성 시점(2026-11-17)에 Docker 데몬이 꺼져 있었다**(`docker ps` 실패). DB 테스트(`ScheduleRepositoryImplTest`, `OpenApiSpecTest` 등 컨텍스트를 띄우는 테스트)는 로컬 Docker PostgreSQL·Redis에 붙고, 꺼져 있으면 실패한다(skip이 아니다). 테스트 전에 `docker ps`로 `snsg-postgres`가 떠 있는지 확인하고, 꺼져 있으면 코드를 고치지 말고 멈춰서 보고한다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다(`&&`는 PowerShell 5.1에서 동작하지 않는다).
- 이미 깨져 있는 것: 알려진 실패 없음. 변경 전 `./gradlew build`가 실패하면 이 작업과 무관한지 먼저 확인하고 보고한다.

## GOAL

1. `period`를 생략하거나 `UPCOMING`(또는 빈 값)으로 부르면 두 목록의 결과·정렬·커서가 지금과 같다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190/snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0. 기존 `participatingIncluded`·`participatingExcluded`·`participatingRejoinedOnce`·`hostedIncluded`·`hostedExcluded`의 **단정(기대 id·순서)은 바뀌지 않는다**. 단정 줄(`assertThat(participatingIds())…`·`assertThat(hostedIds())…`)은 글자 그대로 둔다. 기간을 넘겨야 하면 헬퍼(`participatingIds()`·`hostedIds()`) 안에서 UPCOMING을 넘기고, PAST용 헬퍼는 따로 만든다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && git diff --staged -U0 -- snsg/src/test/java/com/shineunsigan/snsg/schedule/infrastructure/persistence/ScheduleRepositoryImplTest.java | grep -E '^-.*containsExactly|^-.*isEmpty\(\)'` → 출력 없음
2. 참여한 일정 `PAST`: 내가 MEMBER로 확정 참여한 일정 중 `status = COMPLETED`이거나 `startAt <= now - 12h`인 일정이 온다. 시작 + 12시간이 지난 CANCELED 일정도 온다. 내가 참여 취소한 일정, 내가 연 일정, 남만 참여한 일정, 삭제된 일정·참여 행, 완료 전 일정(`startAt`이 `now - 12h`보다 1분 뒤인 SCHEDULED 포함)은 오지 않는다. 경계 `startAt = now - 12h`는 온다.
   - 검증: GOAL 1의 명령 → 이 포함·제외·경계를 단정하는 실제 PostgreSQL 테스트가 `ScheduleRepositoryImplTest`에 새로 있고 통과한다. 테스트 데이터는 무작위 사용자 id로 범위를 좁힌다(`.claude/rules/testing.md`).
3. 개설한 일정 `PAST`: 내가 연 일정 중 `status = COMPLETED`이거나 `startAt <= now - 12h`인 일정이 온다(종료 후 CANCELED 포함). 멤버로 참여만 한 일정, 삭제된 일정, 완료 전 일정은 오지 않는다.
   - 검증: GOAL 1의 명령 → 이 포함·제외를 단정하는 테스트가 `ScheduleRepositoryImplTest`에 새로 있고 통과한다.
4. `PAST`는 `startAt` 내림차순, 같으면 id 내림차순이다. `size`보다 많으면 `nextCursor`로 끝까지 넘겨 중복·누락이 없다(같은 `startAt`이 여러 건이어도).
   - 검증: GOAL 1의 명령 → `PAST` 목록의 순서(서로 다른 startAt, 같은 startAt 여러 건)와 커서로 끝까지 넘긴 결과를 단정하는 테스트가 새로 있고 통과한다. 같은 startAt 건들의 기대 순서는 DB가 정한 순서(커서 없이 한 번에 읽은 결과)와 비교하고 Java `UUID.compareTo`로 만들지 않는다.
5. `GET /api/v1/schedules/participating`·`/hosted`가 `period` 쿼리 파라미터(`UPCOMING`·`PAST`)를 받아 그 값을 조회까지 넘긴다. 생략·빈 값은 `UPCOMING`이다. 그 밖의 값(예: `period=FUTURE`)은 400 `INVALID_REQUEST`다. 토큰 없이 부르면 지금처럼 401이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190/snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest' --tests '*ScheduleQueryServiceTest' --tests '*ListSchedulesFacadeTest' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0. `ScheduleControllerTest`에 `period=PAST` 전달, 생략 시 UPCOMING, 잘못된 값 400 `INVALID_REQUEST`를 단정하는 테스트가 있다.
6. `docs/openapi.yaml`이 코드와 같다. 두 경로에 `period` 파라미터가 있다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && grep -c 'name: period' docs/openapi.yaml` → `2`
7. `docs/api-guide.md`가 바뀐 계약을 FE에 전한다: `period` 파라미터(값·기본·빈 값·잘못된 값 400), `PAST`의 포함·제외, `PAST`의 정렬(늦은 순), 기간을 바꾸면 cursor 없이 처음부터 부른다는 것, `COMPLETED`가 지난 일정 목록에 나온다는 것. "지난(완료된) 일정을 보는 API는 아직 없다" 문장과 `COMPLETED`의 "목록에는 오지 않는다"는 남지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && grep -c 'period=PAST' docs/api-guide.md` → 1 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && grep -nE '지난\(완료된\) 일정을 보는 API는 아직 없다|목록에는 오지 않는다' docs/api-guide.md` → 출력 없음
8. 마이그레이션 파일을 더하거나 고치지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && git diff --staged --name-only | grep 'db/migration'` → 출력 없음
9. 전체 빌드가 통과한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0 (ArchUnit 포함)
10. 변경 경로가 아래 허용 목록 안에 있다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-190 && git diff --staged --name-only | grep -vE '^snsg/src/(main|test)/java/com/shineunsigan/snsg/schedule/|^docs/openapi\.yaml$|^docs/api-guide\.md$|^\.claude/tasks/SHI-190/task\.md$'` → 출력 없음
    - `.claude/rules/`·CLAUDE.md 갱신은 refine-docs 단계(agent4)가 따로 스테이징하므로 이 검사 대상이 아니다.

## 비목표

- 모집 중 목록(`GET /api/v1/schedules`)과 `findRecruiting`은 바꾸지 않는다.
- `ScheduleCursor`의 인코딩 형식은 바꾸지 않는다. 기간을 커서에 싣지 않는다. 다른 기간의 커서가 오면 오류 없이 그 위치부터 읽는다(별도 검증을 더하지 않는다).
- 일정 상세·참여자 명단·참여·취소 API, `Schedule.displayStatus`·`availableActions` 규칙은 바꾸지 않는다.
- 스케줄러(`completeStartedBy` 등)와 `ScheduleProperties`의 설정 키는 바꾸지 않는다. 새 설정·환경변수를 더하지 않는다.
- 인덱스·마이그레이션을 더하지 않는다(결정 6).
- 응답 형태(`ScheduleCardPageResponse`·`ScheduleCardResponse` 필드)는 바꾸지 않는다.
- user·place·auth·admin 도메인 파일과 `common`은 건드리지 않는다. `SecurityConfig`의 매처는 경로가 그대로라 고치지 않는다.
- 기존 테스트의 단정(기대 결과)은 고치지 않는다. 시그니처 변경으로 호출 인자만 바꾸는 것은 된다.
- `docs/openapi-admin.yaml`이 재생성으로 바뀌면 안 된다. 바뀌면 이 작업과 무관한 차이이므로 멈추고 보고한다.
- 기간 이름·값을 다르게 짓고 싶어지면(예: `status`, `scope`) 바꾸지 않는다. FE 계약이라 GOAL에 박았다.

## 제약

- 패키지 구조·계층·DIP·목록 쿼리(조건 조각 이어 붙이기, 값은 파라미터 바인딩, 정렬·커서 비교는 DB에서만)는 `.claude/rules/package-structure.md`를 따른다.
- API·문서(`@Operation`·`@Parameter` 설명, `docs/openapi.yaml` 재생성 명령과 같은 커밋 규칙, `docs/api-guide.md`)는 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다. 테스트는 원래 코드와 별도 커밋으로 나눌 수 있게 스테이징 범위를 보고한다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다(워크트리 안에 만들지 않는다).
- 들여쓰기는 4칸 공백. 시간은 오프셋 없는 `LocalDateTime`, 지금 시각은 요청마다 한 번 잡아 조건과 표시 상태에 함께 쓴다(`ScheduleQueryService` 기존 방식).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 형태는 제안이다. 같은 결과를 다른 형태로 이뤄도 된다.

- 기간 enum은 schedule `domain/constants`에 둔다(예: `SchedulePeriod { UPCOMING, PAST }`). 같은 도메인이라 presentation이 `@RequestParam(required = false) SchedulePeriod period`로 받아도 된다. Spring의 String→enum 변환은 빈 문자열을 null로 바꾸므로 null을 UPCOMING으로 보면 빈 값 = 생략이 된다. 잘못된 값은 타입 변환 실패로 `GlobalExceptionHandler.handleExceptionInternal`을 거쳐 400 `INVALID_REQUEST`가 된다.
- 쿼리: 기존 `findParticipating`·`findHosted`에 기간을 넘기고, 기간 조건 조각만 바꾼다.
  - UPCOMING: `and s.status <> :completed and s.startAt > :boundary` (지금과 같음)
  - PAST: `and (s.status = :completed or s.startAt <= :boundary)`
  - `boundary`는 지금의 `startAfter(now) = now - completionAfter`와 같은 값이다.
  - `findPage`에 방향을 넘겨 PAST는 `s.startAt < :cursorStartAt or (s.startAt = :cursorStartAt and s.id < :cursorId)`와 `order by s.startAt desc, s.id desc`를 붙인다. 모집 중 목록은 오름차순 그대로다.
- `ScheduleCursor` javadoc("다음 페이지는 이 값보다 뒤의 일정")은 PAST에서는 "앞"이 되므로 문구를 기간에 맞게 고쳐도 된다(형식은 그대로).
- 컨트롤러의 `@Operation` 설명("완료 전(시작 + 12시간 전) 일정만 온다", "정렬·페이지는 모집 중 목록과 같다")은 기간에 맞게 고친다. `@Parameter`에 값마다의 뜻·기본값·빈 값·정렬을 적는다.
- `docs/api-guide.md`에서 고칠 곳: "일정 목록" 표(무엇이 오는가), "각 목록에 포함·제외되는 일정", 요청 파라미터 표(`period` 행 추가, `/participating`·`/hosted`가 받는 파라미터 문장), "정렬과 페이지"(PAST는 늦은 순), `displayStatus` 표의 "나오는 목록"(`COMPLETED`는 지난 일정, `CANCELED`도 지난 일정에 올 수 있음), 화면별 API 표(LIST-04·05 행에 지난 일정은 `period=PAST`로 부른다), 일정 취소 절의 "완료 시각 전까지 CANCELED로 남는다"(완료 뒤에는 지난 일정 목록에 CANCELED로 온다).
- 화면 쪽 사용 예(문서에 적을 만한 것): 참여한·개설한 탭은 첫 화면에서 `period` 없이(UPCOMING) 부르고, 지난 일정 구역은 `period=PAST`로 따로 부른다. 기간을 바꾸면 탭을 바꿀 때처럼 cursor 없이 처음부터 부른다.
- `.claude/rules/api.md`의 "정렬은 (정렬 키, id) 오름차순"과 `package-structure.md` 목록 쿼리 절의 오름차순 keyset 예시는 이번 작업으로 예외(PAST 내림차순)가 생긴다. 규칙 문서 갱신은 refine-docs 단계(agent4)가 맡는다.
- 기획 명세의 이후 작업 표("지난 일정 보기")는 저장소 밖 문서라 이 작업에서 고치지 않는다.

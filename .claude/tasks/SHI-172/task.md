## 배경

- Linear SHI-172. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-16 "일정 목록 3종 (schedule)"을 구현한다. 화면은 LIST-01~05(모집 중 목록·필터, 참여한 일정, 개설한 일정)와 CRT-07(개설 후 이동하는 "내가 연 일정")이다. API 번호는 API-S04(모집 중), API-S05(참여한), API-S06(개설한)이다.
- 선행: T-10(일정 개설, `Schedule`·`ScheduleParticipant`·V7, PR #47), T-11(장소 후보, `RegionPort.find`·`ScheduleRegionAdapter`, PR #49)이 develop에 머지돼 있다. T-13(SHI-171, 참여·참여 취소)이 이 작업보다 먼저 머지된다는 전제다(환경 절).
- 명세 완료 조건: 시작 지난 일정·취소 일정 모집 중 목록에서 제외 / 마감 일정 포함 / 지역·활동 복수 필터 / 날짜 하루·범위·역순 400 / size 50 초과 시 50 / 커서로 끝까지 넘겨 중복·누락 없음(같은 start_at 여러 건 포함) / 참여한 목록에 내가 취소한 일정 없음·개설자 취소 일정 있음·완료 일정 없음 / 개설한 목록에 참여만 한 일정 없음.
- 근거(명세에서 확인한 것)
  - 서비스 표: `ListSchedulesFacade`가 API-S04·S05·S06(한 행위 "카드 목록"의 세 범위)을 맡는다. `ScheduleQueryService` → `RegionPort.findAll`로 지역 이름을 한 번에 채운다. `ScheduleQueryService`가 "목록 3종 쿼리"를 맡는다. `RegionPort.findAll`은 T-11에서 "쓰는 작업(T-12·T-16)에서 더한다"고 합의했다.
  - D8: 모집 중 탭은 취소되지 않았고 시작 전인 일정 전부. 정원이 찬 일정도 "모집 마감"으로 함께 보인다.
  - D7: 참여한·개설한 탭은 완료 전 일정만 시작 빠른 순. 개설자가 취소한 일정도 완료 시각 전까지 "일정 취소됨"으로 남는다. 내가 참여 취소한 일정은 빠진다.
  - D17: 지역·활동 모두 여러 개를 받는다. D18: 기본 20, 최대 50, 커서 방식. 설정값 `schedule.page-size-default: 20`, `schedule.page-size-max: 50`(명세 8.4).
  - API-S04: 공개. 쿼리 `categoryIds`(여러 개, 생략 = 전체), `regionCodes`(여러 개, 생략 = 전체), `startDate`·`endDate`(시작 날짜 범위, 양 끝 포함. `endDate < startDate`면 400 `INVALID_DATE_FILTER`. 하나만 오면 그날 하루. 생략 = 제한 없음. 과거 날짜는 오류가 아니다), `cursor`(이전 응답의 `nextCursor`. 생략 = 처음. 해석할 수 없으면 400 `INVALID_CURSOR`), `size`(기본 20. 50을 넘으면 50으로 줄인다). 조건: `is_deleted = false`, `status = SCHEDULED`, `start_at > now`. 정렬 `start_at, id` 오름차순. 커서는 (start_at, id)를 인코딩한 불투명 문자열. 모르는 categoryId·regionCode는 오류가 아니라 빈 결과. 응답 `{items: [ScheduleCard], nextCursor(마지막이면 null)}`, 비어 있으면 `items = []`.
  - ScheduleCard: `id`, `category {id, name}`(비활성 활동이어도 이름은 보인다), `title`, `regionName`(예: 마포구. `RegionPort.findAll`(비활성 포함)을 한 번 불러 코드 → 이름. 지역이 없으면 빈 문자열), `startAt`, `confirmedCount`, `capacity`, `remainingSeats`(= capacity − confirmedCount), `displayStatus`(4.3, `Schedule.displayStatus(now, completionAfter)`), `createdAt`(NEW 배지용). 장소명·대표 이미지·비용은 내보내지 않는다.
  - API-S05: 인증 필요. 조건: 내 참여 행이 `CONFIRMED`·`MEMBER`, `status ≠ COMPLETED`, `start_at + 12h > now`. 정렬 `start_at, id`. 커서·size·응답은 API-S04와 같다.
  - API-S06: 인증 필요. 조건: `host_user_id = 나`, `status ≠ COMPLETED`, `start_at + 12h > now`. 나머지는 API-S05와 같다.
  - 4.4: 스케줄러가 늦을 수 있어 목록 쿼리는 상태와 함께 `start_at` 조건을 건다. 12h는 기존 설정 `ScheduleProperties.completionAfter`다.
  - 5장: `ix_schedules_status_start (status, start_at, id)`는 (start_at, id) keyset을 인덱스만으로 끊기 위한 것이다. `ix_schedules_host (host_user_id, start_at)`, `ix_participants_user (user_id, status)`가 V7에 이미 있다.
  - 9장 오류 코드: `INVALID_DATE_FILTER`(INVALID, API-S04 날짜 범위), `INVALID_CURSOR`(INVALID, 해석할 수 없는 커서). 문구는 명세에 없다.

### 결정

사용자 위임("권장안대로 진행")에 따라 명세·코드로 답이 나오지 않는 지점은 아래 권장안을 채택했다.

1. **`size`가 1보다 작으면 1로 올린다.** 50 초과는 명세대로 50. 즉 `[1, 50]`으로 자른다. 숫자가 아닌 값(`size=abc`)은 기존 공통 처리대로 400 `INVALID_REQUEST`. (권장안 채택)
   - 근거: 명세가 상한을 오류 대신 잘라내기로 정했으므로 하한도 같은 방식으로 맞춘다. 검증 제약(`@Min`)을 두지 않으면 `.claude/rules/api.md`의 "쿼리 파라미터는 검증 제약이 없으면 DTO 없이 `@RequestParam`" 기준에 그대로 들어맞는다.
2. **쿼리 파라미터는 Request DTO 없이 `@RequestParam`으로 받는다.** 파라미터가 여섯 개지만 Bean Validation 제약이 없다(날짜 역순·커서 해석은 업무 오류 코드로 낸다). (권장안 채택, `.claude/rules/api.md` Request DTO 기준)
3. **빈 값 필터(`categoryIds=`, `regionCodes=`)는 400이 아니라 "모르는 값"으로 다뤄 그 값에 맞는 일정이 없다(200).** `startDate=`·`endDate=`·`cursor=`·`size=`처럼 빈 값은 생략과 같다(null). (권장안 채택)
   - 근거: 명세 "모르는 categoryId·regionCode는 오류가 아니라 빈 결과". API-S02의 빈 `regionCode=` → `INVALID_REGION`은 그 API에만 정한 규칙이고 API-S04에는 해당 규칙이 없다. 문서에는 "빈 값으로 보내지 않는다"고 적는다.
   - UUID 형식이 아닌 `categoryIds=abc`, 형식이 틀린 날짜(`startDate=2026-13-01`)는 기존 공통 처리대로 400 `INVALID_REQUEST`다.
4. **커서 형식은 URL-safe Base64(패딩 없음) 문자열이다.** 문자는 `[A-Za-z0-9_-]`만 쓴다. FE가 그대로 쿼리에 붙여도 인코딩이 필요 없다. 내용은 마지막 항목의 (startAt, id)다. 해석 실패(Base64 아님, 구분자 없음, 날짜·UUID 형식 아님)는 모두 `INVALID_CURSOR`. (권장안 채택)
5. **마지막 페이지 판정은 size + 1건을 읽어 정한다.** 다음 건이 없으면 `nextCursor = null`. 총 건수가 size의 배수여도 빈 페이지를 한 번 더 부르게 하지 않는다. (권장안 채택)
6. **오류 판정 순서는 날짜 → 커서다.** 둘 다 틀리면 `INVALID_DATE_FILTER`. (권장안 채택. 파라미터 표의 순서)
7. **오류 문구**: `INVALID_DATE_FILTER` "끝 날짜는 시작 날짜보다 앞설 수 없어요.", `INVALID_CURSOR` "목록을 처음부터 다시 불러주세요." (권장안 채택. 명세에 문구가 없다. FE는 code로 분기한다)
8. **API-S04는 순수 공개 API다.** 응답이 보는 사람에 따라 달라지지 않으므로 principal을 받지 않고, 메서드에 `@SecurityRequirements`를 단다(`.claude/rules/api.md` 문서 절). `SecurityConfig`에는 `GET /api/v1/schedules` 정확한 경로만 연다. `/api/v1/schedules/participating`·`/hosted`·`POST /api/v1/schedules`는 계속 인증이 필요하다. (권장안 채택)
9. **활동 이름**: 활동이 비활성이어도 이름을 준다(명세). 활동 행이 없거나 삭제(`is_deleted`)됐으면 `name = ""`. (권장안 채택. `.claude/rules/package-structure.md` "조회는 삭제되지 않은 행만 본다", 지역이 없을 때 빈 문자열과 같은 처리)
10. **목록 API에서는 활성 사용자 확인(POL-S9)을 하지 않는다.** 명세가 POL-S9를 개설·참여에만 걸었고, 목록은 읽기만 한다. (권장안 채택)
11. **API-S05·S06은 필터(categoryIds 등)를 받지 않는다.** 명세가 "커서·size·응답은 API-S04와 같다"고만 했다. 보내도 무시된다. (권장안 채택)
12. 마이그레이션은 없다. 필요한 인덱스가 V7에 있다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-172` (브랜치 `feat/SHI-172-schedule-list`, 베이스 `3c84a20`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. `snsg/.env`는 이미 복사돼 있다.
- **구현 시작 전에 develop을 맞춘다.** T-13(SHI-171, 워크트리 `C:\Users\ks\nss\dev\snsg-be\wt-SHI-171`)이 먼저 머지된다는 전제다.
  ```bash
  cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172 && git fetch origin && git ls-tree -r origin/develop --name-only | grep JoinScheduleFacade
  ```
  - 출력이 있으면(T-13 머지됨) `git rebase origin/develop` 뒤 시작한다(워크트리의 미추적 파일은 이 task.md뿐이라 rebase를 막지 않는다). 이후 "베이스"는 rebase한 `origin/develop` 커밋이다.
  - 출력이 없으면 **구현하지 말고 멈춰서 보고한다.** 오케스트레이터가 순서를 정한다.
- T-13과 겹치는 파일: `ScheduleRepository`·`ScheduleRepositoryImpl`·`ScheduleJpaRepository`(T-13이 일정 잠금 조회를 더한다), `ScheduleErrorCode`(T-13이 `SCHEDULE_NOT_FOUND` 등 7개를 더한다), `ScheduleController`·`ScheduleControllerTest`(T-13이 참여 엔드포인트와 `@MockitoBean`을 더한다), `ScheduleRepositoryImplTest`, `docs/api-guide.md`(T-13이 "일정" 절에 참여·참여 취소 절, 화면별 표 행을 더하고 "아직 없다" 문장을 고친다), `docs/openapi.yaml`. rebase 후의 내용 위에 더한다. T-13이 더한 코드·문서는 고치지 않는다.
- develop의 마이그레이션은 V1~V8이다(T-13도 더하지 않는다). 이번 작업도 더하지 않으므로 공유 DB의 Flyway 충돌은 없다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 로컬 DB에 붙는다. DB가 꺼져 있거나 다른 워크트리가 테스트를 돌리는 중이면 실패할 수 있다. 그때는 코드를 고치지 말고 멈춰서 보고한다.
- **`schedules` 테이블에는 다른 테스트·워크트리의 행이 있을 수 있다.** 모집 중 목록은 사용자로 범위가 좁혀지지 않으므로, 그 테스트는 무작위 `category_id`로 넣은 행만 `categoryIds` 필터로 골라 단정한다(참고 절).
- 로컬 DB의 초기 데이터: 활성 지역 `11440`(마포구), `11410`(서대문구), `11200`(성동구). 활동 `01a19cc6-3d80-7000-8000-000000000001` BOARD_GAME(보드게임), `...0002` ESCAPE_ROOM(방탈출), `...0003` WALK(산책). 모두 활성이다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 아래 "테스트"는 `.claude/rules/testing.md`의 계층 기준으로 둔다(대상 클래스와 같은 패키지, 같은 이름 + `Test`). 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지(어떤 ErrorCode인지, 어떤 일정이 포함·제외되는지) 확인한다. 기존 테스트 메서드의 단정은 바꾸지 않는다(생성자 인자 추가처럼 컴파일을 위한 수정, 픽스처 헬퍼에 선택 값을 더하는 수정만 허용).

1. `ScheduleErrorCode`에 `INVALID_DATE_FILTER`, `INVALID_CURSOR`가 `ErrorType.INVALID`와 결정 7의 문구로 더해진다. 기존 코드(T-13이 더한 것 포함)의 값·문구는 바뀌지 않는다.
   - 검증: `grep -nE "^\s+(INVALID_DATE_FILTER|INVALID_CURSOR)\(" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → 2줄, 둘 다 `ErrorType.INVALID`와 결정 7의 문구
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172 && git diff --staged -- snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java | grep '^-[^-]'` → 출력 없음, 또는 마지막 기존 상수 줄의 `;` → `,` 변경 한 줄뿐

2. 설정 `schedule.page-size-default: 20`, `schedule.page-size-max: 50`이 `application.yaml`에 있고 `ScheduleProperties`로 읽는다. 목록 3종 모두 size를 생략하면 20, 50 초과면 50, 1 미만이면 1로 쓴다(결정 1).
   - 검증: `grep -nE "page-size-(default|max)" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg/src/main/resources/application.yaml` → `page-size-default: 20`, `page-size-max: 50` 두 줄(`schedule:` 아래)
   - 검증: Mockito 테스트(`ScheduleQueryService` 또는 size를 정하는 클래스의 테스트)가 생략 → 20, 51 → 50, 0 → 1로 조회 한도가 정해지는 것을 단정한다.

3. 모집 중 목록(API-S04)의 조회 범위가 명세·D8과 같다. 삭제되지 않았고, `status = SCHEDULED`이고, `start_at > now`인 일정만 온다. 모집 마감(`recruitment_status = CLOSED`, 정원 찬) 일정도 온다. 정렬은 `start_at, id` 오름차순.
   - 검증: 실제 PostgreSQL 테스트(`ScheduleRepositoryImplTest` 등 `@DataJpaTest`)가 다음을 단정한다.
     - 포함: 시작 전 `SCHEDULED`·`RECRUITING` 일정, 시작 전 `SCHEDULED`·`CLOSED` 일정
     - 제외: `start_at <= now`인 `SCHEDULED` 일정(아직 `RECRUITING`인 것 포함. 경계 `start_at = now`도 제외), `CANCELED` 일정, `COMPLETED` 일정, `is_deleted = true` 일정
     - 같은 `start_at`의 일정 여러 건이 id 오름차순으로 온다.

4. 모집 중 목록의 필터가 명세와 같다.
   - `categoryIds`·`regionCodes`는 여러 개를 받고, 같은 필터 안에서는 OR, 두 필터 사이는 AND다. 생략하면 그 조건이 없다. 모르는 값만 보내면 빈 결과다.
   - `startDate`·`endDate`는 시작 날짜 기준 양 끝 포함이다. 하나만 오면 그날 하루다. `endDate < startDate`면 400 `INVALID_DATE_FILTER`. 같은 날이면 그날 하루다. 과거 날짜는 오류가 아니라 빈 결과다.
   - 검증: 실제 PostgreSQL 테스트가 다음을 단정한다.
     - 활동 두 개 지정 → 두 활동의 일정만, 지역 두 개 지정 → 두 지역의 일정만, 활동 + 지역 지정 → 둘 다 맞는 일정만
     - 날짜 범위 `[D, D+1]` → D 00:00 ~ D+1 23:30 일정 포함, D-1 23:30·D+2 00:00 일정 제외
     - 하루(시작·끝이 같은 날짜 범위) → 그날 일정만
   - 검증: Mockito 테스트가 다음을 단정한다.
     - `startDate`만 → 그날 하루 범위로 조회, `endDate`만 → 그날 하루 범위로 조회
     - `endDate < startDate` → `INVALID_DATE_FILTER`, repository가 호출되지 않는다
     - 날짜와 커서가 둘 다 틀리면 `INVALID_DATE_FILTER`(결정 6)

5. 커서 페이지가 중복·누락 없이 끝까지 넘어간다(같은 `start_at` 여러 건 포함). 응답의 `nextCursor`는 다음 페이지가 있을 때만 값이 있고 마지막 페이지에서 `null`이다. 총 건수가 size의 배수여도 마지막 페이지에서 `null`이다(결정 5). `nextCursor`는 `[A-Za-z0-9_-]`만으로 된 문자열이고, 해석할 수 없는 커서는 400 `INVALID_CURSOR`다(결정 4).
   - 검증: 실제 PostgreSQL 테스트가 같은 `start_at` 3건 이상을 포함한 5건 이상을 size 2로 끝까지 넘겨, 모은 id 목록이 정렬 순서의 전체 목록과 정확히 같다(중복·누락 없음)고 단정한다.
   - 검증: 테스트가 커서를 만들었다 다시 읽으면 같은 (startAt, id)가 나오고, 만든 문자열이 정규식 `^[A-Za-z0-9_-]+$`에 맞는다고 단정한다.
   - 검증: 테스트가 해석할 수 없는 커서(Base64가 아닌 문자열, Base64지만 구분자·날짜·UUID 형식이 아닌 내용)에 `INVALID_CURSOR`를 단정한다.
   - 검증: Mockito 테스트가 repository가 size + 1건을 돌려주면 items는 size건이고 `nextCursor`가 마지막 item의 커서이며, size건 이하를 돌려주면 `nextCursor`가 null임을 단정한다.

6. 참여한 일정(API-S05)의 범위가 명세·D7과 같다. 내 참여 행이 `CONFIRMED`·`MEMBER`(삭제되지 않음)이고, 일정이 삭제되지 않았고 `status ≠ COMPLETED`이며 `start_at + completionAfter(12h) > now`인 일정만 온다. 정렬·커서는 API-S04와 같다.
   - 검증: 실제 PostgreSQL 테스트가 다음을 단정한다.
     - 포함: 내가 확정 참여한 예정 일정, 개설자가 취소한(`CANCELED`) 일정, 시작했지만 시작 + 12h 전인 일정
     - 제외: 내가 참여 취소한(`CANCELED` 행) 일정, `COMPLETED` 일정, 시작 + 12h가 지난(`start_at + 12h <= now`) `SCHEDULED` 일정, 내가 개설자(`HOST` 행)인 일정, 다른 사용자만 참여한 일정, 삭제된 일정
     - 취소 후 다시 참여해 `CANCELED` 행과 `CONFIRMED` 행이 함께 있는 일정이 한 번만 온다.

7. 개설한 일정(API-S06)의 범위가 명세와 같다. `host_user_id = 나`이고, 삭제되지 않았고 `status ≠ COMPLETED`이며 `start_at + 12h > now`인 일정만 온다. 정렬·커서는 API-S04와 같다.
   - 검증: 실제 PostgreSQL 테스트가 다음을 단정한다.
     - 포함: 내가 연 예정 일정, 내가 연 뒤 취소한 일정
     - 제외: 내가 `MEMBER`로 참여만 한 일정, `COMPLETED` 일정, 시작 + 12h가 지난 일정, 삭제된 일정

8. `RegionPort`에 비활성 지역까지 모든 지역을 한 번에 돌려주는 조회가 더해지고, `ScheduleRegionAdapter`가 place `FindRegionUseCase.findAll`로 구현한다. schedule이 받는 지역 결과에 화면에 보일 이름(`RegionResult.sigunguName`, 예: 마포구)이 담긴다. 기존 `find`의 동작과 `ListPlaceCandidatesFacade`의 동작은 그대로다.
   - 검증: `grep -n "findAll" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/port/out/RegionPort.java /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/client/ScheduleRegionAdapter.java` → 두 파일 모두 한 줄 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172 && git diff --staged --name-only -- snsg/src/main/java/com/shineunsigan/snsg/place` → 출력 없음(place의 `FindRegionUseCase.findAll`은 이미 있다)

9. `ListSchedulesFacade`(schedule application, 트랜잭션 없음)가 세 목록을 맡는다. `ScheduleQueryService`(읽기 전용 트랜잭션, repository만 의존)로 일정을 읽고, `RegionPort`의 전체 조회를 요청당 한 번만 불러 `regionName`을 채운다. 지역 코드가 지역 목록에 없으면 `regionName = ""`, 비활성 지역이면 이름을 그대로 준다. 카드의 `category.name`은 비활성 활동이어도 이름, 없거나 삭제된 활동이면 `""`(결정 9). `remainingSeats = capacity − confirmedCount`, `displayStatus`는 `Schedule.displayStatus(now, completionAfter)`로 정한다.
   - 검증: `ListSchedulesFacadeTest`(Mockito)가 다음을 단정한다.
     - 지역 이름 채우기: 활성 지역·비활성 지역은 `sigunguName`, 목록에 없는 코드는 `""`
     - 여러 건이어도 `RegionPort`의 전체 조회가 1번만 호출되고 `RegionPort.find`는 호출되지 않는다
     - 일정이 0건이면 빈 items와 `nextCursor = null`
   - 검증: 테스트(Mockito 또는 실제 PostgreSQL)가 비활성 활동의 일정 카드에 그 활동 이름이 담기는 것을 단정한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg && ./gradlew cleanTest test --tests '*ArchitectureTest'` → BUILD SUCCESSFUL, 실패 0(facade에 `@Transactional` 없음, QueryService가 `port/out`에 의존하지 않음)

10. API 세 개가 명세의 경로·인증·요청·응답으로 열린다.
    - `GET /api/v1/schedules`(공개, 토큰 없이 200), `GET /api/v1/schedules/participating`(인증 필요), `GET /api/v1/schedules/hosted`(인증 필요). 응답 `data`는 `{items, nextCursor}`이고 item 필드는 `id`, `category{id, name}`, `title`, `regionName`, `startAt`, `confirmedCount`, `capacity`, `remainingSeats`, `displayStatus`, `createdAt`이다. 장소명은 없다.
    - 쿼리 파라미터 이름: `categoryIds`, `regionCodes`, `startDate`, `endDate`, `cursor`, `size`(API-S04), `cursor`, `size`(API-S05·S06). Request DTO 없이 받는다(결정 2).
    - 검증: `ScheduleControllerTest`(기존 설정: auth 스캔 제외, `addFilters = false`)가 다음을 단정한다.
      - 세 경로 성공: 200, `$.data.items[0]`에 위 필드가 있고 `place`·`placeName` 필드가 없다, `$.data.nextCursor`
      - `categoryIds=a&categoryIds=b`처럼 반복 파라미터가 목록으로 facade에 넘어간다
      - `categoryIds=`·`regionCodes=`(빈 값) → 200(결정 3)
      - `startDate=2026-13-01`(없는 날짜), `size=abc`, `categoryIds=abc` → 400 `INVALID_REQUEST`
      - facade가 `INVALID_DATE_FILTER`·`INVALID_CURSOR`를 던지면 400과 그 code
      - 참여한·개설한 목록이 SecurityContext의 userId로 facade를 부른다
    - 검증: `SecurityConfigTest`(실제 `SecurityConfig`·인증 필터·진입점)가 다음을 단정한다.
      - 토큰 없이 `GET /api/v1/schedules` → 200
      - 토큰 없이 `GET /api/v1/schedules/participating`, `GET /api/v1/schedules/hosted`, `POST /api/v1/schedules` → 401 `UNAUTHENTICATED`
    - 검증: `grep -n '"/api/v1/schedules' /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java` → `HttpMethod.GET, "/api/v1/schedules"` 한 줄만. `/api/v1/schedules/*`·`/api/v1/schedules/**` 패턴이 없다

11. `docs/openapi.yaml`이 코드에서 다시 만들어져 세 경로를 담는다. 각 엔드포인트의 springdoc 애노테이션이 `.claude/rules/api.md` 문서 절 항목(부르는 화면, 파라미터별 의미·형식·생략/빈 값의 뜻, 정렬·개수 제한·빈 결과, 응답 필드와 null 여부(`nextCursor`), `displayStatus` 값별 뜻, 오류별 상태·code·조건·화면에서 할 일, 인증 여부, 재시도 안전)을 담는다. API-S04에는 `@SecurityRequirements`가 있고 API-S05·S06에는 없다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'` 실행 후 `./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
    - 검증: `grep -nE "^  /api/v1/schedules(/participating|/hosted)?:" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/docs/openapi.yaml` → 세 줄
    - 검증: `grep -nE "INVALID_DATE_FILTER|INVALID_CURSOR" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/docs/openapi.yaml` → 둘 다 한 줄 이상

12. `docs/api-guide.md`의 "일정" 절에 목록 3종 절이 더해지고 `.claude/rules/api.md` 문서 절의 항목을 담는다. 화면별 표에 LIST-01~05·CRT-07의 호출 행이 더해지고, "아직 없다" 문장에서 목록이 빠진다. T-13이 더한 절·행은 고치지 않는다.
    - 담을 것: 세 경로와 인증 여부, 파라미터 표(생략·빈 값의 뜻, 여러 값 보내는 법 `categoryIds=a&categoryIds=b`, 날짜 하루·범위·역순, size 기본·상한·하한), 커서 사용법(`nextCursor`를 그대로 `cursor`로, null이면 끝, 필터를 바꾸면 커서 없이 처음부터, 커서는 해석하지 않는 불투명 문자열), 응답 예시와 필드별 의미(`regionName` 빈 문자열, `remainingSeats`, `createdAt`으로 NEW 배지), `displayStatus` 값별 화면 문구, 각 목록에 무엇이 포함·제외되는지(모집 마감 포함, 개설자 취소 일정이 참여한·개설한 목록에 "일정 취소됨"으로 남음, 완료 일정 없음), 오류 표와 예시 JSON, 재시도 안전
    - 검증: `grep -nE "/api/v1/schedules/participating|/api/v1/schedules/hosted|nextCursor|INVALID_DATE_FILTER|INVALID_CURSOR" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/docs/api-guide.md` → 다섯 패턴 각각 한 줄 이상
    - 검증: `grep -n "아직 없다" /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/docs/api-guide.md` → 일정 API 목록 문장에 "목록"이 없다

13. 전체 빌드가 통과한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
    - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다.
    - 끝내지 않는 경우: 실패가 줄었지만 0이 아닐 때, 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컴파일이 깨져 테스트가 돌지 않았을 때, `OpenApiSpecTest`를 건너뛰었을 때.

14. 범위 밖 파일을 고치지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-172 && git status --short` → 모든 경로가 워크트리 안이고 다음을 만족한다.
      - `user/`·`place/`·`common/` 패키지의 main 파일 변경이 없다. `auth/` main 파일은 `SecurityConfig.java`만 바뀐다.
      - `snsg/src/main/resources/db/migration/` 변경이 없다.
      - 임시 파일(로그·스크래치)이 없다.

## 비목표

- 일정 상세(API-S07, T-12), 명단(API-S08), 전체 취소(API-S11), 상태 전환 스케줄러(T-17), 지난 일정 보기(이후 작업)는 하지 않는다. 상세 경로(`GET /api/v1/schedules/{scheduleId}`)의 공개 설정도 지금 넣지 않는다(`.claude/rules/api.md` "공개 경로는 API를 만들 때 하나씩").
- 마이그레이션·인덱스를 더하지 않는다.
- 목록 API에서 사용자 활성 확인(`UserPort.isActive`)을 부르지 않는다(결정 10).
- API-S05·S06에 필터 파라미터를 더하지 않는다(결정 11).
- size·커서에 Bean Validation 제약이나 Request DTO를 두지 않는다(결정 1·2).
- T-13이 더한 코드(참여·참여 취소 흐름, 일정 잠금 조회, 오류 코드 7개, 참여 엔드포인트, api-guide 참여 절)와 기존 개설 흐름(`CreateScheduleFacade`, `ScheduleCommandService.create`), `ListPlaceCandidatesFacade`, `PlaceCandidateController`, 기존 오류 코드·문구는 바꾸지 않는다. 생성자 주입이 늘어 기존 테스트의 생성 코드가 컴파일되지 않을 때만 그 줄을 고친다.
- `docs/api-guide.md`의 기존 절(인증·지역·활동·장소 후보·일정 개설·T-13의 참여 절)은 고치지 않는다. 화면별 표, "아직 없다" 문장, 목록 절 추가만 한다. 공통 규칙에 목록 페이지(커서) 공통 절을 둘지는 refine-docs 단계가 판단한다.
- `.claude/rules/`, `.claude/skills/`, `CLAUDE.md`는 구현 단계에서 고치지 않는다(refine-docs 단계가 맡는다).
- place 도메인 파일은 고치지 않는다. `FindRegionUseCase.findAll`은 이미 있다.
- 다른 워크트리(`wt-SHI-171` 등)의 파일을 읽기 외로 건드리지 않는다.

## 제약

- 패키지 구조·Facade 기준·도메인 간 참조·soft delete 조회 조건은 `.claude/rules/package-structure.md`를 따른다.
- API·문서·공개 경로·쿼리 파라미터 규칙은 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. DB 슬라이스 구성을 더하면 `./gradlew cleanTest build`로 전체를 다시 돌린다.
- 시간대·Jackson 3·Boot 4 모듈 주의는 `CLAUDE.md`를 따른다.
- 커밋은 하지 않는다. 스테이징까지만 하고(`.claude/rules/git-workflow.md` 3번), 커밋 분리 계획을 함께 보고한다. 권장: ① 오류 코드·설정·RegionPort·repository·QueryService·facade·컨트롤러·SecurityConfig·`docs/openapi.yaml` ② 테스트 ③ `docs/api-guide.md`. (`docs/openapi.yaml`은 API를 바꾼 코드와 같은 커밋)
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 구현 형태 제안 (바꿔도 됨. 단 GOAL에 고정한 이름 `ListSchedulesFacade`, `ScheduleQueryService`, `ScheduleErrorCode`의 코드 이름, 경로·파라미터·응답 필드 이름은 유지)

- 커서: schedule domain에 값 객체(예: `ScheduleCursor(LocalDateTime startAt, UUID id)`)를 두고 `encode()`/`decode(String)`를 둔다. `Base64.getUrlEncoder().withoutPadding()`으로 `"<startAt ISO>|<uuid>"` 같은 문자열을 인코딩한다. 디코딩의 모든 실패(`IllegalArgumentException`, `DateTimeParseException`, 구분자 없음)를 `BusinessException(INVALID_CURSOR)`로 바꾼다. 순수 JUnit으로 테스트한다.
- 날짜 범위: 날짜 둘을 받아 `[from, to)` 시각 범위로 바꾸고 역순이면 `INVALID_DATE_FILTER`를 내는 작은 값 객체나 QueryService private 메서드. startDate만 → `[startDate 00:00, startDate+1 00:00)`, endDate만 → `[endDate 00:00, endDate+1 00:00)`, 둘 다 → `[startDate 00:00, endDate+1 00:00)`.
- size: `Math.max(1, Math.min(size == null ? default : size, max))`. 설정은 `ScheduleProperties`에 `pageSizeDefault`, `pageSizeMax`를 더한다(record에 필드를 더하면 기존 테스트의 `new ScheduleProperties(...)` 호출을 함께 고친다).
- `ScheduleQueryService`(`@Transactional(readOnly = true)`, `ScheduleRepository`·`ActivityCategoryRepository`·`ScheduleProperties`만 의존): `findRecruiting(filter, cursor, size)`, `findParticipating(userId, cursor, size)`, `findHosted(userId, cursor, size)`가 size + 1건을 읽어 `ScheduleCardResult`(지역 코드 포함, 지역 이름 없음) 목록과 nextCursor를 돌려준다. `now`는 `LocalDateTime.now()`를 한 번 잡아 쿼리와 `displayStatus`에 같이 쓴다. 활동 이름은 읽은 일정의 category_id를 모아 한 번에 조회한다(`ActivityCategoryRepository`에 삭제되지 않은 활동을 id 목록으로 찾는 메서드, 활성 여부 무관). N+1을 만들지 않는다.
- `ListSchedulesFacade`: QueryService 결과에 `RegionPort.findAll()`로 만든 코드 → 이름 맵을 붙여 최종 Result를 만든다. 세 목록이 한 facade의 세 메서드다(명세 "한 행위 카드 목록의 세 범위").
- `RegionPort.findAll()` → `List<ScheduleRegionResult>`. `ScheduleRegionResult`에 `name`(= `sigunguName`)을 더하거나 별도 record를 둔다. 필드를 더하면 `find`의 adapter 변환과 기존 테스트의 생성 코드를 함께 고친다.
- repository: `ScheduleRepository`에 세 목록 조회를 더한다. 인자로 `now`, 완료 기준 시각(`now - completionAfter`), keyset (startAt, id)(없으면 처음), 한도를 받는다. 주의:
  - keyset은 `s.startAt > :cursorStartAt or (s.startAt = :cursorStartAt and s.id > :cursorId)`로 쓴다. 정렬은 반드시 DB에서 `order by s.startAt, s.id`로 한다. **Java의 `UUID.compareTo`는 부호 있는 비교라 PostgreSQL uuid 정렬과 다르다.** 메모리에서 다시 정렬하거나 커서를 Java로 비교하지 않는다.
  - 선택 필터(목록·날짜·커서)를 JPQL `(:ids is null or s.categoryId in :ids)`로 쓰면 Hibernate에서 컬렉션 null 바인딩이 깨질 수 있다. JPA Criteria, 조건별 쿼리 분기, 또는 "필터 사용 여부" boolean 파라미터 + 비지 않은 목록 중 하나를 고른다. Spring Data `Pageable`/`Slice`는 쓰지 않아도 된다(`setMaxResults(size + 1)`).
  - 참여한 목록은 `ScheduleParticipant p join p.schedule s where p.userId = :userId and p.role = MEMBER and p.status = CONFIRMED and p.isDeleted = false ...`. 확정 행은 일정당 하나뿐이라(부분 유니크 인덱스) 중복이 생기지 않는다.
  - 모든 조회에 `s.isDeleted = false`를 명시한다(전역 필터 없음).
- 컨트롤러: 기존 `ScheduleController`(`/api/v1/schedules`)에 `@GetMapping`, `@GetMapping("/participating")`, `@GetMapping("/hosted")`를 더한다. `@RequestParam(required = false) List<UUID> categoryIds`, `List<String> regionCodes`, `LocalDate startDate`·`endDate`(`@DateTimeFormat(iso = DATE)` 필요 여부 확인), `String cursor`, `Integer size`. 빈 `categoryIds=`는 Spring 변환에서 `[null]`이 될 수 있으므로 facade에 넘기기 전후 어디서든 결정 3대로(그 값에 맞는 일정 없음) 동작하게 한다. 응답 record 예: `ScheduleCardResponse`, `ScheduleCardPageResponse(items, nextCursor)`.
- `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/v1/schedules").permitAll()`. 상세(T-12)에서 `GET /api/v1/schedules/*`를 열면 `/participating`·`/hosted`도 함께 열리므로, T-12는 패턴 대신 `/{scheduleId}` 형태를 피하거나 participating·hosted를 먼저 authenticated로 둬야 한다. 이번 작업에서는 정확한 경로 하나만 연다(코드 주석으로 남겨도 된다).
- `SecurityConfigTest`는 지금 `@WebMvcTest(controllers = UserController.class)`다. `ScheduleController`를 `controllers`에 더하고 그 컨트롤러가 의존하는 facade(`CreateScheduleFacade`, `ListSchedulesFacade`, T-13의 `JoinScheduleFacade`·`ScheduleCommandService` 등)를 `@MockitoBean`으로 둔다. 테스트 코드는 ArchUnit 대상이 아니다.
- 실제 PostgreSQL 테스트 데이터: 기존 `ScheduleRepositoryImplTest`의 native INSERT 헬퍼(`ScheduleRow`)에 startAt·categoryId·regionCode·hostUserId·recruitmentStatus 선택 값을 더해 쓴다. 기존 테스트의 단정은 그대로 둔다. **모집 중 목록 테스트는 무작위 categoryId를 일정에 넣고 `categoryIds`에 그 id를 넣어 조회한다.** 다른 테스트·워크트리의 행이 섞이지 않는다(지역 필터 테스트도 무작위 categoryId와 함께 건다). 시각 경계 테스트는 `now`를 인자로 넘겨 고정한다.

### refine-docs 단계가 점검할 문서 후보

- `docs/api-guide.md`: 목록 3종 절, 화면별 표(LIST-01~05, CRT-07, 개설 후 LIST-05 이동), "아직 없다" 문장, 공통 규칙에 "목록 페이지(커서)" 절을 둘지(이후 목록 API가 같은 규약을 쓴다).
- `.claude/rules/api.md`: 커서 목록 규약(keyset (정렬 키, id), 불투명 URL-safe 문자열, `nextCursor` null = 끝, size 기본·상한·하한은 잘라내기, 해석 실패는 도메인 `INVALID_CURSOR`)을 처음 정한 사례로 남길지. Request DTO 기준의 쿼리 파라미터 예시에 `ScheduleController` 목록(여섯 파라미터, 제약 없음)을 더할지. 공개 경로 절에 "같은 prefix의 하위 경로가 인증 필요면 정확한 경로만 연다(`GET /api/v1/schedules` vs `/participating`)" 예시를 더할지.
- `.claude/rules/package-structure.md`: Facade 예시에 `ListSchedulesFacade`(QueryService + `RegionPort.findAll` 한 번)를 더할지. UUID 정렬을 Java에서 하지 않는다는 주의를 둘지.
- `.claude/rules/testing.md`: 공유 테이블(사용자로 범위가 좁혀지지 않는 목록) 쿼리 테스트는 무작위 키로 넣은 행만 골라 단정한다는 규칙.
- 명세(`planning/be-spec-mvp.html`)는 저장소 밖이라 고치지 않는다. 결정 1·3·4·7·9를 명세에 반영할지는 사용자에게 보고만 한다.

### 그 밖

- `displayStatus` 판정은 `Schedule.displayStatus(now, completionAfter)` 한 곳에서만 한다(명세 4.4). 모집 중 목록은 `RECRUITING`·`RECRUITMENT_CLOSED`만, 참여한·개설한 목록은 `CANCELED`도 나온다. 쿼리 조건 때문에 `COMPLETED`는 나오지 않는다.
- 공개 API에 토큰을 보내도 응답은 같다. 틀린 토큰도 인증 필터가 그냥 통과시킨다(`.claude/rules/api.md`).
- 개설 성공 후 FE는 LIST-05(개설한 일정, API-S06)로 이동한다(API-S03 응답 설명). 방금 연 일정은 시작 + 12h 전이므로 목록에 나온다.

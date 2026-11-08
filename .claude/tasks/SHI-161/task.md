## 배경

- Linear SHI-161. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-11 "장소 후보 API (schedule → place)"를 구현한다. 화면은 CRT-02(장소 선택)다. API 번호는 API-S02다.
- 선행 T-07(place `FindPlaceUseCase`·`FindRegionUseCase`, PR #44), T-08(활동, PR #45), T-10(일정 개설·`PlacePort`·`SchedulePlaceAdapter`, PR #47)과 facade 이름 정리(PR #48)가 develop에 머지돼 있다.
- 명세 완료 조건: 산책 → PARK 장소만 / 보드게임 → BOARD_GAME_CAFE만 / regionCode 생략 시 활성 지역 전부 / 노출 대상(APPROVED)만 / 결과 없으면 빈 배열 / 비활성 활동 INVALID_CATEGORY / 없는·비활성 지역 INVALID_REGION.
- 사용자와 합의한 결정
  - 흐름 클래스는 schedule application의 `ListPlaceCandidatesFacade`다. "UseCase"라는 이름을 쓰지 않는다(PR #48, `.claude/rules/package-structure.md` "Service와 Facade"·Port 절. 선례 `CreateScheduleFacade`). 트랜잭션을 걸지 않는다. 의존은 `ActivityCategoryQueryService`(`findActivePlaceType`), `RegionPort`, `PlacePort` 셋이다.
  - `RegionPort`(신규, schedule `application/port/out`)는 이번에 `find(code)` 하나만 가진다. 명세의 `findAll`은 쓰는 곳(T-12·T-16)에서 더한다. 어댑터는 `ScheduleRegionAdapter`(schedule `infrastructure/client`) → place `FindRegionUseCase.find`다. `FindRegionUseCase`는 비활성 지역도 돌려주므로 활성 판정은 schedule이 한다.
  - `regionCode`를 아예 보내지 않으면 활성 지역 전체다. `regionCode=`(빈 문자열)이나 공백만 보내면 400 `INVALID_REGION`이다.
  - `PlacePort`에 목록 조회 `findExposed`를 더한다. `SchedulePlaceAdapter`가 `ActivityPlaceType` → place `PlaceType`을 `default` 없는 switch로 바꿔 place `FindPlaceUseCase.findExposed`를 부르고, 결과는 기존 `ExposedPlaceResult`로 바꾼다. 결과에 `UNCLASSIFIED`가 올 수 없지만 기존 변환(`toExposedPlace`)을 그대로 거쳐 빠지게 한다.
  - `categoryId`가 없거나 UUID 형식이 아니면 기존 공통 처리대로 400 `INVALID_REQUEST`다. 새 코드를 만들지 않는다.
  - V5 초기 데이터에 PARK 장소가 없다(PARK는 T-19 적재로 들어온다). "산책 → PARK만"은 테스트가 넣은 행으로 확인한다.
  - 마이그레이션은 없다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-161` (브랜치 `feat/SHI-161-place-candidates`, 베이스 `ef6b4e2`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리는 없다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V7이다. 이번 작업은 더하지 않는다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다. JSON 확인은 `grep`으로 한다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 로컬 DB에 붙는다. DB가 꺼져 있으면 이 테스트들이 실패한다.
- 로컬 DB의 초기 데이터: 활성 지역 `11440`(마포구, display_order 1), `11410`(서대문구, 2), `11200`(성동구, 3). 활동 `01a19cc6-3d80-7000-8000-000000000001` BOARD_GAME(→ BOARD_GAME_CAFE), `...0002` ESCAPE_ROOM(→ ESCAPE_ROOM), `...0003` WALK(→ PARK). 모두 활성이다. V5 장소에 PARK는 0건이다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 그 클래스에 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.

1. `ListPlaceCandidatesFacade`(schedule application, 트랜잭션 없음)가 다음 순서로 처리한다.
   - 활동 조회: `ActivityCategoryQueryService.findActivePlaceType`이 비면 `INVALID_CATEGORY`
   - 지역 확인(지역을 지정했을 때만): `RegionPort.find`로 찾은 지역이 없거나 비활성이면 `INVALID_REGION`. 빈 문자열·공백만인 지역 코드도 `INVALID_REGION`이다.
   - 장소 조회: `PlacePort.findExposed`에 활동의 장소 유형과 지역 조건(지정한 지역 하나, 생략이면 활성 지역 전체)을 넘겨 받은 목록을 순서 그대로 돌려준다. 빈 목록이면 빈 목록을 돌려준다.
   - 검증: `CreateScheduleFacadeTest`와 같은 방식의 `ListPlaceCandidatesFacadeTest`(Mockito)가 다음을 단정한다.
     - 활동이 없거나 비활성: `INVALID_CATEGORY`. `RegionPort`·`PlacePort`가 호출되지 않는다.
     - 활동도 틀리고 지역도 틀림: `INVALID_CATEGORY`(활동을 먼저 본다)
     - 없는 지역: `INVALID_REGION`. `PlacePort`가 호출되지 않는다.
     - 비활성 지역: `INVALID_REGION`. `PlacePort`가 호출되지 않는다.
     - 빈 문자열과 공백만인 지역 코드: `INVALID_REGION`. `PlacePort`가 호출되지 않는다.
     - 지역 지정 정상: `PlacePort.findExposed`가 활동의 장소 유형과 그 지역 하나로 호출되고 결과를 그대로 돌려준다.
     - 지역 생략: `RegionPort`가 호출되지 않고, `PlacePort.findExposed`가 활동의 장소 유형과 "활성 지역 전체" 조건으로 호출된다.
   - 검증: `grep -rn "class ListPlaceCandidatesFacade" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/application` → 한 줄
   - 검증: `grep -n "Transactional" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/ListPlaceCandidatesFacade.java` → 출력 없음

2. `ScheduleErrorCode.INVALID_REGION`이 `ErrorType.INVALID`(→ 400)로 더해진다. 기존 코드의 값·문구는 바뀌지 않는다.
   - 검증: `grep -n "INVALID_REGION" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → 한 줄, 같은 줄에 `ErrorType.INVALID`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161 && git diff ef6b4e2 -- snsg/src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java | grep '^-[^-]'` → 출력 없음(추가만 있다)

3. `RegionPort`(schedule `application/port/out`, 신규)는 지역 코드 하나로 지역을 찾는 `find` 메서드 하나만 가진다. `ScheduleRegionAdapter`(schedule `infrastructure/client`)가 place `FindRegionUseCase.find`로 구현하고, 활성 여부를 schedule 쪽 타입으로 옮긴다. schedule의 application·domain·presentation은 place 패키지를 import하지 않는다.
   - 검증: `grep -nE "\);\s*$" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/port/out/RegionPort.java` → 한 줄(메서드 선언 하나), 그 줄에 ` find(`
   - 검증: `grep -n "FindRegionUseCase" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/client/ScheduleRegionAdapter.java` → 한 줄 이상
   - 검증: `grep -rln "com.shineunsigan.snsg.place\|com.shineunsigan.snsg.user" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule` → `schedule/infrastructure/` 아래 파일만

4. `PlacePort`에 노출 장소 목록 조회가 더해지고, `SchedulePlaceAdapter`가 place `FindPlaceUseCase.findExposed`로 구현한다. `ActivityPlaceType` → place `PlaceType` 변환은 `default` 없는 switch다. 결과는 기존 `ExposedPlaceResult`이고 place 결과의 순서(지역 display_order, 장소명)를 유지한다.
   - 검증: `grep -n "default" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/infrastructure/client/SchedulePlaceAdapter.java` → switch의 `default` 없음
   - 검증: 기존 `SchedulePlaceAdapterTest`(실제 PostgreSQL, 테스트 지역 99001 활성·99002 비활성, 장소는 native INSERT)에 목록 조회 테스트가 더해져 다음을 단정한다. 기존 테스트 메서드의 단정은 바꾸지 않는다.
     - PARK로 부르면 같은 지역에 넣은 BOARD_GAME_CAFE·ESCAPE_ROOM은 빠지고 PARK 장소만 `ActivityPlaceType.PARK`로 온다(산책 → 공원만).
     - BOARD_GAME_CAFE로 부르면 BOARD_GAME_CAFE 장소만 온다(보드게임 → 보드게임카페만).
     - 지역 99001을 지정하면 99001 장소만 온다.
     - 지역을 생략하면 99001의 장소가 포함되고 99002(비활성)의 장소는 없다. 로컬 DB의 다른 장소가 섞일 수 있으므로 "포함/미포함"으로 단정한다.
     - CANDIDATE·REJECTED·폐업·삭제 장소는 목록에 없다(노출 대상만).
     - 조건에 맞는 장소가 없으면 빈 목록이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg && ./gradlew cleanTest test --tests '*SchedulePlaceAdapterTest' --tests '*ListPlaceCandidatesFacadeTest'` → BUILD SUCCESSFUL, 실패 0

5. `GET /api/v1/place-candidates?categoryId=&regionCode=`(API-S02)
   - 토큰 없이 부를 수 있다. `SecurityConfig`는 GET 메서드와 이 경로만 정확히 연다.
   - `categoryId`(UUID) 필수, `regionCode` 선택. 생략과 빈 값을 구분해 facade에 넘긴다(생략 = 전체, 빈 값 = `INVALID_REGION`).
   - 성공은 200 `{isSuccess: true, data: [...]}`이고 원소 필드는 `id`, `name`, `roadAddress`, `addressDetail`, `latitude`, `longitude`, `regionCode` 일곱 개다. `placeType`, `regionName`은 없다. 페이지를 나누지 않는다.
   - 검증: `grep -n "place-candidates" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java` → 한 줄, 같은 줄에 `HttpMethod.GET`
   - 검증: 새 컨트롤러의 `@WebMvcTest` 테스트(`ScheduleControllerTest`와 같은 설정: auth 스캔 제외, `addFilters = false`, `GlobalExceptionHandler` `@Import`, facade는 `@MockitoBean`)가 다음을 단정한다.
     - 성공 200, `data[0]`에 일곱 필드가 있고 `placeType`이 없다.
     - `regionCode` 생략: facade가 "지역 생략"으로 호출된다.
     - `regionCode=`(빈 값): facade가 "지역 생략"이 아니라 빈 값으로 호출된다. facade가 `INVALID_REGION`을 던지면 400 `INVALID_REGION`이다.
   - 검증: 앱을 띄운 상태에서(아래 명령, 확인 후 반드시 멈춘다) 토큰 없이 다음을 확인한다.
     ```bash
     cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg && ./gradlew bootRun   # 별도 셸 또는 백그라운드
     B=http://localhost:8080/api/v1/place-candidates
     BG=01a19cc6-3d80-7000-8000-000000000001; WALK=01a19cc6-3d80-7000-8000-000000000003
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$BG"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$BG&regionCode=11410"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$WALK"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$BG&regionCode=99999"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$BG&regionCode="
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$(python -c 'import uuid;print(uuid.uuid4())')"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=$(python -c 'import uuid;print(uuid.uuid4())')&regionCode=99999"
     curl -s -w '\n%{http_code}\n' "$B"
     curl -s -w '\n%{http_code}\n' "$B?categoryId=abc"
     ```
     기대 결과(순서대로):
     1. `200`, `data`가 비어 있지 않다. `"regionCode"` 값은 `11440`·`11410`·`11200`뿐이고 `11440` 장소가 `11410`보다, `11410` 장소가 `11200`보다 앞에 온다. 본문에 `placeType`·`regionName`이 없다.
     2. `200`, 모든 `"regionCode"`가 `"11410"`이다.
     3. `200`, `"data":[]`(로컬 DB에 PARK 장소가 없다. 있으면 모두 공원이어야 하며 이 경우 결과를 그대로 보고한다)
     4. `400`, `"code":"INVALID_REGION"`
     5. `400`, `"code":"INVALID_REGION"`
     6. `400`, `"code":"INVALID_CATEGORY"`
     7. `400`, `"code":"INVALID_CATEGORY"`(활동을 먼저 본다)
     8. `400`, `"code":"INVALID_REQUEST"`
     9. `400`, `"code":"INVALID_REQUEST"`
   - 검증: `curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:8080/api/v1/place-candidates?categoryId=01a19cc6-3d80-7000-8000-000000000001"` → `401`(GET만 열렸다)

6. FE용 문서를 갱신한다.
   - `docs/openapi.yaml`에 `GET /api/v1/place-candidates`가 들어가고 코드와 일치한다. 컨트롤러 메서드에 `@SecurityRequirements`가 있어 이 경로에 bearer 요구가 없다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `grep -n "/api/v1/place-candidates" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/docs/openapi.yaml /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/docs/api-guide.md` → 두 파일 모두 한 줄 이상
   - 검증: `grep -n "아직 없다" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/docs/api-guide.md` → "장소 후보 API(T-11)는 아직 없다" 문장이 없고, "아직 없다" 목록에 장소 후보가 없다
   - `docs/api-guide.md`에 "장소 후보" 절이 더해지고 다음을 담는다(`.claude/rules/api.md` 문서 절의 항목).
     - 부르는 화면(장소 선택 화면 진입·지역 칩 변경), 인증이 필요 없음
     - 요청: `categoryId`(활동 목록의 `id`, 필수), `regionCode`(지역 목록의 `code`, 선택. 생략하면 서비스 중인 지역 전체. 빈 값으로 보내지 않는다)
     - 응답 필드마다 의미·형식(좌표는 WGS84 소수, `addressDetail`은 없으면 빈 문자열이고 표시할 때 주소 뒤에 붙인다, `roadAddress`에는 상세주소가 없다), `id`는 일정 개설의 `placeId`로 보낸다
     - 정렬(지역 노출 순서, 그 안에서 장소명 가나다순), 페이지 없음, 결과가 없으면 200 + 빈 배열("결과 없음" 표시)
     - 산책(공원) 후보는 장소 적재 전까지 비어 있을 수 있다
     - 오류마다 상태·code·조건·화면에서 할 일과 예시 JSON: `INVALID_REQUEST`(categoryId 누락·형식 오류), `INVALID_CATEGORY`, `INVALID_REGION`
     - 같은 요청을 다시 보내도 된다(조회)
   - "화면별로 쓰는 API" 표에 장소 선택(목록) 행이 더해지고, 일정 개설 절의 `placeId` 설명이 장소 후보 API를 가리킨다.

7. 전체 빌드와 테스트가 통과한다. ArchUnit 테스트(facade의 `@Transactional` 금지, 도메인 간 참조 대상 등)도 포함된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 테스트 실패 0
   - 검증: `grep -l 'skipped="[1-9]' /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/build/test-results/test/TEST-com.shineunsigan.snsg.schedule.*.xml` → 출력 없음

8. 범위 밖 파일을 고치지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161 && git status --short`. 기대 결과는 다음과 같다.
     - `snsg/src/main/resources/db/migration/` 아래 변경·추가가 없다.
     - `place/`·`user/`·`common/` 패키지의 main 파일 변경이 없다. `auth/`는 `SecurityConfig.java` 한 줄 추가만 있다.
     - `snsg/.env`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md` 변경이 없다(이 단계에서는. 문서 단계는 별도다).
     - 임시 파일이 없다.
   - 검증: `grep -rn "findAll" /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg/src/main/java/com/shineunsigan/snsg/schedule/application/port/out/RegionPort.java` → 출력 없음

## 비목표

- `RegionPort.findAll`과 응답의 `regionName`은 만들지 않는다. T-12·T-16에서 더한다(사용자 결정).
- 캐시, 페이지, 검색어, 좌표·거리 정렬은 만들지 않는다.
- 마이그레이션을 더하지 않는다. 기존 V1~V7, `.env`, Flyway 설정은 바꾸지 않는다.
- place·user·common 코드는 바꾸지 않는다. `FindPlaceUseCase`·`FindRegionUseCase`·`PlaceQueryService`·`RegionQueryService`·`PlaceRepositoryImpl`은 그대로 쓴다(정렬·노출 조건·"빈 지역 목록 = 활성 지역 전체"는 이미 place가 하고 `PlaceRepositoryImplTest`가 검증한다).
- auth는 `SecurityConfig`의 공개 경로 한 줄만 더한다.
- 기존 테스트의 단정, 기존 코드의 경고·스타일, `CreateScheduleFacade`·`ScheduleController`·`ScheduleCommandService`는 건드리지 않는다.
- `api-guide.md`의 기존 절(인증·지역·활동)은 고치지 않는다. 일정 개설 절의 `placeId` 설명, "화면별로 쓰는 API" 표, "아직 없다" 문장만 고치고 장소 후보 절을 더한다.
- `ScheduleRegionAdapter`는 필드를 옮기기만 하면 따로 테스트하지 않는다(`.claude/rules/testing.md` "쓰지 않는 테스트"). 어댑터 안에 판정 분기를 두면 그 분기는 테스트한다.
- 로컬 DB를 사용자 확인 없이 초기화하지 않는다. Flyway 검증 오류를 만나면 그 자리에서 멈추고 오류 메시지 원문을 보고한다.
- GOAL 5의 curl 3번에서 로컬 DB에 PARK 장소가 있으면 지우지 않는다. 결과를 그대로 보고한다.

## 제약

- 패키지 구조·의존 방향·Service와 Facade·Port·도메인 간 참조(enum 변환은 infrastructure에서 `default` 없는 switch)·코드 작성(메서드 단일 책임) 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API·Controller·공개 경로·springdoc 문서화·`docs/` 갱신은 `.claude/rules/api.md`를 따른다.
- 테스트 범위와 방식(DB 연결 수, `@Nested`, `cleanTest`)은 `.claude/rules/testing.md`를 따른다. 새 DB 슬라이스 구성을 늘리지 않도록 목록 조회 테스트는 기존 `SchedulePlaceAdapterTest`의 `@Import` 구성을 그대로 쓴다. 테스트는 원래 코드와 별도 커밋이 되도록 나눠 스테이징 계획을 보고한다.
- 앱을 띄워 확인한 뒤 반드시 멈춘다(8080 포트·Gradle 데몬 공유). 로그 등 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다.
- 커밋은 하지 않는다. 스테이징까지만 한다(`.claude/rules/git-workflow.md` 3번).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 문서 근거 (`be-spec-mvp.html`)
- T-11 범위와 완료 조건(10장), API-S02(응답 필드·처리·오류), 3.4 schedule facade 표(`ListPlaceCandidatesFacade`: 활동 조회 + `RegionPort.find` + `PlacePort.findExposed`), 9장 `INVALID_REGION`(INVALID, "API-S02의 regionCode가 없거나 비활성"), POL-P1(노출 조건, API-S02와 개설 검증이 같은 조건), C10(FE는 API-S02 결과로 목록·지도 핀을 그리고 개설에 placeId를 보낸다).
- 명세의 T-11 범위에 적힌 `RegionPort(find·findAll)`은 사용자 결정으로 `find`만 만든다.
- 오류 문구는 명세에 없다. 화면 톤(~요)으로 짓는다. 예: `INVALID_REGION` "선택할 수 없는 지역이에요. 지역을 다시 골라주세요."(기존 `INVALID_CATEGORY`·`INVALID_PLACE` 문구와 맞춘다)

### 구현 형태 제안 (바꿔도 됨. 단 GOAL에 고정한 이름 `ListPlaceCandidatesFacade`, `RegionPort`(`find`), `ScheduleRegionAdapter`, `PlacePort`, `SchedulePlaceAdapter`, `ExposedPlaceResult`, `INVALID_REGION`은 유지)
- `RegionPort.find(String code)` → `Optional<자체 record>`. 예: schedule `application/dto/result`의 `ScheduleRegionResult(String code, boolean active)`. T-12에서 `findAll`과 이름이 필요해지면 그때 필드를 더한다. place의 `RegionResult`를 application에 들이지 않는다. `UserPort.isActive`처럼 `boolean`을 돌려주는 형태도 가능하지만 사용자와 합의한 메서드 이름은 `find`다.
- `PlacePort.findExposed(ActivityPlaceType placeType, Collection<String> regionCodes)` → `List<ExposedPlaceResult>`. place 쪽 계약("비면 활성 지역 전체")과 같은 모양이라 어댑터가 그대로 넘긴다. facade가 지정 지역이면 `List.of(regionCode)`, 생략이면 `List.of()`를 넘긴다.
- 어댑터의 역방향 변환은 `toPlaceType(ActivityPlaceType)` switch(BOARD_GAME_CAFE·ESCAPE_ROOM·PARK). 결과는 기존 `toExposedPlace`로 `flatMap`해 `UNCLASSIFIED`를 뺀다.
- 빈 문자열·공백 지역 코드: facade에서 `isBlank()`면 포트를 부르지 않고 `INVALID_REGION`을 던지거나, 포트 조회가 비어 자연히 `INVALID_REGION`이 되게 둘 수 있다. 어느 쪽이든 GOAL 1의 단정을 만족하면 된다(Mockito strict stubs에서 불필요한 stub이 생기지 않게 주의).
- 컨트롤러: schedule `presentation/PlaceCandidateController`(`@Tag(name = "장소 후보")` 또는 기존 태그), `@RequestMapping("/api/v1/place-candidates")`, `@GetMapping` + `@SecurityRequirements`, `@RequestParam UUID categoryId`, `@RequestParam(required = false) String regionCode`. Spring은 `required = false`인 `String`의 `?regionCode=`를 `null`이 아니라 `""`로 넘긴다(`defaultValue`를 주면 빈 값이 기본값으로 바뀌므로 주지 않는다). 컨트롤러 테스트로 이 구분을 단정한다.
- 응답: `presentation/dto/response/PlaceCandidateResponse(UUID id, String name, String roadAddress, String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode)` + `from(ExposedPlaceResult)`. 필드마다 `@Schema(description, example)`. Request DTO는 만들지 않는다(파라미터를 그대로 넘긴다. api.md Request DTO 기준). 다만 파라미터가 둘이라 facade 인자로 그대로 넘기거나 Command record를 만들 수 있다. 판단은 구현에 맡긴다.
- `@ApiResponses`: 200(정렬·빈 배열), 400(`INVALID_REQUEST`·`INVALID_CATEGORY`·`INVALID_REGION`과 화면에서 할 일).
- `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/v1/place-candidates").permitAll()`을 activity-categories 줄 아래에 둔다.
- 스펙 재생성: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-161/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`
- 지금 `docs/api-guide.md`에서 고칠 곳: 일정 개설 표의 `placeId` 행("장소 후보 API(T-11)는 아직 없다"), "화면별로 쓰는 API" 표, 표 아래 "일정 개설 외의 일정 API(장소 후보·목록·상세·참여 등)와 알림 API는 아직 없다" 문장. 새 절은 "일정" 절 안(일정 개설 앞)에 둔다. 장소 선택 화면에서 고른 후보로 개설하기 때문이다.
- 앱 확인: bootRun은 백그라운드로 띄우고 로그를 스크래치패드에 둔다. 확인 후 프로세스를 멈추고 로그를 지운다.

### 머지 시 충돌 예상
- 지금 진행 중인 다른 브랜치는 없다. T-12 이후 작업은 `RegionPort`에 `findAll`을 더하게 된다.

## 배경

- Linear SHI-181 "상가정보 장소 적재 배치". 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖, 읽기 전용) T-19 "공공데이터 장소 적재 배치" 중 **상가정보(SEMAS) 원천만** 한다. 도시공원(`ParkPlaceSourceAdapter`)은 명세 C9(엔드포인트·이용허락범위 확인 전에는 만들지 않는다)대로 만들지 않는다.
- 사용자 요구(원문): "상가정보 적재 배치를 분기 단위로 할 수 있도록 하되 넉넉히 최신화 데이터를 받을 수 있을 정도의 주기여야 한다."
- 명세 근거 항목(요약. 원문은 명세 파일)
  - JOB-04: 분기 1회(cron 설정값). 활성 지역마다 `ImportPlacesFacade`가 받아 POL-P2~P7대로 반영한다. SEMAS는 1,000건씩 페이지를 넘겨 끝까지 받는다(3개 구 약 64,000건 → 약 65회 호출). place `infrastructure/scheduler`에 두고 facade를 부른다. 외부 호출은 트랜잭션 밖, 반영은 페이지마다 트랜잭션, 폐업 처리는 원천마다 트랜잭션 하나. 여러 서버가 동시에 돌면 호출 한도를 두 배로 쓰므로 시작할 때 `pg_try_advisory_lock`(세션 잠금)을 잡고 못 잡으면 건너뛴다. 수동 실행 경로는 만들지 않는다.
  - POL-P2(적재 대상, SEMAS 부분): 시군구 단위 조회(`storeListInDong`, `divId=signguCd`) 결과 중 (가) 상권업종 소분류(`indsSclsCd`)가 `place.semas-category-codes`(R10405 기타 오락장, R10499 기타 오락관련 서비스업)에 들거나, (나) 그 밖 업종이라도 상호명(+지점명)에 `place.name-keywords`("보드게임"·"보드카페"·"방탈출", 대소문자 무시)가 든 가게.
  - POL-P3(유형 추정): 상호명(+지점명) 키워드로 추정. "보드" → BOARD_GAME_CAFE, "방탈출"·"이스케이프"·"escape"(대소문자 무시) → ESCAPE_ROOM, 그 밖 → UNCLASSIFIED. 키워드는 `place.type-keywords`. 두 적재 경로 모두 같은 규칙.
  - POL-P4(갱신): 이미 구현돼 있다(`PlaceCommandService.upsert`, `Place.confirmSource`). 추가로 "업종 필터나 값 정리에서 걸러진 행이라도 이미 places에 있으면 `last_synced_at`을 갱신한다"(업종 분류 변경·설정 변경·좌표 누락을 폐업으로 오인하지 않기 위해) — **이 부분은 아직 없다.**
  - POL-P5(폐업 처리): 이미 구현돼 있다(`PlaceCommandService.closeMissing`, 안전장치 `place.close-max-ratio`). 기준 시각은 JOB 시작 시각 하나, 대상은 이번에 완주한 지역에 `source_region_code`가 있고 `last_synced_at < 시작 시각`인 열린 CANDIDATE·APPROVED. 다른 지역으로 옮긴 가게는 그 지역 응답에서 확인되어 빠진다.
  - POL-P6(값 정리): 장소명 = 상호명, 지점명이 있으면 공백 하나를 두고 붙인다. 앞뒤 공백 제거, 100자에서 자른다. 주소는 도로명 주소, 없으면 지번 주소. 상세주소는 층(`flrNo`)만: 숫자 n → "n층", "Bn" → "지하 n층", "지" → "지하", 비면 ''. 좌표는 WGS84 위경도 그대로, 위도 33~39·경도 124~132를 벗어나거나 비면 그 행은 적재하지 않는다.
  - POL-P7(완주 판정): 완주 = 모든 페이지 응답의 결과 코드가 정상이고, 받은 건수 합계가 응답의 totalCount와 같고, 0보다 크다. 하나라도 어긋나면(호출 실패·시간 초과·결과 코드 오류·건수 불일치·0건) 그 지역은 미완주다. 이미 upsert한 페이지는 그대로 두고 그 지역은 폐업 처리에서 뺀다. 다른 지역은 계속 진행한다. 미완주와 그 이유는 로그로 남긴다.
  - 8.3 외부 연동: `GET https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong?divId=signguCd&key={시군구코드}&numOfRows=1000&pageNo=&type=json&serviceKey=`. 쓰는 필드: `bizesId, bizesNm, brchNm, indsSclsCd, rdnmAdr, lnoAdr, lon, lat`(+ POL-P6의 `flrNo`). 개발 계정 일 10,000건. 인증키는 `DATA_GO_KR_API_KEY` 하나, 시간 초과는 설정값. `infrastructure/client/SemasPlaceSourceAdapter`가 `PublicPlaceSourcePort`를 구현한다.
  - 9장: place 도메인은 오류 코드를 만들지 않는다. 적재 실패는 로그로 남는다.
  - 명세 완료 조건(공원 항목 제외): 지정 업종 소분류와 그 밖 업종은 상호명에 적재 키워드가 든 가게만 적재 / 키워드별 유형 추정 / 이름 = 상호명 + 지점명 / 좌표가 범위 밖이거나 비면 건너뜀 / 1,000건 단위 페이지를 끝까지 / 중간 페이지 실패·결과 코드 오류·건수가 totalCount와 다름·0건이면 그 지역은 폐업 처리하지 않고 다른 지역은 진행 / 업종 필터에서 걸러진 기존 행은 폐업 처리되지 않음 / 다른 구로 옮긴 가게는 폐업 처리되지 않음 / 잠금을 못 잡은 두 번째 실행은 건너뜀 / 같은 응답으로 두 번 돌려도 결과 같음 / 비활성 지역은 받지 않음. 외부 API는 목 서버로 테스트.
- 코드에서 확인한 현재 상태 (베이스 `e1d0482`)
  - place 도메인: `Place`(화면 값·원천 값·`isClosed`·`lastSyncedAt`, `createCandidate`, `confirmSource`, `close`), `PlaceCommandService`(`upsert(List<UpsertPlaceCommand>)` — 확인 시각은 메서드 안의 `LocalDateTime.now()`, `closeMissing(source, completedRegionCodes, jobStartedAt)` — 안전장치와 경고 로그 포함), `PlaceRepository`(`findAllBySourceIds`, `findAllCloseTargets`, `countOpenBySourceRegion`), `PlaceProperties`(record, 지금은 `closeMaxRatio`만), `RegionQueryService.findActive()`(활성 지역, display_order 순), `UpsertPlaceCommand`.
  - **아직 없는 것**: `PublicPlaceSourcePort`, `SemasPlaceSourceAdapter`, `ImportPlacesFacade`, 필터·추정·정리 규칙(명세가 이름 붙인 `PlaceImportPolicy`), JOB-04 스케줄러, 동시 실행 잠금, 걸러진 기존 행의 `last_synced_at` 갱신, `public-data.*`·`place.*`(close-max-ratio 외) 설정.
  - `places`는 `BaseEntity`(감사 주체 컬럼 없음)라 JOB-03과 달리 스케줄러가 SecurityContext에 주체를 넣을 필요가 없다.
  - `V5__insert_reviewed_places.sql`의 원천 값은 POL-P6 형식 그대로다(`source_address_detail`: "3층"·"지하 1층"·"지하"·'', 이름 "더홀릭보드게임카페 신촌점", 좌표 소수 7자리). 첫 적재가 이 행들을 `confirmSource`로 확인하므로, 정리 결과가 이 형식과 다르면 승인 장소마다 원천 변경 경고가 난다.
  - 외부 API adapter 선례: auth `KakaoSocialLoginAdapter`·`KakaoSocialUnlinkAdapter`(생성자에서 `RestClient.Builder`를 받아 build, URL 상수). 저장소 어디에도 HTTP 시간 초과 설정은 아직 없다.
  - 스케줄러 선례: `ScheduleStatusScheduler`, `UserAnonymizationScheduler`(`@Scheduled(cron = "${withdrawal.anonymize-cron}")`, 04:00). 스위치는 `common/config/SchedulingConfig`.
  - `snsg/.env`에 `DATA_GO_KR_API_KEY` 키가 이미 있다(값은 보지 않았다). `snsg/.env.example`(CI/CD로 추가된 운영용 목록)에는 없다. 운영(ECS)은 task definition이 환경변수를 주입하며 저장소 밖이다.
- 결정 (사용자가 고른 안 + 근거를 찾아 정한 추천안)
  1. **실행 시각: `place.import-cron: "0 0 3 1 3,6,9,12 *"`(3·6·9·12월 1일 03:00).** 사용자가 고른 안이다. 명세의 `"0 0 5 1 1,4,7,10 *"`(분기 첫날 05:00)와 다르다. 분기 끝 2개월 뒤에 받아 분기 데이터 공개가 늦어도 받도록 여유를 둔다. 04:00은 JOB-03(탈퇴 비식별)이 쓴다. `@Scheduled` 자리표시자로 읽어 record에 넣지 않는다(package-structure 설정 절 예외).
  2. **동시 실행 방지: PostgreSQL 세션 advisory lock(`pg_try_advisory_lock`), 새 테이블·마이그레이션 없음.** 명세 기본값 그대로다. 근거:
     - `pg_advisory_xact_lock`은 잡은 트랜잭션이 끝나면 풀려, 실행 전체를 트랜잭션 하나로 감싸야 한다. 외부 호출을 트랜잭션 밖에 두는 규칙(facade 절)과 부딪힌다.
     - 잠금 테이블·ShedLock은 마이그레이션(V11)이나 새 의존성이 필요하고, 서버가 죽으면 잠금 해제를 따로 처리해야 한다. 세션 잠금은 연결이 끊기면 PostgreSQL이 푼다.
     - Redis `SET NX`도 가능하지만 TTL을 실행 시간보다 길게 잡아야 하고, 죽으면 TTL까지 막힌다.
     - 세션 잠금이라 **같은 DB 연결**에서 잡고 풀어야 한다. 실행하는 동안 연결 하나를 잡아 두고, 페이지 반영은 다른 연결의 트랜잭션에서 한다. 끝나면(예외가 나도) 풀고 연결을 돌려준다.
     - 운영은 ECS 서비스라 태스크가 둘 이상일 수 있다. 잠금이 그 경우를 막는다.
  3. **트랜잭션 경계(명세 그대로).** facade는 트랜잭션을 걸지 않는다. 외부 호출은 트랜잭션 밖이다. 한 페이지의 반영(upsert와 걸러진 기존 행의 확인 시각 갱신)은 `PlaceCommandService` 메서드 호출 하나 = 트랜잭션 하나다. 폐업 처리는 모든 지역을 받은 뒤 `closeMissing` 한 번이다.
  4. **완주 판정의 "받은 건수"는 필터·정리 전 원천 행 수다.** totalCount와 비교하는 값이기 때문이다. 페이지 수는 첫 페이지의 totalCount로 정하고, 중간 페이지가 0건이면 더 부르지 않고 미완주(건수 불일치)로 끝낸다(무한 반복 방지).
  5. **반영(DB) 실패도 미완주로 친다.** 한 페이지의 반영이 예외로 끝나면 그 지역은 미완주로 기록하고 다음 지역으로 넘어간다. 한 지역의 예외가 실행 전체를 끝내지 않는다.
  6. **재시도: 페이지 하나를 받는 데 실패하면(호출 예외·시간 초과·HTTP 오류·응답 해석 실패·결과 코드 오류) 같은 페이지를 다시 부른다. 최대 시도 횟수는 설정값 `place.import-max-attempts: 3`(첫 시도 포함), 간격 없이 바로.** 명세는 재시도 없이 "다음 주기에 다시 받는다"였다. 주기가 분기라 한 번의 일시 오류로 그 지역의 새 가게·폐업 반영이 석 달 밀린다. 공공데이터포털 API는 일시 오류가 잦다. 최악에도 페이지당 3회라 한 실행 약 65 × 3 = 195회로 일 10,000건 한도 안이다. 건수 불일치·0건은 지역 단위 판정이라 재시도하지 않는다.
  7. **시간 초과: `place.import-timeout: 10s`(연결·읽기).** 명세 설정 블록 그대로다.
  8. **유형 추정에서 두 유형의 키워드가 함께 걸리면 UNCLASSIFIED.** 명세에 없는 경우다. 추정은 검수를 돕는 표시일 뿐이라(POL-P3) 애매하면 검수자가 정하게 둔다. 설정의 키 순서에 결과가 기대지 않는다.
  9. **값 정리 보강(명세에 없는 세부)**
     - 좌표는 소수 7자리로 반올림(HALF_UP)한다. 컬럼이 `NUMERIC(10,7)`이라 저장 값은 7자리인데, 원천이 더 긴 소수를 주면 다음 적재 때 `confirmSource`가 "원천 값이 바뀌었다"로 판정해 승인 장소마다 매번 경고가 난다(멱등 위반).
     - 주소는 200자에서 자른다(컬럼 길이). 넘는 값이 한 번 들어오면 그 페이지 트랜잭션이 실패해 그 지역이 매 분기 미완주가 되기 때문이다.
     - 층 값이 위 세 형식(숫자, `B`+숫자, "지")이 아니면 ''로 둔다. 이 경우를 포함해 모든 문자열 입력은 앞뒤 공백을 제거한 뒤 판정한다.
     - 상가업소번호(`bizesId`)가 비어 있는 행은 적재하지 않는다(원천 식별자가 없어 upsert 키가 없다).
  10. **인증키는 공공데이터포털 "일반 인증키(Decoding)" 원문을 설정값으로 받고, 요청할 때 adapter가 인코딩한다.** 원문에는 `+`·`/`·`=`가 섞일 수 있어 그대로 쿼리에 붙이면 `+`가 공백으로 읽혀 인증이 실패한다. 로컬 `.env`의 값이 어느 형식인지는 보지 않았다(GOAL 15에서 드러난다).
  11. **`.env.example`에 `DATA_GO_KR_API_KEY=`를 더한다.** 운영 환경변수 목록이라 새 필수 환경변수가 빠지면 운영 기동이 `${DATA_GO_KR_API_KEY}` 해석 실패로 멈춘다. 운영 ECS task definition에 값을 넣는 일은 저장소 밖이라 PR 본문에 적는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-181` (브랜치 `feat/SHI-181-semas-place-import`, 베이스 `e1d0482` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리 파일은 고치지 않는다. 기획 폴더 `C:\Users\ks\nss\dev\snsg-be\planning`은 읽기만 한다.
- `snsg/.env`는 이미 복사돼 있고 `DATA_GO_KR_API_KEY`가 들어 있다. **값을 읽거나 출력하지 않는다**(로그·테스트 출력·보고에도 넣지 않는다).
- develop의 마이그레이션은 V1~V10이다. 이 작업은 마이그레이션을 더하지 않는다.
- 셸은 PowerShell(5.1)이 기본이고 Bash도 쓸 수 있다. PowerShell에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").
  - DB가 꺼져 있으면 테스트가 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다.
  - 테스트와 앱 실행은 다른 워크트리와 동시에 하지 않는다.
  - 공유 DB에는 V5의 실제 장소 204행(지역 11410·11440·11200)이 있다. 폐업 처리·안전장치가 지역 단위로 세므로 DB 테스트는 실제 지역 코드를 쓰지 않고 `99xxx` 같은 가짜 지역 코드를 쓴다(`PlaceRepositoryImplTest`·`PlaceCommandServiceTest` 선례).
- 테스트의 Hikari 풀은 3이다(`snsg/build.gradle`, `.claude/rules/testing.md` DB 연결 수 항목). 잠금 테스트는 연결을 동시에 둘 이상 쓴다(테스트 트랜잭션 + 잠금을 쥔 연결 + adapter가 잡는 연결 = 3). 넘으면 풀 크기를 함께 본다. DB 슬라이스 구성을 더하므로 전체 빌드는 `./gradlew cleanTest build`로 돌린다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.
- 테스트는 실제 공공데이터 API를 부르지 않는다. 실제 호출은 GOAL 15(bootRun) 한 곳뿐이다.

## GOAL

공통 규칙:
- "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 아래에서 클래스 이름을 적은 것은 제안이고(`## 참고`), 이름이 달라도 같은 역할의 테스트 클래스면 된다.
- 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.
- `WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-181`이다.

1. **설정이 들어간다.**
   - `application.yaml`의 `place:` 아래: `import-cron: "0 0 3 1 3,6,9,12 *"`(바로 위·옆에 record에 넣지 않는 이유와 실행 시각 근거를 적은 주석), `import-timeout: 10s`, `import-max-attempts: 3`, `semas-category-codes: [R10405, R10499]`, `name-keywords: [보드게임, 보드카페, 방탈출]`, `type-keywords`(BOARD_GAME_CAFE: [보드], ESCAPE_ROOM: [방탈출, 이스케이프, escape]), 기존 `close-max-ratio: 0.3` 유지.
   - `public-data:` 아래 `api-key: ${DATA_GO_KR_API_KEY}`.
   - `snsg/.env.example`에 `DATA_GO_KR_API_KEY=` 한 줄(값 없음).
   - 검증: `grep -n "import-cron\|import-timeout\|import-max-attempts\|semas-category-codes\|name-keywords\|type-keywords\|close-max-ratio\|api-key" WT/snsg/src/main/resources/application.yaml` → 위 키가 모두 나오고 `import-cron`은 `"0 0 3 1 3,6,9,12 *"`, `api-key`는 `${DATA_GO_KR_API_KEY}`
   - 검증: `grep -x "DATA_GO_KR_API_KEY=" WT/snsg/.env.example` → 한 줄
   - 검증: `cd WT && git diff --staged --name-only -- snsg/.env` → 출력 없음

2. **적재 대상 필터(POL-P2 SEMAS).** 소분류 코드가 설정 목록에 들면 상호명과 무관하게 대상이다. 그 밖 업종은 상호명+지점명에 적재 키워드가 들 때만 대상이다(대소문자 무시). 둘 다 아니면 대상이 아니다.
   - 검증: 순수 JUnit 도메인 테스트가 단정한다: 지정 소분류 + 키워드 없는 상호 → 대상 / 다른 소분류 + "보드게임" 든 상호 → 대상 / 다른 소분류 + 지점명에만 키워드 → 대상 / 다른 소분류 + "보드"만 든 상호(예: "보드람치킨") → 대상 아님 / 영문 키워드를 설정에 둔 경우 대소문자만 다른 상호 → 대상.

3. **유형 추정(POL-P3).** 상호명+지점명에 BOARD_GAME_CAFE 키워드만 들면 BOARD_GAME_CAFE, ESCAPE_ROOM 키워드만 들면 ESCAPE_ROOM, 둘 다 없으면 UNCLASSIFIED, 두 유형이 함께 걸리면 UNCLASSIFIED(결정 8). 대소문자 무시.
   - 검증: 순수 JUnit 도메인 테스트가 위 네 경우와 "ESCAPE"(대문자) → ESCAPE_ROOM을 단정한다.

4. **값 정리(POL-P6 + 결정 9).**
   - 이름: 상호명과 지점명을 각각 앞뒤 공백 제거 후, 지점명이 있으면 공백 하나로 잇는다. 100자에서 자른다.
   - 주소: 도로명 주소, 비면 지번 주소. 200자에서 자른다.
   - 상세주소: "3" → "3층", "B1" → "지하 1층", "지" → "지하", 빈 값·null → '', 그 밖 → ''.
   - 좌표: 소수 7자리 HALF_UP 반올림. 위도 33~39·경도 124~132(경계 포함) 밖이거나 비었거나 숫자가 아니면 그 행은 적재하지 않는다. 상가업소번호가 비어도 적재하지 않는다.
   - 검증: 순수 JUnit 도메인 테스트가 위 각 경우를 단정한다. 좌표는 경계값(33·39·124·132 → 적재), 경계 밖(32.9999999 등 → 건너뜀), 빈 값 → 건너뜀, 소수 8자리 이상 → 7자리 반올림 값을 포함한다.

5. **SEMAS adapter가 요청을 만들고 응답을 옮긴다.**
   - 요청: `GET https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong`, 쿼리 `divId=signguCd`, `key=<지역 코드>`, `numOfRows=1000`, `pageNo=<번호>`, `type=json`, `serviceKey=<인코딩된 인증키>`. 인증키에 `+`·`/`·`=`가 있으면 `%2B`·`%2F`·`%3D`로 나간다(결정 10).
   - 응답: 결과 코드 정상(`"00"`)이면 totalCount와 원천 행 목록(필드 `bizesId, bizesNm, brchNm, indsSclsCd, rdnmAdr, lnoAdr, flrNo, lon, lat`)을 준다. adapter는 행을 걸러내거나 가공하지 않는다.
   - 실패: 결과 코드가 정상이 아님, HTTP 4xx·5xx, JSON이 아닌 본문(공공데이터포털 게이트웨이 오류는 `type=json`이어도 XML로 올 수 있다), 시간 초과 → 호출한 쪽이 실패로 구분할 수 있게 알린다(예외 또는 실패 결과. 방식은 구현이 정한다). 실패 원인(결과 코드·메시지 또는 예외 종류)을 알 수 있어야 한다.
   - 검증: `MockRestServiceServer.bindTo(RestClient.Builder)` 테스트가 단정한다: 요청 URL·쿼리 파라미터(인증키 인코딩 포함) / 정상 응답의 totalCount·행 필드 변환 / 결과 코드 오류 / HTTP 500 / XML 본문 → 각각 실패로 구분된다.

6. **지역마다 페이지를 끝까지 받고 완주를 판정한다(POL-P7, 결정 4·5).**
   - 활성 지역마다 1페이지부터 totalCount를 다 받을 때까지 페이지를 넘긴다. 페이지마다 필터·추정·정리를 거친 행을 반영한다(트랜잭션 하나, 결정 3).
   - 완주한 지역만 `closeMissing(SEMAS, 완주 지역, 시작 시각)`에 넘긴다. 미완주인 경우: 페이지 실패(재시도 소진), 결과 코드 오류, 받은 원천 행 합계 ≠ totalCount, 0건, 반영 실패. 미완주여도 그 전까지 반영한 페이지는 되돌리지 않고, 다른 지역은 계속 진행한다.
   - 미완주는 지역 코드와 이유를 담아 경고 로그로 남긴다.
   - 시작 시각은 실행 시작 때 한 번 정해 모든 지역에 같은 값을 쓴다.
   - 검증: facade 흐름 테스트(Mockito 또는 목 서버)가 단정한다:
     - 2,500건(3페이지) 지역 → 3페이지 모두 요청·반영되고 그 지역이 완주로 넘어간다
     - 지역 둘 중 첫 지역의 2페이지가 실패 → 첫 지역의 1페이지 반영은 일어났고, 첫 지역은 `closeMissing`에 없고, 둘째 지역은 받아서 완주로 넘어간다
     - 결과 코드 오류 / 합계 ≠ totalCount / 0건 각각 → 그 지역이 `closeMissing`에 없다
     - 반영(서비스 호출)이 예외 → 그 지역이 `closeMissing`에 없고 다음 지역은 진행된다
     - 미완주 경고 로그에 지역 코드와 이유가 있다(`OutputCaptureExtension`)
     - `closeMissing`에 넘긴 시작 시각이 모든 반영보다 이르거나 같다(같은 값 하나)

7. **페이지 재시도(결정 6).** 한 페이지 받기가 실패하면 `place.import-max-attempts`까지 같은 페이지를 다시 부른다.
   - 검증: facade 흐름 테스트가 단정한다: 한 번 실패 후 성공 → 그 지역 완주 / 최대 횟수만큼 모두 실패 → 그 지역 미완주, 그 페이지 호출 횟수 = 최대 횟수, 다음 지역 진행.

8. **비활성 지역은 받지 않는다.**
   - 검증: facade 흐름 테스트가 활성 지역 목록에 없는 지역 코드로 원천을 부르지 않음을 단정한다(활성 지역 조회는 `RegionQueryService.findActive()` 같은 활성 전용 경로만 쓴다).

9. **업종 필터·값 정리에서 걸러진 행이 이미 places에 있으면 `last_synced_at`만 갱신해, 폐업 처리되지 않는다(POL-P4).** 걸러진 행의 다른 값(원천 값·화면 값·`is_closed`·검수 상태)은 바꾸지 않는다.
   - 검증: 실제 PostgreSQL 테스트(가짜 지역 코드)가 단정한다: 기존 SEMAS 행(CANDIDATE 또는 APPROVED, 열림, 과거 `last_synced_at`)의 원천 행이 다른 업종 + 키워드 없음으로 바뀌어 들어온 실행 뒤, 그 행의 `is_closed = false`이고 `last_synced_at` ≥ 시작 시각이며 원천 값·화면 값이 그대로다. 좌표가 빈 원천 행으로 들어온 경우도 같은 단정을 한다.

10. **다른 구로 옮긴 가게는 폐업 처리되지 않는다.**
    - 검증: 실제 PostgreSQL 테스트가 단정한다: 가짜 지역 A에 `source_region_code = A`인 열린 행이 있고, 이번 실행에서 그 상가업소번호가 지역 B 응답에만 나온다(A·B 모두 완주). 실행 뒤 그 행은 `is_closed = false`, `source_region_code = B`다. 지역 순서를 A → B, B → A 둘 다 본다(또는 순서와 무관함을 보이는 한 경우와 이유를 테스트 주석에 적는다).

11. **같은 응답으로 두 번 돌려도 결과가 같다(멱등).**
    - 검증: 실제 PostgreSQL 테스트가 같은 원천 응답으로 실행을 두 번 하고 단정한다: 두 번째 실행 뒤 그 지역 SEMAS 행의 수, 각 행의 `place_type`·`status`·`is_closed`·화면 값·원천 값이 첫 실행 뒤와 같다(`last_synced_at`·`updated_at`은 비교하지 않는다). 승인 행 하나를 포함하고, 원천 좌표를 소수 8자리 이상으로 주어 두 번째 실행에서 원천 변경 경고 로그가 나오지 않음을 단정한다(`OutputCaptureExtension`).

12. **잠금을 못 잡은 실행은 아무것도 하지 않고 건너뛴다. 잠금은 실행이 끝나면(예외가 나도) 풀린다.**
    - 다른 연결이 같은 잠금을 쥐고 있으면 원천 호출·DB 반영·폐업 처리를 하나도 하지 않고, 건너뛴다는 로그를 남긴다.
    - 검증: 실제 PostgreSQL 테스트가 단정한다: 테스트가 별도 연결로 `pg_try_advisory_lock(<같은 키>)`을 잡은 상태에서 잠금 adapter(또는 실행)가 잠금을 얻지 못한다 → 잡은 연결이 풀면 얻는다 / 잠금 안에서 실행한 작업이 예외를 던져도 끝난 뒤 다른 연결이 그 잠금을 잡을 수 있다.
    - 검증: facade 흐름 테스트가 잠금을 못 얻으면 원천 포트·`PlaceCommandService`·지역 조회를 부르지 않음을 단정한다(`verifyNoInteractions` 등).

13. **JOB-04 스케줄러.** place `infrastructure/scheduler`의 `@Component`가 `@Scheduled(cron = "${place.import-cron}")`으로 돌고 facade만 부른다. 클래스 단순명이 다른 도메인의 빈과 겹치지 않는다.
    - 검증: `grep -rn "@Scheduled" WT/snsg/src/main/java/com/shineunsigan/snsg/place/infrastructure/scheduler/` → `place.import-cron`을 읽는 한 줄
    - 검증: `grep -rln "Repository\|CommandService\|Port" WT/snsg/src/main/java/com/shineunsigan/snsg/place/infrastructure/scheduler/` → 출력 없음(facade만 부른다)

14. **전체 빌드가 실패 0으로 끝난다.**
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-181/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0(`ArchitectureTest`, `OpenApiSpecTest`, `SnsgApplicationTests`, 기존 `PlaceCommandServiceTest`·`PlaceRepositoryImplTest` 포함)
    - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다.
    - 끝내지 않는 경우:
      - 실패가 줄었지만 0이 아닐 때
      - 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때
      - 컴파일이 깨져 테스트가 돌지 않았을 때
      - 기존 테스트의 단정을 바꿔서 통과시켰을 때. 메서드 시그니처가 바뀌어 호출부만 고친 것은 허용하되, 단정 내용이 바뀌면 이유를 보고한다

15. **앱을 띄우면 JOB-04가 실제 공공데이터 API로 돈다**(Boot 4 모듈 분리로 "에러 없이 조용히 동작하지 않는" 경우와 실제 응답 형식을 잡는다).
    - 사전 조건: 8080 포트를 다른 워크트리가 쓰고 있지 않다. 쓰고 있으면 건너뛰지 말고 멈춰서 보고한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-181/snsg && ./gradlew bootRun --args='--place.import-cron=0 * * * * *' > <세션 스크래치패드>/bootrun.log 2>&1`을 백그라운드로 띄운다(인용이 깨지면 환경변수 `PLACE_IMPORTCRON`으로 넘겨도 된다). 첫 실행의 종료 로그가 찍힐 때까지 기다린 뒤:
      - 로그에 활성 지역 세 곳(11200·11410·11440) 각각의 완주 로그가 있다(미완주면 이유를 보고한다)
      - `grep -ci "serviceKey=" <로그>` → `0`(요청 URL·인증키를 로그에 남기지 않는다). 확인은 개수만 보고 줄 내용은 출력하지 않는다
      - 인증 오류(결과 코드·HTTP 401 등)로 실패하면 `.env` 값의 형식(Decoding/Encoding)을 의심하고, 값을 출력하지 말고 멈춰서 보고한다
    - 확인 뒤 앱을 멈추고 로그 파일을 지운다(`.claude/rules/temp-files.md`). 공유 로컬 DB에 실제 장소 행이 추가·갱신되고 V5 행 일부가 폐업 처리될 수 있다. 로컬 개발 데이터라 허용한다. 한 실행 약 65회 호출이며 1회만 돌리고 멈춘다(일 10,000건 한도).

16. **범위 밖 파일을 건드리지 않는다.**
    - 검증: `cd WT && git diff --staged --name-only` → 모든 경로가 `snsg/src/main/java/com/shineunsigan/snsg/place/`, `snsg/src/test/java/com/shineunsigan/snsg/place/`, `snsg/src/main/resources/application.yaml`, `snsg/.env.example`, `.claude/tasks/SHI-181/task.md` 중 하나 아래에 있다. `snsg/build.gradle`은 테스트 의존성이 꼭 필요할 때만 허용하고 이유를 보고한다
    - 검증: `ls WT/snsg/src/main/resources/db/migration/` → V1~V10만(V11 없음)
    - 검증: `grep -rln "PARK_STANDARD\|ParkPlaceSource" WT/snsg/src/main/java/com/shineunsigan/snsg/place/infrastructure/` → 출력 없음

## 비목표

- 도시공원(`ParkPlaceSourceAdapter`, `place.sido-aliases`, PARK_STANDARD 적재)은 만들지 않는다(C9). `PlaceSource.PARK_STANDARD` enum 값은 그대로 둔다.
- 수동 실행 API·관리 화면을 만들지 않는다(명세 JOB-04). 첫 적재는 다음 cron(2026-12-01 03:00) 또는 배포 때 cron 설정을 덮어써서 한다.
- 새 상가업소번호로 나온 같은 가게를 잇는 자동 매칭은 하지 않는다(D23 ④, 검수자가 한다).
- 마이그레이션을 더하지 않는다. `places`·`regions` 스키마는 그대로다.
- `Place.confirmSource`·`closeMissing`의 기존 규칙(화면 값 보존, 경고 조건, 안전장치)은 바꾸지 않는다. 걸러진 행의 확인 시각 갱신을 위한 메서드·쿼리 추가는 허용한다.
- 다른 도메인(auth·user·schedule·common) 파일, `docs/`(API 변경 없음), `.github/`, `Dockerfile`, `application-*.yaml`은 건드리지 않는다. 문서(rules 등) 갱신은 refine-docs 단계가 한다.
- 스케줄러 스레드 풀 크기(`spring.task.scheduling.pool.size`)는 바꾸지 않는다(`## 참고`).
- Spring Retry 같은 새 라이브러리를 더하지 않는다. 재시도는 반복문으로 충분하다.
- 분기마다 바뀌는 판단(예: 실패 시 다음 실행을 앞당기기)은 하지 않는다.

## 제약

- 패키지 구조·facade·port·이벤트·스케줄러·설정·일괄 UPDATE 규칙은 `.claude/rules/package-structure.md`를 따른다. 특히 facade에 `@Transactional` 금지, Command 서비스는 repository(와 이벤트 포트)만 의존, application·domain은 infrastructure를 import하지 않는다(`ArchitectureTest`가 검사).
- 테스트는 `.claude/rules/testing.md`를 따른다(adapter는 `MockRestServiceServer`, DB는 실제 PostgreSQL, 공유 DB라 가짜 지역 코드, 로그 단정은 `OutputCaptureExtension`, 테스트는 별도 커밋 후보로 분리 가능하게).
- 임시 파일은 `.claude/rules/temp-files.md`대로 세션 스크래치패드에 두고 끝나면 지운다.
- 인증키 값은 코드·테스트·로그·보고 어디에도 쓰지 않는다. 테스트는 가짜 키를 쓴다.
- 스테이징까지만 하고 커밋하지 않는다(`.claude/skills/implement/SKILL.md`).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 구성 제안(이름·배치는 구현이 바꿔도 된다)
  - `place/application/port/out/PublicPlaceSourcePort`(명세 이름) — 지역 코드·페이지 번호로 원천 페이지 하나(totalCount, 원천 행)를 받는다. 원천 행 DTO는 application `dto`에 둔다(adapter는 가공하지 않는다).
  - `place/application/port/out/PlaceImportLockPort` — 잠금을 잡고 작업을 돌리는 입구(예: 잡으면 실행 후 true, 못 잡으면 false). 구현은 `place/infrastructure/persistence` 또는 `infrastructure/lock`에서 `DataSource`의 연결 하나로 `pg_try_advisory_lock(key)` → 작업 → `finally`에서 `pg_advisory_unlock(key)` 후 연결 반환. `JdbcTemplate`은 호출마다 다른 연결을 쓸 수 있어 세션 잠금에 맞지 않는다. 키는 고정 상수(예: `hashtext('place-import')` 대신 상수 bigint) 하나.
  - 잠금을 facade(`port/out`)에서 잡는 이유: 스케줄러는 "facade를 부르기만" 한다(package-structure 스케줄러 절).
  - `place/application/ImportPlacesFacade` — `PlaceImportLockPort`, `PublicPlaceSourcePort`, `RegionQueryService`, `PlaceCommandService`, `PlaceProperties`에 의존. 트랜잭션 없음.
  - `place/domain/...PlaceImportPolicy`(명세 이름) — 필터·유형 추정·값 정리. domain은 application의 `PlaceProperties`를 import할 수 없으므로 facade가 설정값(코드 목록·키워드)을 넘겨 만든다. 순수 JUnit으로 테스트한다.
  - `PlaceProperties` 확장: `semasCategoryCodes`, `nameKeywords`, `typeKeywords`(Map<PlaceType, List<String>>), `importTimeout`(Duration), `importMaxAttempts`, 기존 `closeMaxRatio`. `PublicDataProperties(apiKey)`는 adapter만 쓰므로 `place/infrastructure/client`(또는 `config`)에 두고 등록은 `SnsgApplication`의 `@ConfigurationPropertiesScan`이 한다(슬라이스 테스트에는 적용되지 않으니 테스트에서는 직접 만든다). prefix `public-data`는 흔한 OS 환경변수와 겹치지 않는다.
  - `PlaceCommandService`에 페이지 반영 메서드: upsert할 행과 "걸러졌지만 확인된 상가업소번호"를 함께 받아 한 트랜잭션에서 처리(예: `upsert(rows, confirmedOnlySourceIds)` 또는 별도 메서드를 facade가 같은 페이지에서 차례로 부르기 — 이 경우 트랜잭션이 둘이 되지만 결과는 같다). 걸러진 행 갱신을 `@Modifying` 일괄 UPDATE로 하면 package-structure "일괄 UPDATE" 절대로 `updatedAt`을 쿼리에 직접 넣고 `isDeleted = false`를 건다. `source = SEMAS` 조건도 건다(`findAllBySourceIds`는 원천을 가리지 않는다).
  - 스케줄러: `place/infrastructure/scheduler/PlaceImportScheduler`.
- Spring Boot 4 / RestClient 주의
  - 시간 초과는 request factory에 건다(Boot 4의 `ClientHttpRequestFactoryBuilder`/`HttpClientSettings` 계열 — 구현 시 실제 API 확인). **`MockRestServiceServer.bindTo(builder)`는 builder의 request factory를 목으로 바꾼다. adapter가 생성자에서 builder에 request factory를 다시 설정하면 목이 덮여 테스트가 실제 네트워크로 나간다.** 시간 초과를 입힌 builder를 config 빈으로 만들어 주입하거나, request factory 설정을 bind 이전 단계에 두는 등 순서를 구현이 정한다. 시간 초과 동작 자체는 라이브러리 동작이라 테스트하지 않는다.
  - 인증키 인코딩: `RestClient`의 기본 `DefaultUriBuilderFactory`는 URI 변수 값(`{serviceKey}`)을 엄격히 인코딩해 `+`→`%2B`가 된다. `queryParam("serviceKey", key)`로 리터럴을 붙이면 `+`가 인코딩되지 않을 수 있다. GOAL 5 테스트로 확인한다.
  - Jackson 3(`tools.jackson.databind`). 응답 DTO는 infrastructure 자체 record. 숫자 필드(`lon`, `lat`, `totalCount`)가 문자열로 오는지 숫자로 오는지 명세에 없다. 둘 다 받을 수 있게 하거나 GOAL 15 실호출로 확인한다(구현 시 확인). 좌표는 문자열 → `BigDecimal`로 옮기는 편이 안전하다.
- SEMAS API 세부(웹 확인 결과)
  - 공공데이터포털 15012005 상세(https://www.data.go.kr/data/15012005/openapi.do)의 "지역 단위 상가업소 조회": 필수 `ServiceKey, pageNo, numOfRows, divId(ctprvnCd/signguCd/adongCd), key`, 선택 `indsLclsCd/indsMclsCd/indsSclsCd, type(xml/json)`. 응답 공통 `resultCode, resultMsg, numOfRows, pageNo, totalCount, description, columns, stdrYm`, 업소 필드에 `bizesId, bizesNm, brchNm, indsSclsCd, rdnmAdr, lnoAdr, flrNo, lon, lat`. 정상 결과 코드 예시는 `"00"`/`"NORMAL SERVICE"`.
  - 확인하지 못한 것(구현 시 확인): JSON의 래퍼 구조(`header`/`body`/`items` 위치), 정상 외 결과 코드 목록, 데이터 없음 응답 형태, 한 페이지 최대 행 수(명세는 1,000). 정상 = `"00"`, 그 외는 모두 실패로 본다. 서버 측 업종 필터(`indsSclsCd`)는 키워드 경로 때문에 쓸 수 없어 전체를 받는다.
- 로그 제안(명세 "실행 결과는 로그로 남긴다" 보강): 시작(시작 시각, 활성 지역 수) / 지역마다 결과(지역 코드, totalCount, 받은 원천 행 수, 반영 행 수, 걸러진 행 수, 완주 여부·미완주 이유) / 폐업 처리(지역별 닫은 수 — `closeMissing`에 info 로그를 더해도 된다. 안전장치 경고는 이미 있다) / 종료(완주 지역 목록, 걸린 시간). 요청 URL을 로그에 남기면 인증키가 찍히므로 남기지 않는다.
- 스케줄러 스레드: Spring Boot 기본 스케줄러 스레드는 1개다(구현 시 확인). JOB-04가 도는 몇 분(최악 재시도·시간 초과로 수십 분) 동안 JOB-01·02(1분 주기)가 밀린다. 일정 도메인은 참여·마감·완료 판정을 `startAt`과 현재 시각으로 직접 하므로(`Schedule` 163·166행 부근) DB 상태 전환이 몇 분 늦어도 판정은 틀리지 않는다. 그래서 풀 크기를 바꾸지 않았다. 03:00 실행이 04:00(JOB-03)까지 이어질 가능성은 낮다.
- 호출 한도: 한 실행 약 65회(3개 구 약 64,000건), 재시도 최악 약 195회. 개발 계정 일 10,000건. 지역을 서울 전체로 늘리면 운영 계정 증량을 검토한다.
- 첫 적재·운영 반영(PR 본문에 적을 것): 운영 ECS task definition에 `DATA_GO_KR_API_KEY`(Decoding 키)를 넣어야 기동한다. 첫 적재를 앞당기려면 환경변수 `PLACE_IMPORTCRON`(또는 `PLACE_IMPORT_CRON`)으로 가까운 시각을 주고 실행 뒤 되돌린다. 다음 정기 실행은 2026-12-01 03:00.
- 명세와 달라진 점(명세는 저장소 밖이라 이 작업에서 고치지 않는다. 보고에 적는다): cron 시각(결정 1), 재시도(결정 6), 유형 동시 일치 → UNCLASSIFIED(결정 8), 좌표 반올림·주소 200자·이상한 층 값·빈 상가업소번호(결정 9).
- 이전 시각 비교: `upsert`는 확인 시각을 메서드 안 `LocalDateTime.now()`로 정한다. facade의 시작 시각은 첫 호출 전에 정하므로 항상 이르다. 테스트에서 시작 시각을 고정 과거 값으로 주입하려면 facade가 `Clock` 또는 시작 시각 인자를 받게 할 수 있다(구현 선택).
- DB 통합 테스트 구성 제안: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`에 facade·`PlaceCommandService`·`PlaceRepositoryImpl`·adapter(목 서버에 묶은 builder로 직접 생성)를 `@Import`/`@TestConfiguration`으로 올리고, 활성 지역은 `RegionQueryService`를 `@MockitoBean`으로 가짜 지역(`99xxx`)만 돌려주게 한다. 실제 활성 지역을 쓰면 V5 실데이터와 섞여 안전장치 비율이 틀어진다. `@ConfigurationProperties` record는 `@TestConfiguration`의 `@Bean`으로 직접 만든다(testing.md).

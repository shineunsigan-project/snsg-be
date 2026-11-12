## 배경

- Linear SHI-184 "어드민 장소 검수·수정 API". 지금은 장소 검수를 DB에서 직접 한다(기획 명세 C5). 어드민 앱에서 장소를 검수·수정·등록하게 한다.
- 사용자 원문: "ADMIN에서 직접 수정할 수 있도록 api를 만들어주고 …"(대상: 상가정보 적재 결과인 장소). "면밀히 검토후 진행한다." spec 질문 Q1·Q2와 결정 1~8에는 "추천안대로"라고 답했다(오케스트레이터 전달).
- Linear 범위: 목록(지역·검수 상태·유형·원천·키워드 필터)·상세 조회 / 유형·검수 상태·이름·도로명 주소·상세주소 수정 / MANUAL 등록 / places를 감사 엔티티로 바꾸는 마이그레이션 / 어드민 FE 가이드 갱신.
- 선행: SHI-181(상가정보 적재, #66), SHI-182(어드민 계정·인증, #67)은 머지됐다. SHI-183(MASTER의 승인·거절·이용 중지, 워크트리 `wt-SHI-183`)은 곧 머지된다. 마이그레이션은 없다.
- 기획 명세(저장소 밖, 읽기 전용) `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`의 관련 부분: C5, 11.2("place에 검수 API를 더하고 places를 BaseAuditEntity로"), POL-P1~P7, D20·D23, places 테이블.

### 코드에서 확인한 사실 (설계의 근거)

- `Place`(BaseEntity)는 화면 값(name·roadAddress·addressDetail·latitude·longitude·regionCode)과 원천 값(source_*)을 따로 둔다. `confirmSource`는 원천 값을 늘 갱신하고, 화면 값은 `status == CANDIDATE`일 때만 원천 값으로 덮는다. 원천 값이 바뀌지 않아도 `lastSyncedAt`을 바꾸므로 **적재 때마다 모든 확인된 행이 dirty가 된다**. `markSynced`(@Modifying)도 `updatedAt`을 직접 갱신한다.
  - 그래서 BaseAuditEntity만 붙이면 어드민이 남긴 `updated_by`가 다음 분기 적재에서 덮인다. 결정 Q1(아래 GOAL 5)이 `reviewed_by`·`reviewed_at`을 따로 두는 이유다.
- **적재와 어드민 수정이 겹치면 lost update가 난다.** 적재 페이지(`PlaceCommandService.upsert`)와 폐업 처리(`closeMissing`)는 행을 잠금 없이 읽고, dirty checking으로 전체 컬럼을 UPDATE한다. 그 사이에 어드민이 커밋한 승인·수정은 적재 커밋에 덮여 CANDIDATE·원천 값으로 돌아간다. 반대 순서도 마찬가지로, 어드민 저장이 새 원천 값을 옛 값으로 덮는다. 결정: 행 잠금(GOAL 7).
- 적재는 MANUAL을 건드리지 않는다. `PlaceJpaRepository.OPEN_IN_SOURCE_REGIONS`에 `source <> MANUAL`이 있고, upsert는 `(source, sourceId)`로 맞춘다. `ImportPlacesFacade`는 상가업소번호가 빈 행을 건너뛴다.
- `V4__create_place.sql`: MANUAL의 source_id·source_*는 `''`와 0이다. `uix_places_source`는 MANUAL을 뺀다. `ck_places_approved_type`이 APPROVED + UNCLASSIFIED를 막는다. `source_region_code`는 `CHAR(5)`라 `''`가 공백 다섯 칸으로 읽힐 수 있다.
- 어드민 인증
  - `AdminSecurityConfig`는 `/api/admin/**` 중 공개 3개를 뺀 전부를 `authenticated()`로 둔다. SHI-183 머지 뒤에도 `/admins`·`/admins/**`만 MASTER 전용이 되고 나머지는 인증만 필요하다. 그래서 **장소 API는 ADMIN·MASTER 모두가 쓰고 보안 설정을 고칠 필요가 없다.**
  - principal은 adminId(UUID)다. `SecurityAuditorAware`가 `principal.toString()`을 감사 값으로 쓴다.
- admin은 다른 도메인과 어느 방향으로도 import하지 않는다(`.claude/rules/package-structure.md` 의존 방향 절). 다른 도메인의 어드민 API는 경로만 `/api/admin/v1/**`에 둔다(`.claude/rules/api.md` URL 절).
- 어드민 앱 출처는 CORS가 `/api/admin/**`만 허용한다(`CorsConfig`). 그래서 **어드민 FE는 사용자 API `GET /api/v1/regions`를 부를 수 없다.** MANUAL 등록 폼과 지역 필터에 쓸 지역 목록 API가 어드민 쪽에 따로 필요하다.
- place에는 지금 ErrorCode가 없다. 이 작업에서 처음 생긴다.
- places에 native INSERT를 하는 테스트는 4개다: `place/infrastructure/persistence/PlaceRepositoryImplTest`, `place/application/ImportPlacesFacadeTest`, `schedule/application/ScheduleCommandServiceTest`, `schedule/infrastructure/client/SchedulePlaceAdapterTest`.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-184` (브랜치 `feat/SHI-184-admin-place-review`, 베이스 `7bfb80b`)
- **구현 시작 전에 SHI-183 머지를 확인하고 최신 develop으로 rebase한다.** `git fetch origin && git rebase origin/develop`. rebase 뒤 `git log --oneline -3`에 SHI-183 커밋이 보여야 한다. SHI-183이 아직 머지되지 않았으면 시작하지 말고 멈춰서 보고한다.
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 `wt-SHI-183`은 읽기만 한다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").
  - 테스트는 다른 워크트리가 테스트 중이 아닐 때만 돌린다.
  - V12를 공유 DB에 적용하므로, 다른 워크트리가 마이그레이션 작업 중이면 멈추고 보고한다.
  - DB 테스트는 로컬 Docker PostgreSQL·Redis에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다.
- `snsg/.env`는 워크트리에 이미 복사돼 있다. 읽거나 고치지 않는다.
- 이미 깨져 있는 것: 알려진 실패 없음. rebase 직후 `./gradlew build`가 실패하면, 이 작업과 무관한지 먼저 확인하고 보고한다.

## GOAL

1. 어드민 장소·지역 API가 아래 경로로 열린다. ADMIN·MASTER 토큰 모두 통과한다. 토큰이 없거나 사용자 앱 토큰이면 401 `UNAUTHENTICATED`다.
   - `GET /api/admin/v1/places`(목록), `GET /api/admin/v1/places/{placeId}`(상세), `PUT /api/admin/v1/places/{placeId}`(수정), `POST /api/admin/v1/places`(MANUAL 등록, 201), `GET /api/admin/v1/regions`(지역 전체. 비활성 포함, display_order 순, 활성 여부 포함)
   - `AdminSecurityConfigTest`에 다음을 단정하는 테스트가 있다: 이 경로들의 토큰 없음 401, 사용자 토큰 401, 승인된 ADMIN 토큰이면 서비스가 불림.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests '*AdminSecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && git diff --staged --name-only | grep -E '/AdminSecurityConfig\.java$'` → 출력 없음(보안 설정은 고치지 않는다)

2. place는 admin을 import하지 않고, ArchUnit 규칙을 모두 지킨다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && grep -rn "snsg\.admin" snsg/src/main/java/com/shineunsigan/snsg/place` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests '*ArchitectureTest'` → BUILD SUCCESSFUL, 실패 0

3. 목록 조회
   - 필터는 모두 선택이고 값 하나씩 받는다: `regionCode`(화면 값 기준), `status`, `placeType`, `source`, `closed`(boolean), `keyword`.
   - `keyword`: 이름·도로명 주소·원천 이름·원천 도로명 주소 중 하나에 대소문자 무시 부분 일치. `%`·`_`는 글자 그대로 찾는다. 빈 값이나 공백뿐인 값은 필터를 걸지 않는다.
   - 목록 밖 enum 값은 400 `INVALID_REQUEST`, 없는 지역은 200에 빈 결과다.
   - 페이지는 `.claude/rules/api.md` 목록 절을 따른다: `{items, nextCursor}`, 정렬 `(createdAt, id)` 오름차순 keyset, size 기본 20·상한 50·하한 1 잘라내기(설정값), 해석할 수 없는 커서는 400 `INVALID_CURSOR`.
   - 항목과 상세 응답은 같은 필드다:
     - 화면 값 전부: `placeType`, `status`, `name`, `roadAddress`, `addressDetail`, `latitude`, `longitude`, `regionCode`
     - `isClosed`, `source`, `sourceId`
     - 원천 값 전부: `sourceName`, `sourceRoadAddress`, `sourceAddressDetail`, `sourceLatitude`, `sourceLongitude`, `sourceRegionCode`
     - `lastSyncedAt`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `reviewedBy`, `reviewedAt`(null 가능)
   - 다음을 단정하는 repository 테스트가 실제 PostgreSQL로 통과한다: 필터별로 넣은 행만 걸림, keyword가 원천 도로명 주소로도 걸림, `%` 글자 그대로 일치, 커서 다음 페이지에 중복·누락 없음.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.place.*'` → BUILD SUCCESSFUL, 실패 0

4. 수정(`PUT`, 전체 필드)과 MANUAL 등록의 규칙
   - 수정 필드: `placeType`, `status`, `name`, `roadAddress`, `addressDetail`, `latitude`, `longitude`, `regionCode`. 모두 필수이고 `addressDetail`만 `''`를 허용한다.
   - 등록 필드: 같은 필드에서 `status`를 뺀 것.
   - 형식: 앞뒤 공백을 뺀 뒤 name 1~100자, roadAddress 1~200자, addressDetail 0~100자, 위도 33~39, 경도 124~132(경계 포함, 소수 7자리 HALF_UP 반올림), regionCode는 regions에 있는 코드(비활성 허용).
     - 요청 DTO의 형식 위반은 400 `INVALID_REQUEST` + `errors`다. domain도 같은 규칙을 불변식으로 지킨다.
     - 없는 지역은 400이다(PlaceErrorCode).
   - 수정의 `status`는 `APPROVED`·`REJECTED`만 받는다. `CANDIDATE`면 400(PlaceErrorCode)이다.
   - `APPROVED` + `UNCLASSIFIED`는 수정·등록 모두 400(PlaceErrorCode)이다.
   - 없는 `placeId`는 404 `PLACE_NOT_FOUND`다.
   - 수정은 원천 값(source_*)·`isClosed`·`lastSyncedAt`을 바꾸지 않는다. 폐업 행도 수정할 수 있다.
   - 등록은 `source = MANUAL`, `status = APPROVED`, `isClosed = false`, `sourceId = ''`, 원천 값 `''`·0, `lastSyncedAt` = 생성 시각으로 만든다.
   - 수정·등록 모두 결과 장소를 돌려준다(수정 200, 등록 201). 응답의 MANUAL `sourceRegionCode`는 `""`다.
   - 위 규칙(상태 거부, UNCLASSIFIED 승인 거부, 원천 값 보존, MANUAL 초기값, 좌표 반올림·범위)을 단정하는 domain·application 테스트가 있다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.place.*'` → BUILD SUCCESSFUL, 실패 0

5. V12 마이그레이션과 감사 값
   - `snsg/src/main/resources/db/migration/V12__*.sql`이 places에 다음을 더한다:
     - `created_by`·`updated_by VARCHAR(50) NOT NULL`. 기존 행은 `'SYSTEM'`으로 채우고 DEFAULT는 남기지 않는다.
     - `reviewed_by VARCHAR(50) NOT NULL`. 기존 행은 `''`.
     - `reviewed_at TIMESTAMP`. NULL 허용이고 NULL은 "API로 검수한 적 없음"이다.
   - `Place`는 `BaseAuditEntity`를 상속한다.
   - 감사 값이 들어가는 경로:
     - 어드민 수정·등록: `updated_by`(등록이면 `created_by`도)와 `reviewed_by`가 그 adminId 문자열이 되고, `reviewed_at`이 채워진다.
     - 적재(스케줄러 경로): `created_by`·`updated_by`가 `SYSTEM_SCHEDULER`다. `PlaceImportScheduler`가 실행 동안 SecurityContext에 `Auditor.SYSTEM_SCHEDULER`를 넣고 `finally`에서 비운다.
     - `markSynced`: `updated_by = SYSTEM_SCHEDULER`를 쿼리에 직접 넣는다.
     - **적재·`markSynced`·폐업 처리는 `reviewed_by`·`reviewed_at`을 바꾸지 않는다.**
   - places native INSERT 테스트 4개(배경 참고)가 감사 컬럼을 넣도록 고쳐져 통과한다.
   - 감사 값 단정 테스트가 있다: native SELECT로 `created_by`·`updated_by`·`reviewed_by`·`reviewed_at`을 본다. 어드민 수정 뒤 적재 확인(`confirmSource` 경로)과 `markSynced`를 거쳐도 `reviewed_by`·`reviewed_at`이 남는지 본다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && ls snsg/src/main/resources/db/migration | grep -E '^V12__'` → 한 줄
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && git diff --staged --name-only -- snsg/src/main/resources/db/migration` → `V12__…sql` 한 줄뿐(기존 V1~V11 수정 없음)
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.place.*' --tests '*ScheduleCommandServiceTest' --tests '*SchedulePlaceAdapterTest' --tests '*UserAnonymizationScheduler*'` → BUILD SUCCESSFUL, 실패 0

6. 적재와 수정이 같은 행에서 겹쳐도 서로 덮지 않는다.
   - 어드민 수정은 그 행을 `PESSIMISTIC_WRITE`(`SELECT … FOR UPDATE`)로 처음 읽는다. 적재의 기존 행 조회(`findAllBySourceIds`)와 폐업 대상 조회(`findAllCloseTargets`)도 `PESSIMISTIC_WRITE`로 읽는다.
   - 커밋까지 거치는 동시성 테스트가 있다(`.claude/rules/testing.md` 잠금 절). 한 스레드가 적재 반영(`upsert` 또는 `closeMissing`)을, 다른 스레드가 같은 행의 어드민 수정을 동시에 실행한 뒤, 커밋된 행이 두 변경을 모두 담는지 단정한다: 어드민이 정한 status·화면 값 + 적재가 정한 원천 값·`isClosed`·`lastSyncedAt`.
   - 동시성 테스트는 3회 연속 통과해야 한다.
   - 검증: 아래 명령을 3번 연속 실행 `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.place.*'` → 3번 모두 BUILD SUCCESSFUL, 실패 0
   - 정지 조건: 3회 모두 통과하면 끝낸다. 끝내지 않는 경우: 한 번이라도 실패했을 때, 동시성 테스트를 지우거나 `@Disabled`로 꺼서 통과했을 때, sleep·재시도로 덮었을 때.

7. 문서
   - `docs/admin-api-guide.md`에 장소 검수 절을 더한다. 다음을 담는다:
     - 다섯 엔드포인트, 필터·keyword·페이지 규칙, 응답 필드별 뜻
     - 감사 값: adminId·`SYSTEM`·`SYSTEM_SCHEDULER`. `reviewedBy`는 적재가 바꾸지 않고, `updatedBy`는 적재가 덮는다
     - 수정·등록 규칙(상태 APPROVED·REJECTED만, UNCLASSIFIED 승인 불가, 원천 값 불변, MANUAL은 바로 APPROVED)
     - 폐업 장소를 승인해도 다시 확인되기 전까지 노출되지 않음
     - 이미 열린 일정은 영향이 없음(장소 사본)
     - 노출 중단하면 새 개설에서 쓸 수 없음
     - 동시에 고치면 마지막 요청이 남음
     - C5 절차: 폐업된 승인 장소와 같은 주소의 새 CANDIDATE를 `closed`·`status`·`keyword`로 찾아, 새 행에 `PUT`으로 검수 결과를 옮기고 기존 행은 `REJECTED`로 바꾼다. MANUAL이 원천에 잡히면 둘 중 하나를 `REJECTED`로 한다
     - 에러 표(새 코드 전부, 예시 JSON), 화면별로 쓰는 API 행
   - "아직 없는 것"의 장소 문구를 지운다.
   - `docs/openapi-admin.yaml`은 `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 다시 만든다.
   - 사용자 문서 `docs/openapi.yaml`·`docs/api-guide.md`는 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && git diff --staged --name-only -- docs/openapi.yaml docs/api-guide.md` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && grep -c "/api/admin/v1/places" docs/openapi-admin.yaml docs/admin-api-guide.md` → 두 파일 모두 1 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && grep -c "/api/admin/v1/regions" docs/openapi-admin.yaml` → 1 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest' --tests '*OpenApiConfigTest'` → BUILD SUCCESSFUL, 실패 0

8. 전체 빌드가 통과한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0

9. 변경은 워크트리 안의 허용 경로에만 있다.
   - 허용 경로: `snsg/src/main/java/com/shineunsigan/snsg/place/`, `snsg/src/test/java/com/shineunsigan/snsg/place/`, `snsg/src/main/resources/db/migration/V12__*`, `snsg/src/main/resources/application.yaml`(place 설정만), `snsg/src/test/java/com/shineunsigan/snsg/schedule/application/ScheduleCommandServiceTest.java`, `snsg/src/test/java/com/shineunsigan/snsg/schedule/infrastructure/client/SchedulePlaceAdapterTest.java`, `snsg/src/test/java/com/shineunsigan/snsg/admin/infrastructure/security/AdminSecurityConfigTest.java`, `docs/admin-api-guide.md`, `docs/openapi-admin.yaml`, `.claude/tasks/SHI-184/task.md`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && git diff --staged --name-only | grep -vE '^(snsg/src/(main|test)/java/com/shineunsigan/snsg/place/|snsg/src/main/resources/db/migration/V12__|snsg/src/main/resources/application\.yaml$|snsg/src/test/java/com/shineunsigan/snsg/schedule/application/ScheduleCommandServiceTest\.java$|snsg/src/test/java/com/shineunsigan/snsg/schedule/infrastructure/client/SchedulePlaceAdapterTest\.java$|snsg/src/test/java/com/shineunsigan/snsg/admin/infrastructure/security/AdminSecurityConfigTest\.java$|docs/admin-api-guide\.md$|docs/openapi-admin\.yaml$|\.claude/tasks/SHI-184/task\.md$)'` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-184 && git status --short | grep -E '\.(log|txt|sql)$' | grep -v 'V12__'` → 출력 없음(임시 파일 없음)

## 비목표

- 원천 변경 경고(POL-P4)를 API로 보여주지 않는다. 지금처럼 로그로만 남긴다. 지난 원천 값을 보관하지 않아 다시 계산할 수 없다.
- C5 매칭(폐업 승인 장소 ↔ 같은 주소 새 CANDIDATE) 전용 API를 만들지 않는다. 목록 필터와 `PUT` 두 번으로 한다. 자동으로 옮기지도 않는다.
- 변경 이력 테이블을 만들지 않는다.
- 감사 값(adminId)을 이름으로 풀지 않는다. place는 admin을 부를 수 없다.
- 낙관적 잠금(`@Version`)을 두지 않는다. 어드민끼리 겹치면 마지막 요청이 이긴다.
- 장소 삭제 API를 만들지 않는다. 영구 제외는 `REJECTED`다(명세: places는 soft delete하지 않는다).
- 어드민이 `isClosed`·원천 값·`lastSyncedAt`·`source`를 바꾸는 경로를 만들지 않는다.
- 공원 원천(PARK_STANDARD, C9)과 적재 로직(필터·추정·값 정리·폐업 비율)은 바꾸지 않는다. 바꾸는 것은 잠금(GOAL 6)과 감사 값(GOAL 5)뿐이다.
- 다음은 고치지 않는다:
  - `AdminSecurityConfig`, `AdminAccessDeniedHandler`, admin 도메인의 main 코드
  - auth·user·schedule의 main 코드
  - 사용자 API(`RegionController`, `/api/v1/**`)
  - `FindPlaceUseCase`·`FindRegionUseCase` 계약
- 기존 테스트의 단정을 바꾸지 않는다. places INSERT에 감사 컬럼을 더하는 것과, `findAllBySourceIds` 등에 잠금이 붙어 필요한 설정 조정만 허용한다. 그 밖에 기존 단정이 깨지면 멈추고 보고한다.
- V1~V11을 고치지 않는다.
- 분기별 행동
  - rebase에서 `AdminSecurityConfigTest`·`docs/admin-api-guide.md`가 충돌하면 develop(SHI-183) 쪽 내용을 그대로 받고, 장소 항목만 더한다.
  - `docs/openapi-admin.yaml` 충돌은 손으로 합치지 않는다. develop 쪽을 받은 뒤 다시 만든다.
  - 머지 전에 develop에 V12가 생겼으면 이 브랜치의 번호를 다음 번호로 올린다(`.claude/rules/package-structure.md` 마이그레이션 절). 그때 GOAL 5·9의 `V12`는 그 번호로 읽는다.

## 제약

- 패키지 구조·의존 방향·Service/Facade·일괄 UPDATE·잠금·스케줄러·설정 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API 형식(URL, 응답 봉투, 요청 검증, 목록 커서, springdoc 애노테이션, 문서)은 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다(실제 PostgreSQL, 공유 DB에서 넣은 행만 단정, 동시성은 커밋까지, 감사 값은 native SELECT, 테스트는 원래 코드와 별도 커밋).
- 브랜치·커밋·스테이징은 `.claude/rules/git-workflow.md`를 따른다. 커밋하지 않고 스테이징까지만 한다.
- 임시 파일은 `.claude/rules/temp-files.md`에 따라 세션 스크래치패드에 두고, 스테이징 전에 워크트리에서 치운다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 제안(이름·구성은 구현이 정해도 된다):

- `place/presentation/AdminPlaceController`(`@Tag(name = "어드민 장소")`), `AdminRegionController`(또는 한 컨트롤러). 컨트롤러는 principal을 받지 않아도 된다. 감사 값은 `SecurityAuditorAware`가 채운다.
- 서비스
  - `PlaceCommandService`는 이미 적재로 200줄 가까이 된다. 검수 쓰기는 책임별로 `PlaceReviewCommandService`(수정·MANUAL 등록)로 나누는 것을 권한다. 이름이 `CommandService`로 끝나야 ArchUnit 규칙이 적용된다.
  - 조회는 `PlaceReviewQueryService`(목록·상세), 지역 전체는 기존 `RegionQueryService.findAll`을 쓴다.
  - 지역 존재 확인은 같은 도메인 repository(`RegionRepository.findByCode`)로 서비스 안에서 한다. 같은 도메인이라 facade가 필요 없다.
- domain
  - `Place.review(...)`(수정)와 `Place.createManual(...)`(등록). 검수 메서드에서 `reviewedBy`·`reviewedAt`을 채우려면 adminId가 필요하다. 서비스 인자로 받거나(컨트롤러가 `@AuthenticationPrincipal UUID adminId`로 받아 넘김), 아니면 `reviewedBy`를 `updatedBy`와 같은 값으로 둔다. **adminId를 컨트롤러에서 받아 명시적으로 넘기는 쪽을 권한다.** SecurityContext를 domain에서 읽지 않기 위해서다. `reviewedAt`은 서비스의 `LocalDateTime.now()`.
  - 좌표 범위·반올림 규칙은 `PlaceImportPolicy`에 이미 있다(`coordinate`). 같은 상수를 함께 쓰도록 정리해도 되고, 형식 검사만 따로 둬도 된다.
  - `PlaceErrorCode`(domain/exception): `PLACE_NOT_FOUND`(NOT_FOUND), `INVALID_CURSOR`, 상태 거부(예: `INVALID_REVIEW_STATUS`), UNCLASSIFIED 승인 거부(예: `UNCLASSIFIED_NOT_APPROVABLE`), 없는 지역(예: `INVALID_REGION`), 형식 불변식 코드들. 코드 이름은 문서와만 맞으면 된다.
  - 커서: `PlaceCursor(createdAt, id)`. SHI-183의 `AdminCursor`(wt-SHI-183, 머지 후 develop)와 같은 형식이고 도메인마다 따로 둔다.
- 설정: `place.page-size-default: 20`, `place.page-size-max: 50`(`PlaceProperties`에 더함).
- 목록 쿼리: `.claude/rules/package-structure.md` 목록 쿼리 절. 조건이 있을 때만 JPQL 조각을 이어 붙이고 값은 모두 바인딩한다. keyword는 `lower(x) like :pattern escape '\'`로 쓰고, 패턴은 Java에서 `\`·`%`·`_`를 이스케이프해 만든다. 정렬·커서 비교는 DB에서만 한다.
- MANUAL `source_region_code`가 `CHAR(5)`라 공백으로 읽히면 Result 변환에서 `strip()`한다. 등록 때 `''`로 넣는 것은 그대로다.
- 감사·잠금 구현
  - `markSynced` 쿼리에 `p.updatedBy = :updatedBy`를 더하고 `Auditor.SYSTEM_SCHEDULER`를 넘긴다. `PlaceJpaRepository`의 "places에는 감사 주체 컬럼이 없다" 주석도 고친다.
  - 스케줄러의 SecurityContext 처리는 `user/infrastructure/scheduler/UserAnonymizationScheduler`를 본다. 테스트 방식은 `UserAnonymizationSchedulerTest`.
  - `reviewed_*`는 `@CreatedBy`/`@LastModifiedBy`가 아니라 도메인 메서드만 바꾸는 일반 컬럼이다.
  - 잠금: `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 JPQL 메서드에 단다. 잠그는 조회에 fetch join을 하지 않는다. 내장 `findById`와 다른 이름(`findByIdForUpdate`)으로 두고 `isDeleted = false`를 명시한다.
- `Place` javadoc의 "검수는 DB에서 직접 하고 … 메서드가 없다"와 `V4` CHECK 주석의 근거("검수를 DB에서 직접 하므로")는 사실이 바뀌므로 javadoc만 고친다. V4는 머지된 파일이라 고치지 않는다.
- 요청 DTO: `PUT` 본문과 `POST` 본문은 필드가 다르므로 record를 둘 둔다(`AdminPlaceUpdateRequest`, `AdminPlaceCreateRequest` 등). Bean Validation 문구는 domain ErrorCode 문구와 같게 한다.
- 문서의 응답 예시 JSON에 MANUAL 행과 SEMAS 행을 하나씩 보이면, FE가 원천 값 `''`·0과 `reviewedAt: null`을 이해하기 쉽다.
- refine-docs 단계에서 갱신할 만한 것: `.claude/rules/package-structure.md`의 places 감사·`reviewed_*` 판단, 적재 행 잠금. `.claude/rules/api.md` URL 절의 SHI-184 예시 문구.
- 기획 명세(저장소 밖)의 "place 도메인은 오류 코드를 만들지 않는다", "검수는 DB 직접" 서술은 이 작업으로 낡는다. 명세는 고치지 않고 PR background에 적는다.

## 배경
- Linear SHI-204 "어드민 CORS 허용 메서드에 PUT 추가". 앞선 코드 리뷰에서 찾은 버그다.
- 유형: 버그 수정(기존 테스트는 유지하고 회귀 테스트를 더한다).
- 재현 상태: `common/config/CorsConfig.configuration`이 두 경로(`/api/admin/**`, `/api/v1/**`) 모두에 허용 메서드 `GET, POST, PATCH, DELETE, OPTIONS`를 건다. 어드민 장소 수정 API `PUT /api/admin/v1/places/{placeId}`(`place/presentation/AdminPlaceController.java:123`, `@PutMapping("/{placeId}")`)는 PUT이다. 어드민 앱 출처(`http://localhost:3001`)의 브라우저가 이 API를 부르면 preflight(`OPTIONS`, `Access-Control-Request-Method: PUT`)가 거부돼 요청이 나가지 않는다. 지금 어드민 경로 설정에서 `CorsConfiguration.checkHttpMethod(HttpMethod.PUT)`은 `null`(거부)이다.
- 매핑 확인 결과: `snsg/src/main/java`에서 `@PutMapping`·`RequestMethod.PUT`은 `AdminPlaceController` 한 곳뿐이다. 사용자 경로(`/api/v1/**`)에는 PUT API가 없다.
- 결정(오케스트레이터 지시의 권장안): PUT은 어드민 경로에만 허용한다. 사용자 경로의 허용 메서드는 지금 그대로 둔다(쓰지 않는 메서드를 열지 않는다).

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-204` (브랜치 `fix/SHI-204-admin-cors-put`, 베이스 `96f6cee`)
- 메인 작업 디렉터리 `snsg-be`와 다른 워크트리는 읽기만 한다
- `CorsConfigTest`는 Spring·DB 없이 도는 단위 테스트다. DB·Redis가 필요한 테스트(전체 `./gradlew test`·`build`)는 다른 워크트리(SHI-205·206·207)와 공유 DB를 쓰므로 이 작업의 검증에 넣지 않았다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다

## GOAL
1. 어드민 경로(`/api/admin/**`)의 CORS 설정이 PUT을 허용하고, 기존 메서드(GET·POST·PATCH·DELETE·OPTIONS)도 계속 허용한다. 이를 단정하는 회귀 테스트가 `CorsConfigTest`에 있고 통과한다. 경로는 실제 수정 API 경로 `/api/admin/v1/places/{임의 UUID}`로 본다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-204/snsg && ./gradlew cleanTest test --tests '*CorsConfigTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `grep -n 'PUT' /c/Users/ks/nss/dev/snsg-be/wt-SHI-204/snsg/src/test/java/com/shineunsigan/snsg/common/config/CorsConfigTest.java` → PUT을 단정하는 줄이 있다
2. 사용자 경로(`/api/v1/**`)의 CORS 설정은 PUT을 허용하지 않고, 허용 메서드는 지금과 같다(GET·POST·PATCH·DELETE·OPTIONS). 이를 단정하는 테스트가 `CorsConfigTest`에 있고 1번 명령에서 함께 통과한다.
   - 검증: 1번 명령 → 실패 0
3. 기존 `CorsConfigTest`의 세 테스트(`separatesOrigins`, `emptyAdminOrigins`, `missingAdminOrigins`)의 단정을 바꾸지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-204 && git diff --staged -- snsg/src/test/java/com/shineunsigan/snsg/common/config/CorsConfigTest.java | grep -E '^-[^-]'` → 출력 없음(삭제·수정된 줄 없음, 추가만)
4. 허용 출처·허용 헤더·credentials·maxAge는 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-204 && git diff --staged -- snsg/src/main/java/com/shineunsigan/snsg/common/config/CorsConfig.java | grep -E '^[-+].*(setAllowedOrigins|setAllowedHeaders|setAllowCredentials|setMaxAge|registerCorsConfiguration)'` → 출력 없음
   - (`registerCorsConfiguration` 줄의 인자를 바꿔야 하는 구현이면 이 항목은 출처 인자 `corsProperties.adminAllowedOrigins()`·`corsProperties.allowedOrigins()`가 각 경로에 그대로인지로 판정한다)
5. 변경 파일은 아래 셋뿐이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-204 && git diff --staged --name-only | sort` → 정확히
     ```
     .claude/tasks/SHI-204/task.md
     snsg/src/main/java/com/shineunsigan/snsg/common/config/CorsConfig.java
     snsg/src/test/java/com/shineunsigan/snsg/common/config/CorsConfigTest.java
     ```
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-204 && git status --short` → 스테이징되지 않은 변경·추적되지 않은 파일 없음

## 비목표
- 사용자 경로에 PUT을 열지 않는다. 사용자 API에 PUT이 생기면 그 작업에서 연다
- `AdminPlaceController`의 `@PutMapping`을 PATCH 등으로 바꾸지 않는다. FE 계약(`docs/openapi-admin.yaml`)이 바뀐다
- 허용 헤더(`Authorization`, `Content-Type`), 허용 출처, `CorsProperties`는 건드리지 않는다
- `SecurityConfig`·`AdminSecurityConfig`와 그 테스트(`SecurityConfigTest`·`AdminSecurityConfigTest`의 빈 `UrlBasedCorsConfigurationSource`)는 건드리지 않는다
- auth·user·admin·place 도메인 파일은 건드리지 않는다(SHI-205·206·207이 auth·user를 고친다)
- `docs/`·`README.md`·`.claude/rules/`는 이 단계에서 고치지 않는다. 문서는 refine-docs 단계가 본다
- 전체 `./gradlew test`·`build`는 돌리지 않는다(공유 DB)

## 제약
- 패키지 구조는 `.claude/rules/package-structure.md`를 따른다(`common/config`는 도메인을 import하지 않는다)
- 테스트는 `.claude/rules/testing.md`를 따른다(CORS 규칙은 `CorsConfigTest`가 Spring 없이 실제 `CorsConfig`로 만든 소스로 본다)
- 들여쓰기 4칸, CLAUDE.md "Surgical Changes"
- 임시 파일은 `.claude/rules/temp-files.md`(워크트리 안에 만들지 않는다)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 수정 전 실패 확인(implement가 보고): 회귀 테스트를 먼저 더하고 1번 명령을 돌려, 어드민 PUT 단정이 실패하는 것을 본 뒤 고친다
- 구현 제안(형태는 구현에 맡긴다): `configuration(List<String> allowedOrigins)`에 허용 메서드 목록을 인자로 더해, 어드민 등록에는 PUT을 넣은 목록, 사용자 등록에는 지금 목록을 넘긴다. 메서드 목록 상수 이름은 기존 `USER_PATHS`·`ADMIN_PATHS`에 맞춰 짓는다
- 클래스 javadoc에 어드민만 PUT을 여는 이유(PUT은 어드민 장소 수정만 쓴다) 한 줄을 더해도 된다
- 따라 할 기존 코드
  - 테스트: `CorsConfigTest`(`snsg/src/test/java/com/shineunsigan/snsg/common/config/CorsConfigTest.java:21-61`) — `source(...)`로 실제 `CorsConfig`를 만들고 `configurationFor(source, path)`로 경로별 설정을 꺼내 단정한다. 메서드 허용은 `CorsConfiguration.checkHttpMethod(HttpMethod.PUT)`(허용이면 목록, 거부면 `null`)로 본다. preflight 처리기 `DefaultCorsProcessor`가 같은 메서드로 판정한다
  - 이름: `CorsConfig`(`snsg/src/main/java/com/shineunsigan/snsg/common/config/CorsConfig.java:27-28`) — 경로 상수 `USER_PATHS`·`ADMIN_PATHS`

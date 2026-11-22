## 배경

- Linear SHI-205 "카카오 로그인 OAuth state 검증 추가". 작업 유형: **버그 수정**(보안). 코드 리뷰에서 찾았다.
- 재현 상태: 소셜 로그인 흐름 `GET /api/v1/auth/{provider}/authorize` → 카카오 → `GET /api/v1/auth/{provider}/callback?code=...`에 OAuth `state`가 없다.
  - `KakaoSocialLoginAdapter.authorizeUrl()`은 `response_type`·`client_id`·`redirect_uri`만 싣는다.
  - `AuthController.callback`은 `code` 하나만 받아 곧바로 `SocialLoginFacade.authenticate(provider, code)`를 부른다. 요청이 이 브라우저에서 시작한 로그인의 결과인지 확인하지 않는다.
  - 공격: 공격자가 자기 카카오 계정으로 인가코드를 받아 `GET /api/v1/auth/kakao/callback?code=<공격자 코드>` 링크를 만들어 피해자에게 열게 한다. 피해자 브라우저에 공격자 계정의 `refreshToken` 쿠키(기존 회원) 또는 `signUpToken` 쿠키(신규)가 심어진다(로그인 CSRF). 수정 전 이 요청은 쿠키·state 없이 302로 통과한다(`AuthControllerTest.callbackForExistingUser`가 바로 그 요청 `get(".../kakao/callback").param("code", "code")`로 302를 단정한다).
- 범위 밖: 카카오 연결 끊기 웹훅(SHI-207이 다른 워크트리에서 `HandleSocialUnlinkFacade`·`KakaoWebhookProperties`를 고친다).

### 결정과 근거 (권장안)

1. **state는 쿠키에 둔다. Redis에 두지 않는다.** 로그인 CSRF를 막으려면 state가 "이 브라우저"에 묶여야 한다. Redis에만 두면 공격자가 자기 브라우저로 `/authorize`를 불러 유효한 state를 얻어 링크에 실을 수 있어 막지 못한다. 어차피 브라우저 바인딩용 쿠키가 필요하고, 쿠키 값과 쿼리 값을 대조하는 것으로 충분하다(서버 저장 없음). Redis 키·TTL·정리 규칙(`.claude/rules/package-structure.md` Redis 절)이 늘지 않고, 새 환경변수도 없다.
2. **authorize**: 요청마다 `SecureRandom` 난수 state(32바이트, URL-safe Base64 패딩 없음, `RedisSignUpSessionAdapter.generateToken`과 같은 방식)를 만들어 카카오 인증 URL의 `state` 쿼리에 싣고, 같은 값을 쿠키로 심는다.
   - 쿠키: HttpOnly, Secure, **SameSite=Lax**, Path `/api/v1/auth`, 짧은 Max-Age. Lax인 이유: 콜백은 카카오가 보내는 **최상위 GET 이동**이라 Lax 쿠키가 따라온다. 기존 토큰 쿠키(`None`)보다 좁혀도 흐름이 깨지지 않으므로 좁힌다. 공격자 사이트에서 콜백으로 보내도 피해자 브라우저의 쿠키는 공격자가 모르는 값이거나 없다.
3. **callback**: 쿼리 `state`와 쿠키를 상수 시간(`MessageDigest.isEqual`)으로 대조한다. **같을 때만** 소셜 인증(`SocialLoginFacade.authenticate`)을 부른다. 성공 응답(302)은 기존 쿠키(refresh 또는 signUp)와 함께 state 쿠키 만료(`Max-Age=0`)를 내보낸다.
4. **state 없음·쿠키 없음·불일치는 모두 401 `INVALID_OAUTH_STATE`(`ErrorType.UNAUTHENTICATED`) 한 코드다.** 쿼리 `state`가 빠져도 400 `INVALID_REQUEST`가 아니라 401이다. 검증 실패를 한 코드로 묶는 규칙(`.claude/rules/api.md` 토큰과 쿠키 절)과, 쿠키 없음은 401이라는 기존 컨트롤러 규칙(`AuthController.requireCookie` javadoc)을 따른다.
   - FE로 리다이렉트하지 않는다. 기존 콜백 오류(`INVALID_SOCIAL_CODE` 401, `SOCIAL_USER_FETCH_FAILED` 500)가 `BusinessException` → 전역 예외 처리의 JSON 응답이고 FE 리다이렉트가 없다. 같은 방식을 따른다.
   - 문구는 `INVALID_SOCIAL_CODE`와 같은 "소셜 로그인에 실패했습니다. 다시 시도해주세요."로 둔다(제안. 구현이 바꿔도 된다).
5. **FE 코드는 바뀌지 않는다.** 로그인 시작은 지금처럼 `location.href`로 `/authorize`에 이동하고, state 쿠키는 브라우저가 알아서 주고받는다. FE가 알아야 할 것(같은 브라우저에서 시작·완료해야 한다, 여러 탭에서 동시에 로그인을 시작하면 먼저 시작한 탭이 401, 새 오류 코드)은 `docs/api-guide.md`에 적는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-205` (브랜치 `fix/SHI-205-kakao-oauth-state`, 베이스 `96f6cee`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리는 읽기만 한다
- 마이그레이션이 없다. 새 환경변수도 없다
- DB·Redis·8080 포트를 모든 워크트리가 공유한다. 테스트는 다른 워크트리와 동시에 돌리지 않는다. DB·Redis는 Docker 컨테이너 `snsg-postgres`·`snsg-redis`이고 꺼져 있으면 `@SpringBootTest` 계열(`OpenApiSpecTest`, 전체 build)이 실패한다(skip이 아니다). `docker ps`로 확인하고 없으면 `docker start snsg-postgres snsg-redis`. 그래도 실패하면 코드를 고치지 말고 멈춰서 보고한다
- `WeakKeyException`이 나면 `snsg/.env`에 키가 빠진 것이다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 코드를 고치지 말고 보고한다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다

## GOAL

1. `GET /api/v1/auth/kakao/authorize`는 302이고, `Location`의 카카오 인증 URL에 `state` 쿼리가 있으며, 같은 값의 state 쿠키를 HttpOnly·Secure·SameSite=Lax·Path `/api/v1/auth`·Max-Age 0보다 큼으로 심는다. 두 번 부르면 state 값이 서로 다르다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*AuthControllerTest'` → BUILD SUCCESSFUL, 실패 0. 이 단정을 하는 테스트가 `AuthControllerTest`에 있다(`grep -n "authorize" snsg/src/test/java/com/shineunsigan/snsg/auth/presentation/AuthControllerTest.java` → `/api/v1/auth/kakao/authorize`를 부르는 테스트가 1개 이상)
2. **회귀 테스트(재현 값 그대로)**: 수정 전 통과하던 요청 `GET /api/v1/auth/kakao/callback?code=code`(state 쿼리·쿠키 모두 없음)가 401, `code` = `INVALID_OAUTH_STATE`이고 `SocialLoginFacade.authenticate`가 호출되지 않으며 `refreshToken`·`signUpToken` 쿠키를 심지 않는다. 다음 세 경우도 각각 같은 결과다: 쿠키만 있고 쿼리 state 없음, 쿼리 state만 있고 쿠키 없음, 둘 다 있으나 값이 다름.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*AuthControllerTest'` → 실패 0, 위 네 경우를 단정하는 테스트가 있다(`grep -c "INVALID_OAUTH_STATE" snsg/src/test/java/com/shineunsigan/snsg/auth/presentation/AuthControllerTest.java` → 1 이상, 네 경우가 각각 `verify(socialLoginFacade, never()).authenticate(...)` 또는 같은 뜻의 단정을 가진다)
3. 쿼리 state와 쿠키가 같으면 지금 동작 그대로다(기존 회원: `refreshToken` 쿠키 + `Location` `http://localhost:3000/auth/callback`, 신규: `signUpToken` 쿠키 + `?signUp=true`). 성공 응답은 state 쿠키를 `Max-Age=0`으로 만료시킨다.
   - 바뀌는 기존 단정(동작 변경이 아니라 요청 조건 추가): `AuthControllerTest.callbackForExistingUser`·`callbackForNewUser`의 요청이 `param("code", "code")`만 → `param("code", "code")` + 같은 값의 `state` 쿼리와 state 쿠키로 바뀐다. 두 테스트의 기존 `andExpect` 단정(302, 쿠키 값, httpOnly, Location)은 그대로 두고 state 쿠키 만료 단정을 더한다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*AuthControllerTest'` → 실패 0
4. 쿼리 state와 쿠키 대조는 상수 시간 비교다.
   - 검증: `grep -rn "MessageDigest.isEqual" snsg/src/main/java/com/shineunsigan/snsg/auth --include=*.java | grep -v "HandleSocialUnlinkFacade"` → 1줄 이상(state 대조)
5. 카카오 adapter의 인증 URL에 넘긴 state가 URL 인코딩되어 `state=` 쿼리로 실린다. 기존 단정(`response_type=code`로 시작, `client_id`, `redirect_uri`)은 유지한다.
   - 바뀌는 기존 단정: `KakaoSocialLoginAdapterTest.authorizeUrl`의 호출이 `adapter.authorizeUrl()` → state를 넘기는 호출로 바뀌고 `state=` 포함 단정이 더해진다. 나머지 세 단정은 그대로
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*KakaoSocialLoginAdapterTest'` → 실패 0
6. `AuthErrorCode`에 `INVALID_OAUTH_STATE`(`ErrorType.UNAUTHENTICATED`)가 있다.
   - 검증: `grep -n "INVALID_OAUTH_STATE(ErrorType.UNAUTHENTICATED" snsg/src/main/java/com/shineunsigan/snsg/auth/domain/exception/AuthErrorCode.java` → 1줄
7. OpenAPI 스펙이 코드와 같고, 콜백 문서가 state 검증과 401 `INVALID_OAUTH_STATE`를 설명한다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → 실패 0
   - 검증: `grep -c "INVALID_OAUTH_STATE" docs/openapi.yaml` → 1 이상
8. `docs/api-guide.md`가 바뀐 계약을 적는다: 인증 오류 코드 표에 `INVALID_OAUTH_STATE`(401, 언제, 화면에서 할 일: 카카오 로그인부터 다시) 행, 로그인 흐름에 "시작과 완료가 같은 브라우저여야 하고, 여러 탭에서 동시에 로그인을 시작하면 먼저 시작한 쪽이 실패한다"는 안내. FE 코드 변경이 필요 없다는 것도 적는다.
   - 검증: `grep -n "INVALID_OAUTH_STATE" docs/api-guide.md` → 1줄 이상, 그중 하나가 `| 401 | \`INVALID_OAUTH_STATE\` |`로 시작하는 표 행
9. 전체 빌드(ArchUnit 포함)가 통과한다.
   - 검증: `cd snsg && ./gradlew build` → BUILD SUCCESSFUL, 실패 0
10. 범위 밖 파일을 고치지 않는다.
   - 검증: `git status --short` → 모든 경로가 워크트리 안이고 다음 범위 안에 있다: `snsg/src/main/java/com/shineunsigan/snsg/auth/`(단 `HandleSocialUnlinkFacade.java`·`KakaoWebhookProperties.java` 제외), `snsg/src/test/java/com/shineunsigan/snsg/auth/`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/tasks/SHI-205/task.md`, 그리고 refine-docs가 고칠 수 있는 `.claude/rules/`·`.claude/skills/`·`CLAUDE.md`
   - 검증: `git status --short | grep -E '/(HandleSocialUnlinkFacade|KakaoWebhookProperties)\.java$'` → 출력 없음
   - 검증: `git status --short | grep -E '(^| )docs/openapi-admin\.yaml$|\.sql$|\.env'` → 출력 없음

## 비목표

- `HandleSocialUnlinkFacade`·`KakaoWebhookProperties`·`KakaoWebhookController`와 그 테스트는 건드리지 않는다(SHI-207)
- Redis에 state를 저장하지 않는다. 새 설정 키·환경변수를 만들지 않는다
- 실패 응답(401)에서 state 쿠키를 지우지 않는다. 짧은 Max-Age로 사라지고 다음 `/authorize`가 덮어쓴다. 실패 응답은 전역 예외 처리의 JSON 그대로다(FE 리다이렉트를 새로 만들지 않는다)
- 카카오가 `error=access_denied`(사용자 취소)로 `code` 없이 돌아오는 경우의 처리는 바꾸지 않는다. 단 state 검증을 `code` 확인보다 먼저 하든 나중에 하든 GOAL 2의 네 경우(`code`는 있음)는 401 `INVALID_OAUTH_STATE`여야 한다
- 기존 토큰 쿠키(`refreshToken`·`signUpToken`)의 SameSite(`None`)·Path·수명은 바꾸지 않는다
- PKCE, nonce(OIDC), 네이버·애플 adapter 구현은 하지 않는다. 포트 시그니처가 바뀌어 다른 구현이 있으면 컴파일만 맞춘다(지금 `SocialLoginPort` 구현은 `KakaoSocialLoginAdapter` 하나)
- 기존 테스트 단정은 GOAL 3·5에 적은 두 곳(요청 조건 추가)만 고친다. 나머지 단정은 유지한다. `SocialLoginFacadeTest`는 `authorizeUrl` 시그니처 변경에 따른 컴파일 맞춤만 허용한다
- `docs/openapi-admin.yaml`이 재생성 뒤 `M`으로 뜨면 줄바꿈 차이다. `git diff --stat -- docs/openapi-admin.yaml`로 내용 변경이 없는지 보고 `git checkout -- docs/openapi-admin.yaml`로 되돌린다(`.claude/rules/api.md` 문서 절)

## 제약

- 패키지 구조·계층 방향은 `.claude/rules/package-structure.md`를 따른다(presentation은 infrastructure를 import하지 않는다, facade는 트랜잭션 없음)
- API·쿠키·문서는 `.claude/rules/api.md`를 따른다(토큰과 쿠키 절, 문서 절)
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 워크트리 안에 만들지 않는다
- 스펙 재생성은 구현이 한다: `cd snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`. `docs/openapi.yaml`은 API를 바꾼 코드와 같은 커밋에 넣는다
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 구현 형태 제안(바꿔도 된다)
  - 쿠키 이름 `oauthState`, Max-Age 10분(카카오 인가코드 유효시간이 10분). 상수로 두고 설정 키를 만들지 않는다
  - `SocialLoginPort.authorizeUrl(String state)`, `SocialLoginFacade.authorizeUrl(SocialProvider, String state)`. state 생성·쿠키·대조는 HTTP 프로토콜 세부라 presentation(`AuthController` + `AuthCookieFactory`)에 둔다. 생성을 다른 곳에 두면 그 이유를 보고한다
  - `AuthCookieFactory`는 지금 `build`가 `SameSite=None`을 고정한다. state 쿠키만 `Lax`로 만드는 메서드를 더하고, 기존 `refreshToken`·`signUpToken`·`expired`의 출력은 바꾸지 않는다. 만료 쿠키도 같은 이름·Path로 내야 브라우저가 지운다
  - 콜백에서 `@RequestParam(value = "state", required = false)`, `@CookieValue(value = ..., required = false)`로 받아 빈 값을 직접 401로 끊는다(required로 두면 400이 된다)
- 따라 할 기존 코드
  - 쿠키: `AuthCookieFactory.build`·`expired`(`auth/presentation/AuthCookieFactory.java:43-55`) — HttpOnly·Secure·Path 구성과 수명 0 덮어쓰기로 지우는 방식
  - 쿠키 없음 → 401: `AuthController.requireCookie`(`auth/presentation/AuthController.java:185-189`) — 빈 값은 `BusinessException(AuthErrorCode...)`
  - 성공 응답에 쿠키 여러 개: `AuthController.signUp`(`auth/presentation/AuthController.java:140-143`) — `SET_COOKIE` 헤더를 두 번 단다. `redirectToFrontend`(`:191`)는 지금 쿠키 하나만 받는다
  - 난수: `RedisSignUpSessionAdapter.generateToken`(`auth/infrastructure/redis/RedisSignUpSessionAdapter.java:77-81`) — `SecureRandom` 32바이트, URL-safe Base64 패딩 없음
  - 상수 시간 비교: `HandleSocialUnlinkFacade`(`auth/application/HandleSocialUnlinkFacade.java:40-41`) — `MessageDigest.isEqual(UTF-8 바이트)`. **읽기만 하고 고치지 않는다**(SHI-207)
  - 오류 코드: `AuthErrorCode.INVALID_SOCIAL_CODE`(`auth/domain/exception/AuthErrorCode.java:18`) — 소셜 로그인 실패는 `ErrorType.UNAUTHENTICATED`
  - URL 인코딩: `KakaoSocialLoginAdapter.authorizeUrl`·`encode`(`auth/infrastructure/client/KakaoSocialLoginAdapter.java:44-49`, `:111`)
  - 테스트: `AuthControllerTest.callbackForExistingUser`·`callbackForNewUser`(`auth/presentation/AuthControllerTest.java:98-122`) — `@WebMvcTest` + `@MockitoBean SocialLoginFacade`, `cookie()`·`header()` 단정. 오류 응답 단정은 같은 파일의 `reissueWithInvalidToken`(`:139`)·`missingCookieIsUnauthorized`(`:309`)
  - 테스트: `KakaoSocialLoginAdapterTest.authorizeUrl`(`auth/infrastructure/client/KakaoSocialLoginAdapterTest.java:126-131`)
- `SecurityConfig`는 `/api/v1/auth/*/authorize`·`/callback`을 이미 `permitAll`로 연다. 바꿀 필요가 없다
- MockMvc 요청 쿠키는 `.cookie(new jakarta.servlet.http.Cookie(name, value))`, SameSite 단정은 `cookie().sameSite(name, "Lax")` 또는 `Set-Cookie` 헤더 문자열 단정
- SHI-207이 같은 auth 도메인을 고친다. `AuthErrorCode`를 둘 다 고치면 머지 때 충돌이 날 수 있다. 먼저 머지된 쪽 위에 다시 맞춘다
- 로컬 FE 개발(`http://localhost:3000` → BE `http://localhost:8080`)에서 Secure 쿠키: 브라우저가 localhost를 보안 출처로 취급해 기존 refresh 쿠키(Secure)처럼 동작한다. 실제 브라우저 확인은 명령으로 할 수 없어 GOAL에 넣지 않았다

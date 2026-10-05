---
paths:
  - "snsg/src/main/java/**/presentation/**/*.java"
---

# API

## URL

- 클라이언트용: `/api/v1/<resources>`. 리소스는 복수형 kebab-case 명사 (`/api/v1/user-settings`).
- 서비스 간 내부 호출용: `/internal/v1/<resources>`. 외부에 노출하지 않는다.
- 동사 대신 하위 리소스로 표현한다: `PATCH /api/v1/matchings/{id}/approval`
- prefix를 두는 이유:
  - `/api`: 이후 생길 수 있는 SSR 페이지, 외부 연동(webhook 등), actuator·문서 경로와 구분하고, 보안·CORS·게이트웨이 라우팅을 prefix 하나로 적용하기 위해
  - `/v1`: 호환되지 않는 변경이 필요할 때, 바로 바꿀 수 없는 클라이언트(배포 전에 이미 열려 있던 웹 페이지, 외부 연동)를 v1에 둔 채 v2를 병행 배포하기 위해. 버전은 breaking change가 있을 때만 올린다
  - `/internal`: 도메인이 서비스로 분리될 때 도메인 간 호출 경로가 되며, 외부 API와 인증·권한 정책을 분리하기 위해

## Controller

- 사용자용 Controller는 같은 도메인의 Service에 의존한다.
- `/internal/v1` Controller(서비스 분리 후)는 `application/port/in`의 UseCase에 의존한다.
- 반환 타입은 `ResponseEntity<ApiResponse<XxxResponse>>`. Entity나 Result를 그대로 반환하지 않는다.
- 요청은 `@Valid` + Request record의 Bean Validation으로 형식만 검증한다. 비즈니스 규칙 검증은 domain에서 한다.
  - 검증은 `jakarta.validation.constraints.*`를 쓴다(`spring-boot-starter-validation` 포함됨).
- **Request DTO를 만드는 기준**: 필드가 둘 이상이거나 검증 제약(`@NotBlank`, `@Size` 등)이 붙으면 만든다. 파라미터 하나를 그대로 넘길 뿐이면 만들지 않고 `@RequestParam`·`@PathVariable`로 받아 Command를 만든다. 필드 하나짜리 껍데기 record는 읽는 사람에게 아무것도 알려주지 않는다.
  - 단 **나중에 필드가 늘어날 것이 분명하면** 처음부터 만든다. 공개된 API의 요청 형태를 바꾸면 FE도 함께 고쳐야 한다.
- 인증 주체는 `@AuthenticationPrincipal UUID userId`로 받는다. 인증 필터가 principal에 userId(UUID)를 넣는다.
  - 공개 경로(토큰 없이 호출하는 엔드포인트)에서는 principal이 `"anonymousUser"`라 null이 들어온다. 타입이 맞지 않아도 예외 없이 null이므로 공개 경로에서는 쓰지 않는다.
  - 토큰 없이 호출하는 엔드포인트는 `SecurityConfig`에서 HTTP 메서드와 경로를 정확히 지정해 연다. `/api/v1/auth/**`처럼 prefix 전체를 열면 같은 prefix에 생긴 인증 필요 API까지 열린다. 우리 API에서는 `POST /api/v1/auth/tokens`(재발급)가 공개이고 `DELETE /api/v1/auth/tokens`(로그아웃)는 인증이 필요하다. 경로가 같고 메서드만 다르므로 경로만 지정하면 로그아웃·탈퇴가 열린다.
  - 공개 경로는 API를 만들 때 하나씩 추가한다. 아직 없는 경로를 미리 적어두지 않는다.
- 인증 필터는 공개 경로를 포함한 **모든 요청**에서 돈다. 필터는 토큰이 있을 때만 해석해 principal을 채우고, 없거나 틀리면 그냥 통과시킨다. 인가 판단은 `authorizeHttpRequests`가, 401 응답 생성은 `AuthenticationEntryPoint`가 한다.
  - 공개 경로에서도 principal이 채워져야 한다. 일정 상세처럼 비로그인도 보지만 보는 사람에 따라 응답이 달라지는 API가 있다(참여자면 "참여 취소", 아니면 "참여하기").
  - principal을 UUID로 두는 이유: 전용 타입을 auth에 두면 다른 도메인 컨트롤러가 auth를 import하게 된다. 역할 등 담을 정보가 늘면 principal 타입을 `common`에 둔다.

## 토큰과 쿠키

- 서비스 토큰은 JWT. **토큰에는 userId만 담는다.** 닉네임·역할을 넣으면 발급 후 바뀐 값이 만료까지 틀린 채로 돌아다닌다.
- access와 refresh는 구조가 같고 만료만 다르다. 구분은 **저장 위치**로 한다. refresh만 Redis에 보관하고, 저장된 값과 일치해야 재발급한다.
- **refresh token은 HttpOnly 쿠키, access token은 응답 본문**으로 내려준다.
  - 웹 클라이언트는 refresh를 자바스크립트가 읽을 수 없어야 한다. 본문으로 주면 FE가 localStorage에 담게 되고 XSS 한 번에 14일짜리 토큰이 털린다.
  - access는 짧게 살아 메모리 보관으로 충분하다. 새로고침하면 재발급 API로 복구한다.
  - 리다이렉트로 토큰을 넘길 때 **쿼리 파라미터에 담지 않는다.** 브라우저 히스토리·서버 로그·Referer에 남는다.
- **검증 실패는 한 코드로 묶는다.** 만료·위조·형식 오류를 구분해 알려주면 공격자에게 힌트가 된다. 클라이언트는 어느 쪽이든 재발급을 시도하면 된다.
- 소셜 로그인은 **BE가 인가코드를 받아 토큰으로 교환**한다. 클라이언트 시크릿을 FE에 두지 않는다.

## 응답과 예외

- 반환 타입은 `ResponseEntity<ApiResponse<XxxResponse>>`.
- 봉투는 `{isSuccess, code, message, data}`다.
  - **성공 여부는 `isSuccess`로 본다.** 값은 `ApiResponse`의 팩토리 메서드가 채우므로 HTTP 상태와 어긋나지 않는다.
  - **HTTP 상태 코드는 계속 정확하게 내려준다.** 200으로 고정하고 본문으로만 성공을 알리지 않는다. 캐시·모니터링·에러 추적이 상태 코드를 본다.
  - `code`와 `message`는 **실패 전용**이다. 성공 응답에서는 비어 있어 JSON에 나가지 않는다.
- 성공: `ApiResponse.success(data)` 또는 `ApiResponse.success()`. **성공 코드 체계는 두지 않는다.**
  - FE가 성공을 분기해야 하면 code가 아니라 `data`의 필드로 준다(예: `isNewUser`). code로 분기하면 코드가 하나 늘 때 FE가 조용히 깨진다.
  - 화면 문구는 FE가 가진다. 성공 메시지를 서버가 내려주면 문구 하나 바꾸는 데 서버 배포가 필요하다.
- 실패: `BusinessException(<Domain>ErrorCode)`를 던지고, `common.web`의 전역 예외 처리에서 `ApiResponse.error(...)`로 변환한다.
- `<Domain>ErrorCode`는 `domain/exception`에 두고 `ErrorCode`를 구현하며, `ErrorType`과 메시지를 가진다. `ErrorType` → `HttpStatus` 변환은 전역 예외 처리에서만 한다.
- 특정 Controller 전용 처리가 필요할 때만 `presentation/errorhandler`에 `@RestControllerAdvice(assignableTypes = XxxController.class)`를 둔다.

## 문서

FE는 이 저장소를 보지 않고 따로 작업한다. FE에 전하는 것은 `docs/openapi.yaml`과 `docs/api-guide.md` 두 문서뿐이므로, 두 문서만 보고 화면을 만들 수 있어야 한다. 문서에 없는 것은 FE가 추측하게 되고, 추측은 틀린다.

### 엔드포인트마다 빠짐없이 적는 것

- 무엇을 하는 API이고, 어느 화면에서 언제 부르는지
- 요청 필드마다: 의미, 형식과 단위(분, 한국 시간 ISO-8601 등), 필수 여부, 허용 범위나 길이, 예시
- 응답 필드마다: 의미, 형식과 단위, null이 될 수 있는지와 그때의 뜻, 예시
- enum과 상태 코드는 값마다 뜻과 화면에서 보여줄 것
- 목록이면 정렬 기준, 개수 제한, 비어 있을 때의 응답
- 날 수 있는 에러마다: HTTP 상태, code, 발생 조건, 화면에서 할 일, 예시 JSON
- 인증이 필요한지
- 같은 요청을 다시 보내도 되는지 (재시도하거나 두 번 눌렸을 때 어떻게 되는지)

### 작성 방법


- 컨트롤러에는 springdoc 애노테이션을 단다: `@Tag`, `@Operation(summary, description)`, 응답 코드별 `@ApiResponses`(code, 발생 조건, 화면에서 할 일, 에러 예시).
  - `@ApiResponse` 애노테이션은 `common.web.ApiResponse`와 이름이 겹쳐 `io.swagger.v3.oas.annotations.responses.ApiResponse`로 풀어 쓴다.
  - 토큰 없이 호출하는 엔드포인트는 메서드에 `@SecurityRequirements`를 달아 전역 bearer 요구를 지운다. 같은 컨트롤러에 인증이 필요한 엔드포인트가 섞이므로 클래스에 달지 않는다.
- Request/Response record 필드에는 `@Schema(description, example)`을 단다.
- API를 바꾸면 같은 PR에서 `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 `docs/openapi.yaml`을 다시 만들어 커밋한다. 코드와 다르면 빌드가 실패한다.
- 여러 호출에 걸친 흐름, 공통 규칙, 화면별로 쓰는 API가 바뀌면 `docs/api-guide.md`도 고친다. 이건 빌드가 잡지 못한다.
- swagger-ui와 스펙 경로는 SecurityConfig에서 공개한다. 운영 프로파일에서는 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`로 끈다.

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
- 인증 주체는 `@AuthenticationPrincipal Long userId`로 받는다. 인증 필터가 principal에 userId(Long)를 넣는다.
  - 공개 경로(토큰 없이 호출하는 엔드포인트)에서는 principal이 `"anonymousUser"`라 null이 들어온다. 타입이 맞지 않아도 예외 없이 null이므로 공개 경로에서는 쓰지 않는다.
  - 토큰 없이 호출하는 엔드포인트는 `SecurityConfig`에서 HTTP 메서드와 경로를 정확히 지정해 연다. `/api/v1/auth/**`처럼 prefix 전체를 열면 같은 prefix에 생긴 인증 필요 API까지 열린다.
  - principal을 Long으로 두는 이유: 전용 타입을 auth에 두면 다른 도메인 컨트롤러가 auth를 import하게 된다. 역할 등 담을 정보가 늘면 principal 타입을 `common`에 둔다.

## 응답과 예외

- 성공: `ApiResponse.success(SuccessCode, data)`. 도메인 전용 성공 코드는 `presentation/dto/response/constants/<Domain>SuccessCode`.
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

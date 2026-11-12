---
paths:
  - "snsg/src/main/java/**/presentation/**/*.java"
---

# API

## URL

- 클라이언트용: `/api/v1/<resources>`. 리소스는 복수형 kebab-case 명사 (`/api/v1/user-settings`).
- 어드민(운영 도구)용: `/api/admin/v1/<resources>`. 어드민 보안 체인(`AdminSecurityConfig`, `securityMatcher("/api/admin/**")`)과 어드민 CORS 출처가 이 prefix로 갈린다. 사용자 토큰으로는 이 경로를, 어드민 토큰으로는 사용자 경로를 통과할 수 없다. 다른 도메인의 어드민 API(예: SHI-184 place의 장소 수정)도 이 prefix 아래 두면 admin을 import하지 않고 어드민 인증을 그대로 쓴다.
- 서비스 간 내부 호출용: `/internal/v1/<resources>`. 외부에 노출하지 않는다.
- 동사 대신 하위 리소스로 표현한다: `PATCH /api/v1/matchings/{id}/approval`
- 요청자 자신의 리소스는 id 대신 `me`로 가리킨다. 대상은 인증 주체로 정해진다. 예: `DELETE /api/v1/users/me`(탈퇴), `DELETE /api/v1/schedules/{scheduleId}/participants/me`(참여 취소).
- prefix를 두는 이유:
  - `/api`: 이후 생길 수 있는 SSR 페이지, 외부 연동(webhook 등), actuator·문서 경로와 구분하고, 보안·CORS·게이트웨이 라우팅을 prefix 하나로 적용하기 위해
  - `/v1`: 호환되지 않는 변경이 필요할 때, 바로 바꿀 수 없는 클라이언트(배포 전에 이미 열려 있던 웹 페이지, 외부 연동)를 v1에 둔 채 v2를 병행 배포하기 위해. 버전은 breaking change가 있을 때만 올린다
  - `/internal`: 도메인이 서비스로 분리될 때 도메인 간 호출 경로가 되며, 외부 API와 인증·권한 정책을 분리하기 위해

## Controller

- 사용자용 Controller는 같은 도메인의 application에 의존한다. 자기 도메인 안에서 끝나는 일은 Service(`UserController`, `RegionController`, `ScheduleController`의 참여 취소), 이벤트 발행 포트가 아닌 `port/out`(외부 API·Redis·파일 저장소·타 도메인) 호출이나 여러 서비스 조합이 필요한 흐름은 행위별 facade `XxxFacade`다(`AuthController`, `ScheduleController`의 개설·참여). 한 Controller 안에서도 엔드포인트마다 따로 정한다. 기준은 `.claude/rules/package-structure.md` Service와 Facade 절.
- Controller는 흐름을 조합하는 도메인에 두고, URL과 `@Tag`는 FE가 보는 리소스를 따른다. 그래서 Controller의 도메인과 URL prefix가 다를 수 있다. 예: auth `WithdrawalController`가 `/api/v1/users/me`의 탈퇴 가능 여부(`GET /withdrawal-eligibility`)와 탈퇴(`DELETE`)를 맡고 `@Tag(name = "사용자")`를 쓴다. 흐름을 auth가 조합하는 이유는 `.claude/rules/package-structure.md` 의존 방향 절.
- `/internal/v1` Controller(서비스 분리 후)는 `application/port/in`의 UseCase에 의존한다.
- 반환 타입은 `ResponseEntity<ApiResponse<XxxResponse>>`. Entity나 Result를 그대로 반환하지 않는다.
- 요청은 `@Valid` + Request record의 Bean Validation으로 형식만 검증한다. 비즈니스 규칙 검증은 domain에서 한다.
  - 검증은 `jakarta.validation.constraints.*`를 쓴다(`spring-boot-starter-validation` 포함됨).
  - **형식은 요청 DTO와 domain 양쪽에서 검증한다.** 요청 DTO는 틀린 필드를 FE에 알려주는 곳이고, domain은 같은 규칙을 불변식으로 한 번 더 지키는 안전망이다. 나이 제한·중복처럼 저장된 값이나 기준일이 필요한 판정은 domain에서만 한다. 예: `SignUpRequest`와 `User`·`Profile`.
  - 제약마다 `message`에 화면에 그대로 보여줄 문구를 준다. domain ErrorCode와 같은 문구를 쓰고, 값이 비었을 때(`@NotNull`)도 그 필드의 형식 문구를 쓴다. 다른 도메인의 상수는 import할 수 없으므로 정규식·문구가 두 곳에 적히는 것은 허용한다(`.claude/rules/package-structure.md` 도메인 간 참조의 값 상수 중복).
- **Request DTO를 만드는 기준**: 필드가 둘 이상이거나 검증 제약(`@NotBlank`, `@Size` 등)이 붙으면 만든다. 파라미터 하나를 그대로 넘길 뿐이면 만들지 않고 `@RequestParam`·`@PathVariable`로 받아 Command를 만든다. 필드 하나짜리 껍데기 record는 읽는 사람에게 아무것도 알려주지 않는다.
  - 단 **나중에 필드가 늘어날 것이 분명하면** 처음부터 만든다. 공개된 API의 요청 형태를 바꾸면 FE도 함께 고쳐야 한다.
  - 쿼리·경로 파라미터는 둘 이상이어도 검증 제약이 없으면 DTO 없이 `@RequestParam`·`@PathVariable`로 받는다. 필드 수 기준은 요청 본문(JSON)에 적용한다. 파라미터마다 이름·필수 여부·문서(`@Parameter`)가 메서드 시그니처에 바로 보이고, 생략과 빈 값 구분(아래 항목)도 그 자리에서 드러난다. 예: `PlaceCandidateController`(`categoryId`, `regionCode`), `KakaoWebhookController`(`user_id`, `referrer_type`), `ScheduleController`의 모집 중 목록(`categoryIds`, `regionCodes`, `startDate`, `endDate`, `cursor`, `size`. 범위를 벗어난 `size`는 제약 대신 잘라낸다. 아래 목록 절).
- 선택 쿼리 파라미터에서 생략과 빈 값을 다르게 다뤄야 하면 `@RequestParam(required = false) String`으로 받고 `defaultValue`를 주지 않는다. 생략은 `null`, `?x=`는 `""`로 들어온다. `defaultValue`를 주면 빈 값도 기본값으로 바뀌어 구분이 사라진다. 예: `PlaceCandidateController`의 `regionCode`(생략은 활성 지역 전체, 빈 값은 400 `INVALID_REGION`).
  - 빈 값의 뜻은 API마다 정해 문서에 적는다. 모집 중 일정 목록의 `categoryIds=`·`regionCodes=`는 400이 아니라 "맞는 일정 없음"(200, 빈 결과)이다. 모르는 값이 빈 결과인 것과 같게 맞췄다.
  - 여러 값을 받는 `List<…>` 파라미터는 빈 값이 `null`이나 빈 문자열 원소로 섞여 들어오므로 받은 뒤 걸러낸다. 예: `RecruitingScheduleFilter`.
- 인증 주체는 `@AuthenticationPrincipal UUID userId`로 받는다. 인증 필터가 principal에 userId(UUID)를 넣는다.
  - 공개 경로(토큰 없이 호출하는 엔드포인트)에서는 principal이 `"anonymousUser"`라 null이 들어온다. 타입이 맞지 않아도 예외 없이 null이므로 공개 경로에서는 쓰지 않는다.
  - 예외는 **선택 인증 API**다. 공개 경로지만 토큰이 있으면 응답이 달라진다. 이때만 받아서 쓰고, 유효한 토큰이면 userId, 토큰이 없거나 틀리거나 만료됐으면 null이다. null은 익명으로 다룬다. 인증 필터가 틀린 토큰도 그냥 통과시키므로 **틀린 토큰은 401이 아니라 익명**이 된다. 예: `UserController`의 닉네임 사용 가능 여부(토큰이 있으면 본인의 현재 닉네임을 사용 가능으로 본다), `ScheduleController`의 일정 상세(토큰이 있으면 보는 사람의 역할과 버튼이 달라진다).
  - 토큰 없이 호출하는 엔드포인트는 `SecurityConfig`에서 HTTP 메서드와 경로를 정확히 지정해 연다. `/api/v1/auth/**`처럼 prefix 전체를 열면 같은 prefix에 생긴 인증 필요 API까지 열린다. 우리 API에서는 `POST /api/v1/auth/tokens`(재발급)가 공개이고 `DELETE /api/v1/auth/tokens`(로그아웃)는 인증이 필요하다. 경로가 같고 메서드만 다르므로 경로만 지정하면 로그아웃·탈퇴가 열린다.
    - 하위 경로도 같다. `GET /api/v1/schedules`(모집 중 목록)는 공개이고 `GET /api/v1/schedules/participating`·`/hosted`는 인증이 필요하다. 그래서 `/api/v1/schedules` 정확한 경로 하나만 연다. 일정 상세(`GET /api/v1/schedules/{scheduleId}`)를 열 때 `/api/v1/schedules/*` 패턴을 쓰면 두 목록도 함께 열린다. `SecurityConfigTest`가 두 목록의 401을 단정하므로 그대로 두면 테스트가 깨진다.
    - 경로 변수 패턴(`/api/v1/schedules/{scheduleId}`)은 같은 prefix의 고정 경로(`/participating`·`/hosted`)와도 맞는다. 인증이 필요한 고정 경로를 정확히 지정해 `authenticated()`로 먼저 두고, 그 뒤에 경로 변수 패턴을 `permitAll()`로 연다. 매처는 위에서부터 처음 맞는 것이 적용되므로 순서가 바뀌면 두 목록이 열린다. 순서가 중요하다는 주석을 남긴다. `{scheduleId}` 한 단계 패턴은 하위 경로(`/participants` 등)에 걸리지 않는다.
  - 공개 경로는 API를 만들 때 하나씩 추가한다. 아직 없는 경로를 미리 적어두지 않는다.
- **어드민 경로**의 인증 주체는 `@AuthenticationPrincipal UUID adminId`다. 어드민 필터(`AdminAuthenticationFilter`)가 principal에 adminId를, 권한에 역할(`ROLE_MASTER`·`ROLE_ADMIN`)을 넣는다.
  - 역할은 토큰에 싣지 않고 **요청마다 DB에서 읽는다.** 토큰이 유효해도 그 어드민이 삭제되지 않았고 상태가 `APPROVED`일 때만 인증을 채우고, 아니면 익명이라 보호 경로는 401 `UNAUTHENTICATED`다. 이용 중지 같은 상태 변경이 access 만료를 기다리지 않고 반영된다.
  - principal이 UUID라 감사 컬럼(`created_by`·`updated_by`)에 adminId 문자열이 남고, 다른 도메인 컨트롤러가 admin을 import하지 않고 받는다.
  - 어드민 공개 경로는 `AdminSecurityConfig`에서 메서드와 정확한 경로로만 연다. 지금은 `POST /api/admin/v1/auth/signup`·`/login`·`/tokens` 셋이다. `DELETE /api/admin/v1/auth/tokens`(로그아웃)는 인증이 필요하다(사용자 재발급·로그아웃과 같은 이유). 선택 인증 어드민 API는 없다.
- 인증 필터는 공개 경로를 포함해 **자기 보안 체인의 모든 요청**에서 돈다(사용자 필터는 `/api/admin/**`를 뺀 모든 요청, 어드민 필터는 `/api/admin/**`). 필터는 토큰이 있을 때만 해석해 principal을 채우고, 없거나 틀리면 그냥 통과시킨다. 인가 판단은 `authorizeHttpRequests`가, 401 응답 생성은 `AuthenticationEntryPoint`가 한다.
  - 공개 경로에서도 principal이 채워져야 한다. 일정 상세처럼 비로그인도 보지만 보는 사람에 따라 응답이 달라지는 API가 있다(참여자면 "참여 취소", 아니면 "참여하기").
  - principal을 UUID로 두는 이유: 전용 타입을 auth에 두면 다른 도메인 컨트롤러가 auth를 import하게 된다. 역할 등 담을 정보가 늘면 principal 타입을 `common`에 둔다.

## 토큰과 쿠키

- 서비스 토큰은 JWT. **access token에는 userId(subject)만 담는다.** 닉네임·역할을 넣으면 발급 후 바뀐 값이 만료까지 틀린 채로 돌아다닌다.
- **refresh token에는 userId와 기기 세션 ID(`sid` claim)를 담는다.** 로그인·가입 완료마다 새 세션을 만들어, 로그아웃과 재발급(회전)을 기기 단위로 한다. 다른 기기에서 로그인해도 앞 기기는 끊기지 않는다.
  - sid는 `UUID.randomUUID()`(v4 난수)다. 시간순인 v7은 추측하기 쉬워 쓰지 않는다. 새 sid는 `TokenIssuer`가 만들고, 재발급은 refresh에서 꺼낸 sid를 그대로 쓴다.
  - refresh는 세션마다 Redis에 해시로 보관하고(키는 `.claude/rules/package-structure.md` Redis 절), 그 세션에 저장된 값과 일치해야 재발급한다. 재발급하면 같은 세션의 이전 refresh는 무효가 된다.
- access와 refresh는 같은 키로 서명한다. **종류는 sid 유무로 가른다.** sid가 있는 토큰은 access로 받지 않고(`INVALID_ACCESS_TOKEN`, 보호 경로는 401), sid가 없는 토큰은 refresh로 받지 않는다(`INVALID_REFRESH_TOKEN`). 14일짜리 refresh를 `Authorization: Bearer`로 보내 access처럼 쓰지 못하게 한다.
- 세션을 지우는 범위
  - 로그아웃(`DELETE /api/v1/auth/tokens`)은 인증이 필요하고 refresh 쿠키는 선택이다. 쿠키의 세션이 요청자(principal)의 것일 때만 그 세션 하나를 지운다. 쿠키가 없거나 해석할 수 없거나 다른 사용자의 토큰이면 아무것도 지우지 않고 200 + 쿠키 만료다. 재시도해도 안전하고, 남의 세션을 지울 수 없다. 쿠키 없이 로그아웃하면 서버 세션은 TTL(14일)로 사라진다.
  - 탈퇴, 그리고 재발급 때 사용자가 활성이 아닌 경우(탈퇴 이벤트 유실 안전망)는 그 사용자의 세션을 모두 지운다.
- **refresh token은 HttpOnly 쿠키, access token은 응답 본문**으로 내려준다.
  - 웹 클라이언트는 refresh를 자바스크립트가 읽을 수 없어야 한다. 본문으로 주면 FE가 localStorage에 담게 되고 XSS 한 번에 14일짜리 토큰이 털린다.
  - access는 짧게 살아 메모리 보관으로 충분하다. 새로고침하면 재발급 API로 복구한다.
  - 리다이렉트로 토큰을 넘길 때 **쿼리 파라미터에 담지 않는다.** 브라우저 히스토리·서버 로그·Referer에 남는다.
- **검증 실패는 한 코드로 묶는다.** 만료·위조·형식 오류를 구분해 알려주면 공격자에게 힌트가 된다. sid가 없는 이전 형식 refresh, 세션 불일치(교체됨·로그아웃됨·만료됨)도 같은 `INVALID_REFRESH_TOKEN`이다. 클라이언트는 어느 쪽이든 재발급을 시도하면 된다.
- 소셜 로그인은 **BE가 인가코드를 받아 토큰으로 교환**한다. 클라이언트 시크릿을 FE에 두지 않는다.
- 어드민 토큰은 위 규칙(subject만, sid로 종류 구분, 세션별 해시 보관·회전, 검증 실패 한 코드 `INVALID_ADMIN_REFRESH_TOKEN`)을 그대로 따르고 다음이 다르다.
  - 서명 키가 따로다(`ADMIN_JWT_SECRET`, `JWT_SECRET`과 다른 값). 한쪽 토큰은 다른 쪽에서 서명 검증부터 실패한다.
  - 쿠키는 `adminRefreshToken`, `Path=/api/admin/v1/auth`(`AdminCookieFactory`). 사용자 쿠키와 이름·경로가 다르다.
  - **세션은 로그인 시각부터 `admin.refresh-token-validity`(12시간)에 끝난다(절대 만료).** 재발급은 회전하되 만료를 늘리지 않는다. 사용자 refresh는 재발급마다 14일로 다시 발급된다(슬라이딩). access 만료도 세션 만료를 넘지 않는다(30분과 남은 시간 중 짧은 쪽). 구현은 `.claude/rules/package-structure.md` 보안 체인 절.

## 응답과 예외

- 반환 타입은 `ResponseEntity<ApiResponse<XxxResponse>>`.
- 봉투는 `{isSuccess, code, message, data, errors}`다.
  - **성공 여부는 `isSuccess`로 본다.** 값은 `ApiResponse`의 팩토리 메서드가 채우므로 HTTP 상태와 어긋나지 않는다.
  - **HTTP 상태 코드는 계속 정확하게 내려준다.** 200으로 고정하고 본문으로만 성공을 알리지 않는다. 캐시·모니터링·에러 추적이 상태 코드를 본다.
  - `code`와 `message`는 **실패 전용**이다. 성공 응답에서는 비어 있어 JSON에 나가지 않는다.
  - `errors`(`[{field, message}]`)는 **요청 DTO의 Bean Validation 필드 오류일 때만** 담긴다(`code`는 `INVALID_REQUEST`). JSON 변환 실패(enum 밖 값, 없는 날짜, 타입 오류), `BusinessException`, 404·405 등에는 담지 않는다. 정상적인 FE 입력으로는 나오지 않는 값이라 필드별 안내가 필요 없다.
- 성공: `ApiResponse.success(data)` 또는 `ApiResponse.success()`. **성공 코드 체계는 두지 않는다.**
  - 돌려줄 값이 없으면 반환 타입을 `ResponseEntity<ApiResponse<Void>>`로 두고 `ApiResponse.success()`를 200으로 준다. 본문은 `{"isSuccess":true}`다. 예: 로그아웃, 탈퇴, 일정 참여·참여 취소·전체 취소.
  - FE가 성공을 분기해야 하면 code가 아니라 `data`의 필드로 준다(예: `isNewUser`). code로 분기하면 코드가 하나 늘 때 FE가 조용히 깨진다.
  - 화면 문구는 FE가 가진다. 성공 메시지를 서버가 내려주면 문구 하나 바꾸는 데 서버 배포가 필요하다.
- 실패: `BusinessException(<Domain>ErrorCode)`를 던지고, `common.web`의 전역 예외 처리에서 `ApiResponse.error(...)`로 변환한다.
- `<Domain>ErrorCode`는 `domain/exception`에 두고 `ErrorCode`를 구현하며, `ErrorType`과 메시지를 가진다. `ErrorType` → `HttpStatus` 변환은 전역 예외 처리에서만 한다.
- 특정 Controller 전용 처리가 필요할 때만 `presentation/errorhandler`에 `@RestControllerAdvice(assignableTypes = XxxController.class)`를 둔다.

## 목록 (커서 페이지)

첫 사례는 일정 목록 3종(`ScheduleController`, SHI-172)이다. 이후 목록 API도 이 형식을 따른다.

- 응답 `data`는 `{items, nextCursor}`다. 다음 페이지가 없으면 `nextCursor`는 `null`, 맞는 것이 없으면 200에 `items = []`, `nextCursor = null`이다.
- 정렬은 (정렬 키, id) 오름차순이고, 다음 페이지는 keyset(마지막 항목의 (정렬 키, id)보다 뒤)으로 자른다. offset을 쓰지 않는다. 쿼리 작성은 `.claude/rules/package-structure.md` 인프라 규약의 목록 쿼리 절.
- 커서는 마지막 항목의 (정렬 키, id)를 URL-safe Base64(패딩 없음)로 인코딩한 불투명 문자열이다. 문자가 `[A-Za-z0-9_-]`뿐이라 FE가 인코딩 없이 쿼리에 붙인다. FE는 내용을 해석하지 않는다. 예: `ScheduleCursor`.
  - 해석할 수 없는 커서는 그 도메인 ErrorCode의 `INVALID_CURSOR`(400)다. FE는 커서 없이 처음부터 다시 부른다.
- 한 건 더(size + 1) 읽어 다음 페이지가 있는지 정한다. 남은 개수가 size와 딱 맞아도 마지막 페이지에서 `nextCursor = null`이 나가, FE가 빈 페이지를 한 번 더 부르지 않는다.
- `size`는 생략하면 기본값, 상한을 넘으면 상한, 1보다 작으면 1로 **잘라낸다.** 오류로 내지 않는다. 기본값·상한은 설정 record로 읽는다(`schedule.page-size-default: 20`, `schedule.page-size-max: 50`).
- 공개 목록 API에서 여러 값을 받는 필터는 빈 값·중복을 뺀 뒤 개수 상한을 두고, 넘으면 400 `INVALID_REQUEST`다. 값을 아주 많이 보내면 IN 조건의 바인드 파라미터가 DB 한도를 넘어 500이 나므로 요청 단계에서 끊는다. 예: 모집 중 일정 목록의 `categoryIds`·`regionCodes`는 각각 20개.

## 문서

FE는 작업할 때마다 이 저장소를 받아가 `docs/`를 본다. FE에 API를 전하는 것은 `docs/openapi.yaml`과 `docs/api-guide.md`이므로, 두 문서만 보고 화면을 만들 수 있어야 한다(이슈 관리 규칙은 `docs/linear-convention.md`로 따로 전한다). 문서에 없는 것은 FE가 추측하게 되고, 추측은 틀린다.

어드민 API는 따로 전한다: `docs/openapi-admin.yaml`과 `docs/admin-api-guide.md`. 사용자 문서(`docs/openapi.yaml`·`docs/api-guide.md`)에는 어드민 경로를 적지 않는다(`api-guide.md`에 어드민 문서 위치 안내 한 줄만 있다). 어드민 가이드에는 "어드민은 사용자 앱과 **반드시 별도 앱**(별도 Next.js 앱·별도 도메인)", "로그인 전에는 로그인·가입 말고 어떤 화면도 그리지 않는다"를 필수 요구로 둔다(사용자 합의, SHI-182). 아래 "빠짐없이 적는 것"은 어드민 문서에도 같다.

### 스펙 그룹과 공개 범위

- 스펙은 springdoc 그룹 둘이다(`OpenApiConfig`). `user` 그룹은 `/api/admin/**`를 뺀다. `admin` 그룹은 `openapi.admin-group-enabled=true`일 때만 생기고, **어느 프로파일에서도 켜지 않는다.** `OpenApiSpecTest`만 켠다.
- **서버가 내주는 문서(그룹 없는 `/v3/api-docs`·`/v3/api-docs.yaml`, swagger-ui, `user` 그룹)에는 어드민 경로·스키마가 나오지 않는다.** 일반 사용자는 어드민의 어떤 정보도 볼 수 없어야 한다(사용자 합의). dev도 swagger가 공개라 같다.
  - 그룹 없는 스펙은 `application.yaml`의 `springdoc.paths-to-exclude: /api/admin/**`가 거른다. 이 전역 값은 그룹에도 적용되므로 `OpenApiSpecTest`는 비워서 띄운다.
  - 그룹이 없을 때 `/v3/api-docs/admin`은 springdoc 예외로 500이 된다. `SecurityConfig`가 문서 경로 공개보다 먼저 `.requestMatchers("/v3/api-docs/admin").authenticated()`로 막아 토큰 없이는 401이다. 매처 순서가 바뀌면 다시 열린다.
  - `OpenApiConfigTest`가 이 공개 범위를 단정한다.
- 어드민 그룹의 bearer 설명은 어드민 로그인·재발급 토큰을 가리킨다(그룹 customizer가 `Components`를 새로 만들어 사용자 그룹과 공유하는 객체를 고치지 않는다).

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
    - 선택 인증 API(위 인증 주체 항목)에는 달지 않는다. 전역 요구를 지우면 swagger-ui가 토큰을 보내지 않아 토큰이 있을 때의 응답을 시험할 수 없다. 대신 description에 토큰은 선택이라는 것, 토큰이 있으면 무엇이 달라지는지, 틀린 토큰은 익명으로 처리한다는 것을 적는다. 예: `UserController`의 닉네임 사용 가능 여부.
- Request/Response record 필드에는 `@Schema(description, example)`을 단다.
  - **null이 될 수 있는 응답 필드는 description에 null이 되는 조건을 적는다.** springdoc이 스펙을 OpenAPI 3.1.0으로 만들며, `$ref`로 나가는 필드(중첩 record·enum)에는 `@Schema(nullable = true)`를 출력하지 않는다(`docs/openapi.yaml`에 `nullable`이 한 건도 없다). `types = {"object", "null"}`은 `$ref`와 AND로 묶여 null을 무효로 만드는 잘못된 스키마라 쓰지 않는다. 예: `ScheduleDetailResponse`의 `cancellation`, `minAgeGroup`·`maxAgeGroup`.
- `docs/api-guide.md` 에러 표에서 `INVALID_REQUEST`의 `errors`를 쓰라고 안내할 때는 **필드 검증 실패에만** 쓴다. 본문 없음·JSON 깨짐·경로 변수 형식 오류도 같은 `INVALID_REQUEST`지만 `errors`가 없다(응답과 예외 절). 둘이 같은 화면에서 날 수 있으면 행을 나눠 `errors` 유무와 보여줄 문구(`errors[0].message` / `message`)를 따로 적는다. 예: 일정 전체 취소.
- API를 바꾸면 같은 PR에서 `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 `docs/openapi.yaml`을 다시 만들어 커밋한다. 이 명령은 `docs/openapi-admin.yaml`도 함께 다시 만든다(테스트 안에서 어드민 그룹을 켠다). 코드와 다르면 빌드가 실패한다.
  - `docs/openapi.yaml`·`docs/openapi-admin.yaml`은 API를 바꾼 코드와 **같은 커밋**에 넣는다. `docs:` 커밋으로 떼면 그 사이 커밋에서 `OpenApiSpecTest`가 실패한다. `docs/api-guide.md`는 빌드가 대조하지 않으므로 따로 커밋해도 된다.
- 여러 호출에 걸친 흐름, 공통 규칙, 화면별로 쓰는 API가 바뀌면 `docs/api-guide.md`(어드민이면 `docs/admin-api-guide.md`)도 고친다. 이건 빌드가 잡지 못한다.
- swagger-ui와 스펙 경로는 SecurityConfig에서 공개한다. 운영 프로파일에서는 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`로 끈다. 어드민 스펙은 어느 프로파일에서도 공개하지 않는다(스펙 그룹과 공개 범위 절).

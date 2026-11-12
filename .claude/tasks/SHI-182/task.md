## 배경

- Linear SHI-182 "어드민 계정과 인증". 운영자가 데이터(우선 장소, SHI-184)를 직접 고칠 수 있는 어드민 API의 첫 단계로, 어드민 계정·가입·로그인·토큰·사용자 인증과의 분리·FE 전달 문서를 만든다.
- 작업 분할(Linear)
  - **SHI-182(이 작업)**: 계정·가입·로그인·토큰(재발급·로그아웃)·내 정보·보안 분리·FE 문서
  - SHI-183(다음): MASTER의 가입 승인·거절·이용 중지. **여기서 만들지 않는다.** 단 상태 모델과 "요청마다 어드민 상태 확인" 구조는 SHI-183이 값만 더하면 되게 둔다
  - SHI-184(그다음): 어드민 장소 수정 API(place 도메인). 그때 place 컨트롤러가 `/api/admin/v1/**` 아래에서 이 작업의 어드민 인증을 그대로 쓴다
- 사용자 원문(그대로): "ADMIN에서 직접 수정할 수 있도록 api를 만들어주고, admin은 fe전달문서에 반드시 다른 app으로 만들어서 사용해달라고 작성해야한다. nextjs니까 app아래에 새로운 adminapp을 만드는 것은 어떤지 이건 너의 추천안대로 하도록 하고, admin은 접속부터 로그인을 요구한다. 로그인을 하지 않으면 어떤 페이지도 넘어가지 않는다. master role은 직접 db에 밀어넣고 다른 admin은 회원가입을 통해 master_role이 수락하는 방식으로 진행한다. 일반사용자는 절대 admin에 접속할 수도 어떠한 정보도 볼 수 없도록 설계되어야 하며, admin 정보는 이름 아이디 비밀번호 소속팀 생년월일만 받는다. 인증은 master가 해주는 거라 별다른 인증은 없다. 아이디는 중복 금지다." "면밀히 검토후 진행한다."
- 사용자가 확정한 것
  - 소속팀은 정해진 목록(enum). "기획, 디자인, 개발FE, 개발BE, 마케팅 등 it 스타트업 기준 있을법한 소속으로"
  - 비밀번호 5회 연속 실패 시 15분 잠금(설정값)
  - MASTER 권한은 승인·거절 + 이용 중지(SHI-183)
  - 어드민 앱 구조는 "추천안대로"
- 기획 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)는 어드민을 다루지 않는다. 11.2 확장 지점에 "장소 검수 화면(운영 도구): place에 검수 API를 더하고 places를 BaseAuditEntity로 바꾼다"만 있다(SHI-184 몫).

### 코드에서 확인한 사실 (설계의 근거)

- 사용자 인증은 `auth/infrastructure/security/SecurityConfig` 하나의 `SecurityFilterChain`(securityMatcher 없음, `anyRequest().authenticated()`)과 `JwtAuthenticationFilter`(**`@Component`인 `OncePerRequestFilter`**)다. 필터는 토큰 해석에 실패하면 `SecurityContextHolder.clearContext()`를 부른다.
  - `FilterRegistrationBean`으로 끈 곳이 없다(`grep FilterRegistrationBean` 출력 없음). 그래서 Spring Boot가 이 필터를 **서블릿 필터로도 모든 URL에 자동 등록**한다. 지금은 보안 체인 안에서 먼저 돌아 `OncePerRequestFilter`가 두 번째 실행을 건너뛰므로 드러나지 않는다.
  - **어드민 체인을 따로 두면 이것이 버그가 된다.** `/api/admin/**` 요청은 사용자 필터가 보안 체인 안에서 돌지 않으므로, 체인이 끝난 뒤 서블릿 단계의 사용자 필터가 돌고, 어드민 토큰을 사용자 키로 해석하다 실패해 `clearContext()` → 컨트롤러의 `@AuthenticationPrincipal`이 null이 된다. 어드민 필터를 `@Component`로 만들면 반대로 사용자 경로에서 같은 일이 난다. GOAL 8이 이것을 막는다.
  - `@WebMvcTest`의 MockMvc도 컨텍스트의 Filter 빈을 서블릿 필터처럼 등록하므로 슬라이스 테스트로 재현된다.
- 사용자 토큰: `JwtTokenAdapter`(키 `jwt.secret` ← `JWT_SECRET`), access는 subject만, refresh는 `sid` claim. refresh는 Redis `refresh:{userId}:{sid}`에 SHA-256 해시, `sessions:{userId}` 집합, MULTI/EXEC(`RedisRefreshTokenStoreAdapter`). 쿠키는 HttpOnly·Secure·SameSite=None·`Path=/api/v1/auth`(`AuthCookieFactory`). 재발급 때 사용자가 유효한지 다시 확인한다(`ReissueTokenFacade`).
- 401 본문은 `JwtAuthenticationEntryPoint`가 `CommonErrorCode.UNAUTHENTICATED` 봉투로 쓴다. auth infrastructure라 admin이 import할 수 없다(ArchUnit: 다른 도메인은 port.in·dto·event·constants만).
- CORS: `common/config/CorsConfig`가 `cors.allowed-origins`(`CORS_ALLOWED_ORIGINS`) 하나를 `/api/**`에 건다. `UrlBasedCorsConfigurationSource`는 등록 순서대로 처음 맞는 패턴을 쓴다.
- OpenAPI: `common/config/OpenApiConfig`가 전역 `bearerAuth`를 건다. 그룹이 없고, `OpenApiSpecTest`(`@SpringBootTest`)가 `/v3/api-docs.yaml`과 `docs/openapi.yaml`을 대조한다. `build.gradle` test 태스크 inputs에 `docs/openapi.yaml`이 있다.
- 감사: `SecurityAuditorAware`가 principal의 `toString()`을 `created_by`·`updated_by`에 넣는다. 인증이 없으면 `SYSTEM`.
- `ErrorType` → HTTP: INVALID 400, UNAUTHENTICATED 401, FORBIDDEN 403, NOT_FOUND 404, CONFLICT 409, INTERNAL 500. 429에 해당하는 값은 없다.
- 이름 규칙은 사용자 가입과 같은 `^[가-힣a-zA-Z]{2,20}$`(`SignUpRequest`, `User.NAME_PATTERN`)가 있다.
- 마이그레이션은 develop 기준 V1~V10. 병행 중인 SHI-181(`wt-SHI-181`, 장소 적재)은 마이그레이션을 더하지 않는다(그 task.md에 "V11 없음"). SHI-181은 `snsg/.env.example`·`application.yaml`도 고치고 있어 머지 순서에 따라 이 두 파일이 충돌할 수 있다.
- 배포(`.github/workflows/deploy.yml`)는 ECS 태스크 정의의 환경변수를 저장소 밖에서 관리한다.

### 결정 (spec 단계에서 근거로 정함)

1. **새 도메인 `admin`.** 사용자(user)·사용자 인증(auth)과 테이블·토큰·키·보안 체인을 모두 따로 둔다. admin은 auth·user를 import하지 않고 auth도 admin을 import하지 않는다(도메인 간 의존 0, 순환 없음). 근거: "일반 사용자는 절대 admin에 접속할 수도 어떠한 정보도 볼 수 없도록", 언제든 분리할 수 있어야 한다는 `.claude/rules/package-structure.md`의 목표. 두 보안 설정에 공통인 것(401 진입점 등)은 각자 둔다(도메인 간 중복 허용).
2. **경로는 `/api/admin/v1/**`.** 엔드포인트는 다섯 개다.

   | 메서드·경로 | 인증 | 하는 일 | 성공 |
   |---|---|---|---|
   | `POST /api/admin/v1/auth/signup` | 공개 | 가입 신청. ADMIN·승인 대기로 저장. 토큰·쿠키를 주지 않는다 | 201, data 없음 |
   | `POST /api/admin/v1/auth/login` | 공개 | 아이디·비밀번호 로그인 | 200 `{accessToken}` + refresh 쿠키 |
   | `POST /api/admin/v1/auth/tokens` | 공개(쿠키) | 재발급(회전) | 200 `{accessToken}` + 새 refresh 쿠키 |
   | `DELETE /api/admin/v1/auth/tokens` | 어드민 | 로그아웃(이 기기 세션만) | 200, data 없음, 쿠키 만료 |
   | `GET /api/admin/v1/admins/me` | 어드민 | 내 정보 `{id, loginId, name, team, birthDate, role}` | 200 |

   공개는 위 세 개를 **메서드와 정확한 경로로만** 연다(`.claude/rules/api.md`). 그 밖의 `/api/admin/**`는 모두 어드민 인증이 필요하다. `me`를 두는 이유: 어드민 앱이 로그인 직후 누구인지·역할(MASTER 메뉴 표시, SHI-183)을 그려야 하고, 인증 경계를 검증할 대상 엔드포인트가 된다. 로그인은 `signup`과 같은 형태의 명사 하위 경로(`login`)로 둔다.
3. **보안 체인을 둘로 나눈다.** 어드민 체인은 `securityMatcher("/api/admin/**")`이고 사용자 체인보다 먼저 적용된다. 어드민 체인에는 어드민 토큰 필터만, 사용자 체인에는 사용자 토큰 필터만 있다. 두 토큰 필터 모두 서블릿 필터로 자동 등록되지 않는다(사실 절의 버그). 결과로
   - 사용자 토큰으로 `/api/admin/**`를 부르면 401 `UNAUTHENTICATED`, 어드민 토큰으로 사용자 보호 경로를 부르면 401 `UNAUTHENTICATED`.
   - 어드민 체인의 401 본문도 `{isSuccess:false, code:"UNAUTHENTICATED", ...}` 봉투다.
4. **서명 키를 따로 둔다: `ADMIN_JWT_SECRET`.** 사용자 키와 달라 한쪽 토큰은 다른 쪽에서 서명 검증부터 실패한다. access는 subject(adminId)만, refresh는 `sid` claim, 종류는 sid 유무로 가른다(사용자와 같은 방식, `.claude/rules/api.md` 토큰과 쿠키 절). 키 값은 채팅·문서·저장소에 남기지 않는다. 두 키가 같으면 분리가 깨지므로 `.env.example`과 문서에 "JWT_SECRET과 다른 값, HS256에 맞게 32바이트 이상"을 적는다.
5. **토큰 수명: access 30분, refresh 12시간(설정값).** refresh를 사용자(14일)보다 짧게 둔다. 근거: 데이터를 고칠 수 있는 권한이라 공용 PC에 하루를 넘겨 로그인이 남지 않게 한다. 12시간이면 하루 근무 중에는 다시 로그인하지 않는다.
6. **refresh는 사용자와 같은 방식이다.** HttpOnly·Secure·SameSite=None 쿠키, 이름 `adminRefreshToken`, `Path=/api/admin/v1/auth`. Redis에 해시로 기기 세션마다 보관하고(키는 사용자와 겹치지 않는 `admin-refresh:{adminId}:{sid}`, `admin-sessions:{adminId}`), 재발급하면 회전한다. 키 이름은 구현이 바꿔도 되지만 사용자 키(`refresh:`, `sessions:`)와 겹치면 안 된다.
7. **어드민 상태를 요청마다 확인한다.** 어드민 필터는 토큰이 유효해도 그 어드민이 삭제되지 않았고 상태가 `APPROVED`일 때만 인증을 채운다. 아니면 익명으로 두어 보호 경로는 401이다. 재발급도 `APPROVED`가 아니면 그 어드민의 세션을 모두 지우고 `INVALID_ADMIN_REFRESH_TOKEN`이다. 근거: SHI-183의 이용 중지가 access 만료(30분)를 기다리지 않고 즉시 반영돼야 하고, 어드민 요청은 수가 적어 행 하나 조회가 부담이 아니다. 역할도 DB에서 읽으므로 토큰에 역할을 넣지 않는다(`.claude/rules/api.md` "access token에는 subject만").
   - principal은 사용자와 같이 `UUID`(adminId)다. 그래서 감사 컬럼에 adminId 문자열이 남고(SHI-184 장소 수정), 다른 도메인 컨트롤러가 admin을 import하지 않고 `@AuthenticationPrincipal UUID adminId`로 받는다. 역할은 권한(authority)으로 싣는다(SHI-183의 MASTER 전용 경로가 쓴다).
8. **상태는 `PENDING`(승인 대기)·`APPROVED`(승인) 둘, 역할은 `MASTER`·`ADMIN` 둘.** 가입은 항상 `ADMIN`·`PENDING`이다. 요청 본문에 역할·상태가 있어도 무시한다. 거절(`REJECTED`)·이용 중지(`SUSPENDED`)는 SHI-183이 더한다. 거절을 상태로 남길지(아이디를 계속 막을지) 행을 지울지(아이디를 다시 쓸 수 있게 할지)는 SHI-183의 결정이라 여기서 값을 미리 만들지 않는다. 컬럼은 VARCHAR이고 CHECK 제약을 두지 않아 값을 더할 때 마이그레이션이 필요 없다. 요청마다 `APPROVED`만 통과시키므로(결정 7) 새 상태는 자동으로 막힌다.
9. **입력 형식** (요청 DTO와 domain 양쪽에서 검증, `.claude/rules/api.md`)
   - 아이디 `loginId`: `^[a-z][a-z0-9]{3,19}$`. 영문 소문자로 시작, 영문 소문자·숫자, 4~20자. **소문자만 받으므로 대소문자 무시 비교가 따로 필요 없다**(인덱스에 `lower()`를 쓰지 않는다).
   - 비밀번호: `^(?=.*[A-Za-z])(?=.*[0-9])[\x21-\x7E]{8,64}$`. 8~64자, 영문과 숫자를 각각 1자 이상, 공백 없는 ASCII 출력 문자(특수문자 허용, 한글 불가). ASCII만 받아 최대 64바이트라 **BCrypt 72바이트 한계를 넘지 않는다.** 저장은 BCrypt 해시(`password_hash`).
   - 이름: `^[가-힣a-zA-Z]{2,20}$`. 사용자 가입의 실명 규칙과 같다.
   - 소속팀 `team`: enum(결정 10). 없는 값은 JSON 변환 실패라 400 `INVALID_REQUEST`(errors 없음).
   - 생년월일 `birthDate`: 오늘(한국 시간)보다 이전. 사용자 가입과 같다. 나이 제한은 두지 않는다(요구 없음).
   - 문구는 화면에 그대로 보여줄 한국어로 주고 domain ErrorCode와 같은 문구를 쓴다.
10. **소속팀 목록(enum, `admin/domain/constants`)**: `PLANNING` 기획, `DESIGN` 디자인, `FRONTEND` 개발(FE), `BACKEND` 개발(BE), `MARKETING` 마케팅, `OPERATIONS` 운영, `BUSINESS` 사업개발, `DATA` 데이터, `MANAGEMENT` 경영지원. **목록을 주는 공개 API는 만들지 않는다.** 근거: 로그인 전에 부를 수 있는 어드민 경로를 늘리지 않는다("일반 사용자는 어떤 admin 정보도 볼 수 없다"와 같은 방향). 값은 OpenAPI enum과 FE 가이드의 표(코드·한글 이름)로 전하고 FE가 상수로 둔다. 팀이 바뀌면 BE·FE를 함께 고친다(드문 일).
11. **아이디 중복 금지.** `admins(login_id) WHERE is_deleted = FALSE` 부분 유니크 인덱스. 가입 때 먼저 확인해 409 `DUPLICATED_LOGIN_ID`를 내고, 동시 가입으로 인덱스를 위반해도 같은 409로 바꾼다(잠금 없이 경쟁이 실제로 나는 곳이라 바꾼다, `.claude/rules/package-structure.md` 동시 요청과 잠금 절). 가입 API로 아이디가 있는지 알 수 있게 되는 것은 중복 금지 요구상 피할 수 없다. 대신 로그인은 존재 여부를 드러내지 않는다(결정 12).
12. **로그인 판정과 오류 코드**
    - 순서: 잠금 확인 → 아이디로 조회 → 비밀번호 대조 → 상태 확인 → 실패 횟수 초기화 → 발급.
    - 아이디가 없음·비밀번호 틀림은 **같은 401 `INVALID_ADMIN_CREDENTIALS`**다. 아이디가 없어도 비밀번호 해시 대조를 한 번 해(더미 해시) 응답 시간으로도 존재가 드러나지 않게 한다.
    - 로그인 요청은 형식(정규식)을 검사하지 않는다. 비었으면 400 `INVALID_REQUEST`, 길이만 상한(각 64자)을 둔다. 형식이 틀린 값은 그냥 맞는 계정이 없는 것으로 401이다. 정책이 바뀌어도 기존 계정이 로그인할 수 있다.
    - 입력 비밀번호가 UTF-8로 72바이트를 넘으면(한글 64자 등) 해시 대조 없이 틀린 비밀번호로 본다(401, 500이 아니다).
    - 비밀번호가 맞은 뒤에만 상태를 본다. `PENDING`이면 403 `ADMIN_PENDING_APPROVAL`. 비밀번호를 모르면 승인 대기 여부도 알 수 없다.
13. **로그인 잠금: 같은 아이디(입력값 그대로)로 5회 연속 실패하면 15분 잠금(둘 다 설정값).**
    - 실패는 아이디 없음·비밀번호 틀림 모두 센다. 계정이 없는 아이디도 똑같이 잠기므로 잠금 응답으로 존재가 드러나지 않는다.
    - 1~4번째 실패는 401 `INVALID_ADMIN_CREDENTIALS`, 5번째 실패와 잠금 동안의 모든 시도(비밀번호가 맞아도)는 401 `ADMIN_LOGIN_LOCKED`. 잠금 동안의 시도는 비밀번호를 대조하지 않고 횟수·잠금 시간도 늘리지 않는다. 잠금은 5번째 실패 시각부터 15분 뒤 풀린다.
    - 로그인 성공(비밀번호 일치)하면 횟수를 0으로 되돌린다(PENDING으로 403이 나가는 경우도 비밀번호가 맞았으므로 되돌린다). 실패 사이가 15분 넘게 벌어지면 횟수가 사라져도 된다(Redis TTL, 정리 작업 없음).
    - 저장소는 Redis(TTL). 아이디는 정확히 같은 문자열로만 묶는다(대소문자를 바꿔도 계정이 맞지 않으므로 우회가 안 된다).
    - HTTP 상태를 401로 둔 이유: 로그인 실패의 한 종류이고, 429를 쓰려면 common `ErrorType`과 전역 변환을 늘려야 한다. FE는 code로 분기한다.
14. **오류 코드(`AdminErrorCode`, FE 계약)**

    | code | 상태 | 언제 |
    |---|---|---|
    | `DUPLICATED_LOGIN_ID` | 409 | 가입: 이미 쓰는 아이디 |
    | `INVALID_ADMIN_CREDENTIALS` | 401 | 로그인: 아이디 없음 또는 비밀번호 틀림 |
    | `ADMIN_LOGIN_LOCKED` | 401 | 로그인: 5회 연속 실패로 잠김 |
    | `ADMIN_PENDING_APPROVAL` | 403 | 로그인: 비밀번호는 맞지만 승인 대기 |
    | `INVALID_ADMIN_REFRESH_TOKEN` | 401 | 재발급: 쿠키 없음·만료·위조·회전됨·로그아웃됨·어드민이 APPROVED가 아님 |

    그 밖에 domain 안전망 코드(형식 위반, 400)는 구현이 이름을 정한다(예: `INVALID_LOGIN_ID`, `INVALID_ADMIN_PASSWORD`, `INVALID_ADMIN_NAME`, `INVALID_ADMIN_BIRTH_DATE`). 보호 경로의 토큰 없음·무효·상태 불일치는 공통 401 `UNAUTHENTICATED`다.
15. **`admins` 테이블(V11)은 `BaseAuditEntity`를 상속한다.** 가입은 인증이 없어 `created_by = SYSTEM`, SHI-183의 승인·이용 중지는 `updated_by = MASTER의 id`가 남는다(누가 승인했는지가 운영에서 궁금해지는 값, Entity 절의 Audit 기준). 컬럼: `id UUID`, `login_id VARCHAR(20)`, `password_hash VARCHAR(100)`, `name VARCHAR(20)`, `team VARCHAR(20)`, `birth_date DATE`, `role VARCHAR(20)`, `status VARCHAR(20)`, `created_at`, `updated_at`, `created_by VARCHAR(50)`, `updated_by VARCHAR(50)`, `is_deleted BOOLEAN`. 모두 NOT NULL.
16. **MASTER는 API로 만들지 않는다.** DB에 직접 INSERT하는 절차를 `README.md`에 적는다(BE 운영 절차라 FE용 `docs/`가 아니라 README). 해시는 `htpasswd`(BCrypt, `docker run --rm -it httpd:2.4-alpine htpasswd -nBC 10 <아이디>`로 대화형 입력 → 출력의 `:` 뒤가 해시)로 만든다. Spring `BCryptPasswordEncoder`는 `$2y$` 접두사를 받는다. 비밀번호를 명령줄 인자로 넘기지 않는다(셸 기록에 남는다). id는 수동 1건이라 `gen_random_uuid()`로 충분하다. 저장소에 실제 계정·해시를 넣지 않는다.
17. **CORS를 나눈다.** 어드민 경로(`/api/admin/**`)는 새 설정 `ADMIN_CORS_ALLOWED_ORIGINS`의 출처만, 사용자 경로는 기존 `CORS_ALLOWED_ORIGINS`만 허용한다. 근거: refresh 쿠키가 SameSite=None이라, 사용자 앱 출처까지 어드민 경로를 credentials로 부를 수 있으면 사용자 앱의 XSS 한 번으로 그 브라우저의 어드민 세션이 재발급될 수 있다. 어드민 출처 기본값은 빈 목록(브라우저 호출 전부 거부)이다. 설정이 빠졌을 때 열리는 쪽이 아니라 닫히는 쪽으로 둔다.
18. **OpenAPI를 나눈다.** 사용자용 `docs/openapi.yaml`에는 어드민 경로가 나오지 않고 **내용이 지금과 같다**. 어드민 스펙은 `docs/openapi-admin.yaml`에 따로 만들고 `OpenApiSpecTest`가 두 파일을 모두 대조한다(`OPENAPI_UPDATE=true`로 둘 다 다시 만든다). 어드민 스펙의 bearer 설명은 어드민 로그인·재발급 토큰을 가리킨다. swagger-ui는 지금처럼 local·dev에서만 열리고 prod는 꺼져 있다(`application-prod.yaml`). dev swagger에 어드민 API 형태가 보이는 것은 허용한다(데이터가 아니고 모든 호출이 어드민 인증을 요구한다).
19. **어드민 앱은 FE 저장소 안의 별도 Next.js 앱(모노레포의 `apps/admin` 등), 별도 도메인(예: `admin.` 하위 도메인)·별도 배포다.** 사용자 앱의 `app/admin` 라우트로 만들지 않는다. 근거: 사용자 앱 번들·도메인에 어드민 코드·경로가 섞이지 않아 일반 사용자가 어드민 화면 코드조차 받지 않고, CORS 출처를 어드민 도메인 하나로 좁힐 수 있으며(결정 17), 배포·접근 제한을 따로 걸 수 있다. FE 문서(`docs/admin-api-guide.md`)에 **필수 요구**로 적는다.
20. **"로그인 전에는 어떤 페이지도 넘어가지 않는다"의 FE 구현 방식.** refresh 쿠키는 API 도메인의 HttpOnly 쿠키라 어드민 앱의 Next.js middleware(다른 도메인)는 그 쿠키를 볼 수 없다. 그래서 FE 문서에 이렇게 적는다: 로그인·가입 화면을 뺀 모든 화면은 공통 레이아웃의 인증 확인(앱 진입 시 `POST /api/admin/v1/auth/tokens`, 성공 전에는 아무 내용도 그리지 않음, 401이면 로그인 화면으로 대체)을 통과해야 그려진다. 데이터는 서버 컴포넌트·SSR로 미리 가져오지 않는다(토큰 없이 그려질 자리를 만들지 않는다). middleware는 로그인 여부 판단에 쓰지 않는다. **실제 차단은 BE가 한다**: 공개 세 개 외의 모든 어드민 API가 어드민 토큰을 요구한다. 로그인 화면에서 가입 화면으로는 갈 수 있다(가입 신청은 로그인 전에 해야 한다).

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-182` (브랜치 `feat/SHI-182-admin-auth`, 베이스 `e1d0482`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리(`wt-SHI-181`), 기획 폴더 `C:\Users\ks\nss\dev\snsg-be\planning`은 읽기만 한다
- `snsg/.env`는 복사돼 있다. `ADMIN_JWT_SECRET`·`ADMIN_CORS_ALLOWED_ORIGINS`는 없다. 구현 단계에서 **이 워크트리의 `snsg/.env`에만** 더한다
  - `ADMIN_JWT_SECRET`: 로컬에서 난수로 만든다(예: Bash `openssl rand -base64 48`의 출력을 파일에 바로 덧붙이고 화면에 찍지 않는다). 기존 `JWT_SECRET`과 다른 값. 값을 보고·로그·커밋에 남기지 않는다
  - `ADMIN_CORS_ALLOWED_ORIGINS=http://localhost:3001` (어드민 앱 로컬 개발 주소 가정. FE 가이드에 "다른 주소면 알려달라"로 적는다)
  - `snsg/.env`는 gitignore 대상이다. 스테이징되지 않았는지 확인한다
- DB·Redis 테스트는 로컬 Docker 컨테이너(`snsg-postgres`, `snsg-redis`)에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다
- **DB·Redis·8080 포트는 모든 워크트리가 공유한다. 지금 SHI-181 워크트리가 DB 테스트를 돌리고 있다.** 테스트·`bootRun`은 오케스트레이터가 허락한 시점에만 이 워크트리에서 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). V11을 공유 DB에 적용해도 V11이 없는 SHI-181 쪽 Flyway 검증은 통과한다(DB에만 있는 마이그레이션은 future로 무시). 문제가 생기면 멈추고 보고한다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. `&&`는 PowerShell에서 동작하지 않는다. 아래 검증 명령은 Bash 기준이고 워크트리 루트(`C:\Users\ks\nss\dev\snsg-be\wt-SHI-182`)에서 시작한다
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다

## GOAL

1. 마이그레이션 `snsg/src/main/resources/db/migration/V11__create_admin.sql`이 `admins`를 만든다. 컬럼은 결정 15대로 모두 NOT NULL, PK, `(login_id) WHERE is_deleted = FALSE` 부분 유니크 인덱스. role·status에 CHECK 제약이 없다. 실제 계정 INSERT가 없다. 기존 V1~V10은 바뀌지 않는다. 엔티티는 `BaseAuditEntity`를 상속하고 `ddl-auto: validate`를 통과한다.
   - 검증: `git diff --name-only origin/develop -- snsg/src/main/resources/db/migration` → `snsg/src/main/resources/db/migration/V11__create_admin.sql` 한 줄
   - 검증: `grep -niE "insert|check" snsg/src/main/resources/db/migration/V11__create_admin.sql` → 출력 없음(주석에도 쓰지 않는다)
   - 검증: 14번의 전체 빌드(DB 슬라이스 테스트가 validate로 뜬다) 통과

2. 가입 규칙. 순수 JUnit(domain)과 Mockito(application) 테스트가 아래를 단정한다.
   - 정상 가입 → 역할 `ADMIN`, 상태 `PENDING`, 저장된 비밀번호는 입력 원문이 아니라 해시(해시 포트·인코더를 거친 값)
   - 결정 9의 형식 위반(아이디: 대문자 포함·숫자로 시작·3자·21자, 비밀번호: 7자·65자·숫자 없음·영문 없음·공백 포함·한글 포함, 이름: 1자·숫자 포함, 생년월일: 오늘·미래·null) → 각 domain 안전망 `AdminErrorCode`(400). 비밀번호 형식이 틀리면 해시를 만들지 않는다. 경계 통과: 아이디 4자·20자, 비밀번호 8자·64자, 이름 2자·20자, 생년월일 어제
   - 이미 쓰는 아이디 → `DUPLICATED_LOGIN_ID`, 저장하지 않는다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우를 다루는 테스트 포함

3. 아이디 유니크가 실제 PostgreSQL에서 지켜진다(`@DataJpaTest`, `replace = NONE`). 같은 `login_id`로 삭제되지 않은 행을 둘 저장하면 `BusinessException(DUPLICATED_LOGIN_ID)`(500이 아니다), 앞 행이 삭제(`is_deleted = true`)돼 있으면 저장된다. 테스트 행은 무작위 아이디로 넣는다(공유 DB).
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

4. 로그인 흐름(결정 12·13). Mockito 테스트가 단정한다.
   - 맞는 아이디·비밀번호, `APPROVED` → access·refresh 발급, 새 세션 저장, 실패 횟수 초기화
   - 아이디 없음 → `INVALID_ADMIN_CREDENTIALS`, **비밀번호 해시 대조가 한 번 불린다**(더미 해시), 실패 횟수가 늘어난다
   - 비밀번호 틀림 → `INVALID_ADMIN_CREDENTIALS`, 실패 횟수가 늘어난다
   - 비밀번호가 맞고 `PENDING` → `ADMIN_PENDING_APPROVAL`, 토큰을 발급하지 않는다, 실패 횟수 초기화
   - 5번째 연속 실패 → `ADMIN_LOGIN_LOCKED`. 잠긴 상태에서는 비밀번호가 맞아도 `ADMIN_LOGIN_LOCKED`이고 계정 조회·해시 대조·실패 기록을 하지 않는다
   - 입력 비밀번호가 UTF-8 72바이트 초과(예: 한글 64자) → `INVALID_ADMIN_CREDENTIALS`, 예외가 새지 않는다
   - 최대 실패 횟수와 잠금 시간은 설정값에서 읽는다(테스트에서 값을 바꿔 넣어 확인)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

5. 로그인 실패 기록 Redis adapter를 실제 로컬 Redis로 테스트한다(`@SpringBootTest`, 고정 키 사용, `@AfterEach`에서 이름으로 삭제). 실패를 기록하면 횟수가 늘고 키에 TTL이 걸려 있다. 설정 횟수에 이르면 잠김으로 보이고 잠금 키(또는 횟수 키)의 TTL이 잠금 시간 이하다. 초기화하면 잠김이 아니고 횟수가 0이다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

6. 토큰 분리. Spring 없는 단위 테스트가 실제 두 JWT adapter(사용자 `JwtTokenAdapter`와 어드민 adapter, 서로 다른 키)로 단정한다.
   - 어드민 access는 어드민 adapter에서 adminId로 해석된다
   - 사용자 access·refresh는 어드민 adapter의 access·refresh 해석에서 모두 실패한다. 어드민 access·refresh는 사용자 `JwtTokenAdapter.parseUserId`·`parseRefreshToken`에서 모두 실패한다
   - 어드민 refresh는 어드민 access 해석에서 실패하고, 어드민 access는 어드민 refresh 해석에서 실패한다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

7. 재발급·로그아웃(결정 5·6·7). Mockito 테스트와 Redis 테스트가 단정한다.
   - 저장된 세션과 일치하는 refresh + `APPROVED` → 같은 sid로 새 토큰, 이전 refresh는 더 이상 일치하지 않는다(회전)
   - refresh 해석 실패·세션 불일치 → `INVALID_ADMIN_REFRESH_TOKEN`
   - 어드민이 없거나·삭제됐거나·`PENDING`이면 → 그 어드민의 세션 모두 삭제 + `INVALID_ADMIN_REFRESH_TOKEN`
   - 로그아웃: 쿠키의 세션이 요청자의 것일 때만 그 세션 하나를 지운다. 쿠키 없음·해석 불가·다른 어드민의 토큰이면 아무것도 지우지 않는다
   - Redis 어드민 refresh 저장소(실제 로컬 Redis, 고정 키, `@AfterEach` 이름으로 삭제): 저장 시 값이 원문이 아니라 해시이고 두 키에 TTL(refresh 수명)이 있다. 키가 사용자 키 접두사(`refresh:`, `sessions:`)로 시작하지 않는다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

8. 인증 경계. `@WebMvcTest`에 **두 보안 설정과 두 토큰 필터·진입점을 모두 올리고**(실제 앱처럼 컨텍스트에 둘 다 있는 상태), 토큰 해석 포트와 어드민 상태 조회만 mock으로 바꾼 테스트가 단정한다. 대상 컨트롤러는 어드민 컨트롤러들과 사용자 `UserController`(내 프로필 조회)다.
   - 토큰 없이: `POST /api/admin/v1/auth/signup`·`/login`·`/tokens`는 보안에 막히지 않는다(401 `UNAUTHENTICATED`가 아니다). `GET /api/admin/v1/admins/me`, `DELETE /api/admin/v1/auth/tokens`, 없는 경로 `GET /api/admin/v1/anything` → 401, 본문 `code` = `UNAUTHENTICATED`
   - 사용자 access 토큰(사용자 포트는 userId를 돌려주고 어드민 포트는 거부)으로 `GET /api/admin/v1/admins/me` → 401 `UNAUTHENTICATED`, 어드민 조회가 불리지 않는다
   - 어드민 access 토큰 + 상태 `APPROVED`로 `GET /api/admin/v1/admins/me` → 200, 내 정보 조회가 **그 adminId로** 불린다(principal이 컨트롤러까지 살아 있다)
   - 어드민 access 토큰이지만 그 어드민이 `PENDING`이거나 없으면 → 401 `UNAUTHENTICATED`
   - 어드민 access 토큰(사용자 포트는 거부)으로 `GET /api/v1/users/me` → 401 `UNAUTHENTICATED`
   - 사용자 access 토큰으로 `GET /api/v1/users/me` → 200, 사용자 조회가 **그 userId로** 불린다(어드민 필터가 사용자 인증을 지우지 않는다)
   - 기존 `auth/infrastructure/security/SecurityConfigTest`도 통과한다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

9. 어드민 API 요청·응답(`@WebMvcTest`, 서비스·facade mock).
   - 가입 성공 → 201, `{"isSuccess":true}`, `Set-Cookie` 없음, 본문에 토큰 없음. 형식 위반(결정 9의 필드 각각) → 400 `INVALID_REQUEST`, `errors`에 그 field, facade가 불리지 않는다. 409 `DUPLICATED_LOGIN_ID` 전달
   - 로그인 성공 → 200 `data.accessToken`, `Set-Cookie`에 `adminRefreshToken`(HttpOnly, Secure, SameSite=None, `Path=/api/admin/v1/auth`, Max-Age = refresh 수명 초). 아이디나 비밀번호가 비었으면 400 `INVALID_REQUEST`. 형식이 틀린 아이디(예: 대문자)는 400이 아니라 facade로 넘어간다
   - 재발급: 쿠키 없음 → 401 `INVALID_ADMIN_REFRESH_TOKEN`, 성공 → 200 `data.accessToken` + 새 쿠키
   - 로그아웃 → 200, `adminRefreshToken` 만료 쿠키(Max-Age=0, 같은 Path)
   - `me` → 200 `data`에 `id, loginId, name, team, birthDate, role`, 비밀번호 해시·상태 필드 없음
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

10. CORS 분리(결정 17). Spring 없는 단위 테스트가 실제 `CorsConfig`로 만든 `CorsConfigurationSource`에 요청 경로를 넣어 단정한다: `/api/admin/v1/auth/login`의 허용 출처는 어드민 출처만(사용자 출처 없음), `/api/v1/users/me`의 허용 출처는 사용자 출처만(어드민 출처 없음), 어드민 출처가 빈 목록이면 `/api/admin/**`에 허용 출처가 없다. 둘 다 credentials 허용.
    - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.common.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

11. OpenAPI 분리(결정 18).
    - 사용자 스펙은 바뀌지 않았다. 검증: `git diff --quiet origin/develop -- docs/openapi.yaml; echo $?` → `0`
    - 어드민 스펙에는 어드민 경로 네 개(`/api/admin/v1/auth/signup`, `/api/admin/v1/auth/login`, `/api/admin/v1/auth/tokens`, `/api/admin/v1/admins/me`)만 있다. 검증: `grep -E "^  /" docs/openapi-admin.yaml` → 그 네 줄만
    - 검증: `grep -c "/api/admin" docs/openapi.yaml` → `0`
    - 어드민 스펙에 결정 14의 다섯 code와 결정 10의 아홉 팀 값이 모두 나온다. 검증: `for c in DUPLICATED_LOGIN_ID INVALID_ADMIN_CREDENTIALS ADMIN_LOGIN_LOCKED ADMIN_PENDING_APPROVAL INVALID_ADMIN_REFRESH_TOKEN PLANNING DESIGN FRONTEND BACKEND MARKETING OPERATIONS BUSINESS DATA MANAGEMENT; do grep -q "$c" docs/openapi-admin.yaml || echo "MISSING $c"; done` → 출력 없음
    - `OpenApiSpecTest`가 두 파일을 모두 대조한다. 검증: `cd snsg && OPENAPI_UPDATE=true ./gradlew cleanTest test --tests '*OpenApiSpecTest'` 실행 후 `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
    - 검증: `grep -n "openapi-admin.yaml" snsg/src/test/java/com/shineunsigan/snsg/docs/OpenApiSpecTest.java snsg/build.gradle` → 테스트가 어드민 스펙을 읽어 생성 결과와 비교하는 줄(파일이 없거나 다르면 실패), `build.gradle` test 태스크 inputs에 이 파일을 선언한 줄이 나온다

12. FE 문서 `docs/admin-api-guide.md`가 있다. `docs/api-guide.md`의 형식(공통 규칙·인증·에러 표·화면별 API 표·로컬 실행)을 따르고, 어드민 스펙(`docs/openapi-admin.yaml`)과 함께 이 두 문서만으로 어드민 앱의 로그인·가입·재발급·로그아웃·내 정보 화면을 만들 수 있다. 반드시 들어가는 것:
    - **필수 요구**: 어드민은 사용자 앱과 **반드시 별도 앱**(FE 저장소의 별도 Next.js 앱, 예: `apps/admin`)으로 만들고 별도 도메인에 배포한다. 사용자 앱의 라우트(`app/admin` 등)로 만들지 않는다. 그 이유
    - **필수 요구**: 접속부터 로그인을 요구한다. 로그인·가입 화면 말고는 인증 확인(재발급 성공) 전에 어떤 화면도 그리지 않는다. 결정 20의 구현 방식(공통 레이아웃 가드, SSR로 데이터 미리 가져오지 않기, middleware로는 API 도메인 쿠키를 볼 수 없다는 것, 실제 차단은 BE)
    - 토큰 보관(access 메모리, refresh HttpOnly 쿠키 `adminRefreshToken`, `Path=/api/admin/v1/auth`, credentials 포함), 401 처리(재발급 한 번, 재발급은 한 번에 하나), 사용자 앱 토큰과 섞이지 않는다는 것
    - 가입: 필드별 규칙(결정 9)과 문구, 소속팀 표(코드·한글 이름 아홉 개), 가입 후 MASTER 승인 전까지 로그인할 수 없다는 것
    - 로그인: 오류 코드별 화면 처리, 잠금(5회·15분)
    - 결정 14 에러 표(HTTP·code·조건·화면에서 할 일)와 예시 JSON
    - 화면별 API 표, 재시도해도 되는지
    - 로컬 개발: 어드민 앱 로컬 주소(`http://localhost:3001` 가정, 다르면 알려달라), 승인된 계정이 필요하면 BE에 요청(승인 API는 아직 없다)
    - 승인·거절·이용 중지 API는 다음 작업에서 생긴다는 것
    - 검증: `for s in "반드시 별도" "apps/admin" "/api/admin/v1/auth/signup" "/api/admin/v1/auth/login" "/api/admin/v1/auth/tokens" "/api/admin/v1/admins/me" adminRefreshToken DUPLICATED_LOGIN_ID INVALID_ADMIN_CREDENTIALS ADMIN_LOGIN_LOCKED ADMIN_PENDING_APPROVAL INVALID_ADMIN_REFRESH_TOKEN UNAUTHENTICATED PLANNING DESIGN FRONTEND BACKEND MARKETING OPERATIONS BUSINESS DATA MANAGEMENT middleware "localhost:3001"; do grep -q -- "$s" docs/admin-api-guide.md || echo "MISSING $s"; done` → 출력 없음
    - `docs/api-guide.md`에는 어드민 API가 `docs/admin-api-guide.md`·`docs/openapi-admin.yaml`에 따로 있다는 안내 한 곳만 더한다. 검증: `git diff origin/develop -- docs/api-guide.md | grep -c "^+[^+]"` → 5 이하, 더한 줄에 `admin-api-guide.md`가 있다

13. 설정·비밀값
    - `snsg/.env.example`에 `ADMIN_JWT_SECRET=`와 `ADMIN_CORS_ALLOWED_ORIGINS=`가 **값 없이** 있고, 주석으로 `ADMIN_JWT_SECRET`은 `JWT_SECRET`과 다른 값이어야 한다는 것이 있다. 검증: `grep -nE "^ADMIN_(JWT_SECRET|CORS_ALLOWED_ORIGINS)=" snsg/.env.example` → 두 줄, 둘 다 `=` 뒤가 비어 있다
    - `README.md`에 MASTER 계정을 DB에 넣는 절차(결정 16: 해시 만드는 법, INSERT 템플릿 — 값은 자리표시자, 역할 `MASTER`·상태 `APPROVED`·`created_by`·`updated_by` `SYSTEM`, 운영 환경 변수 `ADMIN_JWT_SECRET`·`ADMIN_CORS_ALLOWED_ORIGINS`를 ECS 태스크 정의에 더해야 한다는 것)가 있다. 검증: `grep -n "MASTER" README.md` → 절차 절이 나온다
    - 실제 BCrypt 해시나 비밀값이 커밋 대상에 없다. 검증: `git diff --staged | grep -E '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}'` → 출력 없음. `git diff --staged --name-only | grep -E '(^|/)\.env$'` → 출력 없음
    - 설정 키(토큰 수명 30m·12h, 최대 실패 5, 잠금 15m)는 `application.yaml`에 있고 코드에 숫자로 박혀 있지 않다(결정 5·13). 검증: `grep -nE "12h|15m" snsg/src/main/resources/application.yaml` → 어드민 refresh 수명과 잠금 시간 줄이 나온다

14. 전체 빌드가 통과한다(`ArchitectureTest` 포함: admin과 다른 도메인 사이 의존이 계약 밖으로 향하지 않고, 순환이 없고, facade에 `@Transactional`이 없고, Command·Query 서비스가 이벤트 포트 아닌 `port/out`에 의존하지 않는다).
    - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
    - 검증: `grep -rlE "import com\.shineunsigan\.snsg\.(auth|user|schedule|place)\." snsg/src/main/java/com/shineunsigan/snsg/admin` → 출력 없음, `grep -rl "import com.shineunsigan.snsg.admin." snsg/src/main/java | grep -v "/snsg/admin/"` → 출력 없음

15. 범위 밖 파일을 고치지 않는다.
    - 검증: `git diff --staged --name-only | grep -vE '^(snsg/src/main/java/com/shineunsigan/snsg/admin/|snsg/src/test/java/com/shineunsigan/snsg/admin/|snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/|snsg/src/test/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfigTest\.java$|snsg/src/main/java/com/shineunsigan/snsg/common/config/(CorsConfig|CorsProperties|OpenApiConfig)\.java$|snsg/src/test/java/com/shineunsigan/snsg/common/config/|snsg/src/test/java/com/shineunsigan/snsg/docs/OpenApiSpecTest\.java$|snsg/src/main/resources/db/migration/V11__create_admin\.sql$|snsg/src/main/resources/application\.yaml$|snsg/\.env\.example$|snsg/build\.gradle$|docs/(admin-api-guide\.md|openapi-admin\.yaml|api-guide\.md)$|README\.md$|\.claude/tasks/SHI-182/task\.md$)'` → 출력 없음

## 비목표

- 승인·거절·이용 중지, 대기 목록, 비밀번호 변경·초기화, 어드민 정보 수정, 어드민 탈퇴·삭제 API는 만들지 않는다(SHI-183 이후). `REJECTED`·`SUSPENDED` 값도 만들지 않는다(결정 8)
- 어드민 장소 수정 API, `places`의 `BaseAuditEntity` 전환은 하지 않는다(SHI-184)
- 소속팀 목록 공개 API, 아이디 중복 확인 API는 만들지 않는다(결정 10·11, 요구 없음)
- 가입 남용 방지(가입 횟수 제한, 캡차), IP 단위 잠금, 네트워크 단 접근 제한(IP 허용 목록)은 하지 않는다
- user·schedule·place 도메인의 main 코드, 기존 마이그레이션 V1~V10, `GlobalExceptionHandler`·`ErrorType`·`SecurityAuditorAware`·`Auditor`는 고치지 않는다
- 사용자 인증의 동작(공개 경로 목록, 토큰 형식, 쿠키, 재발급 규칙)은 바꾸지 않는다. `auth/infrastructure/security`는 사용자 필터가 서블릿 필터로 자동 등록되지 않게 하는 데 필요한 만큼과 체인 순서를 위한 것만 고친다
- 기존 테스트의 단정을 고치지 않는다. `SecurityConfigTest`는 컨텍스트를 띄우는 데 필요한 `@Import`·`@MockitoBean`·설정 조정만 허용한다
- `CLAUDE.md`, `.claude/rules/*`, `.claude/skills/*`는 고치지 않는다. FE 전달 문서 목록(CLAUDE.md)과 `api.md`의 URL·문서 절 갱신은 문서 최신화 단계(agent4) 몫이다
- 메인 작업 디렉터리·다른 워크트리의 `snsg/.env`는 고치지 않는다. 머지 후 그곳에 `ADMIN_JWT_SECRET`·`ADMIN_CORS_ALLOWED_ORIGINS`가 필요하다는 것은 보고에 적는다
- 기획 폴더의 문서는 고치지 않는다
- 판단이 갈리는 경우
  - 머지 직전 develop에 V11이 이미 있으면 이 파일 번호를 다음 번호로 올리고 GOAL 1·15의 파일 이름도 그 번호로 읽는다
  - SHI-181이 먼저 머지돼 `application.yaml`·`.env.example`이 충돌하면 SHI-181 쪽 내용을 살리고 이 작업의 키를 더한다
  - springdoc 그룹 등으로 나누다 `docs/openapi.yaml`이 내용상 바뀌어야만 한다면(GOAL 11 첫 항목이 0이 아니게 되면) 억지로 맞추지 말고 무엇이 왜 바뀌는지 적어 멈추고 보고한다
  - `htpasswd`의 `$2y$` 해시를 쓰는 대신 다른 생성 방법이 더 확실하다고 판단되면 README에 그 방법을 쓰되, 실제 해시·비밀번호를 저장소에 남기지 않는다

## 제약

- 패키지·계층·포트·DTO·Entity·삭제·마이그레이션·Redis·설정 규칙은 `.claude/rules/package-structure.md`를 따른다(특히 "Service와 Facade", "Port"의 adapter 빈 이름 충돌, "Redis"의 TTL·해시·MULTI/EXEC, "설정"의 prefix 규칙, "동시 요청과 잠금"의 유니크 위반 변환)
- API·요청 검증·토큰과 쿠키·응답·문서 규칙은 `.claude/rules/api.md`를 따른다
- 테스트는 `.claude/rules/testing.md`를 따른다. 테스트는 원래 코드와 별도 커밋이다. DB 슬라이스 구성이 늘면 `./gradlew cleanTest build`로 전체를 다시 돌려 연결 수 문제를 확인한다. Redis 테스트는 고정 키를 이름으로 지운다(패턴 삭제 금지)
- `docs/openapi.yaml`·`docs/openapi-admin.yaml`은 API를 바꾼 코드와 같은 커밋에 넣는다
- 비밀값(`ADMIN_JWT_SECRET` 값, 비밀번호, 해시)을 보고·로그·문서·커밋에 남기지 않는다
- 임시 파일은 세션 스크래치패드에 둔다(`.claude/rules/temp-files.md`). 워크트리에 남기지 않는다
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 제안(구현이 바꿔도 된다)
  - 패키지: `admin/domain/model/Admin`, `admin/domain/constants/{AdminRole, AdminStatus, AdminTeam}`, `admin/domain/exception/AdminErrorCode`, `admin/domain/repository/AdminRepository`, `admin/application/{AdminCommandService, AdminQueryService, SignUpAdminFacade, AdminLoginFacade, AdminReissueTokenFacade, AdminProperties, AdminTokenIssuer}`, `admin/application/port/out/{AdminTokenPort, AdminRefreshTokenStorePort, LoginAttemptPort, PasswordHashPort}`, `admin/infrastructure/{persistence, jwt, redis, security}`, `admin/presentation/{AdminAuthController, AdminController, AdminCookieFactory}`
  - 비밀번호 해시·대조는 `port/out`(`PasswordHashPort`) ← infrastructure의 `BCryptPasswordEncoder` adapter. 그러면 가입(해시 → 저장)과 로그인(Redis·해시·토큰)은 facade 기준에 들고, 저장·조회는 Command·Query 서비스가 맡는다(`.claude/rules/package-structure.md` Service와 Facade 절). 더미 해시는 adapter가 기동 때 한 번 만들어 두면 된다
  - 어드민 필터의 상태 확인은 `AdminQueryService`(삭제되지 않았고 APPROVED인 어드민의 역할 조회)를 부른다. infrastructure → 같은 도메인 application은 허용된다
  - 두 토큰 필터가 서블릿 필터로 자동 등록되지 않게 하는 방법: 필터를 `@Component`로 두지 않고 각 보안 설정 안에서 만들어 체인에만 넣거나, `FilterRegistrationBean(filter).setEnabled(false)`. `@WebMvcTest`의 MockMvc도 컨텍스트의 Filter 빈을 등록하므로 GOAL 8 테스트로 차이가 드러난다
  - 체인 순서: 어드민 체인에 `@Order(1)`(또는 사용자 체인보다 앞선 값)과 `securityMatcher("/api/admin/**")`. 사용자 체인은 지금처럼 matcher 없이 마지막
  - 설정 예: `admin.access-token-validity: 30m`, `admin.refresh-token-validity: 12h`, `admin.login-max-failures: 5`, `admin.login-lock-duration: 15m`, `admin-jwt.secret: ${ADMIN_JWT_SECRET}`, `cors.admin-allowed-origins: ${ADMIN_CORS_ALLOWED_ORIGINS:}`. prefix `admin`은 흔한 OS 환경변수 이름이 아니다. `ADMIN_JWT_SECRET`은 relaxed binding으로 `admin.jwt.secret`에도 잡히므로 `admin` record에 `jwt` 필드를 두지 않는다(두면 그 값이 바인딩된다)
  - Redis 키 예: `admin-login-fail:{loginId}`(INCR + EXPIRE), `admin-login-lock:{loginId}`, `admin-refresh:{adminId}:{sid}`, `admin-sessions:{adminId}`. 잠금 시작(5번째 실패)처럼 키 두 개를 함께 바꾸며 TTL을 거는 곳은 MULTI/EXEC로 묶는다
  - OpenAPI 분리: springdoc `GroupedOpenApi` 두 개(사용자 `/api/v1/**` 등 기존 경로, 어드민 `/api/admin/**`)와 그룹별 스펙 경로(`/v3/api-docs.yaml/{group}`) 또는 `pathsToExclude`. 어느 방법이든 GOAL 11의 결과가 같으면 된다. 어드민 그룹의 보안 스킴 설명은 어드민 토큰을 가리키게 한다
  - CORS: `UrlBasedCorsConfigurationSource`에 `/api/admin/**`와 `/api/v1/**`를 겹치지 않게 따로 등록한다(`/api/**` 하나에 걸면 첫 매칭이 이긴다)
- MASTER INSERT 템플릿 예(README용, 값은 자리표시자):
  `INSERT INTO admins (id, login_id, password_hash, name, team, birth_date, role, status, created_at, updated_at, created_by, updated_by, is_deleted) VALUES (gen_random_uuid(), '<아이디>', '<htpasswd 출력의 : 뒤>', '<이름>', '<팀 코드>', '<YYYY-MM-DD>', 'MASTER', 'APPROVED', now(), now(), 'SYSTEM', 'SYSTEM', false);`
  DB 시간대가 UTC면 `now()`가 한국 시간과 9시간 다를 수 있다. 앱은 오프셋 없는 한국 시간 `LocalDateTime`을 쓴다. 템플릿에는 `now() AT TIME ZONE 'Asia/Seoul'`을 쓰는 편이 맞다
- 배포 주의: 운영 ECS 태스크 정의에 `ADMIN_JWT_SECRET`(필수, 없으면 기동 실패)과 `ADMIN_CORS_ALLOWED_ORIGINS`를 더해야 한다. 저장소 밖 작업이라 PR 본문과 README에 적는다
- 머지 후 메인 디렉터리와 다른 워크트리의 `snsg/.env`에 두 값이 없으면 그쪽 테스트 컨텍스트가 뜨지 않는다(`ADMIN_JWT_SECRET` 자리표시자 해석 실패). 오케스트레이터가 사용자에게 알린다
- 문서 최신화 단계(agent4)에 넘길 후보: CLAUDE.md의 FE 전달 문서 목록에 `docs/admin-api-guide.md`·`docs/openapi-admin.yaml` 추가, `.claude/rules/api.md`의 URL 절(`/api/admin/v1`)·문서 절(어드민 스펙 분리, `OPENAPI_UPDATE`가 두 파일을 만든다), `.claude/rules/testing.md`의 보안 테스트 항목(두 체인·두 필터를 함께 올리는 이유), `.claude/rules/package-structure.md` common 판단 기록(CORS 분리)

## 진행 중 더한 요구

구현 단계에서 오케스트레이터가 더했다. 위 GOAL은 고치지 않았다.

- 근거: 사용자 원문 "일반사용자는 절대 admin에 접속할 수도 어떠한 정보도 볼 수 없도록". dev 프로파일은 swagger-ui와 `/v3/api-docs`가 공개인데, 그룹 없는 스펙(`/v3/api-docs`, `/v3/api-docs.yaml`)에 `/api/admin/**` 경로가 섞여 나왔다. 결정 18의 "dev swagger에 어드민 API 형태가 보이는 것은 허용"을 이 요구로 대체한다.
- 요구
  - 서버가 런타임으로 내주는 공개 문서(그룹 없는 스펙 JSON·YAML, swagger-ui 설정, 사용자 그룹)에 `/api/admin` 경로와 어드민 스키마가 나오지 않는다.
  - 어드민 스펙은 어떤 프로파일(dev·prod 포함)에서도 공개 경로로 내주지 않는다. FE에는 저장소의 `docs/openapi-admin.yaml`로만 전한다.
  - `OpenApiSpecTest`는 계속 `docs/openapi.yaml`(어드민 없음, develop 대비 diff 없음)과 `docs/openapi-admin.yaml`을 모두 대조한다.
  - 테스트로 고정한다: 실제 앱 설정에서 그룹 없는 스펙과 사용자 그룹에 `/api/admin`이 0건이고, 어드민 그룹 경로는 토큰 없이 응답하지 않는다(401·404).
  - `docs/admin-api-guide.md`에 "어드민 API 스펙은 서버 swagger에 나오지 않고 저장소 `docs/openapi-admin.yaml`로만 본다"를 적는다.
- 구현
  - `application.yaml`의 `springdoc.paths-to-exclude: /api/admin/**`로 그룹 없는 스펙에서 거른다.
  - 어드민 그룹 빈은 `openapi.admin-group-enabled=true`일 때만 생긴다. 기본값은 false이고 어느 프로파일에서도 켜지 않는다. `OpenApiSpecTest`만 켜고, 전역 exclude가 그룹에도 적용되므로 그 테스트에서는 비운다.
  - 그룹이 없을 때 `/v3/api-docs/admin`이 500(springdoc의 그룹 없음 예외)이 되지 않도록 `SecurityConfig`에서 그 경로를 문서 공개보다 먼저 `authenticated()`로 둔다. 토큰 없이 부르면 401이다.
  - `OpenApiConfigTest`가 위 내용을 단정한다.

### 어드민 세션 절대 만료 (verify 뒤 오케스트레이터 지시)

- 근거: 재발급할 때마다 새 refresh가 12시간으로 다시 발급되면(슬라이딩) 계속 쓰는 동안 세션이 끝나지 않는다. 결정 5의 근거("공용 PC에 하루를 넘겨 로그인이 남지 않게")가 보장되지 않는다. 결정 5·6의 "재발급하면 회전"은 유지하되 만료는 늘리지 않는다.
- 요구
  - 어드민 세션은 로그인 시각부터 12시간(`admin.refresh-token-validity`)에 끝난다(절대 만료). 재발급은 refresh를 회전하되 새 토큰의 exp, Redis TTL, 쿠키 Max-Age가 모두 처음 세션 만료까지 남은 시간이다.
  - 사용자 refresh 동작은 바꾸지 않는다.
  - 테스트로 고정: 로그인 뒤 시간이 지난 시점의 재발급에서 새 refresh의 만료·쿠키 Max-Age·Redis TTL이 처음 세션 만료를 넘지 않는다. 세션 만료 뒤 재발급은 `INVALID_ADMIN_REFRESH_TOKEN`이다.
  - `docs/admin-api-guide.md`의 수명 설명을 "로그인 후 12시간이 지나면 다시 로그인(재발급해도 늘어나지 않음)"으로 고친다.
- 판단: access token의 만료도 세션 만료를 넘지 않게 한다(min(지금 + 30분, 세션 만료)). 30분 그대로 두면 세션이 끝난 뒤에도 access로 최대 30분 더 쓸 수 있어 절대 만료가 그만큼 밀린다. 비용은 세션 끝 무렵 받은 access가 30분보다 짧아지는 것뿐이고, FE는 401 → 재발급 401 → 로그인으로 원래 흐름대로 처리한다.
- 구현
  - 세션 만료 시각은 refresh JWT의 exp에 담는다. 로그인 때 `AdminTokenIssuer`가 지금 + refresh 수명(초 단위로 자름)으로 정하고, 재발급은 refresh에서 읽은 exp를 그대로 넘긴다. `AdminTokenPort`·`AdminRefreshTokenStorePort`는 만료 시각을 인자로 받는다.
  - Redis 세션 키 TTL은 남은 시간이다. 세션 집합의 TTL은 저장할 때마다 refresh 수명 전체로 다시 건다(남은 시간이 가장 긴 세션보다 먼저 사라지지 않게).
  - 쿠키 Max-Age는 남은 시간을 초 단위로 올림한 값이다(로그인 직후에는 12시간과 같다).
  - 재발급은 해석한 세션 만료가 지났으면 발급하지 않고 `INVALID_ADMIN_REFRESH_TOKEN`이다. JWT 해석이 이미 거르지만 남은 시간이 없는 세션으로 발급하지 않게 한 번 더 본다.

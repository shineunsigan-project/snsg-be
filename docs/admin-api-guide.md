# SNSG 어드민 API 가이드

운영 도구(어드민 앱)를 만드는 데 필요한 것을 모았다. 이 문서와 어드민 스펙만 보고 로그인·가입·재발급·로그아웃·내 정보 화면을 만들 수 있게 하는 게 목표다.

- **엔드포인트별 요청·응답·에러 코드**: `docs/openapi-admin.yaml`. 코드에서 생성하고 `OpenApiSpecTest`가 코드와 대조한다.
- **어드민 API 스펙은 서버 swagger에 나오지 않고 저장소 `docs/openapi-admin.yaml`로만 본다.** 일반 사용자가 어드민 정보를 볼 수 없도록 서버의 swagger-ui와 스펙 경로(`/v3/api-docs` 등)에는 어드민 경로가 없다. Swagger Editor·Postman 등에 이 파일을 넣어 본다.
- **흐름, 공통 규칙, 필수 요구**: 이 문서. 빌드가 잡아주지 않으니 사람이 챙긴다.
- 서비스 사용자 앱의 API는 `docs/openapi.yaml`·`docs/api-guide.md`에 있다. 두 스펙은 따로 나뉘어 있고 서로의 경로가 나오지 않는다.

## 필수 요구

아래 두 가지는 선택이 아니다. 지키지 않으면 BE가 막아 둔 분리가 FE에서 깨진다.

### 1. 어드민은 사용자 앱과 반드시 별도 앱으로 만든다

- FE 저장소 안의 **별도 Next.js 앱**으로 만든다. 모노레포라면 `apps/admin`처럼 사용자 앱(`apps/web` 등)과 나란히 둔다.
- **별도 도메인에 따로 배포한다.** 예: 사용자 앱은 `snsg.example`, 어드민 앱은 `admin.snsg.example`.
- 사용자 앱의 라우트(`app/admin`, `pages/admin` 등)로 만들지 않는다. 사용자 앱에 어드민 화면·링크·코드를 넣지 않는다.

이유

- **일반 사용자가 어드민 화면 코드조차 받지 않는다.** 같은 앱이면 사용자 번들·라우트 목록에 어드민 경로와 화면이 섞여 내려간다. "일반 사용자는 어드민에 접속할 수도, 어떤 정보도 볼 수 없다"가 요구다.
- **CORS를 어드민 도메인 하나로 좁힌다.** BE는 어드민 API(`/api/admin/**`)를 어드민 앱 출처에서만 받는다. 사용자 앱 출처에서는 어드민 API를 부를 수 없다. refresh 쿠키가 `SameSite=None`이라, 사용자 앱 출처까지 허용하면 사용자 앱에 스크립트가 한 번 끼어드는 것만으로 그 브라우저의 어드민 세션이 재발급될 수 있다. 같은 앱이면 출처가 같아 이 분리가 불가능하다.
- **배포와 접근 제한을 따로 건다.** 어드민 앱만 사내망·IP 제한 등을 걸 수 있다.

### 2. 접속부터 로그인을 요구한다

로그인·가입 화면 말고는 **인증 확인(재발급 성공) 전에 어떤 화면도 그리지 않는다.** 로그인하지 않았으면 어떤 페이지로도 넘어가지 않는다.

구현 방식

- **공통 레이아웃 가드.** 로그인·가입을 뺀 모든 화면을 하나의 레이아웃(예: `app/(protected)/layout.tsx`)으로 감싼다. 이 레이아웃은 앱에 들어오면(새로고침 포함) `POST /api/admin/v1/auth/tokens`를 부른다.
  - 응답 전에는 **아무 내용도 그리지 않는다**(빈 화면이나 로딩 표시만).
  - 200이면 access token을 메모리에 두고 `GET /api/admin/v1/admins/me`로 내 정보를 받아 화면을 그린다.
  - 401이면 내용 대신 로그인 화면으로 바꾼다(`router.replace('/login')`).
- **서버 컴포넌트·SSR로 데이터를 미리 가져오지 않는다.** 어드민 데이터는 모두 브라우저에서 access token을 붙여 가져온다. 토큰 없이 그려질 자리를 만들지 않는다.
- **Next.js middleware로 로그인 여부를 판단하지 않는다.** refresh 쿠키(`adminRefreshToken`)는 API 도메인의 HttpOnly 쿠키라, 다른 도메인에서 도는 어드민 앱의 middleware는 이 쿠키를 볼 수 없다. middleware로 막으려 하면 항상 비로그인으로 보이거나, 쿠키를 어드민 도메인에 따로 두게 되어 보안이 약해진다.
- **실제 차단은 BE가 한다.** 공개된 세 API(가입 신청, 로그인, 재발급) 말고는 모든 어드민 API가 어드민 access token을 요구한다. 화면 가드는 사용자 경험을 위한 것이고, 가드를 우회해도 데이터는 받을 수 없다.
- 로그인 화면에서 가입 화면으로는 갈 수 있다. 가입 신청은 로그인 전에 해야 한다.

## 공통 규칙

### 경로

모든 어드민 API는 `/api/admin/v1`으로 시작한다. 사용자 API(`/api/v1`)와 경로·토큰·쿠키가 모두 다르다.

### 응답 봉투

사용자 API와 같다.

```json
{ "isSuccess": true,  "data": { } }
{ "isSuccess": false, "code": "INVALID_ADMIN_CREDENTIALS", "message": "아이디 또는 비밀번호가 올바르지 않아요." }
{ "isSuccess": false, "code": "INVALID_REQUEST", "message": "요청 형식이 올바르지 않습니다.",
  "errors": [ { "field": "loginId", "message": "아이디는 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자만 쓸 수 있어요." } ] }
```

- 성공 여부는 `isSuccess`로 본다. 화면 분기는 `code`로 한다. `message`는 표시용이라 문구가 바뀔 수 있다.
- `errors`는 요청 필드 검증에 실패했을 때만 있다(`code`는 `INVALID_REQUEST`). `field`는 요청 JSON의 필드 이름이고, `message`를 그 입력칸 아래에 그대로 보여주면 된다.
- 업무 오류, JSON 변환 실패(잘못된 JSON, 목록 밖의 `team`, 없는 날짜), 404·405에는 `errors`가 없다.

### 날짜

`birthDate`는 `yyyy-MM-dd`다. 기준 시간대는 한국 시간(Asia/Seoul)이다.

## 인증

### 토큰을 주고받는 방식

| | 어디로 | 어디에 보관 |
| --- | --- | --- |
| access token | 응답 본문 `data.accessToken` | **메모리에만**. localStorage·sessionStorage에 넣지 않는다. 30분 뒤 만료. 로그인 후 12시간(세션 만료)이 30분 안에 오면 그때 만료 |
| refresh token | **HttpOnly 쿠키 `adminRefreshToken`** | 브라우저가 보관. 자바스크립트는 읽을 수 없다. **로그인 후 12시간이 지나면 만료**(재발급해도 늘어나지 않음) |

- 쿠키는 `HttpOnly; Secure; SameSite=None; Path=/api/admin/v1/auth`다. 인증 API를 부를 때만 따라간다.
- **쿠키를 쓰는 요청(로그인, 재발급, 로그아웃)에는 반드시 credentials를 포함한다.** 빠뜨리면 쿠키가 저장되지 않거나 전송되지 않아 재발급이 401이 된다.
  ```js
  fetch(url, { credentials: 'include' })   // axios면 withCredentials: true
  ```
- 다른 어드민 API는 `Authorization: Bearer {accessToken}` 헤더로 부른다.
- **로그인 후 12시간이 지나면 다시 로그인해야 한다(재발급해도 늘어나지 않음).** 재발급은 refresh를 새로 바꿔 주지만 만료 시각은 처음 로그인 때 정한 그대로다. 새 쿠키의 수명도 그때까지 남은 시간이다. 계속 쓰고 있어도 12시간이 되면 재발급이 401이 된다. 공용 PC에 로그인이 하루를 넘겨 남지 않게 하기 위해서다. 사용자 앱(재발급할 때마다 14일 연장)과 다르다. 12시간 안에는 새로고침해도 재발급으로 로그인이 이어진다.
- access token도 세션 만료를 넘지 않는다. 세션이 30분보다 적게 남았을 때 받은 access token은 세션 만료 시각에 함께 끝난다.
- **사용자 앱의 토큰과 섞이지 않는다.** 어드민 토큰과 사용자 토큰은 서로 다른 키로 서명된다. 사용자 토큰으로 어드민 API를 부르면 401 `UNAUTHENTICATED`, 어드민 토큰으로 사용자 API를 부르면 401 `UNAUTHENTICATED`다. 쿠키 이름(`adminRefreshToken` / `refreshToken`)과 경로도 다르다. 같은 브라우저에서 두 앱에 동시에 로그인해도 서로 영향을 주지 않는다.
- 토큰에는 역할이 들어 있지 않다. 역할은 `GET /api/admin/v1/admins/me`의 `role`로 받는다. 승인이 풀리는 등 계정 상태가 바뀌면 남은 access token도 다음 요청부터 401이 된다.

### 401을 받았을 때

1. `POST /api/admin/v1/auth/tokens`로 재발급을 **한 번** 시도한다.
2. **재발급 요청은 한 번에 하나만 보낸다.** 재발급하면 이전 refresh token이 즉시 무효가 되므로, 두 요청이 겹치면 뒤 요청이 401을 받고 로그아웃된다. 재발급 중이면 다른 요청은 기다렸다가 새 토큰으로 다시 보낸다.
3. 성공하면 새 access token으로 원래 요청을 한 번 다시 보낸다.
4. 재발급이 401 `INVALID_ADMIN_REFRESH_TOKEN`이면 메모리의 토큰을 버리고 로그인 화면으로 보낸다. 로그인 후 12시간이 지났거나(재발급으로 늘어나지 않는다), 이 기기에서 로그아웃했거나, 계정이 승인 상태가 아니게 된 경우다.

## 가입 신청

`POST /api/admin/v1/auth/signup`. 로그인 전에 부른다(토큰 없이). 성공하면 **201**이고 `data`가 없다. 토큰·쿠키를 주지 않는다.

- 가입하면 **승인 대기** 상태가 된다. **MASTER가 승인하기 전까지는 로그인할 수 없다**(로그인하면 403 `ADMIN_PENDING_APPROVAL`). 가입 성공 화면에서 "승인 후 로그인할 수 있다"를 안내하고 로그인 화면으로 보낸다.
- 역할은 항상 일반 어드민(`ADMIN`)이다. 요청 본문에 역할·상태를 넣어도 무시된다. MASTER는 API로 만들 수 없다.
- 인증 수단(이메일·휴대폰 인증 등)은 없다. MASTER의 승인이 인증이다.

**입력 규칙**

| 필드 | 필수 | 규칙 | 틀렸을 때 `errors[].message` |
| --- | --- | --- | --- |
| `loginId` | 예 | 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자. **대문자는 받지 않는다**(입력칸에서 소문자로 바꿔 보내도 된다). 다른 어드민과 겹칠 수 없다 | 아이디는 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자만 쓸 수 있어요. |
| `password` | 예 | 8~64자. 영문과 숫자를 각각 1자 이상. 공백 없는 영문·숫자·특수문자(ASCII)만. 한글·공백 불가 | 비밀번호는 영문과 숫자를 각각 1자 이상 넣어 8~64자로 써주세요. 공백·한글은 쓸 수 없어요. |
| `name` | 예 | 2~20자의 한글 또는 영문. 공백·숫자 불가 | 이름은 2~20자의 한글 또는 영문만 쓸 수 있어요. |
| `team` | 예 | 아래 소속팀 코드 중 하나 | 소속팀을 선택해주세요. |
| `birthDate` | 예 | 오늘(한국 시간)보다 이전 날짜 | 생년월일이 올바르지 않습니다. |

- 위 형식 오류는 모두 400 `INVALID_REQUEST`와 `errors`로 온다.
- `team`에 목록 밖의 값을 보내거나 `2010-02-30`처럼 없는 날짜를 보내면 JSON 변환 단계에서 실패해 400 `INVALID_REQUEST`만 오고 `errors`는 없다. 화면에서 고를 수 있는 값만 보내면 나지 않는다.
- 아이디가 이미 쓰이고 있으면 409 `DUPLICATED_LOGIN_ID`. 다른 아이디를 입력받는다. 아이디 중복을 미리 확인하는 API는 없다.

**소속팀 코드**

목록을 주는 API는 없다(로그인 전에 부를 수 있는 어드민 API를 늘리지 않는다). 아래 값을 FE 상수로 두고 선택지로 그린다. 팀이 바뀌면 BE·FE를 함께 고친다.

| 코드 | 화면 이름 |
| --- | --- |
| `PLANNING` | 기획 |
| `DESIGN` | 디자인 |
| `FRONTEND` | 개발(FE) |
| `BACKEND` | 개발(BE) |
| `MARKETING` | 마케팅 |
| `OPERATIONS` | 운영 |
| `BUSINESS` | 사업개발 |
| `DATA` | 데이터 |
| `MANAGEMENT` | 경영지원 |

## 로그인

`POST /api/admin/v1/auth/login` (credentials 포함). 본문은 `{ "loginId", "password" }`다.

- 성공하면 200, `data.accessToken`과 `adminRefreshToken` 쿠키를 준다. 이어서 `GET /api/admin/v1/admins/me`로 내 정보를 받아 첫 화면으로 간다.
- 로그인 요청은 아이디·비밀번호의 **형식을 검사하지 않는다.** 비었으면 400 `INVALID_REQUEST`(각 64자 초과도 400), 형식이 틀린 값은 맞는 계정이 없는 것과 같이 401 `INVALID_ADMIN_CREDENTIALS`다.
- 아이디가 없는 것과 비밀번호가 틀린 것은 **구분하지 않는다.** 둘 다 401 `INVALID_ADMIN_CREDENTIALS`다. "아이디 또는 비밀번호가 올바르지 않아요."처럼 하나로 안내한다.
- **잠금: 같은 아이디로 5회 연속 실패하면 15분 동안 잠긴다.**
  - 다섯 번째 실패의 응답부터 401 `ADMIN_LOGIN_LOCKED`다. 잠긴 동안은 비밀번호가 맞아도 같은 코드다.
  - 잠긴 동안 다시 시도해도 잠금 시간은 늘어나지 않는다. 다섯 번째 실패 시각부터 15분 뒤 풀린다.
  - 로그인에 성공하면 실패 횟수가 0으로 돌아간다. 실패 사이가 15분 넘게 벌어져도 횟수가 사라진다.
  - 없는 아이디도 똑같이 잠긴다. 남은 횟수는 알려주지 않는다.
- 비밀번호는 맞았지만 계정이 승인 상태가 아니면 상태별로 403이다. **비밀번호가 맞을 때만** 알려준다. 비밀번호가 틀리면 상태와 관계없이 401 `INVALID_ADMIN_CREDENTIALS`다.
  - 승인 대기: 403 `ADMIN_PENDING_APPROVAL`. 승인 대기 안내를 보여준다.
  - 가입 거절: 403 `ADMIN_REJECTED`. `message`를 보여준다. 이 아이디로는 다시 로그인·가입할 수 없다.
  - 이용 중지: 403 `ADMIN_SUSPENDED`. `message`를 보여준다. MASTER가 해제하면 다시 로그인할 수 있다.
- 로그인할 때마다 새 기기 세션이 생긴다. 다른 기기의 로그인은 끊기지 않는다.

## 로그아웃

`DELETE /api/admin/v1/auth/tokens` (access token + credentials 포함).

- 이 기기의 세션만 끝내고 `adminRefreshToken` 쿠키를 만료시킨다(`Max-Age=0`). 다른 기기의 로그인은 유지된다.
- 쿠키가 없거나 이미 끝난 세션이어도 200이다. 다시 보내도 안전하다.
- 응답과 상관없이 메모리의 access token을 버리고 로그인 화면으로 보낸다. 이미 발급된 access token은 만료(30분)까지 유효하므로 반드시 버린다.

## 내 정보

`GET /api/admin/v1/admins/me` (access token 포함).

```json
{ "isSuccess": true,
  "data": { "id": "0192f3a4-5b6c-7d8e-9f01-23456789abcd", "loginId": "mintadmin", "name": "김민트",
            "team": "BACKEND", "birthDate": "1996-04-12", "role": "ADMIN" } }
```

- `role`: `MASTER`(가입 승인 등 MASTER 전용 메뉴를 볼 수 있다) 또는 `ADMIN`(일반 어드민). MASTER 전용 메뉴는 `role`이 `MASTER`일 때만 그린다. 실제 권한 검사는 BE가 한다.
- 비밀번호와 계정 상태는 응답에 없다. 이 API가 200이면 승인된 계정이다.

## 가입 승인 관리 (MASTER 전용)

MASTER가 가입 신청을 승인·거절하고, 승인된 어드민의 이용을 중지·해제한다. 모두 access token이 필요하다.

- **MASTER 메뉴는 내 정보의 `role`이 `MASTER`일 때만 그린다.** 다만 실제 검사는 BE가 한다. `ADMIN`이 아래 API를 부르면 403 `MASTER_ONLY`다.
- 토큰이 없거나 승인 상태가 아닌 계정이면 다른 API와 같이 401 `UNAUTHENTICATED`다.

| 메서드·경로 | 하는 일 | 성공 |
| --- | --- | --- |
| `GET /api/admin/v1/admins?status=&cursor=&size=` | 어드민 목록 | 200, `data`는 `{ items, nextCursor }` |
| `POST /api/admin/v1/admins/{adminId}/approval` | 가입 승인 | 200 `{"isSuccess":true}`, `data` 없음 |
| `POST /api/admin/v1/admins/{adminId}/rejection` | 가입 거절 | 200 `{"isSuccess":true}`, `data` 없음 |
| `POST /api/admin/v1/admins/{adminId}/suspension` | 이용 중지 | 200 `{"isSuccess":true}`, `data` 없음 |
| `DELETE /api/admin/v1/admins/{adminId}/suspension` | 이용 중지 해제 | 200 `{"isSuccess":true}`, `data` 없음 |

요청 본문은 모두 없다. `adminId`는 목록 항목의 `id`다.

### 상태

| `status` | 뜻 | 화면 표시 | 그릴 버튼 |
| --- | --- | --- | --- |
| `PENDING` | 가입 신청 후 승인 대기 | 승인 대기 | [승인] [거절] |
| `APPROVED` | 승인됨. 로그인해 쓸 수 있다 | 승인됨 | [이용 중지] |
| `REJECTED` | 가입 거절됨 | 거절됨 | 없음 |
| `SUSPENDED` | 이용 중지됨 | 이용 중지 | [이용 중지 해제] |

`role`이 `MASTER`인 행에는 버튼을 그리지 않는다(아래 "MASTER는 대상이 될 수 없다").

### 상태 전이

| 동작 | 지금 상태 | 바뀐 상태 |
| --- | --- | --- |
| 승인 | `PENDING` | `APPROVED` |
| 거절 | `PENDING` | `REJECTED` |
| 이용 중지 | `APPROVED` | `SUSPENDED` |
| 이용 중지 해제 | `SUSPENDED` | `APPROVED` |

- 표에 없는 상태에서 부르면(같은 동작을 다시 보낸 경우, 다른 MASTER가 먼저 처리한 경우 포함) 409 `ADMIN_STATUS_CONFLICT`이고 아무것도 바뀌지 않는다. 목록을 다시 불러 지금 상태를 그린다.
- **거절은 되돌릴 수 없다.** 거절된 아이디는 다시 쓸 수 없다. 같은 사람이 다시 신청하려면 다른 아이디로 가입한다. 거절된 사람이 로그인하면 비밀번호가 맞을 때만 403 `ADMIN_REJECTED`를 받는다.
- **이용 중지하면 그 어드민은 다음 요청부터 401 `UNAUTHENTICATED`다.** 그 어드민의 로그인 세션도 모두 지워져 재발급(`POST /api/admin/v1/auth/tokens`)도 401 `INVALID_ADMIN_REFRESH_TOKEN`이다. 로그인하면 비밀번호가 맞을 때만 403 `ADMIN_SUSPENDED`를 받는다.
- **이용 중지를 해제해도 이전 로그인은 살아나지 않는다.** 해제된 어드민은 다시 로그인한다.
- 이용 중지가 500으로 끝나면 중지는 이미 반영됐을 수 있다. 목록을 다시 불러 상태를 확인한다.
- 여러 조건에 걸리면 `ADMIN_NOT_FOUND`(404) → `MASTER_NOT_MODIFIABLE`(403) → `ADMIN_STATUS_CONFLICT`(409) 순서로 앞의 것이 온다.

### MASTER는 대상이 될 수 없다

대상의 `role`이 `MASTER`면 승인·거절·이용 중지·해제 모두 403 `MASTER_NOT_MODIFIABLE`이다. **요청한 MASTER 자신도 MASTER이므로 자기 자신을 중지·거절할 수 없다.** MASTER 계정은 BE가 DB로만 만들고 관리한다. 필요하면 BE에 요청한다.

### 목록

`GET /api/admin/v1/admins` (access token 포함)

| 파라미터 | 필수 | 뜻 |
| --- | --- | --- |
| `status` | 아니오 | `PENDING`·`APPROVED`·`REJECTED`·`SUSPENDED` 중 하나. 이 상태만 준다. 생략하면 모든 상태. 그 밖의 값은 400 `INVALID_REQUEST` |
| `cursor` | 아니오 | 이전 응답의 `nextCursor`를 그대로 보낸다. 생략하면 첫 페이지. 해석할 수 없으면 400 `INVALID_CURSOR` |
| `size` | 아니오 | 한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다(오류가 아니다) |

- 삭제되지 않은 모든 어드민을 준다. **MASTER와 나 자신도 나온다.** `role`로 구분한다.
- 정렬은 가입 신청 일시(`createdAt`)가 빠른 순, 같으면 `id` 순이다.
- 더 있으면 `nextCursor`를 준다. 다음 페이지는 같은 `status`에 `cursor=nextCursor`를 붙여 부른다. `status`를 바꾸면 `cursor` 없이 처음부터 부른다. 마지막 페이지면 `nextCursor`는 `null`이다.
- 맞는 어드민이 없으면 200에 `items = []`, `nextCursor = null`이다.
- `nextCursor`는 해석하지 않는 문자열이다(영문·숫자·`_`·`-`만 쓴다). 인코딩 없이 쿼리에 붙인다.

```json
{ "isSuccess": true,
  "data": {
    "items": [
      { "id": "0192f3a4-5b6c-7d8e-9f01-23456789abcd", "loginId": "mintadmin", "name": "김민트",
        "team": "BACKEND", "birthDate": "1996-04-12", "role": "ADMIN", "status": "PENDING",
        "createdAt": "2026-11-13T10:15:30" }
    ],
    "nextCursor": null } }
```

| 필드 | 뜻 |
| --- | --- |
| `id` | 어드민 식별자. 승인·거절·이용 중지·해제 경로의 `adminId` |
| `loginId` | 로그인 아이디 |
| `name` | 실명 |
| `team` | 소속팀. 값과 이름은 가입의 `team` 표와 같다 |
| `birthDate` | 생년월일(`yyyy-MM-dd`) |
| `role` | `MASTER` 또는 `ADMIN` |
| `status` | 위 상태 표 |
| `createdAt` | 가입 신청 일시. 한국 시간, 오프셋 없는 ISO-8601 |

비밀번호와 누가 처리했는지는 응답에 없다.

## 에러 코드

| HTTP | code | 언제 | 화면에서 할 일 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필드 형식 오류(`errors` 있음), 본문 없음·잘못된 JSON·목록 밖 `team`·없는 날짜(`errors` 없음) | `errors`가 있으면 필드별 문구, 없으면 `message`를 보여준다 |
| 409 | `DUPLICATED_LOGIN_ID` | 가입: 이미 쓰는 아이디 | 다른 아이디를 입력받는다 |
| 401 | `INVALID_ADMIN_CREDENTIALS` | 로그인: 아이디가 없거나 비밀번호가 틀림(형식이 틀린 값 포함) | "아이디 또는 비밀번호가 올바르지 않아요." 안내 |
| 401 | `ADMIN_LOGIN_LOCKED` | 로그인: 같은 아이디로 5회 연속 실패해 15분 동안 잠김 | 잠시(15분) 후 다시 시도하라고 안내. 입력은 막지 않아도 된다 |
| 403 | `ADMIN_PENDING_APPROVAL` | 로그인: 비밀번호는 맞지만 MASTER 승인 대기 | 승인 대기 안내 |
| 403 | `ADMIN_REJECTED` | 로그인: 비밀번호는 맞지만 가입이 거절됨 | `message`를 보여준다. 이 아이디로는 다시 가입할 수 없다 |
| 403 | `ADMIN_SUSPENDED` | 로그인: 비밀번호는 맞지만 이용 중지됨 | `message`를 보여준다 |
| 401 | `INVALID_ADMIN_REFRESH_TOKEN` | 재발급: 쿠키 없음·만료·위조·회전됨·로그아웃됨·계정이 승인 상태가 아님(이용 중지 포함) | 토큰을 버리고 로그인 화면으로 |
| 401 | `UNAUTHENTICATED` | 인증이 필요한 어드민 API에 access token이 없거나 만료·무효, 사용자 앱 토큰을 보냄, 계정이 승인 상태가 아님(이용 중지 포함). 없는 어드민 경로도 토큰이 없으면 이 코드다 | 재발급 후 한 번 다시 부른다. 재발급도 401이면 로그인 화면으로 |
| 403 | `MASTER_ONLY` | MASTER 전용 API(가입 승인 관리)를 `ADMIN`이 부름 | "MASTER만 할 수 있어요" 안내. MASTER 메뉴는 `role`이 `MASTER`일 때만 그린다 |
| 400 | `INVALID_REQUEST` | 가입 승인 관리: 경로의 `adminId`가 UUID가 아님, 목록의 `status`가 네 값 밖, `size`가 숫자가 아님(`errors` 없음) | 정상 화면 흐름에서는 나지 않는다. `message`를 보여준다 |
| 400 | `INVALID_CURSOR` | 목록: 해석할 수 없는 `cursor` | `cursor` 없이 처음부터 다시 부른다 |
| 404 | `ADMIN_NOT_FOUND` | 승인·거절·이용 중지·해제: 대상이 없거나 삭제됨 | 목록을 다시 불러 그린다 |
| 403 | `MASTER_NOT_MODIFIABLE` | 승인·거절·이용 중지·해제: 대상이 MASTER(자기 자신 포함) | `message`를 보여준다. MASTER 행에는 버튼을 그리지 않는다 |
| 409 | `ADMIN_STATUS_CONFLICT` | 승인·거절·이용 중지·해제: 대상의 지금 상태에서 할 수 없는 동작(이미 처리됨, 다른 MASTER가 먼저 처리함 포함) | 목록을 다시 불러 지금 상태를 그린다 |
| 500 | `INTERNAL_ERROR` | 서버 오류 | 잠시 후 다시 시도 안내 |

예시

```json
{ "isSuccess": false, "code": "DUPLICATED_LOGIN_ID", "message": "이미 사용 중인 아이디예요." }
{ "isSuccess": false, "code": "INVALID_ADMIN_CREDENTIALS", "message": "아이디 또는 비밀번호가 올바르지 않아요." }
{ "isSuccess": false, "code": "ADMIN_LOGIN_LOCKED", "message": "로그인에 여러 번 실패해 잠시 잠겼어요. 잠시 후 다시 시도해주세요." }
{ "isSuccess": false, "code": "ADMIN_PENDING_APPROVAL", "message": "가입 승인을 기다리고 있어요. 승인된 뒤 로그인할 수 있어요." }
{ "isSuccess": false, "code": "ADMIN_REJECTED", "message": "가입이 거절됐어요. 자세한 내용은 MASTER에게 문의해주세요." }
{ "isSuccess": false, "code": "ADMIN_SUSPENDED", "message": "이용이 중지된 계정이에요. 자세한 내용은 MASTER에게 문의해주세요." }
{ "isSuccess": false, "code": "MASTER_ONLY", "message": "MASTER만 할 수 있어요." }
{ "isSuccess": false, "code": "INVALID_CURSOR", "message": "목록을 처음부터 다시 불러주세요." }
{ "isSuccess": false, "code": "ADMIN_NOT_FOUND", "message": "찾을 수 없는 어드민이에요." }
{ "isSuccess": false, "code": "MASTER_NOT_MODIFIABLE", "message": "MASTER 계정은 승인·거절·이용 중지할 수 없어요." }
{ "isSuccess": false, "code": "ADMIN_STATUS_CONFLICT", "message": "지금 상태에서는 할 수 없어요. 목록을 새로 불러 상태를 확인해주세요." }
{ "isSuccess": false, "code": "INVALID_ADMIN_REFRESH_TOKEN", "message": "다시 로그인해야 합니다." }
{ "isSuccess": false, "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다." }
```

## 화면별로 쓰는 API

| 화면 | 호출 | 응답에 따른 처리 | 다시 보내도 되나 |
| --- | --- | --- | --- |
| 앱 진입(로그인·가입 외 모든 화면의 공통 레이아웃) | `POST /api/admin/v1/auth/tokens` (credentials 포함) | 200이면 access token을 메모리에 두고 내 정보를 부른다. 401이면 로그인 화면으로. 응답 전에는 아무것도 그리지 않는다 | 동시에 두 번 보내지 않는다(한 번에 하나) |
| 로그인 (제출) | `POST /api/admin/v1/auth/login` (credentials 포함) | 200이면 access token 저장 후 내 정보 → 첫 화면. 401 `INVALID_ADMIN_CREDENTIALS`·`ADMIN_LOGIN_LOCKED`, 403 `ADMIN_PENDING_APPROVAL`·`ADMIN_REJECTED`·`ADMIN_SUSPENDED`는 위 에러 표대로 안내 | 된다. 다만 실패도 잠금 횟수에 들어가므로 응답 전까지 버튼을 막는다 |
| 가입 신청 (제출) | `POST /api/admin/v1/auth/signup` | 201이면 승인 대기 안내 후 로그인 화면. 400이면 필드별 문구. 409면 다른 아이디 안내 | 성공한 요청을 다시 보내면 409 `DUPLICATED_LOGIN_ID`다. 응답 전까지 버튼을 막는다 |
| 공통 헤더 (진입·재발급 뒤) | `GET /api/admin/v1/admins/me` | 이름·소속팀을 그리고 `role`이 `MASTER`면 MASTER 전용 메뉴를 보인다. 401이면 재발급 후 한 번 다시 | 된다(조회만 한다) |
| 로그아웃 | `DELETE /api/admin/v1/auth/tokens` (access token + credentials 포함) | 응답과 무관하게 토큰을 버리고 로그인 화면 | 된다 |
| 가입 승인 관리 (MASTER, 진입·탭 전환) | `GET /api/admin/v1/admins?status=…` | 행마다 상태와 버튼을 그린다(가입 승인 관리 절의 상태 표). `nextCursor`가 있으면 더 보기에서 `cursor`를 붙여 부른다. 403 `MASTER_ONLY`면 MASTER 메뉴를 숨긴다 | 된다(조회만 한다) |
| 가입 승인 관리 [승인] | `POST /api/admin/v1/admins/{adminId}/approval` | 성공이든 실패든 목록을 다시 부른다. 409 `ADMIN_STATUS_CONFLICT`·404 `ADMIN_NOT_FOUND`면 목록을 다시 불러 지금 상태를 그린다 | 성공한 요청을 다시 보내면 409 `ADMIN_STATUS_CONFLICT`다. 응답 전까지 버튼을 막고, 409면 목록을 다시 부른다 |
| 가입 승인 관리 [거절] | `POST /api/admin/v1/admins/{adminId}/rejection` | 되돌릴 수 없으므로 확인 팝업을 띄운 뒤 부른다. 이후 처리는 [승인]과 같다 | [승인]과 같다 |
| 가입 승인 관리 [이용 중지] | `POST /api/admin/v1/admins/{adminId}/suspension` | 확인 팝업 뒤 부른다. 이후 처리는 [승인]과 같다. 500이면 목록을 다시 불러 상태를 확인한다 | [승인]과 같다 |
| 가입 승인 관리 [이용 중지 해제] | `DELETE /api/admin/v1/admins/{adminId}/suspension` | 이후 처리는 [승인]과 같다 | [승인]과 같다 |

장소 수정 같은 데이터 수정 API는 그 뒤에 `/api/admin/v1` 아래에 생긴다. 생기면 이 문서와 `docs/openapi-admin.yaml`에 추가한다.

## 로컬 개발

1. BE: `cd snsg && ./gradlew bootRun` → `http://localhost:8080`
2. 어드민 앱 로컬 주소는 **`http://localhost:3001`** 로 가정하고 CORS에 등록해 두었다. 사용자 앱(`http://localhost:3000`)과 다른 포트다. 다른 주소를 쓰면 BE에 알려달라. 등록되지 않은 출처에서는 어드민 API를 부를 수 없다(403).
3. 사용자 앱 출처(`http://localhost:3000`)에서는 어드민 API를 부를 수 없다. 반대로 어드민 앱 출처에서는 사용자 API를 부를 수 없다.
4. 가입 신청은 화면에서 하고, MASTER 계정으로 로그인해 가입 승인 관리에서 승인한다. MASTER 계정은 BE에 요청한다(MASTER는 BE가 DB로만 만든다).
5. 쿠키 속성(`Secure; SameSite=None`)은 사용자 앱과 같다. 로컬에서 사용자 앱의 쿠키가 동작하는 방식 그대로 동작한다.

### 서버에 알려줘야 하는 값

| 값 | 쓰이는 곳 | 현재 값 |
| --- | --- | --- |
| 어드민 앱 로컬 주소 | 어드민 CORS 허용 출처 | `http://localhost:3001` |
| 어드민 앱 운영·개발 도메인 | 어드민 CORS 허용 출처 | 미정 |

## 이 문서를 고치는 때

어드민 API를 바꾸는 PR에서 함께 고친다.

- 요청·응답·에러 코드가 바뀌면 컨트롤러의 springdoc 애노테이션을 고치고 스펙을 다시 만든다. 두 스펙(`docs/openapi.yaml`, `docs/openapi-admin.yaml`)이 함께 만들어진다.
  ```bash
  OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'
  ```
- 흐름, 필수 요구, 화면별로 쓰는 API가 바뀌면 이 문서를 고친다.

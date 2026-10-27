# SNSG API 가이드

이 문서와 API 스펙만 보고 화면을 만들 수 있게 하는 게 목표다.

- **엔드포인트별 요청·응답·에러 코드**: `docs/openapi.yaml`. 코드에서 생성한다.
- **여러 호출에 걸친 흐름과 공통 규칙**: 이 문서. OpenAPI로는 표현할 수 없는 것만 적는다.

`docs/openapi.yaml`은 `OpenApiSpecTest`가 코드와 대조한다. 코드와 다르면 빌드가 실패하므로 **스펙이 코드보다 뒤처지지 않는다.** 이 문서는 빌드가 잡아주지 않으니 사람이 챙긴다.

## 스펙 보는 법

- `docs/openapi.yaml`을 Swagger Editor·Postman 등에 넣어 본다.
- 서버를 띄웠다면 `/swagger-ui.html`에서 직접 호출해볼 수 있다. 우측 상단 **Authorize**에 access token을 넣으면(`Bearer` 빼고 토큰 값만) 인증이 필요한 API도 시험할 수 있다. 쿠키가 필요한 API는 같은 출처에서 열어야 쿠키가 함께 간다.
- 스펙 원본은 `/v3/api-docs.yaml`(YAML), `/v3/api-docs`(JSON)에서 받는다.
- 운영 서버에서는 swagger-ui와 스펙 경로를 열지 않는다.

## 공통 규칙

### 경로

모든 API는 `/api/v1`으로 시작한다.

### 응답 봉투

```json
{ "isSuccess": true,  "data": { } }
{ "isSuccess": false, "code": "INVALID_REFRESH_TOKEN", "message": "다시 로그인해야 합니다." }
{ "isSuccess": false, "code": "INVALID_REQUEST", "message": "요청 형식이 올바르지 않습니다.",
  "errors": [ { "field": "nickname", "message": "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요." } ] }
```

- **성공 여부는 `isSuccess`로 본다.** HTTP 상태 코드도 정확하게 내려가지만, 어느 쪽을 봐야 하는지 헷갈리지 않도록 본문에도 담는다.
- **`code`와 `message`는 실패 전용**이다. 성공 응답에는 들어 있지 않다.
- 화면 분기는 **`code`** 로 한다. enum 이름이라 바뀌지 않는다. 성공했을 때 분기가 필요하면 `data`의 필드로 준다.
- `message`는 표시·디버깅용이다. 문구가 바뀔 수 있다. 분기에 쓰지 않는다.
- **`errors`는 요청 필드 검증에 실패했을 때만** 들어 있다. 이때 `code`는 `INVALID_REQUEST`이고, 틀린 필드마다 `{ "field", "message" }`가 하나씩 담긴다. 여러 필드가 틀리면 모두 담기며 순서는 정해져 있지 않다.
  - `field`는 요청 JSON의 필드 이름이다. 해당 입력칸 아래에 `message`를 그대로 보여주면 된다. 값이 비었을 때도 그 필드의 형식 안내 문구가 온다.
  - 성공 응답, 업무 오류(`DUPLICATED_NICKNAME` 등), JSON 변환 실패(잘못된 JSON, 없는 enum 값, 없는 날짜, 타입 오류), 404·405 등에는 `errors`가 없다.
- 실패하면 `data`는 들어 있지 않다. 성공이어도 돌려줄 값이 없으면 빠진다(로그아웃 등).

### 모든 API에서 날 수 있는 에러

| 상태 | code | 언제 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 잘못된 JSON 등 요청 형식 오류. 필드 검증 실패면 `errors`에 틀린 필드가 담긴다 |
| 404·405·415 등 | `INVALID_REQUEST` | 없는 경로, 지원하지 않는 메서드나 Content-Type. 상태 코드는 원래 값 그대로다 |
| 401 | `UNAUTHENTICATED` | 인증이 필요한 API에 토큰이 없거나 만료·무효 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

인증 관련 코드는 다음과 같다.

| 상태 | code | 언제 | 화면에서 할 일 |
| --- | --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | refresh가 만료·무효. 탈퇴했거나 다른 기기에서 로그인함 | 로그인 화면으로 |
| 401 | `INVALID_SIGNUP_TOKEN` | 가입 세션이 15분을 넘김 | 카카오 로그인부터 다시 |
| 401 | `INVALID_SOCIAL_CODE` | 인가코드가 만료·위조됨 | 카카오 로그인부터 다시 |
| 409 | `DUPLICATED_NICKNAME` | 이미 쓰이는 닉네임 | 다른 닉네임 입력 안내 |
| 400 | `INVALID_NICKNAME` | 닉네임이 2~10자의 한글·영문·숫자가 아님 | 형식 안내 후 다시 입력 |
| 400 | `INVALID_PROFILE_IMAGE` | 5MB를 넘거나 JPG·PNG가 아님 | 다른 사진 선택 안내 |
| 400 | `INVALID_PROFILE_IMAGE_URL` | 가입 요청의 `profileImageUrl`이 업로드 API가 준 경로가 아님 | FE 버그다. 업로드 응답의 값을 그대로 넣는다 |
| 409 | `ALREADY_WITHDRAWN` | 이미 탈퇴한 사용자 | 로그인 화면으로 |

엔드포인트마다 추가로 나는 에러는 `openapi.yaml`의 응답 코드별 설명에 있다.

### 날짜·시간

`2026-09-14T00:00:00`처럼 **오프셋 없는 ISO-8601** 이고, 모두 **한국 시간(Asia/Seoul, UTC+9)** 이다. 브라우저(사용자 PC)의 시간대와 상관없이 한국 시간으로 해석한다.

## 인증

### 토큰을 주고받는 방식

| | 어디로 | 어디에 보관 |
| --- | --- | --- |
| access token | 응답 본문 | **메모리에만**. localStorage·sessionStorage에 넣지 않는다 |
| refresh token | **HttpOnly 쿠키** | 브라우저가 보관. 자바스크립트는 읽을 수 없다 |

refresh를 본문으로 주지 않는 이유는 XSS다. 스크립트가 한 번 끼어들면 14일짜리 토큰이 통째로 털린다. access token은 30분이라 메모리로 충분하고, 새로고침해 사라지면 재발급 API로 복구한다.

쿠키는 `Path=/api/v1/auth`라 인증 API를 부를 때만 따라간다. **쿠키를 쓰는 요청에는 반드시 credentials를 포함해야 한다.**

```js
fetch(url, { credentials: 'include' })   // axios면 withCredentials: true
```

빠뜨리면 쿠키가 가지 않아 401이나 400이 난다.

### 로그인 흐름

```
1. 사용자가 "카카오로 시작하기"를 누른다
   location.href = `${API_BASE}/api/v1/auth/kakao/authorize`

2. 서버가 카카오 인증 페이지로 보낸다. 앱 키는 서버만 가진다

3. 카카오 인증이 끝나면 서버가 처리하고 프론트엔드로 돌려보낸다
   {FE_URL}/auth/callback            기존 회원 (refreshToken 쿠키가 심어져 있음)
   {FE_URL}/auth/callback?signUp=true  신규 (signUpToken 쿠키가 심어져 있음)

4-A. 기존 회원: POST /api/v1/auth/tokens 로 access token을 받고 메인으로
4-B. 신규: 프로필 완성 화면 → POST /api/v1/auth/signup
```

토큰은 URL에 담지 않는다. 쿼리에 실으면 브라우저 히스토리·서버 로그·Referer에 남는다.

### 401을 받았을 때

1. `POST /api/v1/auth/tokens`로 재발급을 **한 번** 시도한다.
2. **재발급 요청은 한 번에 하나만 보낸다.** 재발급하면 이전 refresh token이 즉시 무효가 되므로, 두 요청이 겹치면 뒤 요청이 401을 받고 멀쩡한 사용자가 로그아웃된다. 재발급 중이면 다른 요청은 기다렸다가 새 토큰으로 다시 보낸다.
3. 성공하면 새 access token으로 원래 요청을 한 번 다시 보낸다.
4. 재발급이 `401 INVALID_REFRESH_TOKEN`이면 로그인 화면으로 보낸다. 탈퇴했거나 다른 기기에서 로그인한 경우다.

### 가입

소셜 인증과 가입은 나뉘어 있다. 소셜 인증만으로는 **사용자가 만들어지지 않는다.** 성별과 생년월일을 받아야 가입이 완료된다.

- 소셜에서 받은 정보(닉네임, 프로필 사진)는 **서버가 15분간 보관**한다. 클라이언트가 들고 있다가 보내는 방식이 아니다.
- 가입을 마치면 `signUpToken`은 무효가 된다. 중간에 이탈하면 15분 뒤 사라지고, 다시 카카오 로그인부터 시작하면 된다.
- **같은 소셜 계정으로 다시 로그인하면 이전 `signUpToken`은 즉시 무효가 된다.** 한 계정에 유효한 토큰은 항상 하나다.
- 닉네임이 이미 쓰이고 있으면 `409 DUPLICATED_NICKNAME`이 온다. 다른 닉네임을 입력받는다.

**프로필 완성 화면의 기본값**은 `GET /api/v1/auth/signup/session`으로 받는다(`signUpToken` 쿠키 필요).

```json
{ "isSuccess": true, "data": { "nickname": "민트초코", "profileImageUrl": "http://k.kakaocdn.net/..." } }
```

- `nickname`은 입력칸의 기본값으로 쓰고 사용자가 바꿀 수 있다. **가입 요청에는 화면에서 입력받은 값을 보낸다.** 서버가 세션의 닉네임을 쓰지 않는다.
- `profileImageUrl`은 **`null`일 수 있다.** 소셜 기본 이미지였거나 사진을 받지 못한 경우다. 이때는 우리 기본 이미지(`/images/default-profile.png`)가 쓰인다.
- 401 `INVALID_SIGNUP_TOKEN`이면 세션이 만료된 것이다. 카카오 로그인부터 다시 한다.

**프로필 사진을 바꾸려면** 먼저 `POST /api/v1/auth/signup/profile-image`로 올리고, 받은 경로를 가입 요청의 `profileImageUrl`에 넣는다.

- `multipart/form-data`, 파트 이름은 `image`. `signUpToken` 쿠키가 필요하다.
- 5MB 이하의 JPG·PNG만 받는다. **형식은 파일 내용으로 판별하므로 확장자만 바꿔 올리면 거부된다.** 거부되면 400 `INVALID_PROFILE_IMAGE`("JPG, PNG 형식의 사진만 올릴 수 있어요.")다.
- 응답은 `{ "profileImageUrl": "/images/profiles/<uuid>.png" }`. 도메인이 붙지 않은 경로이므로 화면에 쓸 때 서버 주소를 앞에 붙인다.
- 가입 요청의 `profileImageUrl`에는 **이 API가 돌려준 경로만** 넣을 수 있다. 다른 주소를 넣으면 400 `INVALID_PROFILE_IMAGE_URL`이다.
- 비워 두면(`null` 또는 생략) 소셜에서 받은 사진을 쓴다. 그것도 없으면 기본 이미지가 된다.
- 올리기만 하고 가입을 완료하지 않으면 그 사진은 쓰이지 않는다.

### 로그아웃

`DELETE /api/v1/auth/tokens`를 access token으로 호출한다. 서버의 refresh token이 지워지고 쿠키가 만료된다.
이미 발급된 access token은 만료(최대 30분)까지 유효하므로 클라이언트에서 버린다.

### 탈퇴

`DELETE /api/v1/users/me`를 access token으로 호출한다.

- 닉네임·생년월일·프로필 사진이 비식별 값으로 바뀌고 계정이 삭제 표시된다. **되돌릴 수 없다.**
- 올렸던 프로필 사진 파일은 지워진다.
- **카카오 연결도 서버가 끊는다.** 사용자가 카카오 설정에서 따로 끊을 필요가 없다.
- refresh token이 즉시 무효가 된다. **쿠키는 서버가 비우지 않으므로** 탈퇴 직후 `DELETE /api/v1/auth/tokens`(로그아웃)를 한 번 더 호출해 쿠키를 정리한다.
- 다시 가입하면 **이전 활동과 이어지지 않는 새 계정**이 된다. 같은 카카오 계정이어도 그렇다.

### 사용자가 카카오에서 연결을 끊은 경우

사용자가 카카오계정 설정에서 우리 서비스 연결을 끊으면 **카카오가 서버로 알려주고, 서버가 그 사용자를 탈퇴 처리한다.** FE가 할 일은 없다.

다만 그 시점에 로그인 상태였다면 **다음 재발급부터 401 `INVALID_REFRESH_TOKEN`** 이 온다. 401 처리(로그인 화면으로)를 그대로 따르면 된다. **재발급을 반복 시도하지 않는다.**

## 지역

### 지역 목록

`GET /api/v1/regions`. 서비스 중인 지역(시군구) 목록이다.

- **부르는 화면**: 일정 목록의 지역 필터, 장소 선택의 지역 칩.
- **인증이 필요 없다.** 토큰 없이 불러도 200이다.
- **서비스 중인 지역만 온다.** 서비스를 멈춘(비활성) 지역은 목록에서 빠진다.
- **정렬은 서버가 정한 노출 순서(display_order)다.** 받은 순서 그대로 화면에 그린다. 가나다순이나 코드순으로 다시 정렬하지 않는다.
- 응답 예시:
  ```json
  { "isSuccess": true, "data": [ { "code": "11440", "name": "마포구" }, { "code": "11410", "name": "서대문구" }, { "code": "11200", "name": "성동구" } ] }
  ```
  - `code`: 지역 코드. 행정구역 시군구 코드 5자리 **문자열**이다. 숫자로 바꾸지 않는다. 다른 API에 지역을 넘길 때 이 값을 쓴다.
  - `name`: 화면에 보여줄 이름. 시군구 이름이다(시도 이름은 붙지 않는다).
  - 두 필드 모두 null이 아니다. 서비스 중인 지역이 하나도 없으면 `data`는 빈 배열이다.
- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.** 실패하면 그대로 재시도한다.

## 화면별로 쓰는 API

| 화면 | 호출 | 응답에 따른 처리 |
| --- | --- | --- |
| 앱 시작 | `POST /api/v1/auth/tokens` (credentials 포함) | 200이면 access token을 받아 메인. 401이면 비로그인 상태로 둔다 |
| 로그인 | `GET /api/v1/auth/kakao/authorize`로 이동 | 돌아오면 `?signUp` 여부로 분기 |
| 프로필 완성 (진입) | `GET /api/v1/auth/signup/session` | 닉네임·사진 기본값 표시. 401이면 카카오 로그인부터 다시 |
| 프로필 완성 (사진 변경) | `POST /api/v1/auth/signup/profile-image` | 받은 경로를 가입 요청에 넣는다 |
| 프로필 완성 (제출) | `POST /api/v1/auth/signup` | 200이면 access token 저장 후 메인. 409면 닉네임 변경 안내 |
| 설정 · 로그아웃 | `DELETE /api/v1/auth/tokens` | 응답과 무관하게 메모리의 토큰을 버리고 로그인 화면 |
| 설정 · 탈퇴 | `DELETE /api/v1/users/me` → `DELETE /api/v1/auth/tokens` | 되돌릴 수 없음을 한 번 확인받고 호출. 끝나면 로그인 화면 |
| 일정 목록 (지역 필터) | `GET /api/v1/regions` | 받은 순서대로 필터를 그린다 |
| 장소 선택 (지역 칩) | `GET /api/v1/regions` | 받은 순서대로 칩을 그린다 |

일정·알림 API는 아직 없다. 생기면 이 표에 추가한다.

## 로컬에서 서버 띄우기

FE 개발 중 실제 서버에 붙여볼 때.

1. `cd snsg && ./gradlew bootRun` → `http://localhost:8080`

### 웹 FE 개발 서버에서 로컬 서버 호출하기

- **CORS는 설정돼 있다.** 허용 출처는 환경변수로 관리하며 현재 `http://localhost:3000`이 등록돼 있다. 다른 주소를 쓰면 알려줘야 추가할 수 있다. 등록되지 않은 출처는 403이다.
- **모든 요청에 `credentials: 'include'`를 붙인다.** 쿠키가 가지 않으면 재발급·가입이 전부 401이다.
- 서버 주소는 FE 환경 설정으로 바꿀 수 있게 둔다. 운영 서버가 생기면 주소만 바꾼다.
- 로그인 시작(`/auth/{provider}/authorize`)은 **`fetch`로 부르지 않는다.** 브라우저를 그 주소로 이동시킨다(`window.location.href`). fetch는 리다이렉트를 따라가 소셜 도메인에 닿고, 소셜은 CORS 헤더를 주지 않아 실패한다.

### 서버에 알려줘야 하는 값

| 값 | 쓰이는 곳 | 현재 임시값 |
| --- | --- | --- |
| FE 개발 서버 주소 | CORS 허용 출처, 쿠키 전달 | `http://localhost:3000` |
| 로그인 후 돌아올 화면 주소 | 서버가 리다이렉트할 곳(`FRONTEND_REDIRECT_URI`) | `http://localhost:3000/auth/callback` |
| 운영·개발 서버 도메인 | 쿠키의 `SameSite`·`Secure` 설정 | 미정 |

쿠키는 지금 `SameSite=None; Secure`다. FE와 BE가 다른 출처라는 전제다. 같은 도메인으로 묶거나 FE 개발 서버 프록시를 쓰면 `Lax`로 조일 수 있고, 그편이 더 안전하다.

## 이 문서를 고치는 때

API를 바꾸는 PR에서 함께 고친다.

- 요청·응답·에러 코드가 바뀌면: 컨트롤러의 springdoc 애노테이션을 고치고, 같은 PR에서 스펙을 다시 만들어 커밋한다.
  ```bash
  OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'
  ```
  커밋하지 않으면 빌드가 실패한다.
- 흐름, 공통 규칙, 화면별로 쓰는 API가 바뀌면: 이 문서를 고친다. 이건 빌드가 잡아주지 않는다.

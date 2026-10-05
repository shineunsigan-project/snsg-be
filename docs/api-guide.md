# SNSG API 가이드

이 문서와 API 스펙만 보고 화면을 만들 수 있게 하는 게 목표다.

- **엔드포인트별 요청·응답·에러 코드**: swagger-ui. 코드에서 생성하므로 항상 최신이다.
- **여러 호출에 걸친 흐름과 공통 규칙**: 이 문서. OpenAPI로는 표현할 수 없는 것만 적는다.

`docs/openapi.yaml` 파일은 아직 없다. 지금은 서버를 띄워 swagger-ui에서 본다. 스펙 파일 생성(`OpenApiSpecTest`)은 다음 작업에서 붙인다.

## 스펙 보는 법

- 로컬 서버를 띄웠다면 `http://localhost:8080/swagger-ui.html`에서 직접 호출해볼 수 있다.
- 운영 서버에서는 swagger-ui와 스펙 경로를 열지 않는다.

## 공통 규칙

### 경로

모든 API는 `/api/v1`으로 시작한다.

### 응답 봉투

```json
{ "isSuccess": true,  "data": { } }
{ "isSuccess": false, "code": "INVALID_REFRESH_TOKEN", "message": "다시 로그인해야 합니다." }
```

- **성공 여부는 `isSuccess`로 본다.** HTTP 상태 코드도 정확하게 내려가지만, 어느 쪽을 봐야 하는지 헷갈리지 않도록 본문에도 담는다.
- **`code`와 `message`는 실패 전용**이다. 성공 응답에는 들어 있지 않다.
- 화면 분기는 **`code`** 로 한다. enum 이름이라 바뀌지 않는다. 성공했을 때 분기가 필요하면 `data`의 필드로 준다.
- `message`는 표시·디버깅용이다. 문구가 바뀔 수 있고, 요청 형식 오류의 메시지는 서버 로케일에 따라 달라진다. 분기에 쓰지 않는다.
- 실패하면 `data`는 들어 있지 않다. 성공이어도 돌려줄 값이 없으면 빠진다(로그아웃 등).

### 모든 API에서 날 수 있는 에러

| 상태 | code | 언제 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 잘못된 JSON 등 요청 형식 오류 |
| 404·405·415 등 | `INVALID_REQUEST` | 없는 경로, 지원하지 않는 메서드나 Content-Type. 상태 코드는 원래 값 그대로다 |
| 401 | `UNAUTHENTICATED` | 인증이 필요한 API에 토큰이 없거나 만료·무효 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

인증 관련 코드는 다음과 같다.

| 상태 | code | 언제 | 화면에서 할 일 |
| --- | --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | refresh가 만료·무효. 탈퇴했거나 다른 기기에서 로그인함 | 로그인 화면으로 |
| 401 | `INVALID_SIGNUP_TOKEN` | 가입 세션이 30분을 넘김 | 카카오 로그인부터 다시 |
| 401 | `INVALID_SOCIAL_CODE` | 인가코드가 만료·위조됨 | 카카오 로그인부터 다시 |
| 409 | `DUPLICATED_NICKNAME` | 이미 쓰이는 닉네임 | 다른 닉네임 입력 안내 |

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

- 소셜에서 받은 정보(닉네임, 프로필 사진)는 **서버가 30분간 보관**한다. 클라이언트가 들고 있다가 보내는 방식이 아니다.
- 프로필 완성 화면에서 닉네임을 바꿀 수 있다. 기본값은 소셜 닉네임이고, 응답에 담겨 오지 않으므로 화면에서 입력받는다.
- 가입을 마치면 `signUpToken`은 무효가 된다. 중간에 이탈하면 30분 뒤 사라지고, 다시 카카오 로그인부터 시작하면 된다.
- 닉네임이 이미 쓰이고 있으면 `409 DUPLICATED_NICKNAME`이 온다. 다른 닉네임을 입력받는다.

### 로그아웃

`DELETE /api/v1/auth/tokens`를 access token으로 호출한다. 서버의 refresh token이 지워지고 쿠키가 만료된다.
이미 발급된 access token은 만료(최대 30분)까지 유효하므로 클라이언트에서 버린다.

## 화면별로 쓰는 API

| 화면 | 호출 | 응답에 따른 처리 |
| --- | --- | --- |
| 앱 시작 | `POST /api/v1/auth/tokens` (credentials 포함) | 200이면 access token을 받아 메인. 401이면 비로그인 상태로 둔다 |
| 로그인 | `GET /api/v1/auth/kakao/authorize`로 이동 | 돌아오면 `?signUp` 여부로 분기 |
| 프로필 완성 | `POST /api/v1/auth/signup` | 200이면 access token 저장 후 메인. 409면 닉네임 변경 안내 |
| 설정 · 로그아웃 | `DELETE /api/v1/auth/tokens` | 응답과 무관하게 메모리의 토큰을 버리고 로그인 화면 |

일정·알림 API는 아직 없다. 생기면 이 표에 추가한다.

## 로컬에서 서버 띄우기

FE 개발 중 실제 서버에 붙여볼 때.

1. `cd snsg && ./gradlew bootRun` → `http://localhost:8080`

### 웹 FE 개발 서버에서 로컬 서버 호출하기

- FE 개발 서버는 `http://localhost:8080`으로 API를 호출한다.
- **서버에 CORS 설정이 아직 없다.** FE 개발 서버 주소가 정해지면 알려줘야 추가할 수 있다. 등록 전에는 쿠키가 오가지 않아 로그인이 끝까지 되지 않는다.
- 서버 주소는 FE 환경 설정으로 바꿀 수 있게 둔다. 운영 서버가 생기면 주소만 바꾼다.

### 서버에 알려줘야 하는 값

| 값 | 쓰이는 곳 | 현재 임시값 |
| --- | --- | --- |
| FE 개발 서버 주소 | CORS 허용 출처, 쿠키 전달 | 없음 (설정 안 됨) |
| 로그인 후 돌아올 화면 주소 | 서버가 리다이렉트할 곳(`FRONTEND_REDIRECT_URI`) | `http://localhost:3000/auth/callback` |
| 운영·개발 서버 도메인 | 쿠키의 `SameSite`·`Secure` 설정 | 미정 |

쿠키는 지금 `SameSite=None; Secure`다. FE와 BE가 다른 출처라는 전제다. 같은 도메인으로 묶거나 FE 개발 서버 프록시를 쓰면 `Lax`로 조일 수 있고, 그편이 더 안전하다.

## 이 문서를 고치는 때

API를 바꾸는 PR에서 함께 고친다.

- 요청·응답·에러 코드가 바뀌면: 컨트롤러의 springdoc 애노테이션을 고친다. swagger-ui에 그대로 반영된다. (스펙 파일 생성을 붙이면 `openapi.yaml`도 함께 커밋한다)
- 흐름, 공통 규칙, 화면별로 쓰는 API가 바뀌면: 이 문서를 고친다. 이건 빌드가 잡아주지 않는다.

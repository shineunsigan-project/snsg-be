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
| 404·405·415 등 | `INVALID_REQUEST` | 없는 경로, 지원하지 않는 메서드나 Content-Type. 상태 코드는 원래 값 그대로다. 경로는 맞는데 대상이 없어서 나는 404는 업무 코드(`SCHEDULE_NOT_FOUND` 등)로 온다. 상태 코드가 아니라 `code`로 구분한다 |
| 401 | `UNAUTHENTICATED` | 인증이 필요한 API에 토큰이 없거나 만료·무효 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

인증 관련 코드는 다음과 같다.

| 상태 | code | 언제 | 화면에서 할 일 |
| --- | --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | refresh가 만료·무효. 14일 동안 재발급이 없었거나, 이 기기에서 로그아웃했거나, 탈퇴했거나, 같은 기기에서 재발급이 겹침 | 로그인 화면으로 |
| 401 | `INVALID_SIGNUP_TOKEN` | 가입 세션이 15분을 넘김 | 카카오 로그인부터 다시 |
| 401 | `INVALID_SOCIAL_CODE` | 인가코드가 만료·위조됨 | 카카오 로그인부터 다시 |
| 409 | `DUPLICATED_NICKNAME` | 이미 쓰이는 닉네임(대소문자 무시) | 다른 닉네임 입력 안내 |
| 400 | `UNDERAGE` | 가입일 기준 만 18세 미만 | 가입할 수 없음을 안내 |
| 400 | `INVALID_PROFILE_IMAGE` | JPG·PNG가 아니거나 빈 파일. 5MB를 넘는 파일은 이 코드가 아니라 `413 INVALID_REQUEST`로 온다(업로드 크기 한도에서 먼저 걸린다) | 다른 사진 선택 안내 |
| 400 | `INVALID_PROFILE_IMAGE_URL` | 가입·프로필 수정 요청의 `profileImageUrl`이 업로드 API가 준 경로가 아니거나, 다른 회원이 쓰고 있는 경로 | FE 버그다. 업로드 응답의 값을 그대로 넣는다 |
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
4. 재발급이 `401 INVALID_REFRESH_TOKEN`이면 로그인 화면으로 보낸다. 14일 동안 재발급이 없었거나, 이 기기에서 로그아웃했거나, 탈퇴한 경우다. 다른 기기에서 로그인해도 이 기기의 로그인은 유지되므로 그 때문에 401이 오지는 않는다.

### 가입

소셜 인증과 가입은 나뉘어 있다. 소셜 인증만으로는 **사용자가 만들어지지 않는다.** 이름·닉네임·성별·생년월일을 받아야 가입이 완료된다.

- 소셜에서 받은 정보(닉네임, 프로필 사진)는 **서버가 15분간 보관**한다. 클라이언트가 들고 있다가 보내는 방식이 아니다.
- 가입을 마치면 `signUpToken`은 무효가 된다. 중간에 이탈하면 15분 뒤 사라지고, 다시 카카오 로그인부터 시작하면 된다.
- **같은 소셜 계정으로 다시 로그인하면 이전 `signUpToken`은 즉시 무효가 된다.** 한 계정에 유효한 토큰은 항상 하나다.
- 닉네임이 이미 쓰이고 있으면 `409 DUPLICATED_NICKNAME`이 온다. 다른 닉네임을 입력받는다.

**가입 요청(`POST /api/v1/auth/signup`)의 입력 규칙**

| 필드 | 필수 | 규칙 | 틀렸을 때 `errors[].message` |
| --- | --- | --- | --- |
| `name` | 예 | 2~20자의 한글 또는 영문. 공백·숫자 불가. 앞뒤 공백도 다듬지 않고 거부한다. 남에게 보이지 않고 가입 후 바꿀 수 없다 | 이름은 2~20자의 한글 또는 영문만 쓸 수 있어요. |
| `nickname` | 예 | 2~10자의 한글·영문·숫자·`.`·`_` | 닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요. |
| `email` | 아니오 | 생략하거나 `""`이면 없음. 값이 있으면 `local@domain.tld` 형식, 최대 100자. 공백만 있거나 앞뒤에 공백이 있으면 거부한다. 대소문자는 입력한 그대로 저장한다 | 이메일 형식이 올바르지 않아요. |
| `gender` | 예 | `FEMALE`·`MALE` | 성별을 선택해주세요. |
| `birthDate` | 예 | 과거 날짜(`yyyy-MM-dd`) | 생년월일이 올바르지 않습니다. |

- 위 형식 오류는 모두 `400 INVALID_REQUEST`와 `errors`로 온다. 필드별 안내는 `errors`로 한다.
- `gender`에 `FEMALE`·`MALE` 밖의 값을 보내거나 `2010-02-30`처럼 없는 날짜를 보내면 JSON 변환 단계에서 실패해 `400 INVALID_REQUEST`만 오고 `errors`는 없다. 화면에서 고를 수 있는 값만 보내면 나지 않는다.
- 형식은 맞지만 가입일 기준 **만 18세 미만**이면 `400 UNDERAGE`("만 18세 이상만 가입할 수 있어요."). 2월 29일생은 평년에는 3월 1일에 한 살을 먹는다.
- 닉네임 중복은 **대소문자를 무시**한다. 누가 `Mint`를 쓰고 있으면 `mint`도 `409 DUPLICATED_NICKNAME`이다. 닉네임을 바꾸거나 탈퇴한 회원의 이전 닉네임도 **보호 기간**(바꾸기 전 닉네임 14일, 탈퇴 90일) 동안은 409다. 기간이 지나면 다시 쓸 수 있다. 보호 이력은 닉네임 변경·탈퇴가 남긴다. 두 사람이 같은 닉네임으로 동시에 가입해도 한쪽은 409를 받는다.
- 입력 중에 미리 확인하려면 아래 [닉네임 사용 가능 여부](#닉네임-사용-가능-여부)를 부른다. 결과는 안내용이고, 409는 가입 요청에서도 날 수 있다.

**프로필 완성 화면의 기본값**은 `GET /api/v1/auth/signup/session`으로 받는다(`signUpToken` 쿠키 필요).

```json
{ "isSuccess": true, "data": { "nickname": "민트초코", "profileImageUrl": "http://k.kakaocdn.net/..." } }
```

- `nickname`은 입력칸의 기본값으로 쓰고 사용자가 바꿀 수 있다. **가입 요청에는 화면에서 입력받은 값을 보낸다.** 서버가 세션의 닉네임을 쓰지 않는다.
- `profileImageUrl`은 **`null`일 수 있다.** 소셜 기본 이미지였거나 사진을 받지 못한 경우다. 이때는 우리 기본 이미지(`/images/default-profile.png`)가 쓰인다.
- 401 `INVALID_SIGNUP_TOKEN`이면 세션이 만료된 것이다. 카카오 로그인부터 다시 한다.

**프로필 사진을 바꾸려면** 먼저 `POST /api/v1/auth/signup/profile-image`로 올리고, 받은 경로를 가입 요청의 `profileImageUrl`에 넣는다.

- `multipart/form-data`, 파트 이름은 `image`. `signUpToken` 쿠키가 필요하다.
- 5MB 이하의 JPG·PNG만 받는다. **형식은 파일 내용으로 판별하므로 확장자만 바꿔 올리면 거부된다.** JPG·PNG가 아니거나 빈 파일이면 400 `INVALID_PROFILE_IMAGE`("JPG, PNG 형식의 사진만 올릴 수 있어요.")다.
- **5MB를 넘는 파일은 `400 INVALID_PROFILE_IMAGE`가 아니라 `413 INVALID_REQUEST`로 온다.** 업로드 크기 한도에서 먼저 걸리기 때문이다. 올리기 전에 화면에서 크기를 확인해 막고, 413을 받으면 5MB 이하 사진을 고르라고 안내한다.
- 응답은 `{ "profileImageUrl": "/images/profiles/<uuid>.png" }`. 도메인이 붙지 않은 경로이므로 화면에 쓸 때 서버 주소를 앞에 붙인다.
- 가입 요청의 `profileImageUrl`에는 **이 API가 돌려준 경로만** 넣을 수 있다. 다른 주소, 형식을 바꾼 경로, 다른 회원이 쓰고 있는 경로를 넣으면 400 `INVALID_PROFILE_IMAGE_URL`이다.
- 비워 두면(`null` 또는 생략) 소셜에서 받은 사진을 쓴다. 그것도 없으면 기본 이미지가 된다.
- 올리기만 하고 가입을 완료하지 않으면 그 사진은 쓰이지 않는다.

### 닉네임 사용 가능 여부

`GET /api/v1/users/nickname-availability?nickname=<값>`. 입력한 닉네임을 쓸 수 있는지 알려준다.

- **부르는 화면**: 가입 닉네임 입력(프로필 완성 화면), 프로필 수정 화면. 사용자가 **닉네임 칸을 벗어날 때** 부른다.
- **결과는 안내용이다.** 가입·프로필 수정 API가 저장할 때 같은 검사를 다시 한다. 여기서 `true`였어도 그 사이 다른 사람이 먼저 쓰면 가입·수정이 `409 DUPLICATED_NICKNAME`으로 실패할 수 있다. 그때는 다른 닉네임을 입력받는다.
- **토큰은 선택이다.**
  - 가입 중에는 access token이 없으므로 토큰 없이 부른다.
  - 프로필 수정 화면에서는 access token을 붙여 부른다. 토큰이 있으면 **본인의 현재 닉네임은 사용 가능(`true`)** 으로 나온다(대소문자 무시). 붙이지 않으면 본인 닉네임도 `DUPLICATED`로 나온다.
  - 토큰이 틀리거나 만료돼도 **401이 오지 않는다.** 토큰 없이 부른 것과 같게 처리된다. 이 API 때문에 재발급을 시도할 필요는 없다.
- 요청(쿼리 파라미터):

  | 파라미터 | 필수 | 의미·형식 |
  | --- | --- | --- |
  | `nickname` | 필수 | 확인할 닉네임. 입력칸의 값을 그대로 보낸다. 형식 검사도 서버가 한다 |

- 응답 예시:
  ```json
  { "isSuccess": true, "data": { "available": true, "reason": null } }
  ```
  ```json
  { "isSuccess": true, "data": { "available": false, "reason": "DUPLICATED" } }
  ```
  - `available`: 쓸 수 있으면 `true`. null이 아니다.
  - `reason`: 쓸 수 없는 이유. **`available`이 `true`면 `null`이다.**

  | `reason` | 뜻 | 화면에서 할 일 |
  | --- | --- | --- |
  | `INVALID_FORMAT` | 2~10자의 한글·영문·숫자·`.`·`_`가 아니다. 빈 값도 여기다 | 형식 안내 문구("닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요.")를 보여준다 |
  | `DUPLICATED` | 다른 회원이 쓰고 있거나, 바꾸거나 탈퇴한 회원의 이전 닉네임이라 보호 기간(바꾸기 전 닉네임 14일, 탈퇴 90일)이 남았다. 대소문자는 무시한다 | 이미 쓰이는 닉네임이라고 안내하고 다른 닉네임을 입력하게 한다 |

  - `DUPLICATED`는 사용 중과 보호 중을 **구분하지 않는다.** 보호 중이라고 알리면 다른 회원이 탈퇴했거나 닉네임을 바꿨다는 사실이 드러나기 때문이다. 화면 문구도 나누지 않는다.
  - 판정 순서는 형식 → 본인의 현재 닉네임 → 사용 중·보호 중이다. 형식이 틀리면 중복 여부와 상관없이 `INVALID_FORMAT`이다.
- **형식 오류·중복도 200이다.** 결과는 `data.available`과 `data.reason`으로 본다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 400 | `INVALID_REQUEST` | `nickname` 파라미터가 아예 없다. `?nickname=`처럼 빈 값으로 보내면 400이 아니라 200 `INVALID_FORMAT`이다 | 정상 화면 흐름에서는 나지 않는다. 파라미터를 빠뜨리지 않는다 |

- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.** 실패하면 그대로 재시도한다.

### 로그아웃

`DELETE /api/v1/auth/tokens`를 access token으로 호출한다. **이 기기의 로그인만 끝난다.** refreshToken 쿠키가 가리키는 세션이 서버에서 지워지고 쿠키가 만료된다. 다른 기기(브라우저)의 로그인은 유지된다.

- 쿠키를 읽으므로 credentials를 포함해 호출한다(공통 규칙). 쿠키 없이 불러도 200이지만 그때는 서버 세션이 지워지지 않고 14일 뒤 만료된다.
- 쿠키가 없거나 이미 끝난 세션이어도 200이다. 다시 호출해도 안전하다.
- 이미 발급된 access token은 만료(최대 30분)까지 유효하므로 클라이언트에서 버린다.

### 탈퇴

`DELETE /api/v1/users/me`를 access token으로 호출한다.

- 계정이 삭제 표시된다. **되돌릴 수 없다.** 이름·이메일·닉네임·생년월일·프로필 사진 원본은 90일 보관한 뒤 비식별 값으로 바뀌고, 그때 올렸던 사진 파일도 지워진다.
- 탈퇴한 회원의 닉네임은 90일 동안 다른 사람이 쓸 수 없다(`DUPLICATED_NICKNAME`).
- **카카오 연결도 서버가 끊는다.** 사용자가 카카오 설정에서 따로 끊을 필요가 없다.
- 모든 기기의 refresh token이 즉시 무효가 된다. 다른 기기의 로그인도 끊긴다. **쿠키는 서버가 비우지 않으므로** 탈퇴 직후 `DELETE /api/v1/auth/tokens`(로그아웃)를 한 번 더 호출해 쿠키를 정리한다.
- 다시 가입하면 **이전 활동과 이어지지 않는 새 계정**이 된다. 같은 카카오 계정이어도 그렇다.

### 사용자가 카카오에서 연결을 끊은 경우

사용자가 카카오계정 설정에서 우리 서비스 연결을 끊으면 **카카오가 서버로 알려주고, 서버가 그 사용자를 탈퇴 처리한다.** FE가 할 일은 없다.

다만 그 시점에 로그인 상태였다면 **다음 재발급부터 401 `INVALID_REFRESH_TOKEN`** 이 온다. 401 처리(로그인 화면으로)를 그대로 따르면 된다. **재발급을 반복 시도하지 않는다.**

## 내 정보

세 API 모두 **인증이 필요하다.** 토큰이 없거나 만료·무효면 401 `UNAUTHENTICATED`다. 재발급 후 다시 부르고, 재발급도 실패하면 로그인 화면으로 간다.

### 내 프로필 조회

`GET /api/v1/users/me`. 로그인한 사용자의 프로필을 돌려준다.

- **부르는 화면**: 내 정보 화면(MY-01)에 들어올 때, 프로필 수정 화면(MY-02)을 열 때.
- 응답 예시:
  ```json
  { "isSuccess": true, "data": {
      "id": "01a19cc6-3d80-7000-8000-000000000001", "name": "김민트", "nickname": "민트초코",
      "gender": "FEMALE", "birthDate": "1996-04-12", "email": "mint@example.com",
      "profileImageUrl": "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png",
      "ageGroup": { "decade": 20, "phase": "LATE" },
      "nicknameChangeableAt": "2026-11-23T14:30:00" } }
  ```
  - `name`, `gender`(`FEMALE`·`MALE`), `birthDate`: **바꿀 수 없다.** 프로필 수정 화면에서도 읽기 전용으로 보여준다.
  - `email`: 등록하지 않았으면 **빈 문자열**이다. null이 아니다.
  - `profileImageUrl`: 도메인이 붙지 않은 경로이거나 소셜 사진 주소다. 사진을 올린 적이 없으면 소셜 사진 주소나 기본 이미지(`/images/default-profile.png`)다. 경로로 시작하면 서버 주소를 앞에 붙여 쓴다.
  - `ageGroup`: 오늘(한국 시간) 기준 만 나이로 계산한 연령대다. `decade`는 십의 자리 × 10이다. 만 18·19세는 `10`(10대 후반)이다.

    | `phase` | 뜻 | 만 나이의 일의 자리 |
    | --- | --- | --- |
    | `EARLY` | 초반 | 0~3 |
    | `MID` | 중반 | 4~6 |
    | `LATE` | 후반 | 7~9 |

  - `nicknameChangeableAt`: 닉네임을 다시 바꿀 수 있는 시각(한국 시간). **null이면 지금 바꿀 수 있다**(바꾼 적이 없을 때 포함). 값이 있으면 프로필 수정 화면에서 닉네임 칸을 막고 그 날짜를 안내한다.
  - `nicknameChangeableAt` 외의 필드는 null이 아니다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 404 | `USER_NOT_FOUND` | 토큰의 사용자가 없거나 탈퇴했다(남은 access token으로 호출) | 로그인 화면으로 |

- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.**

### 프로필 수정

`PATCH /api/v1/users/me`. 닉네임·이메일·프로필 사진을 바꾼다.

- **부르는 화면**: 프로필 수정 화면(MY-02)의 [저장].
- **바꿀 필드만 보낸다.** 생략하거나 `null`인 필드는 그대로 둔다. 세 필드 모두 선택이다.

  | 필드 | 규칙 | 틀렸을 때 `errors[].message` |
  | --- | --- | --- |
  | `nickname` | 2~10자의 한글·영문·숫자·`.`·`_`. 14일 변경 제한(아래) | 닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요. |
  | `email` | `local@domain.tld` 형식, 최대 100자. **`""`이면 이메일을 지운다.** 앞뒤 공백은 다듬지 않고 거부한다 | 이메일 형식이 올바르지 않아요. |
  | `profileImageUrl` | 아래 [프로필 사진 업로드](#프로필-사진-업로드)가 돌려준 경로만. 바꾸면 이전에 올렸던 사진 파일은 지워진다 | (`errors` 없이 400 `INVALID_PROFILE_IMAGE_URL`) |

- **이름·성별·생년월일은 바꿀 수 없다.** 요청에 넣어도 오류 없이 무시된다.
- **현재 값과 정확히 같은 값은 바꾸지 않은 것으로 본다.** 변경 제한에도 세지 않는다. 닉네임은 **대소문자만 달라도 변경**이다(`Mint` → `mint`).
- **사진을 지우는(기본 이미지로 되돌리는) 기능은 없다.** `profileImageUrl`에 `""`를 보내면 400 `INVALID_PROFILE_IMAGE_URL`이다.
- **닉네임은 14일에 한 번만 바꿀 수 있다.** 마지막 변경 후 14일 안에 다른 닉네임을 보내면 409 `NICKNAME_CHANGE_RESTRICTED`다. 가입 후 첫 변경은 제한이 없다. 다시 바꿀 수 있는 시각은 응답과 내 프로필 조회의 `nicknameChangeableAt`으로 안다.
  - 닉네임 사용 가능 여부 API는 이 제한을 보지 않는다. 그 API가 `true`여도 제한 기간이면 409 `NICKNAME_CHANGE_RESTRICTED`다.
  - 바꾸기 전 닉네임은 14일 동안 다른 사람이 쓰지 못한다.
- **검사 순서는 형식 → 닉네임 변경 제한 → 닉네임 중복이다.** 하나라도 실패하면 **아무것도 저장하지 않는다.** 닉네임은 맞고 이메일만 틀려도 닉네임은 바뀌지 않는다.
- 성공하면 200이고, `data`는 바뀐 뒤의 내 프로필이다(내 프로필 조회와 같은 형태). 닉네임을 바꿨으면 `nicknameChangeableAt`에 약 14일 뒤 시각이 담긴다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 400 | `INVALID_REQUEST` | 닉네임·이메일 형식 위반(`errors`에 필드별 문구), 잘못된 JSON | 해당 입력칸 아래에 `message`를 보여준다 |
  | 400 | `INVALID_PROFILE_IMAGE_URL` | 업로드 API가 준 경로가 아니다. 빈 문자열, 다른 주소, 형식을 바꾼 경로, 다른 회원이 쓰고 있는 경로 | FE 버그다. 업로드 응답의 값을 그대로 넣는다. 사진을 다시 올리게 한다 |
  | 400 | `INVALID_NICKNAME`·`INVALID_EMAIL` | 서버의 안전망. 요청 검증을 통과한 값이면 나지 않는다 | `message`를 보여준다 |
  | 409 | `NICKNAME_CHANGE_RESTRICTED` | 마지막 닉네임 변경 후 14일이 지나지 않았다 | `nicknameChangeableAt`으로 바꿀 수 있는 날짜를 안내한다 |
  | 409 | `DUPLICATED_NICKNAME` | 다른 회원이 쓰고 있거나 보호 기간이 남은 닉네임(대소문자 무시) | 다른 닉네임을 입력받는다 |
  | 404 | `USER_NOT_FOUND` | 토큰의 사용자가 없거나 탈퇴했다 | 로그인 화면으로 |

  ```json
  { "isSuccess": false, "code": "NICKNAME_CHANGE_RESTRICTED", "message": "닉네임은 14일에 한 번만 바꿀 수 있어요." }
  ```
- **같은 요청을 다시 보내도 된다.** 두 번째 요청은 바뀐 것이 없어 그대로 성공한다(닉네임 변경 제한에 걸리지 않는다).

### 프로필 사진 업로드

`POST /api/v1/users/me/profile-image`. 사진 파일을 저장하고 경로를 돌려준다.

- **부르는 화면**: 프로필 수정 화면(MY-02)에서 사진을 고를 때.
- **저장만 하고 프로필에는 반영하지 않는다.** 받은 경로를 [저장] 때 `PATCH /api/v1/users/me`의 `profileImageUrl`에 넣어야 반영된다. 저장하지 않고 나가면 그 사진은 쓰이지 않는다.
- `multipart/form-data`, 파트 이름은 `image`. 파일 규칙(5MB 이하 JPG·PNG, 내용으로 판별)과 5MB 초과가 `413 INVALID_REQUEST`로 오는 점은 가입의 사진 업로드와 같다.
- 응답은 `{ "profileImageUrl": "/images/profiles/<uuid>.png" }`. 도메인이 붙지 않은 경로다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 400 | `INVALID_PROFILE_IMAGE` | JPG·PNG가 아니거나 빈 파일 | 다른 사진을 고르게 한다 |
  | 500 | `PROFILE_IMAGE_STORE_FAILED` | 서버가 사진을 저장하지 못했다 | 잠시 후 다시 시도하게 한다 |

- 다시 보내면 새 파일로 저장되고 다른 경로가 온다. **마지막에 받은 경로만** 프로필 수정에 넣는다.

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

## 활동

### 활동 목록

`GET /api/v1/activity-categories`. 일정에서 고를 수 있는 활동(보드게임·방탈출·산책) 목록이다.

- **부르는 화면**: 일정 개설의 활동 선택, 일정 목록의 활동 필터.
- **인증이 필요 없다.** 토큰 없이 불러도 200이다.
- **활성 활동만 온다.** 숨긴(비활성) 활동은 목록에서 빠진다.
- **정렬은 서버가 정한 노출 순서(display_order)다.** 받은 순서 그대로 화면에 그린다. 가나다순 등으로 다시 정렬하지 않는다.
- 응답 예시:
  ```json
  { "isSuccess": true, "data": [ { "id": "01a19cc6-3d80-7000-8000-000000000001", "code": "BOARD_GAME", "name": "보드게임" }, { "id": "01a19cc6-3d80-7000-8000-000000000002", "code": "ESCAPE_ROOM", "name": "방탈출" }, { "id": "01a19cc6-3d80-7000-8000-000000000003", "code": "WALK", "name": "산책" } ] }
  ```
  - `id`: 활동 ID(UUID 문자열). 다른 API에 활동을 넘길 때 이 값을 쓴다.
  - `code`: 활동 코드(`BOARD_GAME` 등). 화면에 보여줄 이름이 바뀌어도 그대로인 식별자다. 활동은 데이터로 추가되므로 위 세 값 외의 코드가 올 수 있다.
  - `name`: 화면에 보여줄 활동 이름.
  - 세 필드 모두 null이 아니다. 활성 활동이 하나도 없으면 `data`는 빈 배열이다.
- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.** 실패하면 그대로 재시도한다.

## 일정

### 장소 후보

`GET /api/v1/place-candidates?categoryId=&regionCode=`. 고른 활동으로 갈 수 있는 장소 목록이다(보드게임 → 보드게임카페, 방탈출 → 방탈출 매장, 산책 → 공원).

- **부르는 화면**: 장소 선택 화면(CRT-02)에 들어올 때, 지역 칩을 바꿀 때. 목록과 지도 핀을 이 결과로 그린다.
- **인증이 필요 없다.** 토큰 없이 불러도 200이다.
- **노출 대상 장소만 온다.** 검수를 통과했고, 폐업하지 않았고, 서비스 중인 지역에 있는 장소다.
- 요청(쿼리 파라미터):

  | 파라미터 | 필수 | 의미·형식 |
  | --- | --- | --- |
  | `categoryId` | 필수 | 활동 목록 API(`GET /api/v1/activity-categories`)의 `id`(UUID 문자열) |
  | `regionCode` | 선택 | 지역 목록 API(`GET /api/v1/regions`)의 `code`(5자리 문자열). **생략하면 서비스 중인 지역 전체다.** 전체를 보려면 파라미터를 아예 빼고, `regionCode=`처럼 빈 값으로 보내지 않는다. 빈 값은 400 `INVALID_REGION`이다 |

- 응답 예시:
  ```json
  { "isSuccess": true, "data": [ { "id": "01a1a2b3-0000-7000-8000-000000000001", "name": "보드게임카페 홍대점", "roadAddress": "서울특별시 마포구 와우산로 1", "addressDetail": "2층", "latitude": 37.5512345, "longitude": 126.9212345, "regionCode": "11440" } ] }
  ```
  - `id`: 장소 ID(UUID 문자열). 고른 장소의 `id`를 일정 개설의 `placeId`로 보낸다.
  - `name`: 장소명.
  - `roadAddress`: 도로명주소. 도로명주소가 없는 장소는 지번주소가 온다. 상세주소(층 등)는 들어 있지 않다.
  - `addressDetail`: 상세주소(층 등). **없으면 빈 문자열**이다. 표시할 때 `roadAddress` 뒤에 붙인다.
  - `latitude`, `longitude`: 위도·경도. WGS84 좌표의 소수(JSON 숫자)다. 지도 핀에 쓴다.
  - `regionCode`: 장소가 속한 지역 코드. 지역 목록의 `code`와 같은 5자리 문자열이다.
  - 모든 필드가 null이 아니다.
- **정렬은 지역 노출 순서(지역 목록과 같은 순서), 같은 지역 안에서는 장소명 가나다순이다.** 받은 순서 그대로 그린다.
- **페이지를 나누지 않는다.** 조건에 맞는 장소를 한 번에 모두 준다.
- **결과가 없으면 200과 빈 배열이다.** 화면에 "결과 없음"을 보여준다. 오류가 아니다.
- 산책(공원) 후보는 공원 장소를 적재하기 전까지 비어 있을 수 있다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 400 | `INVALID_REQUEST` | `categoryId`가 없거나 UUID 형식이 아니다 | 정상 화면 흐름에서는 나지 않는다. 활동 선택부터 다시 하게 한다 |
  | 400 | `INVALID_CATEGORY` | 없거나 숨긴 활동. 지역도 틀렸어도 이 코드가 먼저 온다 | 활동 목록을 다시 불러 고르게 한다 |
  | 400 | `INVALID_REGION` | 없거나 서비스하지 않는 지역, 빈 값으로 보낸 `regionCode` | 지역 목록을 다시 불러 칩을 다시 그리고 고르게 한다 |

  ```json
  { "isSuccess": false, "code": "INVALID_REQUEST", "message": "요청 형식이 올바르지 않습니다." }
  ```
  ```json
  { "isSuccess": false, "code": "INVALID_CATEGORY", "message": "선택할 수 없는 활동이에요. 활동을 다시 골라주세요." }
  ```
  ```json
  { "isSuccess": false, "code": "INVALID_REGION", "message": "선택할 수 없는 지역이에요. 지역을 다시 골라주세요." }
  ```
- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.** 실패하면 그대로 재시도한다.

### 일정 개설

`POST /api/v1/schedules`. 일정을 연다.

- **부르는 화면**: 일정 개설 화면(CRT-06)의 [일정 열기].
- **인증이 필요하다.** 토큰이 없거나 만료되면 401 `UNAUTHENTICATED`다. 재발급 후 다시 부르고, 재발급도 실패하면 로그인 후 개설 화면으로 돌아온다.
- 개설자는 확정 참여자 1명으로 함께 들어가고, 일정은 모집 중으로 시작한다.
- 요청 예시:
  ```json
  {
    "categoryId": "01a19cc6-3d80-7000-8000-000000000001",
    "placeId": "01a1a2b3-0000-7000-8000-000000000001",
    "title": "보드게임 같이 해요",
    "durationType": "NORMAL",
    "description": "처음 오시는 분도 환영해요. 룰은 같이 배우면서 해요.",
    "startAt": "2026-11-07T19:30:00",
    "capacity": 6
  }
  ```
  일곱 필드 모두 필수다.

  | 필드 | 의미 | 형식·범위 |
  | --- | --- | --- |
  | `categoryId` | 활동 | 활동 목록 API(`GET /api/v1/activity-categories`)의 `id`. 숨긴 활동은 고를 수 없다 |
  | `placeId` | 장소 | 장소 후보 API(`GET /api/v1/place-candidates`)의 `id`. 활동이 고를 수 있는 유형의 장소여야 한다(보드게임 → 보드게임카페, 방탈출 → 방탈출 매장, 산책 → 공원). **장소명·주소·좌표는 보내지 않는다.** 서버가 장소에서 읽어 개설 시점 값으로 저장한다. 그 뒤 장소 정보가 바뀌거나 폐업해도 일정에는 개설 시점 값이 남는다 |
  | `title` | 활동명 | 앞뒤 공백을 뺀 길이로 1~20자. 저장되는 값은 앞뒤 공백을 뺀 값이다 |
  | `durationType` | 예상 소요 시간 | `SHORT` 1시간 내외(60분), `NORMAL` 2시간 내외(120분), `LONG` 3시간 이상(180분). 이 셋 밖의 값은 400 `INVALID_REQUEST` |
  | `description` | 간략 설명 | 앞뒤 공백을 뺀 길이로 10~500자. 줄바꿈을 쓸 수 있고 한 글자로 센다. 저장되는 값은 앞뒤 공백을 뺀 값이다 |
  | `startAt` | 시작 일시 | 오프셋 없는 한국 시간(`2026-11-07T19:30:00`). 분은 00 또는 30, 초는 0. **지금 + 1시간 이후**(정확히 1시간 뒤는 허용)이고 **시작 날짜가 오늘 + 30일 이내**(30일째 날은 23:30까지 허용) |
  | `capacity` | 인원 | 개설자를 포함한 전체 인원. 4~24 |

  길이는 글자 수로 센다. 이모지도 한 글자다.
- 성공하면 **201**이다. 내가 연 일정 목록(LIST-05)으로 이동하고 개설 완료 팝업을 띄운다.
  ```json
  { "isSuccess": true, "data": { "scheduleId": "01a1a2b3-4c5d-7e6f-8a9b-0c1d2e3f4a5b" } }
  ```
  - `scheduleId`: 만든 일정의 ID(UUID 문자열). null이 아니다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 400 | `INVALID_REQUEST` | 필드 누락, 활동명·설명 길이 위반(`errors`에 필드별 문구), `durationType`이 셋 밖, 잘못된 JSON·날짜 형식 | `errors`가 있으면 해당 입력칸 아래에 `message`를 보여준다 |
  | 400 | `INVALID_CATEGORY` | 없거나 숨긴 활동 | 활동 목록을 다시 불러 고르게 한다 |
  | 400 | `INVALID_PLACE` | 없는 장소, 노출이 끝난 장소(검수 전·제외·폐업·서비스하지 않는 지역), 활동과 유형이 다른 장소 | 장소를 다시 고르게 한다 |
  | 400 | `INVALID_START_AT` | 30분 단위가 아니거나, 지금 + 1시간보다 이르거나, 오늘 + 30일보다 늦은 시작 일시 | 시작 일시를 다시 고르게 한다. 화면에 오래 머문 사이 1시간 기준을 넘겼을 수 있다 |
  | 400 | `INVALID_CAPACITY` | 인원이 4~24 밖 | 인원을 다시 고르게 한다 |
  | 400 | `INVALID_TITLE` | 활동명 길이 위반. 서버의 안전망이라 요청 검증을 통과한 값이면 나지 않는다 | `message`를 보여준다 |
  | 400 | `INVALID_DESCRIPTION` | 설명 길이 위반. 서버의 안전망이라 요청 검증을 통과한 값이면 나지 않는다 | `message`를 보여준다 |
  | 409 | `SCHEDULE_TIME_CONFLICT` | 내가 개설했거나 참여 확정한 예정 일정과 시작 시각이 12시간 미만으로 떨어진다. 정확히 12시간 차이는 허용한다 | 시작 일시를 바꾸게 안내한다 |
  | 401 | `USER_NOT_ACTIVE` | 탈퇴한 사용자(남은 access token으로 호출) | 로그인 화면으로 |
  | 401 | `UNAUTHENTICATED` | 토큰이 없거나 만료·무효 | 재발급 후 다시 부르고, 실패하면 로그인 화면으로 |

  ```json
  { "isSuccess": false, "code": "SCHEDULE_TIME_CONFLICT", "message": "앞뒤 12시간 안에 참여하는 다른 일정이 있어요." }
  ```
- **두 번 눌렸을 때**: 같은 사용자의 요청은 서버에서 차례로 처리된다. 두 번째 요청은 첫 번째가 만든 일정과 시작 시각이 같아 409 `SCHEDULE_TIME_CONFLICT`가 되고, **중복 개설은 생기지 않는다.** 그래도 첫 응답을 받기 전까지 [일정 열기] 버튼을 막는다. 첫 응답을 받지 못한 채 다시 보내 409를 받았다면 첫 요청이 이미 성공했을 수 있다.

### 참여

`POST /api/v1/schedules/{scheduleId}/participants`. 일정에 멤버로 참여한다.

- **부르는 화면**: 일정 상세(DTL-03)의 [참여하기].
- **인증이 필요하다.** 토큰이 없거나 만료되면 401 `UNAUTHENTICATED`다. 재발급 후 다시 부르고, 재발급도 실패하면 로그인 화면으로 간다.
- **요청 본문은 없다.** 경로의 `scheduleId`(UUID 문자열)만 보낸다. 참여하는 사람은 토큰의 사용자다.
- 성공하면 **200이고 `data`가 없다.**
  ```json
  { "isSuccess": true }
  ```
- **성공이든 실패든 일정 상세를 다시 불러 화면을 그린다.** 이 API의 응답은 상세를 돌려주지 않는다. 일정 상세 API는 아직 없다. 생기면 그 API로 다시 그린다.
- 마지막 자리가 차면 일정은 모집 마감이 된다. 마지막 한 자리에 두 사람이 동시에 참여하면 한 명만 성공하고 다른 한 명은 409 `RECRUIT_CLOSED`다.
- **참여를 취소한 뒤 다시 참여할 수 있다.** 모집 중이면 [참여하기]를 다시 보여준다.
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 401 | `UNAUTHENTICATED` | 토큰이 없거나 만료·무효 | 재발급 후 다시 부르고, 실패하면 로그인 화면으로 |
  | 401 | `USER_NOT_ACTIVE` | 탈퇴한 사용자(남은 access token으로 호출) | 로그인 화면으로 |
  | 404 | `SCHEDULE_NOT_FOUND` | 없거나 삭제된 일정 | 일정 목록 화면으로 보내고 토스트로 `message`("찾을 수 없는 일정이에요.")를 보여준다. 일정 상세 API의 404와 같은 처리다 |
  | 409 | `HOST_CANNOT_JOIN` | 내가 연 일정 | 상세를 다시 불러 그린다 |
  | 409 | `ALREADY_JOINED` | 이미 참여 확정한 일정(두 번 눌렸을 때 포함) | 상세를 다시 불러 그린다. 참여는 된 상태다 |
  | 409 | `RECRUIT_CLOSED` | 모집이 끝났다. 정원이 찼거나, 시작 시각이 됐거나(같은 시각 포함), 취소·완료된 일정 | 상세를 다시 불러 그린다 |
  | 409 | `SCHEDULE_TIME_CONFLICT` | 내가 개설했거나 참여 확정한 예정 일정과 시작 시각이 12시간 미만으로 떨어진다. 정확히 12시간 차이는 허용한다 | `message`를 보여준다 |
  | 400 | `INVALID_REQUEST` | `scheduleId`가 UUID 형식이 아니다 | 정상 화면 흐름에서는 나지 않는다 |

  여러 조건에 걸리면 **`USER_NOT_ACTIVE` → `SCHEDULE_NOT_FOUND` → `HOST_CANNOT_JOIN` → `ALREADY_JOINED` → `RECRUIT_CLOSED` → `SCHEDULE_TIME_CONFLICT`** 순서로 앞의 것이 온다. 예를 들어 이미 참여한 일정이 그 참여로 마감됐어도 `ALREADY_JOINED`이고, 모집이 끝난 일정에는 시간이 겹쳐도 `RECRUIT_CLOSED`다.

  `SCHEDULE_NOT_FOUND`(404)는 없는 경로의 404(`INVALID_REQUEST`)와 **`code`로 구분한다.** 상태 코드만 보고 처리하지 않는다.

  ```json
  { "isSuccess": false, "code": "USER_NOT_ACTIVE", "message": "이용할 수 없는 사용자예요. 다시 로그인해주세요." }
  ```
  ```json
  { "isSuccess": false, "code": "SCHEDULE_NOT_FOUND", "message": "찾을 수 없는 일정이에요." }
  ```
  ```json
  { "isSuccess": false, "code": "HOST_CANNOT_JOIN", "message": "내가 연 일정에는 참여할 수 없어요." }
  ```
  ```json
  { "isSuccess": false, "code": "ALREADY_JOINED", "message": "이미 참여한 일정이에요." }
  ```
  ```json
  { "isSuccess": false, "code": "RECRUIT_CLOSED", "message": "모집이 끝난 일정이에요." }
  ```
  ```json
  { "isSuccess": false, "code": "SCHEDULE_TIME_CONFLICT", "message": "앞뒤 12시간 안에 참여하는 다른 일정이 있어요." }
  ```
- **두 번 눌렸을 때**: 참여는 한 번만 된다. 두 번째 요청은 409 `ALREADY_JOINED`이고, 상태는 한 번 참여한 것과 같다. 그래도 첫 응답을 받기 전까지 [참여하기] 버튼을 막는다.

### 참여 취소

`DELETE /api/v1/schedules/{scheduleId}/participants/me`. 내 참여를 취소한다.

- **부르는 화면**: 일정 상세의 참여 취소 확인 팝업(DTL-07)의 [확인].
- **인증이 필요하다.** 토큰이 없거나 만료되면 401 `UNAUTHENTICATED`다. 재발급 후 다시 부르고, 재발급도 실패하면 로그인 화면으로 간다.
- **요청 본문은 없다.** 경로의 `scheduleId`(UUID 문자열)만 보낸다. 취소하는 사람은 토큰의 사용자다.
- 성공하면 **200이고 `data`가 없다.**
  ```json
  { "isSuccess": true }
  ```
- **성공이든 실패든 일정 상세를 다시 불러 화면을 그린다.** 일정 상세 API는 아직 없다. 생기면 그 API로 다시 그린다.
- **시작 후에도 완료(시작 + 12시간) 전까지 취소할 수 있다.** 시작 + 12시간이 되면(같은 시각 포함) 취소할 수 없다.
- 시작 전에 취소해 자리가 나면 마감됐던 모집이 다시 열린다. **시작 후 취소로 자리가 나도 모집은 다시 열리지 않는다.**
- **취소한 뒤 다시 참여할 수 있다**(위 참여 API).
- 오류:

  | 상태 | code | 언제 | 화면에서 할 일 |
  | --- | --- | --- | --- |
  | 401 | `UNAUTHENTICATED` | 토큰이 없거나 만료·무효 | 재발급 후 다시 부르고, 실패하면 로그인 화면으로 |
  | 404 | `SCHEDULE_NOT_FOUND` | 없거나 삭제된 일정 | 일정 목록 화면으로 보내고 토스트로 `message`("찾을 수 없는 일정이에요.")를 보여준다. 일정 상세 API의 404와 같은 처리다 |
  | 409 | `HOST_CANNOT_LEAVE` | 내가 연 일정. 개설자는 참여를 취소할 수 없다 | 상세를 다시 불러 그린다 |
  | 409 | `NOT_PARTICIPANT` | 참여 확정 상태가 아니다. 참여한 적 없음, 이미 취소함 포함 | 상세를 다시 불러 그린다. 재시도해도 안전하다 |
  | 409 | `PARTICIPATION_NOT_CANCELABLE` | 취소됐거나 완료된(시작 + 12시간이 지난) 일정 | 상세를 다시 불러 그린다 |
  | 400 | `INVALID_REQUEST` | `scheduleId`가 UUID 형식이 아니다 | 정상 화면 흐름에서는 나지 않는다 |

  여러 조건에 걸리면 **`SCHEDULE_NOT_FOUND` → `HOST_CANNOT_LEAVE` → `NOT_PARTICIPANT` → `PARTICIPATION_NOT_CANCELABLE`** 순서로 앞의 것이 온다. 예를 들어 이미 취소한 사람은 일정이 완료됐어도 `NOT_PARTICIPANT`다.

  `SCHEDULE_NOT_FOUND`(404)는 없는 경로의 404(`INVALID_REQUEST`)와 **`code`로 구분한다.**

  ```json
  { "isSuccess": false, "code": "SCHEDULE_NOT_FOUND", "message": "찾을 수 없는 일정이에요." }
  ```
  ```json
  { "isSuccess": false, "code": "HOST_CANNOT_LEAVE", "message": "내가 연 일정은 참여를 취소할 수 없어요. 일정 전체 취소를 이용해주세요." }
  ```
  ```json
  { "isSuccess": false, "code": "NOT_PARTICIPANT", "message": "참여 중인 일정이 아니에요." }
  ```
  ```json
  { "isSuccess": false, "code": "PARTICIPATION_NOT_CANCELABLE", "message": "취소되었거나 끝난 일정은 참여를 취소할 수 없어요." }
  ```
- **두 번 눌렸을 때**: 취소는 한 번만 된다. 두 번째 요청은 409 `NOT_PARTICIPANT`이고, **재시도해도 안전하다.** 첫 응답을 받지 못해 다시 보냈다가 `NOT_PARTICIPANT`를 받았다면 첫 요청이 이미 성공한 것이다.

### 일정 목록

일정 카드 목록 세 가지다. 응답 형태와 페이지 넘기는 방법이 같다.

| API | 화면 | 인증 | 무엇이 오는가 |
| --- | --- | --- | --- |
| `GET /api/v1/schedules` | 모집 중 탭(LIST-01·02), 필터(LIST-03) | **필요 없다.** 토큰을 보내도 응답은 같다 | 취소되지 않았고 시작 전인 일정 전부 |
| `GET /api/v1/schedules/participating` | 참여한 일정 탭(LIST-04) | 필요하다 | 내가 멤버로 참여한 완료 전 일정 |
| `GET /api/v1/schedules/hosted` | 개설한 일정 탭(LIST-05), 개설 직후 내가 연 일정(CRT-07) | 필요하다 | 내가 연 완료 전 일정 |

인증이 필요한 두 API는 토큰이 없거나 만료되면 401 `UNAUTHENTICATED`다. 재발급 후 다시 부르고, 재발급도 실패하면 로그인 화면으로 간다.

**각 목록에 포함·제외되는 일정**

- 모집 중(`GET /api/v1/schedules`)
  - 포함: 시작 전 일정. **정원이 찬 일정도 `RECRUITMENT_CLOSED`(모집 마감)로 함께 온다.**
  - 제외: 시작 시각이 된 일정(같은 시각 포함), 취소된 일정, 완료된 일정.
- 참여한 일정(`/participating`)
  - 포함: 내가 [참여하기]로 참여 확정한 일정 중 완료(시작 + 12시간) 전인 것. 시작했어도 완료 전이면 온다. **개설자가 취소한 일정도 완료 시각 전까지 `CANCELED`(일정 취소됨)로 남는다.**
  - 제외: 내가 참여를 취소한 일정(다시 참여하면 다시 온다), 완료된 일정, 내가 연 일정(개설한 일정 탭에 있다).
- 개설한 일정(`/hosted`)
  - 포함: 내가 연 일정 중 완료(시작 + 12시간) 전인 것. 방금 연 일정도 온다. **내가 취소한 일정도 완료 시각 전까지 `CANCELED`(일정 취소됨)로 남는다.**
  - 제외: 멤버로 참여만 한 일정(참여한 일정 탭에 있다), 완료된 일정.
- 지난(완료된) 일정을 보는 API는 아직 없다.

**요청(쿼리 파라미터)**. 모두 선택이다. `/participating`·`/hosted`는 `cursor`, `size`만 받는다. 필터는 없고, 보내도 무시된다.

| 파라미터 | 대상 | 의미·형식 |
| --- | --- | --- |
| `categoryIds` | 모집 중 | 활동 목록 API(`GET /api/v1/activity-categories`)의 `id`(UUID 문자열). 여러 개는 `categoryIds=a&categoryIds=b`처럼 **이름을 반복**해 보낸다. 그중 하나에 맞으면 온다. **생략하면 모든 활동**이다. 모르는 ID만 보내면 오류가 아니라 빈 결과다. `categoryIds=`처럼 빈 값으로 보내지 않는다(맞는 일정이 없어 빈 결과가 된다). 전체를 보려면 파라미터를 뺀다 |
| `regionCodes` | 모집 중 | 지역 목록 API(`GET /api/v1/regions`)의 `code`(5자리 문자열). 여러 개는 `regionCodes=11440&regionCodes=11410`. 그중 하나에 맞으면 온다. 생략·모르는 값·빈 값은 `categoryIds`와 같다. **활동과 지역을 함께 보내면 둘 다 맞는 일정만 온다** |
| `startDate` | 모집 중 | 시작 날짜 범위의 첫날. `yyyy-MM-dd`(한국 날짜). 그날 00:00부터 포함한다 |
| `endDate` | 모집 중 | 시작 날짜 범위의 마지막 날. `yyyy-MM-dd`. 그날 23:59까지 포함한다 |
| `cursor` | 세 목록 모두 | 이전 응답의 `nextCursor`를 그대로 보낸다. 생략하면 첫 페이지다 |
| `size` | 세 목록 모두 | 한 페이지 개수. **생략하면 20, 50보다 크면 50, 1보다 작으면 1**로 맞춘다(오류가 아니다). 숫자가 아니면 400 `INVALID_REQUEST` |

`categoryIds`·`regionCodes`는 **각각 중복을 빼고 최대 20개**다. 넘으면 400 `INVALID_REQUEST`다. 활동·지역은 각각 몇 개뿐이라 화면에서 고를 수 있는 만큼 보내면 닿지 않는다.

날짜는 이렇게 해석한다.

| 보낸 값 | 범위 |
| --- | --- |
| 둘 다 생략 | 날짜 제한 없음 |
| `startDate`만 | 그날 하루 |
| `endDate`만 | 그날 하루 |
| 같은 날짜 | 그날 하루 |
| `startDate` < `endDate` | 두 날짜와 그 사이 모든 날(양 끝 포함) |
| `endDate` < `startDate` | 400 `INVALID_DATE_FILTER` |

지난 날짜를 보내도 오류가 아니다. 시작 전 일정만 오므로 빈 결과다. `startDate=`·`endDate=`·`cursor=`·`size=`처럼 빈 값으로 보내면 생략과 같다.

**정렬과 페이지**

- **시작 일시가 빠른 순**이다. 시작 일시가 같으면 일정 id 순이다. 받은 순서 그대로 그린다.
- 한 번에 `size`개를 준다. 더 있으면 `nextCursor`에 값이 있고, **마지막 페이지면 `nextCursor`가 `null`** 이다. 남은 개수가 `size`와 딱 맞아도 마지막 페이지에서 `null`이 오므로, 빈 페이지를 한 번 더 부를 일은 없다.
- 다음 페이지: **같은 파라미터에 `cursor=<nextCursor>`만 붙여** 부른다. 받은 items를 기존 목록 뒤에 붙인다. 끝까지 넘겨도 중복·누락이 없다(같은 시작 일시인 일정이 여러 개여도 같다).
- **필터나 탭을 바꾸면 `cursor` 없이 처음부터** 부른다. 다른 필터에서 받은 커서를 쓰지 않는다.
- `nextCursor`는 **해석하지 않는 불투명 문자열**이다. 영문·숫자·`_`·`-`만 쓰므로 인코딩 없이 쿼리에 붙인다. 내용이나 형식에 기대지 않는다.
- 맞는 일정이 없으면 200, `items`는 빈 배열, `nextCursor`는 `null`이다. 화면에 "결과 없음"을 보여준다. 오류가 아니다.

**응답 예시**

```json
{
  "isSuccess": true,
  "data": {
    "items": [
      {
        "id": "01a1c3d4-0000-7000-8000-000000000001",
        "category": { "id": "01a19cc6-3d80-7000-8000-000000000001", "name": "보드게임" },
        "title": "보드게임 같이 해요",
        "regionName": "마포구",
        "startAt": "2026-11-14T19:30:00",
        "confirmedCount": 3,
        "capacity": 6,
        "remainingSeats": 3,
        "displayStatus": "RECRUITING",
        "createdAt": "2026-11-10T09:12:34.567"
      }
    ],
    "nextCursor": "MjAyNi0xMS0xNFQxOTozMHwwMWExYzNkNC0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDE"
  }
}
```

| 필드 | 의미 |
| --- | --- |
| `items[].id` | 일정 ID(UUID 문자열). 일정 상세로 갈 때 쓴다 |
| `items[].category.id` | 활동 ID |
| `items[].category.name` | 활동 이름. 지금은 고를 수 없는(숨긴) 활동이어도 이름이 온다. 활동 정보를 찾을 수 없으면 **빈 문자열**이다 |
| `items[].title` | 활동명(일정 제목) |
| `items[].regionName` | 지역 이름(시군구, 예: 마포구). 서비스를 멈춘 지역이어도 이름이 온다. 지역 정보를 찾을 수 없으면 **빈 문자열**이고, 그때는 지역을 표시하지 않는다 |
| `items[].startAt` | 시작 일시. 오프셋 없는 한국 시간. D-DAY 계산에 쓴다 |
| `items[].confirmedCount` | 확정 인원. 개설자를 포함한다 |
| `items[].capacity` | 정원. 개설자를 포함한다 |
| `items[].remainingSeats` | 남은 자리 = `capacity` − `confirmedCount`. "2자리 남음" 같은 문구는 이 값으로 만든다. 0이면 정원이 찼다 |
| `items[].displayStatus` | 화면에 보여줄 상태. 아래 표 |
| `items[].createdAt` | 개설 일시. 오프셋 없는 한국 시간. **NEW 배지**를 붙일지 이 값으로 정한다 |
| `nextCursor` | 다음 페이지 커서. 마지막 페이지면 `null` |

`nextCursor`를 뺀 모든 필드는 null이 아니다. 장소명·주소·대표 이미지·비용은 목록에 오지 않는다(상세에서 본다).

`displayStatus`는 서버가 일정 상태·모집 상태·시각을 합쳐 정한 값이다. FE는 이 값만 보고 배지를 그린다.

| 값 | 화면 문구 | 언제 | 나오는 목록 |
| --- | --- | --- | --- |
| `RECRUITING` | 모집 중 | 그 밖의 경우 | 세 목록 모두 |
| `RECRUITMENT_CLOSED` | 모집 마감 | 정원이 찼거나 시작 시각이 지났다 | 세 목록 모두(모집 중 목록에서는 정원이 찬 경우) |
| `CANCELED` | 일정 취소됨 | 개설자가 일정을 취소했다 | 참여한·개설한 일정 |
| `COMPLETED` | 일정 완료 | 시작 + 12시간이 지났다 | 목록에는 오지 않는다 |

**오류**

| 상태 | code | 언제 | 화면에서 할 일 |
| --- | --- | --- | --- |
| 400 | `INVALID_DATE_FILTER` | `endDate`가 `startDate`보다 앞선다(모집 중 목록) | 날짜를 다시 고르게 한다 |
| 400 | `INVALID_CURSOR` | 해석할 수 없는 `cursor` | `cursor` 없이 처음부터 다시 부른다 |
| 400 | `INVALID_REQUEST` | `categoryIds`가 UUID 형식이 아니다, `categoryIds`·`regionCodes`가 중복을 빼고 20개를 넘는다, 날짜가 `yyyy-MM-dd`가 아니거나 없는 날짜다(`2026-13-01`), `size`가 숫자가 아니다 | 정상 화면 흐름에서는 나지 않는다 |
| 401 | `UNAUTHENTICATED` | `/participating`·`/hosted`에 토큰이 없거나 만료·무효 | 재발급 후 다시 부르고, 실패하면 로그인 화면으로 |

날짜와 커서가 둘 다 틀리면 `INVALID_DATE_FILTER`가 온다.

```json
{ "isSuccess": false, "code": "INVALID_DATE_FILTER", "message": "끝 날짜는 시작 날짜보다 앞설 수 없어요." }
```
```json
{ "isSuccess": false, "code": "INVALID_CURSOR", "message": "목록을 처음부터 다시 불러주세요." }
```

- 조회만 하므로 **같은 요청을 몇 번 다시 보내도 된다.** 실패하면 그대로 재시도한다. 같은 커서로 다시 불러도 같은 위치부터 온다(그 사이 일정이 생기거나 바뀌었으면 그만큼 다르다).

## 화면별로 쓰는 API

| 화면 | 호출 | 응답에 따른 처리 |
| --- | --- | --- |
| 앱 시작 | `POST /api/v1/auth/tokens` (credentials 포함) | 200이면 access token을 받아 메인. 401이면 비로그인 상태로 둔다 |
| 로그인 | `GET /api/v1/auth/kakao/authorize`로 이동 | 돌아오면 `?signUp` 여부로 분기 |
| 프로필 완성 (진입) | `GET /api/v1/auth/signup/session` | 닉네임·사진 기본값 표시. 401이면 카카오 로그인부터 다시 |
| 프로필 완성 (닉네임 칸을 벗어날 때) | `GET /api/v1/users/nickname-availability` (토큰 없이) | `available`이 false면 `reason`에 따라 형식 안내(`INVALID_FORMAT`) 또는 다른 닉네임 안내(`DUPLICATED`). 가입 요청도 같은 검사를 다시 하므로 409 처리는 그대로 둔다 |
| 프로필 완성 (사진 변경) | `POST /api/v1/auth/signup/profile-image` | 받은 경로를 가입 요청에 넣는다 |
| 프로필 완성 (제출) | `POST /api/v1/auth/signup` | 200이면 access token 저장 후 메인. 400 `INVALID_REQUEST`면 `errors`의 필드별 문구 표시. 400 `UNDERAGE`면 가입 불가 안내. 409면 닉네임 변경 안내 |
| 내 정보 (진입) | `GET /api/v1/users/me` | 프로필·연령대를 그린다. 404 `USER_NOT_FOUND`면 로그인 화면 |
| 프로필 수정 (진입) | `GET /api/v1/users/me` | 입력칸 기본값을 채운다. 이름·성별·생년월일은 읽기 전용. `nicknameChangeableAt`이 있으면 닉네임 칸을 막고 그 날짜를 안내 |
| 프로필 수정 (닉네임 칸을 벗어날 때) | `GET /api/v1/users/nickname-availability` (access token 포함) | 위와 같다. 본인의 현재 닉네임은 `true`로 온다. 변경 제한(14일)은 이 API가 보지 않는다 |
| 프로필 수정 (사진 변경) | `POST /api/v1/users/me/profile-image` | 받은 경로를 미리보기에 쓰고 저장 요청의 `profileImageUrl`에 넣는다. 이 호출만으로는 프로필이 바뀌지 않는다 |
| 프로필 수정 (저장) | `PATCH /api/v1/users/me` | 200이면 응답으로 화면을 갱신. 400 `INVALID_REQUEST`면 `errors`의 필드별 문구 표시. 409 `DUPLICATED_NICKNAME`이면 다른 닉네임 안내, `NICKNAME_CHANGE_RESTRICTED`면 바꿀 수 있는 날짜 안내 |
| 설정 · 로그아웃 | `DELETE /api/v1/auth/tokens` | 응답과 무관하게 메모리의 토큰을 버리고 로그인 화면 |
| 설정 · 탈퇴 | `DELETE /api/v1/users/me` → `DELETE /api/v1/auth/tokens` | 되돌릴 수 없음을 한 번 확인받고 호출. 끝나면 로그인 화면 |
| 일정 목록 (지역 필터) | `GET /api/v1/regions` | 받은 순서대로 필터를 그린다 |
| 장소 선택 (지역 칩) | `GET /api/v1/regions` | 받은 순서대로 칩을 그린다 |
| 장소 선택 (목록·지도) | `GET /api/v1/place-candidates` | 화면 진입과 지역 칩 변경 때 부른다. 받은 순서대로 목록과 지도 핀을 그린다. 빈 배열이면 결과 없음. 400 `INVALID_CATEGORY`면 활동을, `INVALID_REGION`이면 지역을 다시 고르게 한다 |
| 일정 개설 (활동 선택) | `GET /api/v1/activity-categories` | 받은 순서대로 활동을 그린다 |
| 일정 목록 (활동 필터) | `GET /api/v1/activity-categories` | 받은 순서대로 필터를 그린다 |
| 일정 개설 ([일정 열기]) | `POST /api/v1/schedules` | 201이면 내가 연 일정 목록(LIST-05)으로 이동하고 팝업을 띄운다. 400 `INVALID_REQUEST`면 `errors`의 필드별 문구 표시. 그 밖의 400·409는 해당 입력을 다시 고르게 한다. 응답 전까지 버튼을 막는다 |
| 일정 상세 ([참여하기]) | `POST /api/v1/schedules/{scheduleId}/participants` | 200이든 409든 상세를 다시 불러 그린다. 409 `SCHEDULE_TIME_CONFLICT`면 `message`를 보여준다. 404 `SCHEDULE_NOT_FOUND`면 일정 목록으로 보내고 토스트. 응답 전까지 버튼을 막는다 |
| 일정 상세 (참여 취소 팝업 [확인]) | `DELETE /api/v1/schedules/{scheduleId}/participants/me` | 200이든 409든 상세를 다시 불러 그린다. 409 `NOT_PARTICIPANT`는 이미 취소된 상태다. 404 `SCHEDULE_NOT_FOUND`면 일정 목록으로 보내고 토스트 |
| 일정 목록 · 모집 중 탭 (LIST-01·02, 필터 LIST-03) | `GET /api/v1/schedules` (토큰 없이) | 탭 진입과 필터 변경 때 `cursor` 없이 부르고, 끝까지 스크롤하면 `cursor=nextCursor`로 다음 페이지를 붙인다. `nextCursor`가 null이면 더 부르지 않는다. 빈 `items`면 결과 없음. 400 `INVALID_DATE_FILTER`면 날짜를 다시 고르게 하고, `INVALID_CURSOR`면 처음부터 다시 부른다 |
| 일정 목록 · 참여한 일정 탭 (LIST-04) | `GET /api/v1/schedules/participating` | 페이지 넘기기는 모집 중 탭과 같다. `CANCELED`는 "일정 취소됨"으로 그린다. 401이면 재발급 후 다시 부른다 |
| 일정 목록 · 개설한 일정 탭 (LIST-05), 개설 직후 내가 연 일정 (CRT-07) | `GET /api/v1/schedules/hosted` | 개설 201을 받은 뒤 이 목록으로 와서 부른다. 방금 연 일정이 들어 있다. 페이지 넘기기·401 처리는 위와 같다 |

일정 상세 등 위 표에 없는 일정 API와 알림 API는 아직 없다. 생기면 이 표에 추가한다.

## 로컬에서 서버 띄우기

FE 개발 중 실제 서버에 붙여볼 때.

1. `cd snsg && ./gradlew bootRun` → `http://localhost:8080`

### 웹 FE 개발 서버에서 로컬 서버 호출하기

- **CORS는 설정돼 있다.** 허용 출처는 환경변수로 관리하며 현재 `http://localhost:3000`이 등록돼 있다. 다른 주소를 쓰면 알려줘야 추가할 수 있다. 등록되지 않은 출처는 403이다.
- **모든 요청에 `credentials: 'include'`를 붙인다.** 쿠키가 가지 않으면 재발급·가입이 전부 401이고, 로그아웃은 200이어도 서버의 세션이 지워지지 않는다.
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

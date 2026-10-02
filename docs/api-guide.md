# SNSG API 가이드

이 문서와 [`openapi.yaml`](openapi.yaml)만 보고 화면을 만들 수 있게 하는 게 목표다.

- **엔드포인트별 요청·응답·에러 코드**: `openapi.yaml`. 코드에서 생성하고, 코드와 다르면 서버 빌드가 실패하므로 항상 최신이다.
- **여러 호출에 걸친 흐름과 공통 규칙**: 이 문서. OpenAPI로는 표현할 수 없는 것만 적는다.

아직 API가 없어 `openapi.yaml`도 없다. 첫 API를 만들 때 스펙 생성(springdoc, `OpenApiSpecTest`)과 함께 만든다.

## 스펙 보는 법

- 파일: `docs/openapi.yaml`
- 로컬 서버를 띄웠다면 `http://localhost:8080/swagger-ui.html`에서 직접 호출해볼 수 있다.
- 운영 서버에서는 swagger-ui와 스펙 경로를 열지 않는다.

## 공통 규칙

### 경로

모든 API는 `/api/v1`으로 시작한다.

### 응답 봉투

성공이든 실패든 같은 형태로 온다.

```json
{ "code": "<성공 코드>", "message": "<메시지>", "data": { } }
{ "code": "INVALID_REQUEST", "message": "<메시지>", "data": null }
```

- 성공·실패는 **HTTP 상태 코드**로 판단한다. 봉투에 성공 여부 필드는 없다.
- 화면 분기는 **`code`** 로 한다. enum 이름이라 바뀌지 않는다.
- `message`는 표시·디버깅용이다. 문구가 바뀔 수 있고, 요청 형식 오류의 메시지는 서버 로케일에 따라 달라진다. 분기에 쓰지 않는다.
- 실패하면 `data`는 항상 `null`이다.

### 모든 API에서 날 수 있는 에러

| 상태 | code | 언제 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 잘못된 JSON 등 요청 형식 오류 |
| 404·405·415 등 | `INVALID_REQUEST` | 없는 경로, 지원하지 않는 메서드나 Content-Type. 상태 코드는 원래 값 그대로다 |
| 401 | `UNAUTHENTICATED` | 인증이 필요한 API에 토큰이 없거나 만료·무효 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

엔드포인트마다 추가로 나는 에러는 `openapi.yaml`의 응답 코드별 설명에 있다.

### 날짜·시간

`2026-09-14T00:00:00`처럼 **오프셋 없는 ISO-8601** 이고, 모두 **한국 시간(Asia/Seoul, UTC+9)** 이다. 브라우저(사용자 PC)의 시간대와 상관없이 한국 시간으로 해석한다.

## 화면별로 쓰는 API

| 화면 | 호출 | 응답에 따른 처리 |
| --- | --- | --- |

아직 API가 없다. 생기면 이 표에 추가한다.

## 로컬에서 서버 띄우기

FE 개발 중 실제 서버에 붙여볼 때.

1. `cd snsg && ./gradlew bootRun` → `http://localhost:8080`

### 웹 FE 개발 서버에서 로컬 서버 호출하기

- FE 개발 서버는 `http://localhost:8080`으로 API를 호출한다.
- FE 개발 서버와 포트가 다르면 출처(origin)가 달라, 브라우저가 CORS로 요청을 막는다. 서버의 CORS 설정은 아직 없다. FE 저장소가 생겨 개발 서버 출처가 정해지면, 서버에 CORS 허용 출처를 추가하거나 FE 개발 서버의 프록시로 같은 출처처럼 호출한다.
- 서버 주소는 FE 환경 설정으로 바꿀 수 있게 둔다. 운영 서버가 생기면 주소만 바꾼다.

## 이 문서를 고치는 때

API를 바꾸는 PR에서 함께 고친다.

- 요청·응답·에러 코드가 바뀌면: `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 `openapi.yaml`을 다시 만들어 커밋한다. 잊으면 빌드가 실패한다.
- 흐름, 공통 규칙, 화면별로 쓰는 API가 바뀌면: 이 문서를 고친다. 이건 빌드가 잡아주지 않는다.

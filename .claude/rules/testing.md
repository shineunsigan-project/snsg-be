---
paths:
  - "snsg/src/test/**/*.java"
---

# Testing

## 기준 한 줄

**우리가 내린 판단만 테스트한다.** 프레임워크·라이브러리가 보장하는 동작은 테스트하지 않는다.
코드를 짜고 "이걸 테스트해야 하나" 싶으면 이렇게 묻는다. **이 테스트가 빨간불이 되면 우리 버그인가, 아니면 라이브러리가 바뀐 것인가?** 후자면 쓰지 않는다.

## 계층별 범위

| 계층 | 테스트한다 | 테스트하지 않는다 | 방식 |
|---|---|---|---|
| domain | 불변식, 상태 전이, 계산 규칙 | getter, 단순 위임 | 순수 JUnit. Spring·mock 없이 |
| application | **분기와 예외가 있는 흐름** (가입/로그인 갈림, 권한 검사, 상태 전이 호출) | repository를 그대로 호출해 돌려주기만 하는 메서드 | JUnit + Mockito. repository·`port/out`을 mock |
| infrastructure | 커스텀 쿼리, **DB 제약(부분 유니크 인덱스 등)**, 외부 API adapter의 요청·응답 변환 | Spring Data가 만들어주는 기본 메서드(`save`, `findById`) | `@DataJpaTest`(실제 PostgreSQL), `MockRestServiceServer` |
| presentation | **인증·인가 경계**(토큰 없이 부르면 막히는지), 우리가 정한 요청 검증 규칙과 에러 코드 | 라우팅, JSON 직렬화, Bean Validation 연동, 405·415 처리 | `@WebMvcTest` + Service mock |

**domain이 가장 넓고 presentation이 가장 좁다.** domain 테스트는 Spring 없이 밀리초 단위로 돌고, 규칙이 그대로 들어 있어 실패하면 원인이 바로 보인다. 같은 규칙을 service 테스트로 간접 확인하면 mock을 거친 무거운 경로를 지나고, 실패했을 때 서비스 문제인지 엔티티 문제인지 구분되지 않는다.

## 쓰지 않는 테스트

- 설정만 추가한 변경. 앱이 뜨는지, DB·Redis에 붙는지는 실행으로 확인하고 결과를 PR의 test 절에 적는다.
- 라이브러리 객체를 직접 만들어 검증하는 것(`new ObjectMapper()` 등). 앱이 실제로 쓰는 설정과 달라 통과해도 의미가 없다.
- DTO 변환 메서드(`XxxResponse.from`)처럼 필드를 그대로 옮기는 코드. 변환 중 계산이나 분기가 들어가면 그때 테스트한다.
- 커버리지 숫자를 목표로 삼지 않는다. 숫자를 채우려고 쓰는 테스트가 위 항목들이다.

- `@SpringBootTest`는 계층을 넘는 흐름 확인이 꼭 필요할 때만 쓴다.
- 성공 케이스뿐 아니라 예외 분기(어떤 ErrorCode가 나오는지)도 검증한다.
- 테스트 클래스는 대상 클래스와 **같은 패키지, 같은 이름 + `Test`**로 둔다. 테스트는 원래 코드와 **별도 커밋**으로 올린다.
- Spring Boot 4는 슬라이스 테스트에 기술별 test 스타터가 필요하다 (예: `spring-boot-starter-webmvc-test`). 없으면 build.gradle에 추가한다.
- Boot 4에서 슬라이스 테스트 애너테이션의 패키지도 바뀌었다. `@DataJpaTest`는 `org.springframework.boot.data.jpa.test.autoconfigure`에 있다(`...boot.test.autoconfigure.orm.jpa` 아님). 스타터는 `spring-boot-starter-data-jpa-test`.
- `@DataJpaTest`는 `@Entity`와 Spring Data repository만 등록한다. `@Repository` 구현체를 쓰려면 `@Import`로 직접 넣는다.
- `@DataJpaTest`는 기본적으로 DataSource를 임베디드 DB로 바꿔치기한다. **우리 스키마는 PostgreSQL 전용 문법(부분 유니크 인덱스)에 의존하므로 반드시 실제 PostgreSQL로 돌린다.** Testcontainers를 쓰거나 `@AutoConfigureTestDatabase(replace = NONE)`로 로컬 컨테이너에 붙인다. 임베디드로 돌리면 제약이 아예 만들어지지 않아, 테스트는 통과하는데 운영에서 중복 데이터가 들어간다.
- `@EnableJpaAuditing`은 `SnsgApplication`이 아니라 `common/config/JpaAuditingConfig`에 둔다. 메인 클래스에 붙이면 `@WebMvcTest`가 `jpaAuditingHandler`를 만들려다 "JPA metamodel must not be empty"로 실패한다.
- 감사 필드를 검증하는 JPA 슬라이스 테스트는 `@Import`에 `JpaAuditingConfig`를 함께 넣는다.
- `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있고 스타터는 `spring-boot-starter-webmvc-test`. Service는 `@MockitoBean`으로 대체한다.
- 테스트는 기본 프로파일(`local`)로 돌고, 설정값은 `snsg/.env`에서 읽는다(`application-local.yaml`의 `spring.config.import`). 새 환경변수를 추가하면 `.env`에도 넣어야 컨텍스트가 뜬다.
- 외부 API adapter는 슬라이스 없이 `MockRestServiceServer.bindTo(RestClient.Builder)`로 테스트한다.
- Redis 같은 외부 저장소 adapter는 Testcontainers로 실제 컨테이너에서 테스트한다. 의존성은 `org.testcontainers:testcontainers-junit-jupiter`(Boot 4.0.8 관리 버전 2.0.5, 2.x에서 artifact 이름이 바뀌었다).
  - `@Testcontainers(disabledWithoutDocker = true)`로 Docker가 없으면 실패가 아니라 건너뛰게 한다. 대신 build가 통과해도 실제로 돌았는지는 테스트 결과의 `skipped` 수로 확인한다.
  - 슬라이스 없이 `LettuceConnectionFactory`를 컨테이너 host·port로 직접 만들어 adapter에 넣는다.
- `@WebMvcTest`에서 Spring Security를 쓰려면 `spring-boot-starter-security-test`가 있어야 한다. 이 모듈이 `@AutoConfigureMockMvc`에 보안 자동 설정을 넣는다. 없으면 `HttpSecurity` 빈도 `@AuthenticationPrincipal` 리졸버도 없다.
- 인가 규칙과 401 형식은 auth에서 `@Import`로 `SecurityConfig`와 인증 진입점(entry point)을 넣고, 토큰 검증 adapter를 `@MockitoBean`으로 대체해 검증한다.
- 다른 도메인의 컨트롤러 테스트는 auth를 import하지 않는다. `@AutoConfigureMockMvc(addFilters = false)`로 필터를 끄고 `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(userId, null, List.of())`을 직접 넣는다.
- `OpenApiSpecTest`는 `/v3/api-docs.yaml`로 생성한 스펙과 커밋된 `docs/openapi.yaml`을 비교한다. 줄바꿈은 정규화해 비교한다(autocrlf). build.gradle의 test task inputs에 스펙 파일과 `OPENAPI_UPDATE`를 넣어, 파일만 바뀌어도 테스트가 건너뛰어지지 않게 한다. 스펙 파일은 `inputs.files`로 선언한다. Gradle 9.7에서 `inputs.file(...).optional()`은 파일이 없으면 테스트 태스크를 시작하지 않아, 스펙을 처음 만들 수 없다.
- 패키지 의존 규칙(`.claude/rules/package-structure.md`)은 ArchUnit 테스트 `com.shineunsigan.snsg.ArchitectureTest`가 검사한다. 규칙 하나에 테스트 메서드 하나다.
  - 프로덕션 클래스만 검사한다(`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`). 테스트 코드는 다른 도메인을 import해도 걸리지 않는다.
  - 도메인은 base package 바로 아래 패키지 중 `common`을 뺀 것으로 자동 인식한다. 도메인 이름을 적어두지 않으므로 도메인을 추가해도 테스트를 고치지 않는다. base package 바로 아래 클래스(`SnsgApplication`)와 base package 밖(라이브러리)으로 향하는 의존은 보지 않는다.
  - Spring 컨텍스트 없이 돈다. `./gradlew test --tests '*Architecture*'`로 따로 돌릴 수 있다.

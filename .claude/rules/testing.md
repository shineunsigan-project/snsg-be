---
paths:
  - "snsg/src/test/**/*.java"
---

# Testing

| 계층 | 방식 | 검증 대상 |
|---|---|---|
| domain | 순수 JUnit (Spring 없이) | Entity/VO 생성, 상태 변경, 불변식 위반 시 ErrorCode |
| application | JUnit + Mockito. repository·`port/out` 인터페이스를 mock | Service 흐름, 예외 분기 |
| presentation | `@WebMvcTest` + Service mock | 요청 검증, HTTP status, 응답 형식 |
| infrastructure | `@DataJpaTest` 등 슬라이스 테스트 | 커스텀 쿼리, adapter의 DTO 변환 |

- `@SpringBootTest`는 계층을 넘는 흐름 확인이 꼭 필요할 때만 쓴다.
- 성공 케이스뿐 아니라 예외 분기(어떤 ErrorCode가 나오는지)도 검증한다.
- 테스트 클래스는 대상 클래스와 같은 패키지에 둔다.
- Spring Boot 4는 슬라이스 테스트에 기술별 test 스타터가 필요하다 (예: `spring-boot-starter-webmvc-test`). 없으면 build.gradle에 추가한다.
- Boot 4에서 슬라이스 테스트 애너테이션의 패키지도 바뀌었다. `@DataJpaTest`는 `org.springframework.boot.data.jpa.test.autoconfigure`에 있다(`...boot.test.autoconfigure.orm.jpa` 아님). 스타터는 `spring-boot-starter-data-jpa-test`.
- `@DataJpaTest`는 `@Entity`와 Spring Data repository만 등록한다. `@Repository` 구현체를 쓰려면 `@Import`로 직접 넣는다.
- `@EnableJpaAuditing`은 `SnsgApplication`이 아니라 `common/config/JpaAuditingConfig`에 둔다. 메인 클래스에 붙이면 `@WebMvcTest`가 `jpaAuditingHandler`를 만들려다 "JPA metamodel must not be empty"로 실패한다.
- 감사 필드를 검증하는 JPA 슬라이스 테스트는 `@Import`에 `JpaAuditingConfig`를 함께 넣는다.
- `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있고 스타터는 `spring-boot-starter-webmvc-test`. Service는 `@MockitoBean`으로 대체한다.
- 테스트는 Gradle에서 `spring.profiles.active=test`로 돈다(build.gradle의 `systemProperty`). 운영에서 환경변수로 받는 설정값은 `src/test/resources/application-test.yaml`에 테스트용 값을 둔다. 새 환경변수를 추가하면 여기에도 넣어야 `contextLoads`가 뜬다.
- 외부 API adapter는 슬라이스 없이 `MockRestServiceServer.bindTo(RestClient.Builder)`로 테스트한다.
- Redis 같은 외부 저장소 adapter는 Testcontainers로 실제 컨테이너에서 테스트한다. 의존성은 `org.testcontainers:testcontainers-junit-jupiter`(Boot 4.0.8 관리 버전 2.0.5, 2.x에서 artifact 이름이 바뀌었다).
  - `@Testcontainers(disabledWithoutDocker = true)`로 Docker가 없으면 실패가 아니라 건너뛰게 한다. 대신 build가 통과해도 실제로 돌았는지는 테스트 결과의 `skipped` 수로 확인한다.
  - 슬라이스 없이 `LettuceConnectionFactory`를 컨테이너 host·port로 직접 만들어 adapter에 넣는다.
- `@WebMvcTest`에서 Spring Security를 쓰려면 `spring-boot-starter-security-test`가 있어야 한다. 이 모듈이 `@AutoConfigureMockMvc`에 보안 자동 설정을 넣는다. 없으면 `HttpSecurity` 빈도 `@AuthenticationPrincipal` 리졸버도 없다.
- 인가 규칙과 401 형식은 auth에서 `@Import`로 `SecurityConfig`와 인증 진입점(entry point)을 넣고, 토큰 검증 adapter를 `@MockitoBean`으로 대체해 검증한다.
- 다른 도메인의 컨트롤러 테스트는 auth를 import하지 않는다. `@AutoConfigureMockMvc(addFilters = false)`로 필터를 끄고 `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(userId, null, List.of())`을 직접 넣는다.
- `OpenApiSpecTest`는 `/v3/api-docs.yaml`로 생성한 스펙과 커밋된 `docs/openapi.yaml`을 비교한다. 줄바꿈은 정규화해 비교한다(autocrlf). build.gradle의 test task inputs에 스펙 파일과 `OPENAPI_UPDATE`를 넣어, 파일만 바뀌어도 테스트가 건너뛰어지지 않게 한다. 스펙 파일은 `inputs.files`로 선언한다. Gradle 9.7에서 `inputs.file(...).optional()`은 파일이 없으면 테스트 태스크를 시작하지 않아, 스펙을 처음 만들 수 없다.

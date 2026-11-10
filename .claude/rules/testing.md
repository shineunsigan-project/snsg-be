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
  - 스케줄러가 실제로 도는지는 `./gradlew bootRun` 로그의 SQL로 확인한다(local 프로파일이 `org.hibernate.SQL: debug`라 0건 갱신도 찍힌다). `format_sql: true`라 SQL 한 문장이 여러 줄로 찍힌다. grep은 줄을 이어 붙인 뒤 하거나, 한 줄에 같이 찍히는 짧은 문자열로 찾는다. 로그는 세션 스크래치패드에 두고 확인 후 지운다(`.claude/rules/temp-files.md`).
- 라이브러리 객체를 직접 만들어 검증하는 것(`new ObjectMapper()` 등). 앱이 실제로 쓰는 설정과 달라 통과해도 의미가 없다.
- DTO 변환 메서드(`XxxResponse.from`)처럼 필드를 그대로 옮기는 코드. 변환 중 계산이나 분기가 들어가면 그때 테스트한다.
- 커버리지 숫자를 목표로 삼지 않는다. 숫자를 채우려고 쓰는 테스트가 위 항목들이다.

- `@SpringBootTest`는 계층을 넘는 흐름 확인이 꼭 필요할 때만 쓴다.
- 성공 케이스뿐 아니라 예외 분기(어떤 ErrorCode가 나오는지)도 검증한다.
- 테스트 클래스는 대상 클래스와 **같은 패키지, 같은 이름 + `Test`**로 둔다. 테스트는 원래 코드와 **별도 커밋**으로 올린다.
- Spring Boot 4는 슬라이스 테스트에 기술별 test 스타터가 필요하다 (예: `spring-boot-starter-webmvc-test`). 없으면 build.gradle에 추가한다.
- Boot 4에서 슬라이스 테스트 애너테이션의 패키지도 바뀌었다. `@DataJpaTest`는 `org.springframework.boot.data.jpa.test.autoconfigure`에 있다(`...boot.test.autoconfigure.orm.jpa` 아님). 스타터는 `spring-boot-starter-data-jpa-test`.
- `@DataJpaTest`는 `@Entity`와 Spring Data repository만 등록한다. `@Repository` 구현체를 쓰려면 `@Import`로 직접 넣는다.
- `@DataJpaTest`는 기본적으로 DataSource를 임베디드 DB로 바꿔치기한다. **우리 스키마는 PostgreSQL 전용 문법(부분 유니크 인덱스)에 의존하므로 반드시 실제 PostgreSQL로 돌린다.** `@AutoConfigureTestDatabase(replace = NONE)`로 `.env`가 가리키는 로컬 PostgreSQL 컨테이너(`snsg-postgres`)에 붙인다. Testcontainers는 쓰지 않는다(의존성 없음). 임베디드로 돌리면 제약이 아예 만들어지지 않아, 테스트는 통과하는데 운영에서 중복 데이터가 들어간다.
- **DB 연결 수는 "캐시된 테스트 컨텍스트 수 × Hikari 풀 크기"다.** Spring은 컨텍스트를 구성(`@Import` 조합 등)별로 캐시해 JVM이 끝날 때까지 닫지 않고, 컨텍스트마다 풀을 하나씩 잡는다. 기본 풀(10)로는 DB 슬라이스 구성이 열 개쯤 되면 PostgreSQL `max_connections`(100)를 넘어 `FATAL: sorry, too many clients already`로 컨텍스트가 뜨지 않는다. 그래서 `snsg/build.gradle`의 test 태스크가 `spring.datasource.hikari.maximum-pool-size`를 3으로 둔다(SHI-156에서 DB 테스트 컨텍스트가 늘며 간헐 실패가 났다).
  - 한 테스트에서 동시에 쓰는 연결이 셋을 넘어야 하면 이 값을 함께 본다. 지금 가장 많이 쓰는 곳은 `ScheduleCommandServiceTest`의 동시 개설·동시 참여(각 둘)다.
  - 실패가 간헐적이라 한 번 통과한 결과로 안심하지 않는다. DB 슬라이스 구성을 더한 작업은 `./gradlew cleanTest build`로 전체를 다시 돌려 확인한다.
- 한 대상 클래스에 Mockito 흐름 테스트와 실제 PostgreSQL 테스트가 함께 필요하면, 테스트 클래스 이름 규칙(대상 이름 + `Test`)을 지키며 `@Nested` 클래스로 나눠 각자 애너테이션을 단다. 예: `ScheduleCommandServiceTest`의 `Flow`(Mockito), `Concurrency`·`SavedSchedule`(`@DataJpaTest`), `UserWithdrawalCommandServiceTest`의 `Flow`·`Stored`.
  - `@ConfigurationProperties` record(`NicknameProperties` 등)는 `@DataJpaTest`에 올라오지 않는다. 테스트 클래스에 static `@TestConfiguration`을 두고 record를 `@Bean`으로 직접 만들어 `@Import`에 넣는다. Mockito 쪽과 같은 값을 쓰도록 record 상수를 한 곳에 둔다. 예: `UserWithdrawalCommandServiceTest.PropertiesConfig`.
- **DB를 모든 워크트리가 공유하므로, 사용자로 범위가 좁혀지지 않는 쿼리(모집 중 일정 목록 등) 테스트에는 다른 테스트·워크트리의 행이 섞인다.** 테스트마다 무작위 키(무작위 `categoryId` 등)로 행을 넣고 그 키를 필터로 걸어 넣은 행만 골라 단정한다. 다른 필터(지역·날짜·커서)를 볼 때도 그 키를 함께 건다. 시각 경계는 `now`를 인자로 받는 repository 메서드에 고정 시각을 넘겨 본다. 예: `ScheduleRepositoryImplTest`의 모집 중 목록 테스트.
  - 필터로 걸 무작위 키가 없는 쿼리(시각 조건만 있는 배치 대상 조회)는 기준 시각을 과거 고정 값(예: `2020-01-01T12:00`)으로 넘기고 넣은 행의 시각을 그 근처에 둔다. 실제 데이터는 시각이 미래라 걸리지 않는다. 그래도 결과 전체가 아니라 넣은 행의 id가 있는지와 넣은 행끼리의 순서로만 단정한다. 예: `UserWithdrawalRepositoryImplTest`(비식별 대상 keyset 페이지).
- 잠금(`.claude/rules/package-structure.md` "동시 요청과 잠금")은 실제 PostgreSQL에서 **커밋까지 거치는** 동시성 테스트로 확인한다. 테스트 트랜잭션 하나 안에서는 잠금이 경쟁하지 않는다. 예: `ScheduleCommandServiceTest.Concurrency`(동시 개설, 마지막 자리 동시 참여, 같은 일정 동시 취소).
  - 클래스에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 달아 테스트 트랜잭션을 끄고, 서비스 호출이 각자 커밋하게 한다.
  - 두 스레드를 `CountDownLatch` 둘(준비·출발)로 맞춰 동시에 부르고, 성공 수·실패의 ErrorCode·DB에 커밋된 행을 단정한다.
  - 커밋한 행은 `@AfterEach`에서 그 테스트만 쓰는 id(무작위 개설자 id) 기준으로 지운다. DB를 모든 워크트리가 공유하므로 테이블 전체를 지우지 않는다.
    - FK가 있으므로 구성요소 테이블부터 지운다(`schedule_cancellations` → `schedule_participants` → `schedules`). 일정에 구성요소 테이블이 늘면 정리 쿼리에도 먼저 더한다.
  - 새 동시성 테스트는 이 중첩 클래스에 메서드로 더한다. DB 슬라이스 구성(`@Import` 조합)이 늘면 컨텍스트와 연결 풀이 늘어난다(위 DB 연결 수 항목).
  - 간헐 실패를 sleep·재시도로 덮지 않는다. 잠금이 트랜잭션 안에 있는지, 판정보다 먼저인지, 그 행을 처음 읽는 조회에 걸렸는지부터 본다. 통과는 `cleanTest`로 여러 번 돌려 확인한다(SHI-171은 3회).
- `@EnableJpaAuditing`은 `SnsgApplication`이 아니라 `common/config/JpaAuditingConfig`에 둔다. 메인 클래스에 붙이면 `@WebMvcTest`가 `jpaAuditingHandler`를 만들려다 "JPA metamodel must not be empty"로 실패한다.
- 감사 필드를 검증하는 JPA 슬라이스 테스트는 `@Import`에 `JpaAuditingConfig`를 함께 넣는다.
  - 특정 주체(`Auditor.SYSTEM_SCHEDULER` 등)가 남는지 보려면 코드가 넣는 것과 같은 방식으로 `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(주체, null, List.of())`을 넣고 서비스를 부른 뒤 flush하고, native SELECT로 `updated_by`·`created_by`를 읽는다. `@AfterEach`에서 `SecurityContextHolder.clearContext()`를 부른다. 예: `UserWithdrawalCommandServiceTest.Stored`.
- SecurityContext를 바꾸는 코드(엔티티를 바꾸는 스케줄러)는 Mockito로 서비스를 대신하고, `doAnswer` 안에서 `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`을 호출 시점에 캡처해 단정한다. 끝난 뒤 인증이 `null`인지도, 예외가 난 경우를 포함해 본다. `@AfterEach`에서 `clearContext()`를 부른다. 예: `UserAnonymizationSchedulerTest`.
- `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있고 스타터는 `spring-boot-starter-webmvc-test`. Service는 `@MockitoBean`으로 대체한다.
- 테스트는 기본 프로파일(`local`)로 돌고, 설정값은 `snsg/.env`에서 읽는다(`application-local.yaml`의 `spring.config.import`). 새 환경변수를 추가하면 `.env`에도 넣어야 컨텍스트가 뜬다.
- **테스트에서는 스케줄링이 꺼져 있다.** `snsg/src/test/resources/config/application.yaml`의 `scheduling.enabled: false`가 끈다. `@SpringBootTest` 컨텍스트가 스케줄러를 띄우면 공유 로컬 DB의 실제 데이터에 상태 전환을 커밋한다(`@WebMvcTest`·`@DataJpaTest`는 스케줄러 빈을 올리지 않는다). `SnsgApplicationTests.schedulingIsDisabledInTests`가 꺼진 것과 main 설정이 함께 읽히는 것을 단정한다.
  - Gradle test 태스크의 `systemProperty`로 끄지 않는다. IDE의 JUnit 러너로 돌리면 적용되지 않아 스케줄러가 켜진다.
  - 테스트 전용 설정은 `src/test/resources/config/application.yaml`에 둔다. `classpath:/config/`는 `classpath:/`와 함께 읽혀 합쳐지고 우선한다. `src/test/resources/application.yaml`은 같은 경로의 main 설정을 통째로 가리므로 쓰지 않는다.
- 일괄 UPDATE(`@Modifying`, `.claude/rules/package-structure.md` 일괄 UPDATE 절)는 테이블 전체가 대상이라 공유 DB의 다른 행도 WHERE에 걸릴 수 있다. 테스트 트랜잭션이 롤백해 남지는 않지만, 다른 행을 건드리지 않고 넣은 행만 단정하도록 이렇게 쓴다. 예: `ScheduleRepositoryImplTest`의 상태 전환 테스트.
  - 기준 시각을 과거 고정 값(예: `2020-01-01T12:00`)으로 넘기고 테스트 행의 시각을 그 근처에 둔다. 실제 개설 데이터(미래 시작)는 조건에 걸리지 않는다.
  - 바뀐 건수로 단정하지 않는다. 넣은 행의 id로 native SELECT해서 상태와 감사 컬럼(`updated_by`·`updated_at`·`created_by`·`created_at`)을 단정한다. 일괄 UPDATE 뒤 영속성 컨텍스트의 엔티티는 옛 값이라 엔티티로 다시 읽지 않는다.
  - 경계(기준 시각과 같음 → 바뀜, 1분 뒤 → 그대로), 대상이 아닌 행(취소·삭제·이미 목표 상태)의 감사 값 보존, 더 늦은 기준 시각으로 한 번 더 불러도 `updated_at`이 첫 값으로 남는지(멱등)를 본다.
- 결과가 경고 로그로만 드러나는 분기는 `OutputCaptureExtension`(`org.springframework.boot.test.system`)으로 로그를 받아 단정한다. Mockito와 함께 쓸 때는 `@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})`. 예: `PlaceCommandServiceTest`.
- 외부 API adapter는 슬라이스 없이 `MockRestServiceServer.bindTo(RestClient.Builder)`로 테스트한다.
- 파일 저장소 adapter는 Spring 없이 JUnit `@TempDir` 아래 디렉터리를 설정 record로 넘겨 실제 파일로 테스트한다. 예: `LocalFileStorageAdapterTest`(형식 밖 경로로 디렉터리 안팎의 파일이 지워지지 않는지).
- Redis adapter는 `@SpringBootTest`로 `.env`가 가리키는 로컬 Redis(Docker 컨테이너 `snsg-redis`)에 붙어 실제로 테스트한다. Testcontainers 의존성은 저장소에 없다. 예: `RedisRefreshTokenStoreAdapterTest`, `RedisSignUpSessionAdapterTest`.
  - Redis가 꺼져 있으면 건너뛰지 않고 실패한다. 실패하면 코드보다 컨테이너가 떠 있는지 먼저 본다.
  - 모든 워크트리가 Redis를 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 테스트는 고정된 키(고정 UUID 사용자·세션)를 쓰고, 만든 키를 `@AfterEach`에서 이름으로 지운다. 패턴으로 찾아 지우지 않는다. 다른 작업의 키까지 지운다.
- `@WebMvcTest`에서 Spring Security를 쓰려면 `spring-boot-starter-security-test`가 있어야 한다. 이 모듈이 `@AutoConfigureMockMvc`에 보안 자동 설정을 넣는다. 없으면 `HttpSecurity` 빈도 `@AuthenticationPrincipal` 리졸버도 없다.
- 인가 규칙과 401 형식은 auth에서 `@Import`로 `SecurityConfig`와 인증 진입점(entry point)을 넣고, 토큰 검증 adapter를 `@MockitoBean`으로 대체해 검증한다.
  - 예: `auth/infrastructure/security/SecurityConfigTest`. `@WebMvcTest(controllers = 대상 컨트롤러)`에 `SecurityConfig`·`JwtAuthenticationFilter`·`JwtAuthenticationEntryPoint`를 `@Import`하고 `TokenPort`와 컨트롤러가 의존하는 서비스를 `@MockitoBean`으로 둔다. 테스트 코드는 ArchUnit 검사 대상이 아니라 다른 도메인 컨트롤러를 대상으로 삼아도 된다. 지금은 `UserController`·`ScheduleController`·`WithdrawalController`(auth)를 대상으로 공개 경로(닉네임 사용 가능 여부, `GET /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`)와 인증 필요 경로(`/api/v1/users/me`의 조회·수정·사진 업로드, `GET /api/v1/users/me/withdrawal-eligibility`, `DELETE /api/v1/users/me`, `GET /api/v1/schedules/participating`·`/hosted`, `GET /api/v1/schedules/{scheduleId}/participants`, `POST /api/v1/schedules`, `POST /api/v1/schedules/{scheduleId}/cancellation`)를 함께 본다.
  - 대상 컨트롤러에 의존이 늘면 이 테스트와 그 컨트롤러의 `@WebMvcTest`에 `@MockitoBean`을 함께 더해야 컨텍스트가 뜬다.
  - `SecurityConfig`는 `CorsConfigurationSource` 빈을 주입받는데, 실제 빈은 `common/config/CorsConfig`가 설정값으로 만든다. 슬라이스에는 올라오지 않으므로 빈 `UrlBasedCorsConfigurationSource`를 nested `@TestConfiguration`으로 준다. CORS 규칙은 이 테스트의 대상이 아니다.
- 다른 도메인의 컨트롤러 테스트는 auth를 import하지 않는다. `@AutoConfigureMockMvc(addFilters = false)`로 필터를 끄고 `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(userId, null, List.of())`을 직접 넣는다.
- `OpenApiSpecTest`는 `/v3/api-docs.yaml`로 생성한 스펙과 커밋된 `docs/openapi.yaml`을 비교한다. 줄바꿈은 정규화해 비교한다(autocrlf). build.gradle의 test task inputs에 스펙 파일과 `OPENAPI_UPDATE`를 넣어, 파일만 바뀌어도 테스트가 건너뛰어지지 않게 한다. 스펙 파일은 `inputs.files`로 선언한다. Gradle 9.7에서 `inputs.file(...).optional()`은 파일이 없으면 테스트 태스크를 시작하지 않아, 스펙을 처음 만들 수 없다.
- 패키지 의존 규칙(`.claude/rules/package-structure.md`)은 ArchUnit 테스트 `com.shineunsigan.snsg.ArchitectureTest`가 검사한다. 규칙 하나에 테스트 메서드 하나다.
  - 프로덕션 클래스만 검사한다(`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`). 테스트 코드는 다른 도메인을 import해도 걸리지 않는다.
  - 도메인은 base package 바로 아래 패키지 중 `common`을 뺀 것으로 자동 인식한다. 도메인 이름을 적어두지 않으므로 도메인을 추가해도 테스트를 고치지 않는다. base package 바로 아래 클래스(`SnsgApplication`)와 base package 밖(라이브러리)으로 향하는 의존은 보지 않는다.
  - 클래스 이름 접미사로 대상을 고르는 규칙이 있다. `Facade`로 끝나는 클래스와 그 메서드에 `@Transactional`(Spring·jakarta)이 없는지, `CommandService`·`QueryService`로 끝나는 클래스가 `application.port.out`의 타입 중 `EventPort`로 끝나지 않는 것에 의존하지 않는지 본다. 이름 규칙을 벗어나면 규칙이 조용히 빠지므로 클래스 이름을 바꿀 때 함께 본다.
  - Spring 컨텍스트 없이 돈다. `./gradlew test --tests '*Architecture*'`로 따로 돌릴 수 있다.

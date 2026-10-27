# Package structure

도메인별 DDD 4계층. 각 도메인은 언제든 별도 서비스로 분리될 수 있어야 하며, 아래 규칙은 모두 이 목표를 위한 것이다.

## 레이아웃

`{base}`는 CLAUDE.md의 base package.

```
{base}
├── common/                       # 유틸 + 비즈니스 로직 없는 공통 규약만
└── <domain>/
    ├── presentation/             # Controller, errorhandler
    │   └── dto/request, dto/response
    ├── application/              # XxxCommandService, XxxQueryService
    │   ├── port/in/              # inbound: XxxUseCase (타 도메인 전용 입구)
    │   ├── port/out/             # outbound: XxxPort (infrastructure가 구현)
    │   ├── event/                # 타 도메인에 알리는 이벤트 (발행 도메인 소유)
    │   └── dto/command, dto/result
    ├── domain/
    │   ├── model/                # @Entity, VO(@Embeddable)
    │   ├── repository/           # repository 인터페이스
    │   ├── constants/            # enum
    │   └── exception/            # <Domain>ErrorCode (ErrorType + 메시지)
    └── infrastructure/           # 기술별 하위 패키지: persistence/, client/, event/, redis/, config/ ...
```

## 의존 방향

- presentation → application → domain. 역방향 import 금지.
- **도메인 간 의존은 한 방향만 둔다.** A가 B를 호출하면 B는 A를 호출하지 않는다. 서로 참조하는 두 도메인은 사실상 한 덩어리여서 나중에 분리할 수 없다.
  - 반대 방향이 필요해지면 호출 대신 **이벤트**를 쓴다. 발행하는 쪽은 받는 쪽을 모른다.
  - 어느 쪽이 상대를 알아야 하는지 애매하면, **데이터를 소유한 쪽이 모르게** 만든다.
- application·domain과 infrastructure 사이는 반드시 DIP. application·domain은 infrastructure를 import하지 않는다.
  - DB: `domain/repository` 인터페이스 ← `infrastructure/persistence`의 `XxxRepositoryImpl`
  - 그 외 외부 기술(Redis, Kafka, 외부 API, 타 도메인 등): `application/port/out`의 `XxxPort` ← `infrastructure`의 `XxxAdapter`
- presentation은 infrastructure를 import하지 않는다. 같은 도메인의 presentation과 infrastructure가 같은 설정값을 읽어야 하면 그 설정 record를 application에 둔다. 예: refresh 쿠키 수명(presentation)과 Redis TTL(infrastructure)은 `auth/application/AuthProperties` 하나에서 읽는다.
- 계층 방향, infrastructure 참조 금지, 도메인 간 참조 허용 대상, 도메인 간 순환 금지, common의 도메인 import 금지는 ArchUnit 테스트(`ArchitectureTest`)가 검사한다. 리뷰에 맡기지 않는다. 검사 범위는 `.claude/rules/testing.md`의 ArchUnit 항목.

## Service와 UseCase

| | 담당 | 의존할 수 있는 것 | 트랜잭션 |
|---|---|---|---|
| `XxxCommandService` | 자기 도메인의 쓰기 | repository, 이벤트 발행 포트 | `@Transactional` |
| `XxxQueryService` | 자기 도메인의 조회 | repository | 읽기 전용 |
| `XxxUseCase` (행위별 클래스) | 흐름 조합 | 자기 도메인의 `port/out`(외부 API·저장소·타 도메인), Command·Query 서비스 | **걸지 않는다** |

- **Command·Query 서비스는 repository와 이벤트 발행 포트만 의존한다.** 외부 API, 타 도메인 호출, 다른 기술(Redis 등)은 여기 들어오지 않는다.
  - **같은 도메인의 다른 서비스도 주입하지 않는다.** 서비스끼리 엮이면 트랜잭션이 중첩되고 의존이 번진다. 조합은 UseCase가 한다.
  - `port/out`을 서비스에 넣고 싶어지면, 그 흐름을 UseCase로 옮긴다. 이벤트 발행 포트만 예외다.
  - **설정(`@ConfigurationProperties` record)은 의존으로 세지 않는다.** I/O도 호출할 메서드도 없는 값 묶음이라, 규칙이 막으려는 외부 시스템·타 도메인과 성격이 다르다. 설정값은 `@Value` 대신 record로 묶어 주입한다.
  - 의존이 4개를 넘으면 애그리거트 경계가 잘못 그어진 건 아닌지 다시 본다. 한 애그리거트의 구성요소 수가 상한이다.
- **외부 호출이나 타 도메인 호출, 여러 서비스 조합이 필요한 흐름은 `XxxUseCase` 클래스가 맡는다.** 행위마다 하나씩 만든다(`SocialLoginUseCase`, `CompleteSignUpUseCase`).
  - 이렇게 두면 **트랜잭션 안에 외부 호출이 들어갈 수 없다.** 규칙으로 막는 게 아니라 구조가 막는다. 외부 API가 느려져 DB 커넥션이 말라붙는 사고를 구조적으로 차단한다.
- 가진 데이터가 없는 도메인은 Command 서비스 없이 UseCase만 가질 수 있다. auth가 그렇다. 자기 테이블이 생기면 그때 만든다.
- 서비스가 커져 한눈에 안 들어오면(대략 150줄) 책임별로 나눈다. 행위별로 나누지 않는다.

## Port

- inbound `port/in/XxxUseCase`: 타 도메인이 이 도메인을 호출하는 입구만 둔다 (코드 참조, 타 도메인 이벤트 수신). `port/in` 목록이 곧 도메인 간 계약이다.
- **UseCase는 행위별로 만든다.** 단 자기 도메인 안에서 Command/Query 서비스만으로 끝나는 일에는 만들지 않는다. 만드는 경우는 둘이다.
  - 타 도메인이 호출해야 할 때
  - 여러 서비스를 조합해야 할 때
- **구현은 `XxxCommandService`·`XxxQueryService`가 UseCase를 구현한다.** UseCase 구현만을 위한 별도 클래스를 만들지 않는다.
- 사용자 요청은 UseCase가 아니다. Controller는 같은 도메인의 Service를 직접 호출한다.
- 같은 기능을 사용자와 타 도메인이 모두 쓰면 Service가 UseCase를 구현하고, Controller는 Service를 그대로 호출한다.
- UseCase는 기능 단위로 나누고 이름은 동사로 시작한다 (`CreateMatchingUseCase`, `AcceptMatchingUseCase`). 하나의 Service가 여러 UseCase를 구현해도 된다.
- UseCase 구현만을 위한 별도 facade/adapter 클래스는 만들지 않는다. 여러 Service를 조합해야 할 때만 둔다.
- 서비스 분리 시 UseCase 하나가 `/internal/v1` API 하나가 된다. 호출 측 adapter는 HTTP 클라이언트로 교체한다.
- outbound `port/out/XxxPort`: application이 필요로 하는 외부 기능. 기술 이름이 아니라 역할로 이름 짓는다 (`UserSettingPort`, `MatchingEventPort`).
- adapter가 설정값을 읽어 정책을 수행해도 된다. 예로 JWT 어댑터가 `auth.access-token-validity`를 읽어 만료를 계산한다. 정책의 이름은 포트 메서드(`issueAccessToken`)가 드러내고 숫자는 설정에 있으면 충분하다.
  - 단 **정책에 분기가 생기면 application으로 올린다.** 사용자·상황에 따라 값이 달라질 때, 종류가 셋 이상으로 늘 때, 계산에 조건이 붙을 때다.

## 도메인 간 참조

- 다른 도메인을 import할 수 있는 곳은 infrastructure 구현체뿐이고, 대상은 상대 도메인의 다음 넷으로 한정한다.
  - `application/port/in`: UseCase
  - `application/dto`: UseCase의 Command·Result
  - `application/event`: 이벤트
  - `domain/constants`: 계약(UseCase·Command·Result·이벤트)에 실린 enum
- 코드 참조: `A.application` → `A.application.port.out.XxxPort` ← `A.infrastructure.client.XxxAdapter` → `B.application.port.in.XxxUseCase`
- 이벤트: `B.infrastructure.event`의 리스너가 `A.application.event`의 이벤트를 받아 `B.application.port.in.XxxUseCase`를 호출한다.
- 상대 도메인의 Result·이벤트는 adapter/리스너 안에서 자기 DTO(Command)로 변환한다.
- **enum도 도메인마다 각자 소유한다.** 상대 도메인 enum은 **infrastructure의 adapter·리스너에서만** 참조하고, 그 자리에서 자기 enum(또는 필요한 값)으로 바꾼다. application·domain·presentation은 상대 도메인 enum을 참조하지 않는다. 포트와 UseCase로 경계를 세워놓고 enum 한 줄로 구멍을 내지 않기 위해서다.
  - 계약 전용 enum을 따로 만들지 않는다. 계약에 실린 도메인 enum(`domain/constants`)을 그대로 쓴다. 예외: 계약에 내보내면 안 되는 값이 있는 enum만 그 도메인에 계약용 enum을 따로 둔다.
  - 변환은 `default` 없는 switch로 쓴다. 값을 추가하면 컴파일이 깨져 변환 누락을 잡는다.
  - 값만 가진 상수의 중복은 허용한다. 로직이 들어 있으면 중복하지 말고 어느 도메인 소유인지부터 다시 정한다.

## 이벤트 (Spring Event)

### 동기 호출과 이벤트를 가르는 기준

- **결과를 기다려야 하면 동기 호출.** 전제 조건 검증, 조회처럼 답에 따라 다음 동작이 갈리는 경우다.
- **내 도메인의 변경을 다른 도메인에 알리기만 하면 이벤트.** 받는 쪽이 자기 일을 할 뿐, 호출한 쪽에 답을 주지 않는다.
- **검증을 이벤트로 하지 않는다.** 이벤트는 AFTER_COMMIT이라 이미 저장된 뒤에 도착한다. 그때 "조건 위반"을 알아도 되돌리려면 보상 트랜잭션(saga)이 필요하다. 단순 전제 조건에 쓸 패턴이 아니다.

| 상황 | 방식 |
|---|---|
| 일정 생성 전 개설자가 유효한 사용자인지 | 동기 호출 |
| 참여자 목록에 보여줄 프로필 조회 | 동기 호출 |
| 탈퇴했으니 토큰을 지워라 | 이벤트 |
| 일정이 취소됐으니 참여자에게 알려라 | 이벤트 |

### 작성 규칙

- 이벤트는 발행 도메인의 `application/event`에 record로 두고, 과거형으로 이름 짓는다 (`MatchingCreatedEvent`).
- ID, 원시 타입, enum만 담는다. Entity/VO 금지 (Kafka 전환 시 그대로 직렬화할 수 있도록).
- 발행: Service는 `port/out/XxxEventPort`로 발행하고, `infrastructure/event/XxxEventAdapter`가 `ApplicationEventPublisher`로 구현한다. Kafka 도입 시 adapter만 교체한다.
- 도메인 간 이벤트는 기본적으로 결과적 일관성으로 처리한다.
  - 리스너: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
  - 리스너가 호출하는 UseCase 구현: `@Transactional(propagation = Propagation.REQUIRES_NEW)`. 없으면 AFTER_COMMIT 시점이라 수신 측 DB 변경이 저장되지 않는다.
- 같은 트랜잭션(`@EventListener`)은 두 도메인 변경이 반드시 원자적이어야 할 때만 쓰고, 그 이유와 "서비스 분리 시 saga 필요"를 주석으로 남긴다.
- AFTER_COMMIT 방식은 수신 실패 시 이벤트가 유실된다. 유실되면 안 되는 이벤트가 생기면 Spring Modulith(Event Publication Registry) 도입을 제안한다.
- **유실이 보안·정합성 문제로 번지는 이벤트는 수신 측에 안전망을 함께 둔다.** 이벤트는 즉시 정리용이고, 안전망이 최종 보장이다.
  - 예: 탈퇴 이벤트로 refresh token을 지우되, 재발급 시점에 사용자가 유효한지 다시 확인한다. 이벤트가 유실돼도 탈퇴한 사용자는 재발급받지 못한다.
  - 안전망을 만들 수 없으면 이벤트가 아니라 같은 트랜잭션에서 처리할 방법을 찾는다.

## DTO

- 계층별로 따로 만든다. 모두 record.
  - presentation: `XxxRequest`, `XxxResponse`
  - application: `XxxCommand`, `XxxResult`
  - infrastructure: 외부 연동용 자체 DTO
- 변환 메서드는 변환 결과 쪽에 둔다: `request.toCommand()`, `XxxResult.from(entity)`, `XxxResponse.from(result)`.
- Entity와 VO는 application 밖으로 나가지 않는다. Result에 Entity/VO를 담지 않는다.
- enum은 **같은 도메인 안에서는** 모든 계층이 공유해도 된다. 도메인을 넘을 때는 공유하지 않는다(위 "도메인 간 참조" 참고).

## domain

- 생성은 정적 팩토리(`create`, `of`), 상태 변경은 의도가 드러나는 메서드(`accept()`, `close()`). setter 금지.
- 불변식 검증은 Entity/VO 내부에서 하고, 위반 시 `BusinessException(<Domain>ErrorCode)`.
- `<Domain>ErrorCode`는 `ErrorType`과 메시지만 가진다. `HttpStatus` 등 전달 방식(HTTP, 메시지)에 속한 타입은 domain에서 import하지 않는다.
- JPA용 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.

### Entity

- 모든 엔티티는 `common/entity`의 `BaseEntity`를 상속한다. id(UUID v7), `createdAt`, `updatedAt`, `isDeleted`가 딸려온다.
- 변경 주체를 남겨야 하는 엔티티는 `BaseAuditEntity`를 상속한다. `createdBy`, `updatedBy`가 더해진다.
  - 판단 기준: 도메인 컬럼으로는 알 수 없는 "누가 바꿨나"가 운영에서 궁금해지면 Audit. 예로 일정을 누가 취소했는지(개설자인지 운영자인지).
  - 값은 사용자 id 문자열이거나 `Auditor.SYSTEM`, `Auditor.SYSTEM_SCHEDULER`다. 인증이 없는 요청은 `SYSTEM`이 들어간다.
- id는 UUID v7(`@UuidGenerator(style = VERSION_7)`)이다. 시간순으로 증가해 인덱스가 조각나지 않고, URL로 남의 데이터를 추측할 수 없다. 다른 키 전략이 꼭 필요한 테이블만 `BaseEntity`를 상속하지 않는다.
- `@Column(name = ...)`으로 컬럼명을 따로 주지 않는다. 필드명과 컬럼명을 항상 같게 두고, 이름을 두 곳에 적지 않는다.
- 모든 컬럼은 되도록 NOT NULL. 값이 없는 상태는 빈 문자열이나 0으로 표현한다. NULL에 의미를 담아야 할 때만 허용한다.

### 삭제

- **hard delete 하지 않는다.** 삭제는 `BaseEntity.delete()`로 `isDeleted`만 바꾼다.
- 조회는 삭제되지 않은 행만 본다. 전역 필터로 숨기지 않고 repository 쿼리에 조건을 명시한다. 관리자·통계 조회에서 빠져나오기 쉽고, 조인된 연관에서 조용히 누락되지 않는다.
- 개수 계산(정원 등)도 같은 조건으로 센다.
- 유니크 제약은 삭제되지 않은 행에만 건다. PostgreSQL 부분 유니크 인덱스를 쓴다.
  ```sql
  CREATE UNIQUE INDEX uix_xxx ON xxx (a, b) WHERE is_deleted = false;
  ```
- 작업 큐 성격의 테이블(outbox 등)은 예외를 둘 수 있다. 지울 때 보고하고 이유를 남긴다.

### 관계의 층위

```
도메인(패키지)  >  애그리거트  >  엔티티
```

| 관계 | 같은 도메인 | FK | 접근 방법 |
|---|---|---|---|
| 애그리거트 루트 ↔ 구성요소 | 예 | **건다** | 루트를 통해 접근. 같은 서비스가 다룬다 |
| 같은 도메인의 다른 애그리거트 | 예 | 걸지 않는다 | 각자 repository로 접근. 포트는 필요 없다 |
| 다른 도메인 | 아니오 | 걸지 않는다 | 반드시 자기 `port/out` → adapter → 상대 `port/in` |

예: `User`(루트) ← `UserSocialAccount`, `TermsAgreement`는 한 애그리거트라 FK를 건다. `Schedule`(루트) ← `ScheduleParticipant`, `ScheduleMessage`도 같다. 반면 `Schedule`이 참조하는 개설자(user)는 다른 도메인이라 ID만 보관한다.

### FK

- 애그리거트 루트와 그 구성요소 사이에만 FK를 건다. 구성요소는 루트와 생명주기가 같아야 한다. 루트 없이 혼자 존재하지 않는다.
- 애그리거트 경계를 넘는 참조는 FK를 걸지 않고 ID만 보관한다. 이 선이 나중에 서비스 분리 경계가 된다.
- 경계를 넘는 참조의 정합성은 생성 시점 검증, 이벤트나 UseCase 호출로 맞춘다. 조회 시 대상이 없을 수 있다는 전제로 화면을 만든다.

## 인프라 규약

### 마이그레이션 (Flyway)

- 파일은 `snsg/src/main/resources/db/migration/V<번호>__<설명>.sql`. 설명은 소문자와 밑줄.
- **머지 전에는 기존 마이그레이션 파일을 고쳐도 된다.** 로컬 DB는 스키마를 지우고 다시 적용한다. 머지된 뒤에는 절대 고치지 않고 새 버전을 더한다. 이미 적용된 환경에서 체크섬이 어긋나면 기동이 실패한다.
- 엔티티와 스키마는 `ddl-auto: validate`가 대조한다. 어긋나면 앱이 뜨지 않는다. Hibernate가 스키마를 만들게 하지 않는다.
- PostgreSQL 전용 문법(부분 유니크 인덱스 등)을 쓰므로 테스트도 실제 PostgreSQL로 돌린다.

### Redis

- 키는 `용도:식별자` 형태로 쓴다. 예: `refresh:{userId}`, `signup:{token}`.
- **TTL을 반드시 건다.** 만료를 Redis가 지우므로 정리 작업을 따로 만들지 않는다. 같은 값이 토큰 만료 등 다른 곳에도 쓰이면 **한 설정값에서 가져와** 두 곳이 어긋나지 않게 한다.
- 토큰처럼 탈취되면 피해가 큰 값은 **원문이 아니라 해시**를 저장한다. 덤프·백업이 유출돼도 복원할 수 없다.
- 운영 Redis는 `maxmemory-policy`를 `noeviction`(또는 `volatile-ttl`)로 둔다. `allkeys-*`면 TTL이 남아 있어도 임의로 지워져, 가입 세션처럼 진행 중인 데이터가 끊긴다.

## 코드 작성

- Lombok은 **반복되는 기계적 코드**에만 쓴다. 단순 주입은 `@RequiredArgsConstructor`, 엔티티·DTO는 `@Getter`.
- **생성자에서 변환이나 계산을 한다면 생성자를 직접 쓴다.** Lombok 생성자는 필드를 그대로 대입할 뿐이라, 억지로 맞추면 매번 다시 계산하거나 필드가 `final`이 아니게 된다.

## common

- 유틸과 비즈니스 로직 없는 공통 규약만 두고, 관심사별로 나눈다.
  - `common/exception`: `ErrorType`, `ErrorCode` 인터페이스, `BusinessException`, `CommonErrorCode`
  - `common/entity`: `BaseEntity`, `BaseAuditEntity`, `Auditor`
  - `common/config`: `JpaAuditingConfig`, `SecurityAuditorAware`
  - `common/web`: `ApiResponse`, 전역 예외 처리(`ErrorType` → `HttpStatus` 변환)
- domain·application은 `common.exception`, `common.entity`만 import한다. `common.web`은 presentation 전용.
- common은 어떤 도메인도 import하지 않는다.
- 도메인 간 코드 중복은 기본적으로 허용한다. 중복이 많이 쌓였을 때만 common 이동을 제안하고, 임의로 옮기지 않는다.

### common이 비대해지는 것을 경계한다

- **기본값은 "넣지 않는다"다.** 유틸처럼 보여도 서비스가 내린 판단이 담겨 있으면 도메인에 둔다.
  - 예: 생년월일 → 연령대("20대 후반") 변환은 공통 유틸이 아니라 user 도메인의 규칙이다. 다른 도메인이 필요해지면 user의 `port/in`으로 받아간다.
  - 예: JWT 발급·검증은 auth 도메인의 infrastructure다. 토큰 정책은 기술 유틸이 아니다.
- 쓰일 곳이 없는 패키지를 미리 만들지 않는다. 빈 `common/util`을 만들어두면 "애매하면 여기"가 된다.
- common에 넣거나 뺄 때는 트레이드오프를 사용자에게 보고하고, 판단 근거를 이 문서에 남긴다.

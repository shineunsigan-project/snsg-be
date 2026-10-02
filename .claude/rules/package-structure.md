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
- application·domain과 infrastructure 사이는 반드시 DIP. application·domain은 infrastructure를 import하지 않는다.
  - DB: `domain/repository` 인터페이스 ← `infrastructure/persistence`의 `XxxRepositoryImpl`
  - 그 외 외부 기술(Redis, Kafka, 외부 API, 타 도메인 등): `application/port/out`의 `XxxPort` ← `infrastructure`의 `XxxAdapter`

## Port

- inbound `port/in/XxxUseCase`: 타 도메인이 이 도메인을 호출하는 입구만 둔다 (코드 참조, 타 도메인 이벤트 수신). `port/in` 목록이 곧 도메인 간 계약이다.
- 사용자 요청은 UseCase가 아니다. Controller는 같은 도메인의 Service를 직접 호출한다.
- 같은 기능을 사용자와 타 도메인이 모두 쓰면 Service가 UseCase를 구현하고, Controller는 Service를 그대로 호출한다.
- UseCase는 기능 단위로 나누고 이름은 동사로 시작한다 (`CreateMatchingUseCase`, `AcceptMatchingUseCase`). 하나의 Service가 여러 UseCase를 구현해도 된다.
- UseCase 구현만을 위한 별도 facade/adapter 클래스는 만들지 않는다. 여러 Service를 조합해야 할 때만 둔다.
- 서비스 분리 시 UseCase 하나가 `/internal/v1` API 하나가 된다. 호출 측 adapter는 HTTP 클라이언트로 교체한다.
- outbound `port/out/XxxPort`: application이 필요로 하는 외부 기능. 기술 이름이 아니라 역할로 이름 짓는다 (`UserSettingPort`, `MatchingEventPort`).

## 도메인 간 참조

- 다른 도메인을 import할 수 있는 곳은 infrastructure 구현체뿐이고, 대상은 상대 도메인의 `port/in` UseCase와 그 Command/Result, `application/event`로 한정한다.
- 코드 참조: `A.application` → `A.application.port.out.XxxPort` ← `A.infrastructure.client.XxxAdapter` → `B.application.port.in.XxxUseCase`
- 이벤트: `B.infrastructure.event`의 리스너가 `A.application.event`의 이벤트를 받아 `B.application.port.in.XxxUseCase`를 호출한다.
- 상대 도메인의 Result·이벤트는 adapter/리스너 안에서 자기 DTO(Command)로 변환한다.

## 이벤트 (Spring Event)

- 이벤트는 발행 도메인의 `application/event`에 record로 두고, 과거형으로 이름 짓는다 (`MatchingCreatedEvent`).
- ID, 원시 타입, enum만 담는다. Entity/VO 금지 (Kafka 전환 시 그대로 직렬화할 수 있도록).
- 발행: Service는 `port/out/XxxEventPort`로 발행하고, `infrastructure/event/XxxEventAdapter`가 `ApplicationEventPublisher`로 구현한다. Kafka 도입 시 adapter만 교체한다.
- 도메인 간 이벤트는 기본적으로 결과적 일관성으로 처리한다.
  - 리스너: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
  - 리스너가 호출하는 UseCase 구현: `@Transactional(propagation = Propagation.REQUIRES_NEW)`. 없으면 AFTER_COMMIT 시점이라 수신 측 DB 변경이 저장되지 않는다.
- 같은 트랜잭션(`@EventListener`)은 두 도메인 변경이 반드시 원자적이어야 할 때만 쓰고, 그 이유와 "서비스 분리 시 saga 필요"를 주석으로 남긴다.
- AFTER_COMMIT 방식은 수신 실패 시 이벤트가 유실된다. 유실되면 안 되는 이벤트가 생기면 Spring Modulith(Event Publication Registry) 도입을 제안한다.

## DTO

- 계층별로 따로 만든다. 모두 record.
  - presentation: `XxxRequest`, `XxxResponse`
  - application: `XxxCommand`, `XxxResult`
  - infrastructure: 외부 연동용 자체 DTO
- 변환 메서드는 변환 결과 쪽에 둔다: `request.toCommand()`, `XxxResult.from(entity)`, `XxxResponse.from(result)`.
- Entity와 VO는 application 밖으로 나가지 않는다. Result에 Entity/VO를 담지 않는다.
- enum은 모든 계층에서 공유해도 된다.

## domain

- 생성은 정적 팩토리(`create`, `of`), 상태 변경은 의도가 드러나는 메서드(`accept()`, `close()`). setter 금지.
- 불변식 검증은 Entity/VO 내부에서 하고, 위반 시 `BusinessException(<Domain>ErrorCode)`.
- `<Domain>ErrorCode`는 `ErrorType`과 메시지만 가진다. `HttpStatus` 등 전달 방식(HTTP, 메시지)에 속한 타입은 domain에서 import하지 않는다.
- JPA용 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.

## common

- 유틸과 비즈니스 로직 없는 공통 규약만 두고, 관심사별로 나눈다.
  - `common/exception`: `ErrorType`(INVALID, NOT_FOUND, CONFLICT, FORBIDDEN 등), `ErrorCode` 인터페이스, `BusinessException`
  - `common/entity`: 감사 필드 base entity
  - `common/web`: `ApiResponse`, `SuccessCode`, 전역 예외 처리(`ErrorType` → `HttpStatus` 변환)
  - `common/util`
- domain·application은 `common.exception`, `common.entity`, `common.util`만 import한다. `common.web`은 presentation 전용.
- common은 어떤 도메인도 import하지 않는다.
- 도메인 간 코드 중복은 기본적으로 허용한다. 중복이 많이 쌓였을 때만 common 이동을 제안하고, 임의로 옮기지 않는다.

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
    ├── application/              # XxxCommandService, XxxQueryService, XxxFacade
    │   ├── port/in/              # inbound: XxxUseCase (타 도메인 전용 입구)
    │   ├── port/out/             # outbound: XxxPort (infrastructure가 구현)
    │   ├── event/                # 타 도메인에 알리는 이벤트 (발행 도메인 소유)
    │   └── dto/command, dto/result
    ├── domain/
    │   ├── model/                # @Entity, VO(@Embeddable)
    │   ├── repository/           # repository 인터페이스
    │   ├── constants/            # enum
    │   └── exception/            # <Domain>ErrorCode (ErrorType + 메시지)
    └── infrastructure/           # 기술별 하위 패키지: persistence/, client/, event/, redis/, scheduler/, config/ ...
```

## 의존 방향

- presentation → application → domain. 역방향 import 금지.
- **도메인 간 의존은 한 방향만 둔다.** A가 B를 호출하면 B는 A를 호출하지 않는다. 서로 참조하는 두 도메인은 사실상 한 덩어리여서 나중에 분리할 수 없다.
  - 반대 방향이 필요해지면 호출 대신 **이벤트**를 쓴다. 발행하는 쪽은 받는 쪽을 모른다.
  - 어느 쪽이 상대를 알아야 하는지 애매하면, **데이터를 소유한 쪽이 모르게** 만든다.
  - 한 흐름이 두 도메인에 걸치는데 어느 쪽이 상대를 부르면 순환이 되면, 자기 데이터가 없는 도메인이 흐름을 조합한다.
    - 예: 탈퇴는 user의 일이지만 확인할 데이터(남은 일정)는 schedule에 있다. schedule → user가 이미 있어 user가 schedule을 부를 수 없으므로 auth가 조합한다. `WithdrawFacade`(`SchedulePort`로 탈퇴 가능 확인 → `UserPort.withdraw`), `CheckWithdrawalFacade`. 의존은 auth → schedule, auth → user다.
    - 사용자 요청 탈퇴만 확인하고, 카카오 연결 끊기 웹훅 탈퇴(`HandleSocialUnlinkFacade`)는 확인하지 않는다. 그래서 user `WithdrawUserUseCase`의 입구가 둘(`withdraw`, `withdrawBySocialAccount`)이다.
- **admin(운영 도구 계정·인증)은 다른 도메인과 어느 방향으로도 import하지 않는다.** 일반 사용자는 어드민에 접속할 수도, 어떤 정보도 볼 수 없어야 한다(사용자 합의). 테이블(`admins`)·토큰 키·Redis 키·보안 체인을 user·auth와 모두 따로 둔다. 두 보안 설정에 공통인 것(401 진입점 `AdminAuthenticationEntryPoint` ↔ auth `JwtAuthenticationEntryPoint`, refresh 해시 저장 adapter)은 각자 둔다(도메인 간 중복 허용). 다른 도메인의 어드민 API는 admin을 부르지 않고 `/api/admin/v1/**` 아래에 두기만 한다(`.claude/rules/api.md` URL 절).
- application·domain과 infrastructure 사이는 반드시 DIP. application·domain은 infrastructure를 import하지 않는다.
  - DB: `domain/repository` 인터페이스 ← `infrastructure/persistence`의 `XxxRepositoryImpl`
  - 그 외 외부 기술(Redis, Kafka, 외부 API, 타 도메인 등): `application/port/out`의 `XxxPort` ← `infrastructure`의 `XxxAdapter`
- presentation은 infrastructure를 import하지 않는다. 같은 도메인의 presentation과 infrastructure가 같은 설정값을 읽어야 하면 그 설정 record를 application에 둔다. 예: refresh 쿠키 수명(presentation)과 Redis TTL(infrastructure)은 `auth/application/AuthProperties` 하나에서 읽는다.
- 계층 방향, infrastructure 참조 금지, 도메인 간 참조 허용 대상, 도메인 간 순환 금지, common의 도메인 import 금지, facade의 `@Transactional` 금지, Command·Query 서비스의 이벤트 발행 포트 외 `port/out` 의존 금지는 ArchUnit 테스트(`ArchitectureTest`)가 검사한다. 리뷰에 맡기지 않는다. 검사 범위는 `.claude/rules/testing.md`의 ArchUnit 항목.

## Service와 Facade

| | 담당 | 의존할 수 있는 것 | 트랜잭션 |
|---|---|---|---|
| `XxxCommandService` | 자기 도메인의 쓰기 | repository, 이벤트 발행 포트 | `@Transactional` |
| `XxxQueryService` | 자기 도메인의 조회 | repository | 읽기 전용 |
| `XxxFacade` (행위별 클래스) | 흐름 조합 | 자기 도메인의 `port/out`(외부 API·Redis·파일 저장소·타 도메인), Command·Query 서비스 | **걸지 않는다** |

- **Command·Query 서비스는 repository와 이벤트 발행 포트만 의존한다.** 외부 API, 타 도메인 호출, 다른 기술(Redis 등)은 여기 들어오지 않는다.
  - **같은 도메인의 다른 서비스도 주입하지 않는다.** 서비스끼리 엮이면 트랜잭션이 중첩되고 의존이 번진다. 조합은 facade가 한다.
  - `port/out`을 서비스에 넣고 싶어지면, 그 흐름을 facade로 옮긴다. 이벤트 발행 포트만 예외다.
  - **설정(`@ConfigurationProperties` record)은 의존으로 세지 않는다.** I/O도 호출할 메서드도 없는 값 묶음이라, 규칙이 막으려는 외부 시스템·타 도메인과 성격이 다르다. 설정값은 `@Value` 대신 record로 묶어 주입한다.
  - 의존이 4개를 넘으면 애그리거트 경계가 잘못 그어진 건 아닌지 다시 본다. 한 애그리거트의 구성요소 수가 상한이다.
    - 예: `UserWithdrawalCommandService`는 User 애그리거트 구성요소 다섯(users, 소셜 계정, 약관, 닉네임 이력, 탈퇴 기록)의 repository와 이벤트 발행 포트를 쓴다. 탈퇴·비식별이 애그리거트 전체를 다루므로 상한 안이다.
- **facade(`XxxFacade`)는 아래 두 경우에만 만든다.** application 패키지에 두고, 행위마다 하나씩 만든다(`SocialLoginFacade`, `CompleteSignUpFacade`). 트랜잭션을 걸지 않는다.
  - **이벤트 발행 포트가 아닌 `port/out`을 호출하는 흐름.** 외부 API, Redis, 파일 저장소, 다른 도메인이 여기 든다. 다른 도메인은 지금은 코드 참조 adapter, 서비스 분리 후에는 Feign → 상대의 `/internal` 컨트롤러다. 이유: 외부 호출을 트랜잭션 밖에 둔다.
    - 조회도 같다. 읽은 결과에 다른 도메인 값(이름 등)을 붙여야 하면 QueryService는 자기 데이터만 읽고 facade가 포트를 불러 붙인다. 포트는 건마다 부르지 않고 요청당 한 번 불러 맵으로 붙인다. 예: `ListSchedulesFacade`(`ScheduleQueryService`로 일정 카드를 읽고 `RegionPort.findAll`을 한 번 불러 지역 이름을 붙인다), `GetScheduleDetailFacade`(개설자와 확정 참여자 ID를 모아 `UserPort.findProfiles`를 한 번 부른다).
    - `GetScheduleDetailFacade`·`ListParticipantsFacade`(참여자 명단)는 맵에서 꺼낸 값이 있는지 따로 확인하지 않는다. user `FindUserProfilesUseCase`가 "요청한 서로 다른 ID마다 항목이 정확히 하나 있다"(탈퇴자·행이 없는 ID도 가려서 넣는다)를 javadoc에 계약으로 두기 때문이다. 이 계약을 바꾸면 이 facade들도 함께 고친다.
  - **같은 도메인의 여러 서비스를 조합하는 흐름.** 이유: 서비스끼리 주입하지 않는다.
  - 이렇게 두면 **트랜잭션 안에 외부 호출이 들어갈 수 없다.** 규칙으로 막는 게 아니라 구조가 막는다. 외부 API가 느려져 DB 커넥션이 말라붙는 사고를 구조적으로 차단한다.
- "다른 도메인 동기 호출"은 별도 기준이 아니다. facade는 다른 도메인의 서비스를 직접 부르지 않고 자기 `port/out`만 부르므로 첫째 경우에 든다.
- facade에는 비즈니스 규칙을 두지 않는다. 규칙은 엔티티·서비스에 둔다.
  - 포트·서비스가 돌려준 결과를 보고 흐름을 이어갈지 정하는 전제 조건 확인은 흐름 검증이라 facade에 둔다. 대상이 있는지, 활성인지, 활동이 고를 수 있는 유형인지 보고 아니면 `<Domain>ErrorCode`로 끊는 것이다. 활성 여부 같은 판정 값은 포트·서비스가 돌려준 것을 그대로 쓴다. 예: `CreateScheduleFacade`의 `validateActiveUser`·`findPlace`, `ListPlaceCandidatesFacade`의 `validateActiveRegion`.
- facade가 다른 facade를 부르지 않는다.
- 복잡도는 facade를 만드는 기준이 아니다. 흐름이 길어도 위 두 경우가 아니면 서비스에 둔다.
- 가진 데이터가 없는 도메인은 Command 서비스 없이 facade만 가질 수 있다. auth가 그렇다. 자기 테이블이 생기면 그때 만든다.
- 서비스가 커져 한눈에 안 들어오면(대략 150줄) 책임별로 나눈다. 행위별로 나누지 않는다.
  - 예: user의 `UserCommandService`(가입), `UserWithdrawalCommandService`(탈퇴와 보관 뒤 비식별), `UserProfileCommandService`(가입한 사용자의 프로필 수정). 한 서비스에 두면 150줄과 의존 4개를 넘는다. 나눈 서비스도 이름이 `CommandService`로 끝나야 ArchUnit 규칙이 적용된다.

## Port

- inbound `port/in/XxxUseCase`: 타 도메인이 이 도메인을 호출하는 입구만 둔다 (코드 참조, 타 도메인 이벤트 수신). `port/in` 목록이 곧 도메인 간 계약이다.
- **UseCase는 행위별로 만들고, 타 도메인이 호출해야 할 때만 만든다.** 자기 도메인 안에서만 쓰는 흐름에는 만들지 않는다. 여러 서비스 조합은 facade가 맡는다.
  - 기능 명세가 도메인 간 계약으로 정해 둔 UseCase는 호출하는 쪽 작업보다 먼저 만들어도 된다. 예: place `FindRegionUseCase`·`FindPlaceUseCase`, user `FindUserProfilesUseCase`.
- **`UseCase` 접미사는 `port/in` 인터페이스에만 쓴다.** 다른 도메인(코드 참조·이벤트 수신)이 부르지 않는 행위별 클래스는 `XxxFacade`다. 이름만 보고 도메인 간 계약인지 알 수 있게 한다.
- **구현은 `XxxCommandService`·`XxxQueryService`가 UseCase를 구현한다.** 흐름이 facade 기준에 해당하면 facade가 구현한다(`UploadProfileImageFacade`, `RevokeAuthFacade`).
- 사용자 요청은 UseCase가 아니다. Controller는 같은 도메인의 Service나 facade를 직접 호출한다(`.claude/rules/api.md` Controller 절).
- 같은 기능을 사용자와 타 도메인이 모두 쓰면 Service(facade 기준에 해당하면 facade)가 UseCase를 구현하고, Controller는 그 클래스를 그대로 호출한다.
- UseCase는 기능 단위로 나누고 이름은 동사로 시작한다 (`CreateMatchingUseCase`, `AcceptMatchingUseCase`). 하나의 Service가 여러 UseCase를 구현해도 된다.
- UseCase 구현만을 위한 별도 facade/adapter 클래스, 즉 서비스 메서드 하나로 넘기기만 하는 클래스는 만들지 않는다. facade 기준(Service와 Facade 절)에 해당하는 흐름이면 facade가 `port/in`을 구현해도 된다.
- 서비스 분리 시 UseCase 하나가 `/internal/v1` API 하나가 된다. 호출 측 adapter는 HTTP 클라이언트로 교체한다.
- outbound `port/out/XxxPort`: application이 필요로 하는 외부 기능. 기술 이름이 아니라 역할로 이름 짓는다 (`UserSettingPort`, `MatchingEventPort`).
  - 포트 이름은 도메인마다 겹쳐도 된다(auth·schedule 모두 `UserPort`). 단 **adapter는 Spring 빈이라 단순 클래스명이 다른 도메인과 같으면 빈 이름이 겹쳐 기동이 실패한다**(`ConflictingBeanDefinitionException`). 겹치면 앞에 자기 도메인 이름을 붙인다. 예: auth의 `UserAdapter`가 있어 schedule은 `ScheduleUserAdapter`·`SchedulePlaceAdapter`다. auth가 schedule을 부르는 adapter는 `AuthScheduleAdapter`다.
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
- 상대가 준 값으로 계산하는 규칙이 받는 쪽 화면·흐름의 규칙이면 받는 쪽 도메인에 둔다. 그 계산을 위해 상대 도메인에 계약(UseCase·Result 필드)을 늘리지 않는다. 받는 쪽 adapter가 상대 Result·enum을 자기 값으로 바꾸고, 계산은 자기 domain에서 한다.
  - 예: 일정 상세의 참여자 요약(성별 수, 최소·최대 연령대). user `FindUserProfilesUseCase`가 준 연령대(`AgeGroupResult`, user `AgePhase`)를 `ScheduleUserAdapter`가 schedule의 `ScheduleAgeGroupResult`·`AgePhase`로 바꾸고, 비교는 schedule `ParticipantAgeGroup`, 요약은 `ParticipantSummary`가 한다.
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
  - 리스너가 호출하는 UseCase 구현: `@Transactional(propagation = Propagation.REQUIRES_NEW)`. 없으면 AFTER_COMMIT 시점이라 수신 측 DB 변경이 저장되지 않는다. 구현이 facade면 facade에는 걸지 않고 DB를 바꾸는 서비스 메서드에 건다.
  - **트랜잭션 밖에서 발행한 이벤트는 AFTER_COMMIT 리스너에 도착하지 않는다.** 커밋할 트랜잭션이 없기 때문이다. 트랜잭션을 걸지 않는 facade(예: auth의 facade)에서 발행하지 않는다.
- **커밋된 뒤에만 해야 하는 되돌릴 수 없는 외부 정리(파일 삭제 등)는 같은 도메인 이벤트로 넘긴다.** Command 서비스가 트랜잭션 안에서 이벤트 발행 포트로 발행하고, 같은 도메인 `infrastructure/event`의 리스너가 AFTER_COMMIT으로 받아 `port/out`을 부른다. facade 없이 외부 호출이 트랜잭션 밖에 남고, 롤백되면 지우지 않는다. 리스너가 DB를 바꾸지 않으므로 `REQUIRES_NEW`는 필요 없다.
  - 예: `UserAnonymizedEvent`(보관 기간이 지난 탈퇴 회원의 비식별)·`ProfileImageReplacedEvent`(사진 교체) → `ProfileImageCleanupListener` → `FileStoragePort.delete`. 비식별 이벤트는 비식별 전 경로가 우리 업로드 경로일 때만 낸다(기본 이미지·소셜 사진은 지울 파일이 없다). 탈퇴(`UserWithdrawnEvent`)는 원본을 보관하므로 사진을 지우지 않는다.
  - **지울 대상이 사용자가 보낸 값(경로·키)에서 왔으면 저장할 때 막는다.** 우리가 만든 형식과 정확히 일치하는지, 다른 소유자가 이미 참조하고 있지 않은지 본다. 남의 경로를 자기 값으로 넣고 교체하거나 비식별되면 남의 파일이 지워진다. 삭제 표시됐어도 나중에 그 값으로 지울 행(보관 중인 탈퇴 회원)은 소유자로 센다. 삭제 adapter도 형식 밖의 값이면 아무것도 지우지 않는다. 예: `ProfileImage.isUploadedPath`(수정·가입·삭제가 함께 쓰는 형식 규칙), `UserRepository.isProfileImageUsedByOthers`, `LocalFileStorageAdapter.delete`.
- 같은 트랜잭션(`@EventListener`)은 두 도메인 변경이 반드시 원자적이어야 할 때만 쓰고, 그 이유와 "서비스 분리 시 saga 필요"를 주석으로 남긴다.
- AFTER_COMMIT 방식은 수신 실패 시 이벤트가 유실된다. 유실되면 안 되는 이벤트가 생기면 Spring Modulith(Event Publication Registry) 도입을 제안한다.
- **유실이 보안·정합성 문제로 번지는 이벤트는 수신 측에 안전망을 함께 둔다.** 이벤트는 즉시 정리용이고, 안전망이 최종 보장이다.
  - 예: 탈퇴 이벤트로 그 사용자의 refresh 세션을 모두 지우되, 재발급 시점에 사용자가 유효한지 다시 확인한다. 이벤트가 유실돼도 탈퇴한 사용자는 재발급받지 못한다.
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
  - 쓰기 경로가 없는 엔티티(마이그레이션으로만 관리하는 기준 데이터, 예: `Region`)에는 정적 팩토리를 두지 않는다. 팩토리를 쓸 곳이 테스트뿐이기 때문이다. 테스트 데이터는 native INSERT로 넣는다.
- 불변식 검증은 Entity/VO 내부에서 하고, 위반 시 `BusinessException(<Domain>ErrorCode)`.
- `<Domain>ErrorCode`는 `ErrorType`과 메시지만 가진다. `HttpStatus` 등 전달 방식(HTTP, 메시지)에 속한 타입은 domain에서 import하지 않는다.
- JPA용 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.

### Entity

- 모든 엔티티는 `common/entity`의 `BaseEntity`를 상속한다. id(UUID v7), `createdAt`, `updatedAt`, `isDeleted`가 딸려온다.
- 변경 주체를 남겨야 하는 엔티티는 `BaseAuditEntity`를 상속한다. `createdBy`, `updatedBy`가 더해진다.
  - 판단 기준: 도메인 컬럼으로는 알 수 없는 "누가 바꿨나"가 운영에서 궁금해지면 Audit. 예로 일정을 누가 취소했는지(`schedule_cancellations`, 개설자인지 운영자인지), 탈퇴 기록(`user_withdrawals`)을 누가 만들고(본인 요청이면 사용자 id, 웹훅이면 `SYSTEM`) 누가 비식별했는지(`SYSTEM_SCHEDULER`), 어드민을 누가 승인·거절·중지했는지(`admins.updated_by`, 처리한 MASTER의 adminId).
  - 값은 사용자 id 문자열이거나 `Auditor.SYSTEM`, `Auditor.SYSTEM_SCHEDULER`다. 인증이 없는 요청은 `SYSTEM`이 들어간다.
- id는 UUID v7(`@UuidGenerator(style = VERSION_7)`)이다. 시간순으로 증가해 인덱스가 조각나지 않고, URL로 남의 데이터를 추측할 수 없다. 다른 키 전략이 꼭 필요한 테이블만 `BaseEntity`를 상속하지 않는다.
  - 예: `regions`. 행정구역 코드(`CHAR(5)`)가 PK인 기준 데이터다. 지우지 않으므로 `is_deleted`가 없고, 숨김은 `is_active`가 맡는다. `created_at`·`updated_at`은 엔티티에서 직접 매핑한다.
- `@Column(name = ...)`으로 컬럼명을 따로 주지 않는다. 필드명과 컬럼명을 항상 같게 두고, 이름을 두 곳에 적지 않는다.
- 모든 컬럼은 되도록 NOT NULL. 값이 없는 상태는 빈 문자열이나 0으로 표현한다. NULL에 의미를 담아야 할 때만 허용한다.
- 외부 원천에서 적재하고 사람이 고치는 데이터는 화면 값과 원천 값(`source_*`)을 따로 둔다.
  - 원천 값은 적재할 때마다 갱신한다. 화면 값은 검수 전일 때만 원천 값으로 갱신한다. 사람이 정한 값을 적재가 덮어쓰지 않기 위해서다.
  - 승인된 행의 원천 변경 경고는 새 원천 값이 **지난 원천 값**과 다를 때만 낸다. 화면 값과 비교하면 사람이 고친 행이 적재 때마다 걸린다.
  - 예: `places`(`Place.confirmSource`).
  - 원천 응답에는 있지만 적재 대상에서 걸러진 행(업종 필터·값 정리)이 이미 저장돼 있으면 마지막 확인 시각(`last_synced_at`)만 갱신한다. 원천에 남아 있는 가게를 폐업으로 오판하지 않기 위해서다. 예: `PlaceCommandService.reflectPage` → `PlaceRepository.markSynced`.
  - 원천 값은 저장 컬럼의 정밀도·길이에 맞춘 뒤 비교하고 저장한다. 맞추지 않으면 같은 응답으로 다시 적재해도 원천이 바뀐 것으로 판정돼 승인 행마다 경고가 난다. 예: 좌표는 `NUMERIC(10,7)`라 소수 7자리 HALF_UP 반올림, 주소는 200자에서 자른다(`PlaceImportPolicy`).
  - 원천에서 사라진 행의 폐업 처리는 모든 페이지를 받은(완주한) 지역에만 한다. 받다 실패한 지역은 이미 반영한 페이지만 두고 폐업 처리에서 뺀다(`ImportPlacesFacade`, `PlaceCommandService.closeMissing`).

### 삭제

- **hard delete 하지 않는다.** 삭제는 `BaseEntity.delete()`로 `isDeleted`만 바꾼다.
- 조회는 삭제되지 않은 행만 본다. 전역 필터로 숨기지 않고 repository 쿼리에 조건을 명시한다. 관리자·통계 조회에서 빠져나오기 쉽고, 조인된 연관에서 조용히 누락되지 않는다.
  - 삭제된 행까지 읽어야 하는 메서드는 이름에 드러내고(`IncludingDeleted`), javadoc에 쓸 곳을 적어 묶는다. 활성 여부 판정에는 쓰지 않는다. 삭제된 행이 통과한다. 예: `UserRepository.findAllByIdIncludingDeleted`(탈퇴자를 가려 보여주는 `FindUserProfilesUseCase` 전용), `UserSocialAccountRepository`·`UserNicknameHistoryRepository`의 `findAllByUserIdIncludingDeleted`(탈퇴 회원 비식별 전용).
  - JPA repository(`JpaRepository`)의 내장 `findById`는 삭제된 행도 준다. 삭제 조건을 단 JPQL 메서드는 내장 메서드와 다른 이름으로 둔다. 예: `ScheduleRepository.findById`(잠금 없는 단건 조회)는 `ScheduleJpaRepository.findNotDeletedById`(`isDeleted = false` 명시)로 구현한다.
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

예: `User`(루트) ← `UserSocialAccount`, `TermsAgreement`는 한 애그리거트라 FK를 건다. `Schedule`(루트) ← `ScheduleParticipant`, `ScheduleCancellation`, `ScheduleMessage`도 같다. 반면 `Schedule`이 참조하는 개설자(user)는 다른 도메인이라 ID만 보관한다.

- 루트가 구성요소를 0~1건 갖더라도 루트 쪽에 역방향 `@OneToOne(mappedBy)`를 두지 않는다. Hibernate가 역방향 `@OneToOne`은 지연 로딩하지 못해, 루트를 읽는 목록·잠금 조회마다 루트 수만큼 SELECT가 더 나간다(N+1). 루트에서 읽어야 하면 지연 `@OneToMany(mappedBy)` 컬렉션으로 두고, 필요한 경로에서만 읽는다. 예: `Schedule.cancellations`(0~1건). `Schedule.cancellation()`은 상태가 `CANCELED`일 때만 컬렉션을 읽어, 취소되지 않은 일정은 취소 행을 조회하지 않는다. 루트에서 읽을 일이 없으면 구성요소 쪽에만 소유 측 매핑을 둔다(`UserWithdrawal`의 `@OneToOne(fetch = LAZY)`).

### FK

- 애그리거트 루트와 그 구성요소 사이에만 FK를 건다. 구성요소는 루트와 생명주기가 같아야 한다. 루트 없이 혼자 존재하지 않는다.
- 애그리거트 경계를 넘는 참조는 FK를 걸지 않고 ID만 보관한다. 이 선이 나중에 서비스 분리 경계가 된다.
- 경계를 넘는 참조의 정합성은 생성 시점 검증, 이벤트나 UseCase 호출로 맞춘다. 조회 시 대상이 없을 수 있다는 전제로 화면을 만든다.

## 인프라 규약

### 마이그레이션 (Flyway)

- 파일은 `snsg/src/main/resources/db/migration/V<번호>__<설명>.sql`. 설명은 소문자와 밑줄.
- **머지 전에는 기존 마이그레이션 파일을 고쳐도 된다.** 로컬 DB는 스키마를 지우고 다시 적용한다. 머지된 뒤에는 절대 고치지 않고 새 버전을 더한다. 이미 적용된 환경에서 체크섬이 어긋나면 기동이 실패한다.
- **번호는 개발 중에 develop의 다음 번호를 쓴다.** 기능 명세의 마이그레이션 표에 적힌 번호(V5 등)는 가칭이므로 쓰지 않는다. 다른 브랜치가 먼저 머지돼 번호가 겹치면 머지 직전에 이 브랜치의 번호를 올린다.
  - 가칭 번호를 그대로 쓰면 안 되는 이유: Flyway `outOfOrder`가 꺼져 있다(설정 없음, 기본값). 공유 로컬 DB에 더 큰 번호가 먼저 적용되면, 작은 번호를 가진 다른 워크트리는 "Detected resolved migration not applied to database"로 검증에 막혀 DB 테스트가 전부 실패한다.
- 미리 정해진 초기 데이터(지역 목록, 장소 검수 결과 등)는 마이그레이션 INSERT로 넣는다. UUID 키 테이블이면 id를 고정 UUID v7 리터럴로 적고 `gen_random_uuid()` 같은 생성 함수를 쓰지 않는다. 어느 환경에 적용해도 같은 id가 된다. 예: `V5__insert_reviewed_places.sql`.
- 엔티티와 스키마는 `ddl-auto: validate`가 대조한다. 어긋나면 앱이 뜨지 않는다. Hibernate가 스키마를 만들게 하지 않는다.
- PostgreSQL 전용 문법(부분 유니크 인덱스 등)을 쓰므로 테스트도 실제 PostgreSQL로 돌린다.

### 동시 요청과 잠금

- 동시에 오면 결과가 어긋나는 판정(정원, 시간 겹침)은 Command 서비스의 트랜잭션 안에서 **잠근 뒤 판정한다.** 잠금은 repository 메서드로 둔다.
  - 사용자 단위: `pg_advisory_xact_lock`(`ScheduleRepository.lockUser`). 같은 사용자의 개설·참여를 차례로 세워 겹침 검사가 서로의 결과를 보게 한다.
  - 행 단위: `@Lock(PESSIMISTIC_WRITE)` 조회, 즉 `SELECT … FOR UPDATE`(`ScheduleRepository.findByIdForUpdate`). 같은 일정의 참여·참여 취소·일정 전체 취소를 차례로 세운다.
    - 상태 전이도 같다. 어드민 승인·거절·중지·해제(`AdminCommandService`)는 대상 어드민을 `AdminRepository.findByIdForUpdate`로 처음 읽고 엔티티의 전이 메서드가 출발 상태를 본다. 두 MASTER가 같은 대상을 동시에 처리하면 뒤 요청은 앞 커밋을 기다려 바뀐 상태를 보고 409 `ADMIN_STATUS_CONFLICT`다. 조건부 일괄 UPDATE로 하지 않은 이유: Auditing을 거치지 않아 `updated_by`를 직접 넣어야 하고, 404·403·409를 가르려면 어차피 한 번 더 읽어야 한다.
- **둘을 함께 잡으면 순서는 항상 사용자 → 일정이다.** 순서가 섞이면 교착이 난다. 일정 하나만 잡는 흐름(참여 취소, 일정 전체 취소)은 사용자 잠금을 걸지 않는다.
- **잠금 조회는 그 트랜잭션에서 그 행을 처음 읽는 조회여야 한다.** 먼저 잠금 없이 읽어 영속성 컨텍스트에 올려두면, 잠근 뒤에도 옛 값(확정 인원·모집 상태)을 쓴다.
- **잠그는 조회에 연관 컬렉션을 fetch join하지 않는다.** PostgreSQL은 outer join의 nullable 쪽에 `FOR UPDATE`를 걸 수 없고, 잠글 대상은 루트 행 하나면 된다. 구성요소는 잠근 뒤 지연 로딩으로 읽는다(READ COMMITTED라 앞 트랜잭션이 커밋한 행을 본다).
- **부분 유니크 인덱스 위반을 오류 코드로 바꾸는 곳은 잠금 없이 경쟁이 실제로 나는 곳뿐이다.** 예: 닉네임(`UserRepositoryImpl.save`가 409 `DUPLICATED_NICKNAME`으로 바꾼다), 어드민 아이디(`AdminRepositoryImpl.save`가 `uix_admins_login_id` 위반을 409 `DUPLICATED_LOGIN_ID`로 바꾼다). 잠금으로 직렬화한 경로의 인덱스(`uix_participants_confirmed`)는 앱 경로로는 위반되지 않는 마지막 방어선이라 바꾸지 않는다. 위반되면 잠금 버그이므로 500으로 드러나게 둔다.
- **배치 실행 전체를 잠글 때는 세션 advisory lock(`pg_try_advisory_lock`)을 쓴다.** 언제 잠그는지는 스케줄러 절. 예: `place/infrastructure/persistence/PostgresPlaceImportLockAdapter`.
  - `pg_advisory_xact_lock`은 잡은 트랜잭션이 끝나면 풀려, 실행 전체를 트랜잭션 하나로 감싸야 한다. 외부 호출을 트랜잭션 밖에 두는 facade 규칙과 맞지 않는다.
  - 세션 잠금은 잡은 연결에서 풀어야 한다. `DataSource`에서 연결 하나를 직접 받아 잡고, 작업이 끝나면(예외가 나도) 같은 연결로 풀고 돌려준다. `JdbcTemplate`은 호출마다 다른 연결을 받을 수 있어 쓰지 않는다. 실행 동안 연결 하나를 점유하고, 반영은 다른 연결의 트랜잭션에서 한다. 서버가 죽어 연결이 끊기면 PostgreSQL이 푼다.
  - 작업이 예외로 끝난 뒤 해제까지 실패하면 작업의 예외를 던지고 해제 실패는 거기에 suppressed로 붙인다. 원인이 해제 실패에 가려지지 않게 한다.
  - 잠금 키는 다른 advisory lock과 겹치지 않게 고른다. 사용자 잠금(`ScheduleRepository.lockUser`)은 `hashtext`(int 범위) 키라 장소 적재는 int 범위 밖의 bigint 상수(`PostgresPlaceImportLockAdapter.LOCK_KEY`)를 쓴다.

### 목록 쿼리 (JPQL)

API 형식(커서·size·`nextCursor`)은 `.claude/rules/api.md` 목록 절. 여기는 쿼리 작성 방법이다. 예: `ScheduleRepositoryImpl`의 `findRecruiting`·`findParticipating`·`findHosted`, `AdminRepositoryImpl.findAll`(선택 상태 필터, (`createdAt`, id) keyset, 커서 `AdminCursor`).

- **선택 조건이 있는 쿼리는 고정 JPQL 조각을 조건이 있을 때만 이어 붙이고, 값은 모두 파라미터로 바인딩한다.** 값을 문자열에 넣지 않는다. `(:ids is null or s.x in :ids)`처럼 한 쿼리에 넣지 않는다. 컬렉션 파라미터에 null을 바인딩하게 된다.
  - 빈 목록 조건(빈 값만 보낸 필터)은 쿼리를 돌리지 않고 빈 결과를 준다. `in ()`을 만들지 않는다.
- 다음 페이지는 keyset으로 자른다: `s.startAt > :cursorStartAt or (s.startAt = :cursorStartAt and s.id > :cursorId)`, `order by s.startAt, s.id`. 한 건 더 읽기는 `setMaxResults(size + 1)`.
- **정렬과 커서 비교는 DB에서만 한다.** Java의 `UUID.compareTo`는 부호 있는 비교라 PostgreSQL의 uuid 순서와 다르다. 읽은 목록을 메모리에서 다시 정렬하거나 커서를 Java로 비교하면 페이지 경계에서 중복·누락이 난다.
  - 페이지·커서가 없고 정원으로 크기가 묶인 목록은 이 규칙에 들지 않는다. 애그리거트 루트가 구성요소를 메모리에서 정렬해 돌려준다. 예: 참여자 명단(`Schedule.confirmedParticipantsInListOrder`, 개설자 먼저 → `createdAt` → 행 id). 이때 id는 순서를 매 요청 같게 하는 보조 키일 뿐이고, DB uuid 순서와 같다고 기대하지 않는다.
- 삭제 조건(`isDeleted = false`)은 루트와 조인한 구성요소 모두에 명시한다(삭제 절).

### 일괄 UPDATE (`@Modifying`)

예: `ScheduleJpaRepository`의 `closeRecruitmentStarted`·`completeStartedBy`(일정 상태 전환).

- **JPA Auditing을 거치지 않는다.** 엔티티를 로드하지 않고 SQL을 바로 보내 `@LastModifiedDate`·`@LastModifiedBy`가 돌지 않는다. `updatedAt`·`updatedBy`를 쿼리에 직접 넣는다. `updatedAt`은 조건 판정에 쓴 기준 시각(now), `updatedBy`는 `Auditor` 상수(스케줄러면 `Auditor.SYSTEM_SCHEDULER`)를 파라미터로 넘긴다.
- **삭제된 행은 바꾸지 않는다**(`isDeleted = false`). 삭제한 행의 `updated_by`는 삭제한 주체를 가리키는데, 덮어쓰면 그 기록이 사라진다.
- **바뀔 행만 WHERE에 걸리게 쓴다.** 이미 목표 상태인 행을 다시 갱신하면 감사 값만 덮인다. 예: 모집 마감은 `RECRUITING`만 고른다. 이미 `CLOSED`(정원 도달)인 행의 `updated_by`가 그대로 남는다. 이렇게 쓰면 여러 번 돌아도 결과가 같다(멱등).
- 시각 경계는 `<=`다. 기준 시각을 서비스가 `LocalDateTime.now()`로 정해 넘긴다.
- `clearAutomatically`를 걸지 않았다. 지금은 그 트랜잭션에서 UPDATE 하나만 돌아 문제없다. **같은 트랜잭션에서 엔티티를 먼저 읽고 일괄 UPDATE를 부르면, 영속성 컨텍스트의 엔티티는 옛 값을 보인다.** 그런 흐름을 만들면 `@Modifying(clearAutomatically = true)`를 걸거나 순서를 바꾼다.

### 스케줄러

- 스케줄러는 그 도메인의 `infrastructure/scheduler`에 둔다. 같은 도메인의 Command 서비스(흐름이 facade 기준에 들면 facade)를 부르기만 하고, 트랜잭션은 서비스 메서드가 건다. 예: `schedule/infrastructure/scheduler/ScheduleStatusScheduler` → `ScheduleCommandService.closeStartedRecruitments`·`completeFinishedSchedules`.
  - 서로 독립인 작업은 서비스 메서드를 나눠 각자의 트랜잭션에서 돌린다. 한 작업이 실패해도 다른 작업은 돈다.
  - `@Component` 클래스라 단순 클래스명이 다른 도메인과 겹치지 않게 짓는다(Port 절의 빈 이름 충돌).
- 주기는 `@Scheduled(fixedDelayString = "${...}")`로 설정에서 읽는다. `fixedDelay`는 앞 실행이 끝난 뒤 간격을 재서 같은 서버에서 실행이 겹치지 않는다. 키는 그 도메인 설정 아래 둔다(`schedule.status-transition-interval: 1m`, 설정 절의 예외).
  - 정해진 시각에 도는 작업은 `@Scheduled(cron = "${...}")`로 읽는다. 예: `withdrawal.anonymize-cron: "0 0 4 * * *"`(탈퇴 회원 비식별, 매일 04:00). user 도메인이지만 prefix가 `user`가 아닌 이유는 설정 절.
- 조건부 일괄 UPDATE처럼 여러 서버에서 동시에 돌아도 결과가 같은 작업에는 분산 락(ShedLock 등)을 두지 않는다. READ COMMITTED에서 뒤 UPDATE는 앞 커밋을 기다린 뒤 WHERE를 다시 평가해 이미 바뀐 행을 건너뛴다.
  - 탈퇴 회원 비식별도 두지 않는다. 두 서버가 같은 회원을 처리해도 같은 값을 쓰고, 파일 삭제는 없으면 넘어간다.
- **결과는 같아도 겹쳐 돌면 비용이 두 배가 되는 작업은 실행 전체를 잠근다.** 호출 한도가 있는 외부 API를 부르는 적재가 그렇다. 잠금을 못 잡은 실행은 아무것도 하지 않고 로그만 남긴다. 운영은 ECS 서비스라 태스크가 둘 이상일 수 있다.
  - 예: 장소 적재(JOB-04, `place/infrastructure/scheduler/PlaceImportScheduler` → `ImportPlacesFacade`, 공공데이터포털 일 호출 한도). `@Scheduled(cron = "${place.import-cron}")`, 3·6·9·12월 1일 03:00.
  - 잠금은 facade가 `port/out`(`PlaceImportLockPort.runExclusively`)으로 잡는다. 스케줄러는 facade를 부르기만 한다. 잠그는 방법은 "동시 요청과 잠금" 절.
- **엔티티를 읽어 바꾸는(dirty checking) 스케줄러는 실행 동안 SecurityContext의 인증 주체를 `Auditor.SYSTEM_SCHEDULER`로 두고, `finally`에서 `SecurityContextHolder.clearContext()`로 비운다.** JPA Auditing이 SecurityContext에서 `updatedBy`를 읽기 때문이다. 넣지 않으면 `SYSTEM`이 남는다. 일괄 UPDATE는 Auditing을 거치지 않아 쿼리에 직접 넣는다(일괄 UPDATE 절).
  - 넣는 값: `new UsernamePasswordAuthenticationToken(Auditor.SYSTEM_SCHEDULER, null, List.of())`. 3인자 생성자라 인증된 상태가 되어 `SecurityAuditorAware`가 principal을 그대로 쓴다.
  - 예: `user/infrastructure/scheduler/UserAnonymizationScheduler`.
- **대상마다 따로 처리하고 한 건의 실패가 나머지를 막지 않아야 하면, 스케줄러가 대상 하나마다 서비스 메서드(`@Transactional`)를 따로 부른다.** 서비스 프록시를 거치므로 대상마다 트랜잭션이 나뉜다. 예외는 잡아 로그로 남기고 다음 대상으로 넘어간다. 예: `UserAnonymizationScheduler` → `UserWithdrawalCommandService.anonymize(userId)`.
  - 대상은 keyset 페이지로 대상이 없어질 때까지 읽는다(목록 쿼리 절). 기준 시각은 실행 시작 때 한 번 정해 모든 페이지에 같은 값을 쓴다. 페이지 크기는 설정값이다(`withdrawal.anonymize-page-size: 100`).
  - 한 번에 정해진 수만 처리하지 않는다. 대상이 그보다 많아지면 경고 없이 처리가 밀린다.
  - 커서 없이 "남은 대상이 없을 때까지" 반복하지 않는다. 계속 실패하는 한 건 때문에 끝나지 않는다. 커서가 실패한 대상을 지나가므로 그 대상은 다음 실행에서 다시 처리된다.
  - 이미 처리된 대상으로 다시 불려도 아무것도 바꾸지 않게 서비스 메서드를 쓴다(멱등).
- 켜고 끄는 스위치는 `common/config/SchedulingConfig` 하나다(`@EnableScheduling` + `@ConditionalOnProperty(scheduling.enabled=true)`). `application.yaml`에서 켜고(모든 프로파일), 테스트에서는 끈다(`.claude/rules/testing.md`). 도메인 스케줄러를 더할 때 따로 켜지 않는다. common에 둔 이유는 common 절.

### 보안 체인 (Spring Security)

사용자 인증(auth)과 어드민 인증(admin)은 `SecurityFilterChain`이 따로다(SHI-182).

- 어드민 체인 `admin/infrastructure/security/AdminSecurityConfig`: `securityMatcher("/api/admin/**")` + `@Order(Ordered.HIGHEST_PRECEDENCE)`로 먼저 고른다. 사용자 체인 `auth/infrastructure/security/SecurityConfig`는 matcher 없이 나머지 전부를 받는다. 요청 하나에는 체인 하나만 돈다.
- 체인마다 자기 토큰 필터·401 진입점만 넣는다. 사용자 토큰은 어드민 경로에서, 어드민 토큰은 사용자 경로에서 401 `UNAUTHENTICATED`다.
- **토큰 필터를 `@Component`(빈)로 두면 Spring Boot가 서블릿 필터로도 모든 URL에 자동 등록한다.** 체인이 하나일 때는 체인 안에서 먼저 돌아 `OncePerRequestFilter`가 두 번째 실행을 건너뛰어 드러나지 않는다. 체인이 둘이면 다른 체인의 필터가 체인이 끝난 뒤 서블릿 단계에서 돌고, 다른 키로 토큰을 해석하다 실패해 `clearContext()`를 불러 **컨트롤러의 `@AuthenticationPrincipal`이 null이 된다.** `@WebMvcTest`의 MockMvc도 Filter 빈을 등록하므로 슬라이스 테스트로 재현된다(`.claude/rules/testing.md` 보안 테스트 항목).
  - 사용자 필터(`JwtAuthenticationFilter`, 빈)는 `SecurityConfig`의 `FilterRegistrationBean.setEnabled(false)`로 자동 등록을 끈다.
  - 어드민 필터(`AdminAuthenticationFilter`)는 빈이 아니다. `AdminSecurityConfig`가 `new`로 만들어 체인에만 넣는다. 새 체인·필터를 더하면 이 방식을 따른다.
- **어드민 상태는 요청마다 DB에서 확인한다.** 필터가 `AdminQueryService.findApprovedRole`(삭제되지 않았고 `APPROVED`인 어드민의 역할)을 불러 있을 때만 인증을 채운다. 역할을 토큰에 싣지 않는다. 재발급(`AdminReissueTokenFacade`)도 승인 상태가 아니면 그 어드민의 세션을 모두 지운다. `APPROVED`만 통과하므로 승인 대기·거절·이용 중지는 다음 요청부터 401이고, 상태를 더해도 필터를 고치지 않는다.
- **역할 검사는 보안 체인의 매처로 한다**(`hasRole`). 메서드 보안(`@PreAuthorize`)과 서비스 안의 역할 확인은 쓰지 않는다. 역할은 필터가 요청마다 DB에서 읽은 값이라 토큰과 어긋나지 않는다.
  - 어드민 체인의 매처 순서: 공개 셋(`POST signup·login·tokens`) → `GET /api/admin/v1/admins/me` `authenticated()` → `/api/admin/v1/admins`·`/api/admin/v1/admins/**` `hasRole("MASTER")` → `anyRequest().authenticated()`. 처음 맞는 매처가 이기므로 `me`를 MASTER 경로보다 먼저 둬야 ADMIN도 내 정보를 본다. `me`는 GET만 연다. MASTER 전용 경로가 더 생기면 같은 방식으로 `hasRole` 매처를 더한다.
  - 역할이 모자라면 `AdminAccessDeniedHandler`(`exceptionHandling.accessDeniedHandler`)가 403 `MASTER_ONLY`를 우리 봉투로 쓴다. 없으면 Spring 기본 403(우리 봉투가 아님)이 나간다. 401 진입점과 같은 방식이다. `@Component`라 보안 슬라이스 테스트의 `@Import`에 넣어야 한다(`.claude/rules/testing.md`).
- **어드민 상태는 넷이다**(`PENDING`·`APPROVED`·`REJECTED`·`SUSPENDED`). 전이는 승인(`PENDING`→`APPROVED`), 거절(`PENDING`→`REJECTED`), 이용 중지(`APPROVED`→`SUSPENDED`), 해제(`SUSPENDED`→`APPROVED`)뿐이고 `Admin`의 전이 메서드가 지킨다. 그 밖의 출발 상태는 409 `ADMIN_STATUS_CONFLICT`다(같은 동작 반복 포함).
  - 거절은 행을 지우지 않고 `REJECTED`로 남긴다. 아이디 부분 유니크 인덱스에 남아 **거절된 아이디는 다시 쓸 수 없다.** 거절 이력과 처리한 MASTER(`updated_by`)가 남고, 로그인에서 거절을 알려줄 수 있다. `REJECTED`는 끝 상태다.
  - **MASTER는 어떤 상태 변경의 대상도 아니다**(자기 자신 포함, 403 `MASTER_NOT_MODIFIABLE`). MASTER는 DB로만 생기므로 관리도 DB로 한다. API로 열면 MASTER 하나가 다른 MASTER를 모두 중지하거나, 마지막 MASTER가 자기를 중지해 아무도 승인할 수 없게 된다.
  - 판정 순서는 대상 조회(404 `ADMIN_NOT_FOUND`) → MASTER인지(403) → 전이 가능한지(409)다.
- **이용 중지는 즉시 반영된다.** access token은 필터의 상태 확인으로 다음 요청부터 401이다. refresh 세션은 `SuspendAdminFacade`가 서비스의 중지(트랜잭션, 커밋) 뒤 `AdminRefreshTokenStorePort.deleteAll`로 모두 지운다. Redis는 이벤트 발행 포트가 아닌 `port/out`이라 facade다. 중지가 예외로 끝나면 지우지 않는다.
  - 재발급도 상태를 보므로 지우지 않아도 막히지만, 해제했을 때 중지 전 세션(최대 12시간)이 되살아나지 않게 지운다. 해제된 어드민은 다시 로그인한다. 커밋 뒤 삭제가 실패하면 응답은 500이지만 중지는 반영돼 있고, 재발급의 상태 확인이 안전망이다.
  - 받아들인 틈: 중지 뒤 access 수명(30분) 안에 해제하면, 중지 전에 받은 access token이 해제 뒤 다시 통한다. 막으려면 토큰 발급 시각과 상태 변경 시각을 비교해야 해 컬럼·토큰 형식이 늘어난다(사용자 합의).
- **어드민 세션은 절대 만료다(로그인 후 `admin.refresh-token-validity`).** 재발급은 회전하되 만료를 늘리지 않는다(사용자 refresh는 재발급마다 수명 전체로 다시 발급한다).
  - 세션 만료 시각은 refresh JWT의 `exp`에 싣는다. 로그인 때 `AdminTokenIssuer`가 지금 + 수명(초 단위로 자름, JWT exp가 초 단위라 재발급 때 읽은 값과 같아진다)으로 정하고, 재발급은 refresh에서 읽은 만료를 그대로 넘긴다. `AdminTokenPort`·`AdminRefreshTokenStorePort`는 만료 시각을 인자로 받는다.
  - Redis 세션 키 TTL은 남은 시간, 세션 집합 TTL은 저장할 때마다 수명 전체(남은 시간이 가장 긴 세션보다 먼저 사라지지 않게), 쿠키 Max-Age는 남은 시간을 초 단위로 올림한 값이다.
  - access 만료는 `min(지금 + access 수명, 세션 만료)`다. 세션이 끝난 뒤 access로 더 쓰는 시간을 없앤다.
  - 재발급은 해석한 세션 만료가 지났으면 발급하지 않는다(`INVALID_ADMIN_REFRESH_TOKEN`).
- **로그인 실패 잠금**(`AdminLoginFacade`, `RedisLoginAttemptAdapter`): 같은 아이디로 `admin.login-max-failures`번 연속 실패하면 `admin.login-lock-duration` 동안 잠근다.
  - 아이디는 **입력 문자열 그대로** 묶는다. 계정 존재 여부와 상관없이 센다. 없는 아이디도 똑같이 잠겨 잠금 응답으로 존재가 드러나지 않는다.
  - 아이디 없음과 비밀번호 틀림은 같은 401 `INVALID_ADMIN_CREDENTIALS`다. 아이디가 없어도 **더미 해시와 한 번 대조해** 응답 시간으로도 존재가 드러나지 않게 한다(`BCryptPasswordHashAdapter`가 기동 때 더미 해시를 만든다).
  - 잠긴 동안은 계정 조회·해시 대조·실패 기록을 하지 않는다. 비밀번호가 맞으면(승인 대기·거절·이용 중지로 403이 나가는 경우도) 횟수를 지운다. 상태(403 `ADMIN_PENDING_APPROVAL`·`ADMIN_REJECTED`·`ADMIN_SUSPENDED`)는 비밀번호가 맞은 뒤에만 알려준다. 정당한 사용자는 원인을 알아야 MASTER에게 문의할 수 있고, 비밀번호를 모르면 존재·상태 어느 것도 알 수 없다.
- **비밀번호는 BCrypt 해시로 저장한다**(`PasswordHashPort` ← `infrastructure/crypto/BCryptPasswordHashAdapter`). 해시가 필요한 흐름(가입·로그인)은 facade다. BCrypt는 입력의 앞 72바이트만 쓴다. 가입 규칙은 공백 없는 ASCII 최대 64자라 넘지 않고, 로그인 입력이 UTF-8 72바이트를 넘으면 대조 없이 불일치로 본다(`Admin.isComparablePassword`, 500이 아니라 401). `$2a$`·`$2b$`·`$2y$`(htpasswd, README의 MASTER 절차)를 모두 받는다.
- MASTER는 API로 만들지 않고 운영자가 DB에 직접 넣는다(절차는 `README.md`). 다른 어드민은 가입 신청(`ADMIN`·`PENDING`) 후 MASTER가 승인·거절하고, 승인된 어드민은 MASTER가 이용 중지·해제한다(SHI-183, 사용자 합의).

### Redis

- 키는 `용도:식별자` 형태로 쓴다. 식별자가 여럿이면 `:`로 잇는다. 예: `refresh:{userId}:{sessionId}`, `sessions:{userId}`, `signup:{token}`.
- 도메인끼리 키 접두사가 겹치지 않게 한다. admin은 `admin-`을 붙인다: `admin-refresh:{adminId}:{sessionId}`, `admin-sessions:{adminId}`, `admin-login-fail:{loginId}`(INCR, TTL 잠금 시간), `admin-login-lock:{loginId}`. 잠금 시작(잠금 키 SET + 횟수 키 DEL)과 실패 기록(INCR + EXPIRE)은 MULTI/EXEC로 묶는다.
- **TTL을 반드시 건다.** 만료를 Redis가 지우므로 정리 작업을 따로 만들지 않는다. 같은 값이 토큰 만료 등 다른 곳에도 쓰이면 **한 설정값에서 가져와** 두 곳이 어긋나지 않게 한다.
  - SET처럼 여러 번 덧붙이는 키는 덧붙일 때마다 TTL을 다시 건다. 예: `sessions:{userId}`는 세션을 저장할 때마다 refresh 만료와 같은 TTL로 늘린다.
  - **여러 키를 함께 만들며 TTL을 걸어야 하면 MULTI/EXEC(`SessionCallback`)로 묶는다.** 명령을 따로 보내면 중간에 실패할 때 TTL 없는 키가 남는다(SADD 뒤 EXPIRE 전). 예: `RedisRefreshTokenStoreAdapter.save`(SET + SADD + EXPIRE).
  - 지우는 쪽은 중간에 실패해 남는 것이 TTL이 걸린 무해한 값이면 묶지 않는다. 예: 로그아웃의 DEL + SREM(SET에 세션 id만 남아도 지울 대상이 없을 뿐이다), 탈퇴의 SMEMBERS 후 DEL 한 번(그 사이 생긴 세션은 재발급 때 사용자 확인에 막힌다).
- 토큰처럼 탈취되면 피해가 큰 값은 **원문이 아니라 해시**를 저장한다. 덤프·백업이 유출돼도 복원할 수 없다.
- 운영 Redis는 `maxmemory-policy`를 `noeviction`(또는 `volatile-ttl`)로 둔다. `allkeys-*`면 TTL이 남아 있어도 임의로 지워져, 가입 세션처럼 진행 중인 데이터가 끊긴다.

### 외부 API 연동

예: 상가정보 API(`place/infrastructure/client/SemasPlaceSourceAdapter`).

- 시간 초과는 config 빈이 request factory에 걸어 완성한 `RestClient`를 만들고, adapter는 그 `RestClient`를 주입받는다(`SemasClientConfig.semasRestClient`). adapter 생성자에서 builder에 request factory를 걸지 않는다. 테스트의 목 서버가 덮인다(`.claude/rules/testing.md`).
- **인증키가 요청 URL에 들어가는 API는 요청 URL과 예외 메시지를 로그·실패 이유에 남기지 않는다.** 예외는 종류(클래스 이름)와 HTTP 상태만 남긴다. 호출하는 facade도 같다(`ImportPlacesFacade.fetch`).
- 쿼리에 넣는 인증키는 URI 변수(`queryParam("serviceKey", "{serviceKey}")` → `encode()` → `buildAndExpand`)로 넣어 엄격히 인코딩한다. 템플릿 리터럴로 붙이면 `+`가 인코딩되지 않아 서버가 공백으로 읽는다. 공공데이터포털 키는 "일반 인증키(Decoding)" 원문을 설정(`public-data.api-key`)으로 받는다.
- **호출한 쪽이 실패 종류에 따라 흐름을 가르면, `port/out`은 실패를 예외 대신 결과로 돌려준다.** 예: `PublicPlaceSourcePort.fetchPage` → `PlaceSourcePageResult`(성공 / 데이터 없음 / 실패와 이유). facade는 실패면 같은 페이지를 다시 부르고, 데이터 없음(공공데이터포털 결과 코드 03)은 다시 불러도 같은 답이라 재시도하지 않는다. 포트가 예상하지 못한 예외를 던져도 facade가 실패로 다룬다.
- 재시도는 새 라이브러리 없이 반복문으로 한다. 최대 시도 횟수는 설정값이다(`place.import-max-attempts`, 첫 시도 포함).

### 설정 (`@ConfigurationProperties`)

- **prefix를 흔한 OS 환경변수 이름(`user`, `home`, `path`, `lang` 등)과 같게 두지 않는다.** Spring은 OS 환경변수도 속성으로 읽어 `USER`를 `user` 속성으로 잡는다. prefix와 정확히 같은 이름의 값이 있으면 Binder가 그 문자열을 record에 바인딩하려 해 기동이 실패할 수 있다. 리눅스 운영 서버에는 `USER`·`HOME`·`PATH`가 항상 있다. 예: 닉네임 정책은 `user.*`가 아니라 `nickname.*`(`user/application/NicknameProperties`)다. 탈퇴 회원 비식별 배치는 `withdrawal.*`(`user/application/WithdrawalProperties`)다. 페이지 크기·실행 시각은 닉네임 정책이 아니라 `nickname.*`에 두지 않았다.
  - 환경변수 이름도 relaxed binding으로 속성이 된다. `ADMIN_JWT_SECRET`은 `admin.jwt.secret`으로도 잡히므로 `admin.*` record(`AdminProperties`)에 `jwt` 필드를 두지 않는다(두면 그 값이 바인딩된다). 서명 키는 `admin-jwt.secret`(`admin/infrastructure/jwt/AdminJwtProperties`)에 따로 둔다.
- 두 규칙이 같은 기간을 쓰면 설정 하나에서 읽는다. 예: `nickname.protect-after-change`는 바꾸기 전 닉네임 보호 기간이자 닉네임 재변경 제한 기간이다. `nickname.protect-after-withdrawal`은 탈퇴 닉네임 보호 기간이자 탈퇴 원본 보관 기간(`user_withdrawals.anonymize_due_at`)이다.
- 설정은 그 값으로 계산하는 도메인에 둔다. 예: `schedule.withdrawal-block-after-start`(탈퇴를 막는 기간)는 흐름은 auth가 조합하지만 schedule이 탈퇴 가능 시각을 계산해 돌려주므로 `ScheduleProperties`에 있다.
- 예외: 애노테이션 속성이 읽는 값은 record를 주입할 자리가 없어 자리표시자(`"${...}"`)로 읽는다. 예: `@Scheduled(fixedDelayString = "${schedule.status-transition-interval}")`, `@Scheduled(cron = "${withdrawal.anonymize-cron}")`, `@Scheduled(cron = "${place.import-cron}")`, `@ConditionalOnProperty(name = "openapi.admin-group-enabled")`. 그 키는 record에 넣지 않고, 넣지 않은 이유를 yaml 주석으로 남긴다(`@ConfigurationProperties`는 모르는 키를 무시한다).
- **새 환경변수를 설정에서 읽으면 운영 목록 `snsg/.env.example`에 키를 값 없이 더한다.** 로컬 `snsg/.env`에도 넣어야 테스트 컨텍스트가 뜬다(`.claude/rules/testing.md`). 운영 값은 ECS task definition(`snsg-be-web-task`, 저장소 밖)에 넣는다. `.github/workflows/deploy.yml`은 최신 task definition의 이미지만 바꿔 배포하므로, 기본값 없는 자리표시자(`${DATA_GO_KR_API_KEY}`)가 task definition에 없으면 운영 기동이 멈춘다. task definition에 넣어야 한다는 것을 PR의 background에 적는다. 예: SHI-181의 `DATA_GO_KR_API_KEY`(`public-data.api-key`), SHI-182의 `ADMIN_JWT_SECRET`(`admin-jwt.secret`, 기본값 없음)·`ADMIN_CORS_ALLOWED_ORIGINS`(`cors.admin-allowed-origins`, 기본값 빈 목록).
  - `ADMIN_CORS_ALLOWED_ORIGINS`는 빠졌을 때 열리지 않고 닫히게 두었다. 없으면 어드민 경로의 브라우저 호출을 모두 거부한다(`CorsProperties`, SHI-182 결정). 사용자 쪽 `CORS_ALLOWED_ORIGINS`의 기본값(`*`)과 다르다.
  - 머지 전 워크트리에만 넣은 값을 다른 워크트리·메인 디렉터리 `.env`에 옮기는 절차는 `.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의".

## 코드 작성

- **메서드는 한 가지 일만 한다.** 검증·저장·후처리처럼 단계가 여럿 보이면 단계마다 이름 있는 private 메서드로 나눠, 공개 메서드는 단계 이름만 읽어도 흐름이 보이게 한다.
  - 나누는 단위는 같은 클래스 안의 private 메서드다. 이 목적으로 새 클래스·인터페이스를 만들지 않는다(CLAUDE.md "Simplicity First"의 한 번만 쓰는 추상화 금지와 맞춘다).
- Lombok은 **반복되는 기계적 코드**에만 쓴다. 단순 주입은 `@RequiredArgsConstructor`, 엔티티·DTO는 `@Getter`.
- **생성자에서 변환이나 계산을 한다면 생성자를 직접 쓴다.** Lombok 생성자는 필드를 그대로 대입할 뿐이라, 억지로 맞추면 매번 다시 계산하거나 필드가 `final`이 아니게 된다.

## common

- 유틸과 비즈니스 로직 없는 공통 규약만 두고, 관심사별로 나눈다.
  - `common/exception`: `ErrorType`, `ErrorCode` 인터페이스, `BusinessException`, `CommonErrorCode`
  - `common/entity`: `BaseEntity`, `BaseAuditEntity`, `Auditor`
  - `common/config`: `JpaAuditingConfig`, `SecurityAuditorAware`, `SchedulingConfig`, `CorsConfig`·`CorsProperties`, `OpenApiConfig`, `RedisConfig`
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

판단 기록:

- `common/config/SchedulingConfig`(SHI-173): 스케줄링 스위치(`scheduling.enabled`)다. schedule(상태 전환)·user(탈퇴 비식별, SHI-175) 스케줄러가 쓰고, 기능 명세의 place(장소 적재) 스케줄러도 같은 스위치를 쓴다. 한 도메인에 두면 다른 도메인이 그 도메인 설정에 기댄다. 비즈니스 로직이 없고 도메인을 import하지 않는다. 메인 클래스에 `@EnableScheduling`을 두지 않는 것은 `JpaAuditingConfig`와 같이 설정으로 끌 수 있어야 해서다. 대가: common/config가 하나 늘어난다. 주기·작업 내용은 각 도메인에 둔다.
- `common/config/CorsConfig`·`CorsProperties`의 어드민 출처 분리(SHI-182): `/api/admin/**`는 `cors.admin-allowed-origins`만, `/api/v1/**`는 `cors.allowed-origins`만 허용한다. refresh 쿠키가 `SameSite=None`이라 사용자 앱 출처가 어드민 경로를 credentials로 부를 수 있으면 사용자 앱의 XSS 한 번으로 어드민 세션이 재발급될 수 있다. 두 보안 체인이 같은 `CorsConfigurationSource` 빈을 주입받으므로 common에 그대로 두고 경로 문자열로 나눴다(도메인 import 없음). 두 패턴은 겹치지 않게 등록한다. `UrlBasedCorsConfigurationSource`는 처음 맞는 패턴 하나만 쓴다. 대가: 전에는 `/api/**` 전체였으므로, 두 prefix 밖의 새 경로(예: `/api/v2/**`)를 만들면 CorsConfig에 패턴을 더해야 한다.
- `common/config/OpenApiConfig`의 그룹 분리(SHI-182): 사용자·어드민 그룹을 경로 문자열로 나눈다. 규칙은 `.claude/rules/api.md` 스펙 그룹과 공개 범위 절.

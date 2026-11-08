## 배경
- Linear SHI-157. application 계층에서 "흐름 조합 클래스"의 이름과 기준을 정리한다.
- 지금 `XxxUseCase`라는 이름이 두 가지 뜻으로 쓰인다. `port/in`의 인바운드 인터페이스(`FindUserUseCase` 등)와, auth `application` 루트의 행위별 클래스(`SocialLoginUseCase` 등)다. 이름만 보고 다른 도메인이 부르는 계약인지 구분되지 않는다.
- `.claude/rules/package-structure.md` Port 절의 "UseCase 구현만을 위한 별도 facade/adapter 클래스는 만들지 않는다. 여러 Service를 조합해야 할 때만 둔다"가 아래 facade 기준(외부 호출도 facade)과 충돌한다.
- `UserCommandService`가 `FileStoragePort`(이벤트 발행 포트가 아닌 `port/out`)에 의존한다. "Command·Query 서비스는 repository와 이벤트 발행 포트만 의존한다" 규칙 위반인데 ArchUnit이 잡지 못한다.
- 사용자와 합의한 결정(2026-11-03)은 GOAL에 그대로 옮겼다.

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-157` (브랜치 `refactor/SHI-157-facade-rules`, 베이스 `845f085` = origin/develop). `snsg/.env` 복사돼 있다.
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다.
- 다른 세션이 `C:\Users\ks\nss\dev\snsg-be\wt-SHI-156`(일정 생성)을 작업 중이다. DB·Redis·8080 포트를 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 이번 작업은 마이그레이션을 더하지 않는다. 테스트를 돌리기 직전에 오케스트레이터에게 알리고, 그쪽이 테스트 중이면 기다린다.
- Docker 컨테이너 `snsg-postgres`, `snsg-redis`가 떠 있다(spec 작성 시 확인). 꺼져 있으면 Redis·DB 테스트가 skip되므로 skipped 수를 반드시 본다.
- 이미 깨져 있는 것: 알려진 것 없음. 베이스의 테스트 개수는 spec 단계에서 돌리지 않았다(공유 DB). implement가 변경 전에 `./gradlew test`를 한 번 돌려 개수를 기록하고, 그 수를 기준으로 삼는다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다. `&&`는 PowerShell 5.1에서 동작하지 않는다.

## GOAL
1. **facade 기준이 규칙 문서에 들어간다.** `.claude/rules/package-structure.md`(Service와 UseCase 절의 표·본문, Port 절)가 아래 결정을 담는다.
   - facade(`XxxFacade`, application 패키지, 트랜잭션을 걸지 않음)는 다음 두 경우에만 만든다.
     - 이벤트 발행 포트가 아닌 `port/out`을 호출하는 흐름(외부 API, Redis, 파일 저장소, 다른 도메인. 다른 도메인은 지금 코드 참조 adapter, 서비스 분리 후 Feign → 상대의 `/internal` 컨트롤러). 이유: 외부 호출을 트랜잭션 밖에 둔다.
     - 같은 도메인의 여러 서비스를 조합하는 흐름. 이유: 서비스끼리 주입하지 않는다.
   - "다른 도메인 동기 호출"은 별도 기준이 아니다. facade는 다른 도메인 서비스를 직접 부르지 않고 자기 `port/out`만 부른다.
   - facade에는 비즈니스 규칙을 두지 않는다(규칙은 엔티티·서비스). facade가 다른 facade를 부르지 않는다. 복잡도는 facade를 만드는 기준이 아니다.
   - `UseCase` 접미사는 `port/in` 인터페이스에만 쓴다. 다른 도메인(코드 참조·이벤트 수신)이 부르지 않는 행위별 클래스는 `XxxFacade`다.
   - "UseCase 구현만을 위한 별도 facade 금지" 문구는 의도(서비스 메서드 하나로 넘기기만 하는 클래스 금지)를 유지하고, facade 기준에 해당하는 흐름이면 facade가 `port/in`을 구현해도 된다로 고친다. "여러 Service를 조합해야 할 때만"이라는 제한은 남지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -n "Facade\|facade" .claude/rules/package-structure.md` → 위 항목 각각이 드러나는 줄이 있다. `grep -n "여러 Service를 조합해야 할 때만" .claude/rules/package-structure.md` → 출력 없음
2. **`application` 루트(포트 패키지 밖)에 `UseCase`로 끝나는 클래스가 없다.** auth의 `SocialLoginUseCase`, `CompleteSignUpUseCase`, `ReissueTokenUseCase`, `HandleSocialUnlinkUseCase`는 각각 `SocialLoginFacade`, `CompleteSignUpFacade`, `ReissueTokenFacade`, `HandleSocialUnlinkFacade`가 된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && find snsg/src/main/java -name '*UseCase.java' -not -path '*/port/in/*'` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && ls snsg/src/main/java/com/shineunsigan/snsg/auth/application/*Facade.java` → 위 넷과 `RevokeAuthFacade.java`
3. **RevokeAuth는 인바운드 계약이 된다.** `auth/application/port/in/RevokeAuthUseCase` 인터페이스가 있고 `RevokeAuthFacade`가 구현한다. `auth/infrastructure/event/UserWithdrawnEventListener`는 인터페이스에 의존하고 `RevokeAuthFacade`를 참조하지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -n "interface RevokeAuthUseCase" snsg/src/main/java/com/shineunsigan/snsg/auth/application/port/in/RevokeAuthUseCase.java` → 1줄. `grep -n "implements RevokeAuthUseCase" snsg/src/main/java/com/shineunsigan/snsg/auth/application/RevokeAuthFacade.java` → 1줄. `grep -n "RevokeAuthFacade" snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/event/UserWithdrawnEventListener.java` → 출력 없음
4. **프로필 사진 업로드가 `UserCommandService`에서 facade로 옮겨지고, `UserCommandService`는 `FileStoragePort`에 의존하지 않는다.** 업로드는 `user/application/UploadProfileImageFacade`가 `UploadProfileImageUseCase`를 구현해 맡는다. 예외로 남기지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -n "FileStoragePort\|UploadProfileImageUseCase\|upload" snsg/src/main/java/com/shineunsigan/snsg/user/application/UserCommandService.java` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -n "implements UploadProfileImageUseCase\|FileStoragePort" snsg/src/main/java/com/shineunsigan/snsg/user/application/UploadProfileImageFacade.java` → 둘 다 나온다
5. **ArchUnit 규칙 두 개가 `ArchitectureTest`에 더해진다.** 규칙 하나에 테스트 메서드 하나다(`.claude/rules/testing.md`).
   - R-F: 이름이 `Facade`로 끝나는 클래스와 그 메서드에 `@Transactional`이 없다.
   - R-S: 이름이 `CommandService`·`QueryService`로 끝나는 클래스는 `port.out` 패키지의 타입 중 이름이 `EventPort`로 끝나지 않는 것에 의존하지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157/snsg && ./gradlew test --tests '*ArchitectureTest'` → BUILD SUCCESSFUL, 기존 7개 + 2개 = 9개 통과, 실패 0
6. **새 규칙 각각이 위반 클래스로 실패한다.** implement가 임시 위반 코드를 main에 넣어 해당 규칙의 테스트 하나만 실패하는 것을 확인하고 되돌린다(SHI-144 선례). 최소 3건: R-F 클래스 레벨 `@Transactional`, R-F 메서드 레벨 `@Transactional`, R-S 비이벤트 `port/out` 의존.
   - 판정 방법: 검증 에이전트는 파일을 만들 수 없다. **(a) implement 보고에 건별로 "넣은 위반 코드 경로·내용, 실패한 테스트 이름(하나), 나머지 통과"가 있고 (b) 위반 코드가 워크트리·스테이징에 남아 있지 않으면** PASS다. 오케스트레이터가 사용자 보고 전에 직접 재실행할 수 있다.
   - 검증(b): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && git status --short && grep -rln "ArchProbe" snsg/src` → `git status`에 위반용 파일 없음, grep 출력 없음
7. **동작이 바뀌지 않고 전체 빌드가 통과한다.** 기존 테스트는 클래스 이름 변경에 맞춰 이름·참조만 바뀌고(테스트 파일 이름 변경 포함) 단정과 시나리오는 그대로다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157/snsg && ./gradlew build` → BUILD SUCCESSFUL. 테스트 결과(`build/test-results/test/*.xml`의 tests/failures/errors/skipped 합계)가 실패 0, 에러 0, **skipped 0**, 개수 = 베이스 개수 + 2
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && git diff --staged -M --stat -- snsg/src/test` → 테스트 파일은 이름 변경(rename)과 참조 수정, `ArchitectureTest` 추가분, `UserCommandServiceTest`의 `FileStoragePort` mock 제거만 보인다. `git diff --staged -M -- snsg/src/test ':!*ArchitectureTest.java'`에서 `assert`/`verify`/`when(` 줄의 변경은 클래스·변수 이름 치환뿐이다
8. **규칙·스킬·문서에 옛 이름이 남지 않는다.** `.claude/rules/package-structure.md`의 ArchUnit 검사 범위 문장에 두 규칙이 더해지고, `.claude/rules/testing.md`의 ArchUnit 항목이 현재 규칙 구성과 맞는다. 옛 클래스 이름을 언급하는 rules/skills/docs/코드 주석은 새 이름이 된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -rn "SocialLoginUseCase\|CompleteSignUpUseCase\|ReissueTokenUseCase\|HandleSocialUnlinkUseCase" --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=tasks .` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && grep -rn "RevokeAuthUseCase" --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=tasks .` → `port/in/RevokeAuthUseCase.java`, 그것을 import·구현·주입하는 코드와 테스트만 나온다(옛 `auth/application/RevokeAuthUseCase` 클래스를 가리키는 곳 없음)
9. **범위 밖 파일을 고치지 않는다.** 변경은 auth·user 도메인의 application/presentation/infrastructure 중 이름 변경·이동이 닿는 파일, 해당 테스트, `ArchitectureTest`, `.claude/rules`·`.claude/skills`·`docs`의 관련 문장, 이 task 폴더뿐이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-157 && git diff --staged --name-status -M` → 위 범위 밖 경로 없음. `place`·`activity` 등 다른 도메인 경로, `db/migration`, `build.gradle`, `docs/openapi.yaml` 없음

## 비목표
- 동작 변경은 하지 않는다. 메서드 시그니처·로직·예외·로그 문구는 그대로 둔다. 이름 변경, 클래스 이동, 규칙·문서 추가뿐이다.
- 기존 테스트의 단정과 시나리오를 고치지 않는다. 테스트 로직을 "개선"하지 않는다.
- `UploadProfileImageFacade`의 새 단위 테스트는 쓰지 않는다. 옮긴 메서드에는 분기가 없고(`ProfileImage.detect`는 `ProfileImageTest`가 본다) 기존에도 업로드 테스트가 없었다(`.claude/rules/testing.md` application 행).
- `auth/application/TokenIssuer`는 이름·위치를 바꾸지 않는다. facade가 아니라 로그인·가입·재발급이 공유하는 구성요소이며 이번 결정 대상이 아니다.
- `port/in`의 기존 인터페이스(`FindUserUseCase`, `RegisterUserUseCase`, `UploadProfileImageUseCase`, `WithdrawUserUseCase`, `FindPlaceUseCase`, `FindRegionUseCase`)는 이름을 바꾸지 않는다.
- `place`, `activity` 등 이번 변경과 무관한 도메인 파일은 건드리지 않는다.
- `.claude/tasks/` 아래 다른 이슈의 과거 task.md에 남은 옛 이름은 고치지 않는다(작업 기록이다).
- `docs/openapi.yaml`, `docs/api-guide.md`는 API가 바뀌지 않으므로 고치지 않는다. 옛 클래스 이름이 있으면 그 줄만 고친다(spec 작성 시 검색 결과 없음).
- 사용자 메모리(`C:\Users\ks\.claude\projects\...\memory\`)는 고치지 않는다.
- 판단이 갈리는 지점
  - 테스트 클래스 안의 `@Mock`/`@MockitoBean` 필드·지역 변수 이름은 새 클래스 이름에 맞춰 바꾼다(`socialLoginUseCase` → `socialLoginFacade`). `@DisplayName`·주석에 옛 클래스 이름이 있으면 그 단어만 바꾼다.
  - 위반 확인 중 기존 클래스가 새 규칙에 걸리면(예: 예상 못 한 `CommandService`의 비이벤트 포트 의존, `Facade` 클래스의 `@Transactional`) 고치지 말고 멈춰 보고한다. spec 작성 시점에는 `UserCommandService`의 `FileStoragePort`만 확인됐다.

## 제약
- 패키지 구조는 `.claude/rules/package-structure.md`를 따른다(이번 작업으로 고친 내용 기준).
- 테스트는 `.claude/rules/testing.md`를 따른다. ArchUnit 관련 주의도 그 파일의 ArchUnit 항목에 있다.
- git 흐름과 스테이징은 `.claude/skills/implement/SKILL.md`, `.claude/rules/git-workflow.md`를 따른다. 커밋하지 않는다.
- 임시 파일(위반 코드, 로그)은 `.claude/rules/temp-files.md`를 따른다. 위반 코드는 돌린 직후 지우고, 로그는 세션 스크래치패드에 둔다. 스테이징 전에 워크트리에 남은 것이 없어야 한다.
- 파일 이름 변경은 `git mv`로 해서 diff가 rename으로 보이게 한다.
- 들여쓰기 4칸 공백, 기존 import 순서·스타일을 따른다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- **SHI-156 쪽에 새 기준을 알려야 한다.** `wt-SHI-156`(일정 생성)이 application 루트에 새 `XxxUseCase` 클래스를 만들 수 있다. 이 작업이 먼저 머지되면 그 브랜치의 새 ArchUnit 규칙·이름 규칙 위반이 develop 최신화 때 드러난다. 오케스트레이터가 사용자에게 알린다.
- 현재 위치(베이스 `845f085`)
  - auth 행위별 클래스: `snsg/src/main/java/com/shineunsigan/snsg/auth/application/{SocialLogin,CompleteSignUp,ReissueToken,HandleSocialUnlink,RevokeAuth}UseCase.java`. 테스트는 `snsg/src/test/java/com/shineunsigan/snsg/auth/application/` 아래 같은 이름 + `Test`.
  - 사용처: `auth/presentation/AuthController`(SocialLogin·CompleteSignUp·ReissueToken), `auth/presentation/KakaoWebhookController`(HandleSocialUnlink), `auth/infrastructure/event/UserWithdrawnEventListener`(RevokeAuth, javadoc에 `ReissueTokenUseCase` 언급). 테스트 `AuthControllerTest`, `KakaoWebhookControllerTest`.
  - `auth/presentation/StringToSocialProviderConverter` 주석의 "UseCase에서"는 이 행위별 클래스를 뜻한다. facade로 바꾼다.
  - 업로드: `user/application/UserCommandService.upload`(트랜잭션 없음, `ProfileImage.detect` 후 `fileStoragePort.store`). 인바운드 사용처는 `auth/infrastructure/client/UserAdapter`(인터페이스 `UploadProfileImageUseCase` 주입, 그대로 둔다). user 도메인 컨트롤러에서 부르는 곳은 없다. `UserCommandServiceTest`에 `@Mock FileStoragePort`가 있고 업로드 테스트는 없다. 의존을 없애면 이 mock 필드와 import만 지운다.
  - `UserCommandService`의 javadoc("저장만 하므로 트랜잭션을 걸지 않는다")은 메서드와 함께 facade로 옮긴다.
  - `RevokeAuthUseCase`(현 클래스)는 `RefreshTokenStorePort`, `SocialUnlinkPort`만 쓴다. Redis·외부 API 호출이라 facade 기준에 맞는다.
  - `HandleSocialUnlinkUseCase`는 `RevokeAuth`를 부르지 않는다. facade끼리 호출하는 곳은 지금 없다.
- 이름 변경 순서 제안: `RevokeAuthUseCase` 클래스를 `RevokeAuthFacade`로 `git mv`한 뒤 `port/in/RevokeAuthUseCase` 인터페이스를 새로 만든다. 순서를 거꾸로 하면 같은 이름이 두 패키지에 동시에 생겨 import가 헷갈린다. 테스트 `RevokeAuthUseCaseTest` → `RevokeAuthFacadeTest`.
- 인터페이스 메서드: `void revoke(UUID userId, SocialProvider provider, String providerUserId, boolean unlinkSocial)`(현 시그니처 그대로). `SocialProvider`는 auth 자기 enum이다.
- ArchUnit 구현 메모
  - 기존 7개 메서드: `domainDoesNotDependOnOuterLayers`, `applicationDoesNotDependOnPresentationOrInfrastructure`, `presentationDoesNotDependOnInfrastructure`, `onlyInfrastructureDependsOnOtherDomains`, `otherDomainDependenciesTargetOnlyContracts`, `noCyclesBetweenDomains`, `commonDoesNotDependOnDomains`.
  - R-F는 Spring의 `org.springframework.transaction.annotation.Transactional`과 `jakarta.transaction.Transactional` 둘 다 잡는 편이 안전하다.
  - R-S의 "port.out 패키지"는 도메인 패키지 기준 `application.port.out`이다. 같은 도메인만 보든 모든 도메인을 보든 결과는 같다(다른 도메인의 port.out 참조는 기존 규칙이 이미 막는다).
  - ArchUnit 1.x는 `that()`이 아무 클래스도 고르지 못하면 실패한다(`archRule.failOnEmptyShould`). 끄지 않는다. 이름 변경 후에는 `Facade` 클래스가 6개 있다.
  - 위반 코드 예(경로는 `snsg/src/main/java/com/shineunsigan/snsg/` 아래, 확인 후 삭제)
    - R-F 클래스: `auth/application/ArchProbeFacade.java` — `@Transactional class ArchProbeFacade {}`
    - R-F 메서드: `auth/application/ArchProbeMethodFacade.java` — `class ArchProbeMethodFacade { @Transactional void run() {} }`
    - R-S: `user/application/ArchProbeQueryService.java` — `class ArchProbeQueryService { com.shineunsigan.snsg.user.application.port.out.FileStoragePort ref; }` (이벤트 포트 `UserEventPort` 의존 클래스는 통과해야 하므로, 원하면 대조군으로 함께 확인)
    - 위반 코드는 다른 규칙(계층 방향 등)을 건드리지 않아야 "해당 테스트 하나만 실패"가 성립한다.
- 문서 갱신 대상 후보
  - `.claude/rules/package-structure.md`: 레이아웃 주석(`application/` 줄에 Facade 추가), "Service와 UseCase" 절 제목·표·본문(행위별 클래스 → `XxxFacade`, 예시 이름), Port 절(UseCase 이름 규칙, 68행 facade 문구), 의존 방향 절의 ArchUnit 검사 범위 문장(38행), 이벤트 절 "트랜잭션을 걸지 않는 UseCase(예: auth의 UseCase)"(112행).
  - `.claude/rules/testing.md` 51행 ArchUnit 항목. 규칙 개수를 적고 있지는 않으나, 새 두 규칙이 이름 기반(`Facade`, `CommandService`/`QueryService`, `EventPort` 접미사)이라는 점을 적어 두면 이름을 바꿀 때 규칙이 조용히 빠지는 것을 막는다.
  - `.claude/rules/api.md` 21행("`/internal/v1` Controller는 `port/in`의 UseCase에 의존")은 새 규칙과 맞으므로 그대로 둔다.
  - spec 작성 시 `.claude/skills`, `docs/`, `CLAUDE.md`에서 옛 클래스 이름 언급은 찾지 못했다. CLAUDE.md는 고칠 것이 없다.
- 커밋 분리 제안(implement는 스테이징까지만, 계획만 보고)
  1. `refactor: auth 행위별 클래스 이름을 Facade로 바꾸고 RevokeAuth를 port/in으로 연다` (main + 테스트 이름·참조)
  2. `refactor: 프로필 사진 업로드를 UploadProfileImageFacade로 옮긴다`
  3. `test: Facade 트랜잭션 금지와 서비스의 port/out 의존 금지를 ArchUnit으로 검사한다`
  4. `docs: facade 기준과 UseCase 이름 규칙을 package-structure에 반영한다`
  - 3번은 1·2번 뒤라야 현재 코드가 통과한다.

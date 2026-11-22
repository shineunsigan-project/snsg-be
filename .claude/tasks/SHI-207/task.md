## 배경

- Linear SHI-207 "카카오 어드민 키가 비면 연결 끊기 웹훅이 통과하는 문제 수정". 앞선 코드 리뷰에서 찾은 보안 버그다.
- 작업 유형: **버그 수정**(기존 테스트 유지 + 회귀 테스트 추가).
- 재현 상태·값:
  - 엔드포인트: `POST /webhooks/kakao/unlink?user_id=<임의 카카오 회원번호>` (`KakaoWebhookController`). 인증 없이 열려 있다.
  - 설정: `application.yaml`의 `oauth.kakao.admin-key: ${KAKAO_ADMIN_KEY}`. `.env.example`은 `KAKAO_ADMIN_KEY=`(빈 값)이라, 템플릿을 그대로 복사하거나 운영 task definition에 빈 값으로 넣으면 `KakaoWebhookProperties.adminKey()`가 `""`이 된다.
  - 요청 헤더: `Authorization: KakaoAK ` (접두사 뒤 빈 키).
  - 결과: `HandleSocialUnlinkFacade.verifyKakao`가 `""`와 `""`를 `MessageDigest.isEqual`로 비교해 통과하고, `UserPort.withdrawBySocialAccount(KAKAO, user_id)`가 불려 **누구나 임의 회원을 탈퇴시킨다.**
- 같은 성질의 구멍(코드·rules에서 확인한 사실): 환경변수가 **아예 없으면** `@ConfigurationProperties` 바인딩이 해석하지 못한 `${KAKAO_ADMIN_KEY}`를 글자 그대로 값으로 넣는다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의", SHI-190에서 `ADMIN_JWT_SECRET`으로 관찰). 그러면 `Authorization: KakaoAK ${KAKAO_ADMIN_KEY}`가 통과한다. 이번 수정에 함께 막는다(GOAL 2).

### 막는 방법 결정 (권장안)

- **(나) 요청 때 설정 키가 비어 있으면(blank) 항상 거부** + **자리표시자에 빈 기본값**(`${KAKAO_ADMIN_KEY:}`)을 준다. 환경변수가 없을 때도 값이 `""`이 되어 (나)에 걸린다. (가) 기동 실패는 넣지 않는다.
- 근거:
  - 설정 절의 `ADMIN_CORS_ALLOWED_ORIGINS` 선례가 "빠지면 열리지 않고 닫히게, 컨텍스트는 뜨게"(`${ADMIN_CORS_ALLOWED_ORIGINS:}` + 빈 목록이면 거부)다. 같은 모양을 따른다.
  - 어드민 키는 웹훅 검증과 연동 해제(`KakaoSocialUnlinkAdapter`)에만 쓰인다. 로컬·테스트에서 키 없이도 컨텍스트가 떠야 한다. `.env.example`이 빈 값이라 템플릿을 복사한 팀원의 `@SpringBootTest`가 전부 막히는 (가)는 대가가 크다. 현재 메인·이 워크트리의 `snsg/.env`에는 `KAKAO_ADMIN_KEY`가 비어 있지 않은 값으로 있어 어느 쪽이든 지금 테스트는 뜬다.
  - (가)만으로는 부족하다. 검증이 기동 경로에만 있으면 `KakaoWebhookProperties`를 직접 만드는 경로(테스트 등)에서 다시 열린다. 거부는 비교하는 자리(facade)에 있어야 한다.
  - 대가: 운영에서 키가 빠지면 기동은 되고 웹훅만 모두 거부된다(컨트롤러가 200 + warn 로그). 연결 끊기 통지가 처리되지 않는 것이 남의 계정이 탈퇴되는 것보다 낫다. 운영 키 누락을 기동 시점에 잡고 싶으면 (가)를 따로 더할 수 있다(비목표).

### `KakaoSocialUnlinkAdapter` 판단

- `KakaoWebhookProperties`(prefix `oauth.kakao`, 필드 `adminKey`)와 `OAuthProperties.kakao().adminKey()`(prefix `oauth`)는 **같은 키 `oauth.kakao.admin-key`를 읽는다.**
- adapter는 키를 **보내는** 쪽이라 빈 키면 카카오가 요청을 거부할 뿐 우회가 생기지 않는다. `RevokeAuthFacade.revoke`가 unlink 실패를 잡아 warn 로그만 남긴다. 이번 범위에 넣지 않는다(비목표). 자리표시자 기본값 변경(GOAL 2)은 adapter에도 같은 값(`""`)으로 들어가고, 지금(글자 그대로의 자리표시자)과 마찬가지로 카카오 호출이 실패할 뿐이다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-207` (브랜치 `fix/SHI-207-empty-kakao-admin-key`, 베이스 `96f6cee`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 워크트리 밖 파일은 고치지 않는다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다. `&&`는 PowerShell 5.1에서 동작하지 않는다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 이 작업의 GOAL 테스트(`HandleSocialUnlinkFacadeTest`, `KakaoWebhookControllerTest`)는 Mockito·`@WebMvcTest`라 DB·Redis가 필요 없다. 전체 `./gradlew test`·`build`는 다른 워크트리와 조율이 필요하므로 GOAL에 넣지 않았다.
- 다른 작업 SHI-205가 같은 auth 도메인의 로그인 흐름(`AuthController`의 authorize·callback, `SocialLoginFacade`)을 고치고 있다.

## GOAL

1. 설정 어드민 키가 비어 있거나(`""`) 공백뿐이면(`"   "`), 헤더의 키가 무엇이든(같은 빈 값·공백 포함) `HandleSocialUnlinkFacade.handleKakaoUnlink`가 `BusinessException(AuthErrorCode.INVALID_WEBHOOK_SENDER)`를 던지고 `UserPort.withdrawBySocialAccount`를 부르지 않는다. 회귀 테스트는 `HandleSocialUnlinkFacadeTest`(Mockito 단위)에 더하고, 최소 아래 두 경우를 그 값 그대로 단정한다.
   - 설정 `new KakaoWebhookProperties("")` + 헤더 `"KakaoAK "` → `INVALID_WEBHOOK_SENDER`, `withdrawBySocialAccount` 호출 없음
   - 설정 `new KakaoWebhookProperties("   ")` + 헤더 `"KakaoAK    "`(접두사 뒤 공백 3칸) → `INVALID_WEBHOOK_SENDER`, `withdrawBySocialAccount` 호출 없음
   - 기존 4개 테스트(맞는 키 통과, 다른 키·헤더 없음·접두사 없음 거부)의 단정은 그대로 통과한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207/snsg && ./gradlew cleanTest test --tests '*HandleSocialUnlinkFacadeTest'` → BUILD SUCCESSFUL, 실패 0, 실행 6개 이상
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207 && git diff --staged -U0 -- snsg/src/test/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacadeTest.java | grep -E '^-' | grep -vE '^---'` → 출력 없음(기존 줄을 지우거나 바꾸지 않는다)
2. 환경변수 `KAKAO_ADMIN_KEY`가 없을 때 어드민 키가 글자 그대로의 자리표시자가 아니라 빈 값이 되어 GOAL 1로 거부된다. `application.yaml`의 `oauth.kakao.admin-key`가 빈 기본값을 가진다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207 && grep -nE '^\s+admin-key: \$\{KAKAO_ADMIN_KEY:\}\s*$' snsg/src/main/resources/application.yaml` → 정확히 1줄
3. 웹훅 컨트롤러의 기존 계약(처리 실패도 200)이 그대로다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207/snsg && ./gradlew cleanTest test --tests '*KakaoWebhookControllerTest'` → BUILD SUCCESSFUL, 실패 0
4. 변경은 아래 경로 안에만 있다. SHI-205가 고치는 파일, `KakaoSocialUnlinkAdapter`·`OAuthProperties`, `.env`·`.env.example`, 워크트리 밖 파일은 바뀌지 않는다.
   - 허용 목록: `snsg/src/main/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacade.java`, `snsg/src/main/java/com/shineunsigan/snsg/auth/application/KakaoWebhookProperties.java`, `snsg/src/main/resources/application.yaml`, `snsg/src/test/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacadeTest.java`, `.claude/tasks/SHI-207/task.md`, 그리고 refine-docs 단계가 고치는 `.claude/rules/*.md`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207 && git diff --staged --name-only | grep -vxE 'snsg/src/main/java/com/shineunsigan/snsg/auth/application/(HandleSocialUnlinkFacade|KakaoWebhookProperties)\.java|snsg/src/main/resources/application\.yaml|snsg/src/test/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacadeTest\.java|\.claude/tasks/SHI-207/task\.md|\.claude/rules/[a-z-]+\.md'` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-207 && git status --short` → 모든 경로가 워크트리 안, 미스테이징·미추적 파일 없음

## 비목표

- (가) 기동 시 빈 키면 실패(fail-fast)는 넣지 않는다. 사용자가 따로 원하면 별도 작업으로 한다.
- `KakaoSocialUnlinkAdapter`·`OAuthProperties`는 고치지 않는다(배경의 판단). 두 설정 record를 하나로 합치지 않는다.
- SHI-205 범위인 `AuthController`, `SocialLoginFacade`와 그 테스트는 건드리지 않는다. auth의 다른 파일도 허용 목록 밖이면 건드리지 않는다.
- `KakaoWebhookController`의 "실패해도 200" 동작과 로그 문구를 바꾸지 않는다. `KakaoWebhookControllerTest`는 고치지 않는다.
- `AuthErrorCode`에 새 코드를 더하지 않는다. 빈 설정 키도 기존 `INVALID_WEBHOOK_SENDER`로 끊는다(요청자에게 서버 설정 상태를 드러내지 않는다).
- `.env`·`.env.example`은 고치지 않는다. 새 환경변수가 없으므로 ECS task definition 안내도 필요 없다.
- 기존 테스트의 단정을 고치지 않는다. 회귀 테스트만 더한다.
- `docs/`(FE 문서)는 고치지 않는다. 웹훅은 `@Hidden`이라 OpenAPI에 없다.
- `HandleSocialUnlinkFacade`의 import 순서 등 눈에 띄는 다른 스타일 문제는 그대로 둔다.

## 제약

- 패키지 구조·설정 규칙은 `.claude/rules/package-structure.md`를 따른다(설정 절: `ADMIN_CORS_ALLOWED_ORIGINS`의 "빠지면 닫히게").
- 테스트는 `.claude/rules/testing.md`를 따른다. facade 단위 테스트는 기존 `HandleSocialUnlinkFacadeTest`의 Mockito 방식 그대로 쓴다.
- 들여쓰기 4칸 공백. 상수 시간 비교(`MessageDigest.isEqual`)는 유지한다.
- 임시 파일은 세션 스크래치패드에 두고 워크트리에 남기지 않는다(`.claude/rules/temp-files.md`).
- 커밋·push·PR은 하지 않는다. 스테이징까지만 한다(`.claude/rules/git-workflow.md` 3번).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 구현 제안(형태는 구현에 맡긴다): `verifyKakao`에서 헤더를 보기 전에 설정 키가 `null`이거나 `isBlank()`면 `INVALID_WEBHOOK_SENDER`를 던진다. `KakaoWebhookProperties`의 javadoc에 "비어 있으면 모든 웹훅을 거부한다"를 적는다(`CorsProperties`의 `adminAllowedOrigins` 설명과 같은 방식). record에 compact 생성자로 기본값을 넣을지는 구현이 정하되, GOAL 2의 yaml 기본값은 있어야 한다.
- `application.yaml`의 `admin-key` 줄에 빈 기본값의 이유(없으면 자리표시자가 글자 그대로 바인딩되어 그 문자열로 통과할 수 있다, 비면 웹훅을 모두 거부한다)를 짧은 주석으로 남기면 다음 사람이 기본값을 지우지 않는다. 같은 파일의 `admin-allowed-origins: ${ADMIN_CORS_ALLOWED_ORIGINS:}`(53행 부근)가 같은 모양이다.
- "수정 전에는 빈 키 요청이 통과했다"는 implement가 GOAL 1의 새 테스트를 수정 전 코드로 먼저 돌려 실패를 확인하고 보고한다(GOAL에 넣지 않음).
- 따라 할 기존 코드
  - 설정(빠지면 닫히게): `CorsProperties`(`snsg/src/main/java/com/shineunsigan/snsg/common/config/CorsProperties.java:13`) + `application.yaml`의 `admin-allowed-origins: ${ADMIN_CORS_ALLOWED_ORIGINS:}` — 자리표시자 빈 기본값, 비면 거부, javadoc에 그 뜻을 적는다
  - 오류: `AuthErrorCode.INVALID_WEBHOOK_SENDER`(`snsg/src/main/java/com/shineunsigan/snsg/auth/domain/exception/AuthErrorCode.java:15`, `ErrorType.FORBIDDEN`) — 발신자 확인 실패는 모두 이 코드
  - 흐름: `HandleSocialUnlinkFacade.verifyKakao`(`snsg/src/main/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacade.java:35`) — 기존 거부 분기와 같은 방식으로 이른 반환(throw)
  - 테스트: `HandleSocialUnlinkFacadeTest.rejectsWrongAdminKey`(`snsg/src/test/java/com/shineunsigan/snsg/auth/application/HandleSocialUnlinkFacadeTest.java:44`) — `assertThatThrownBy` + `hasFieldOrPropertyWithValue("errorCode", ...)` + `verify(userPort, never())`. 설정 값이 다른 경우는 기존 `facade()` 헬퍼 대신 `new HandleSocialUnlinkFacade(userPort, new KakaoWebhookProperties(...))`로 만든다
- refine-docs 후보: `.claude/rules/package-structure.md` 설정 절에 "검증용 비밀값(웹훅 키 등)은 빈 기본값 + 비면 거부"를 `ADMIN_CORS_ALLOWED_ORIGINS` 예 옆에 더할지 본다.

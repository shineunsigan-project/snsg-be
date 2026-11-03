## 배경
- Linear SHI-153. develop에 머지된 #41(`b8ee0d9`)이 `.claude/rules/package-structure.md` "코드 작성" 절에 "메서드는 한 가지 일만 한다" 규칙을 더했다. 이 규칙에 맞춰 기존 메서드 세 개를 리팩터링한다.
  1. `snsg/src/main/java/com/shineunsigan/snsg/user/application/UserCommandService.java`의 `withdraw(UUID, WithdrawReason)`: 사용자 조회, 사진 경로 보관, 애그리거트 삭제(사용자·소셜 계정·약관 동의), 이벤트 발행이 한 메서드에 섞여 있다.
  2. 같은 파일의 `register(RegisterUserCommand)`: 닉네임 중복 검증, 사용자 저장, 소셜 계정 저장, 약관 동의 저장이 한 메서드에 섞여 있다.
  3. `snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/client/KakaoSocialLoginAdapter.java`의 `exchangeToken(String)`: 요청 폼 조립, 토큰 API 호출, 응답 검증·예외 변환이 한 메서드에 섞여 있다.
- 사용자와 합의한 결정
  - 동작은 바꾸지 않는다. 예외 코드, 저장 순서와 내용, 반환값, 이벤트 발행 횟수와 내용, 트랜잭션 경계, 호출 순서가 그대로여야 한다.
  - 기존 테스트가 리팩터링 전후 모두 통과해야 한다.
  - `register`는 처음에는 범위 밖이었다. SHI-146이 PR #43(`3d4c3df`)으로 머지돼 충돌 위험이 사라졌으므로 사용자가 범위에 넣기로 승인했다.
  - `FileStoragePort` 의존 문제는 범위 밖이다.

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-153` (브랜치 `refactor/SHI-153-single-responsibility-methods`, 베이스 `87b3c36` = 장소 마스터 PR #44까지 포함한 develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리(`wt-SHI-148`)는 고치지 않는다.
- `snsg/.env`는 복사돼 있다.
- Docker 컨테이너 `snsg-postgres`·`snsg-redis`는 오케스트레이터가 띄운다. 테스트를 돌리기 전에 `docker ps`로 두 컨테이너가 떠 있는지 확인한다. 떠 있지 않으면 멈추고 보고한다.
  - 꺼진 채로 돌리면 DB 연결 테스트가 대거 실패하고 Redis adapter 테스트는 skip된다. 이번 변경과 무관한 실패다.
- **DB·Redis·8080 포트는 모든 워크트리가 공유한다.** `wt-SHI-148`이 함께 있다(베이스 `13d3745`). 이번 작업은 마이그레이션을 더하지 않는다.
  - 베이스의 마이그레이션은 `V1__create_user.sql` ~ `V5__insert_reviewed_places.sql`이다. 공유 DB에 다른 워크트리의 마이그레이션이 적용돼 있어 Flyway 검증 오류(예: "Detected resolved migration not applied to database"), 모르는 버전, `flyway_schema_history` 불일치가 나면 **멈추고 보고한다.** 스키마나 `flyway_schema_history`를 직접 고치지 않는다.
  - 테스트는 한 번에 한 워크트리에서만 돌린다. 조율은 오케스트레이터가 한다.
- 대상 메서드를 단정하는 기존 테스트. 둘 다 DB·Redis 없이 돈다.
  - `UserCommandServiceTest`(Mockito 단위 테스트): `@Test` 6개와 `@ParameterizedTest @NullAndEmptySource` 1개(실행 2회)로, 실행 기준 8개다.
    - register: 이름·이메일 저장, 이메일 null·빈 문자열이면 빈 문자열로 저장(2회), 중복 닉네임이면 `DUPLICATED_NICKNAME`이고 저장하지 않음
    - withdraw: 애그리거트 전체 삭제, 이벤트에 비식별 처리 전 사진 경로 포함, 없는 사용자면 `USER_NOT_FOUND`
    - withdrawBySocialAccount: 미가입 소셜 계정 통지 무시
  - `KakaoSocialLoginAdapterTest`(`MockRestServiceServer`, `@Test` 5개): 토큰 교환→사용자 조회 흐름, 토큰 API 실패 시 `INVALID_SOCIAL_CODE` 등
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 기준이다. PowerShell에서는 `&&`가 동작하지 않는다.

## GOAL
1. 리팩터링 전(베이스 그대로) 대상 테스트가 통과함을 먼저 확인한다.
   - 검증(코드를 고치기 전에 실행): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.user.application.UserCommandServiceTest' --tests 'com.shineunsigan.snsg.auth.infrastructure.client.KakaoSocialLoginAdapterTest'` → BUILD SUCCESSFUL, 13개 통과(UserCommandServiceTest 8, KakaoSocialLoginAdapterTest 5), 실패 0
2. `UserCommandService.withdraw(UUID, WithdrawReason)`가 `.claude/rules/package-structure.md` "코드 작성" 절의 "메서드는 한 가지 일만 한다" 규칙에 맞게 단계별로 나뉜다. 단계는 같은 클래스 안의 private 메서드로 나누고, 공개 메서드 본문은 단계 메서드 호출만으로 흐름이 읽힌다.
   - 공개 시그니처, `@Override` 없음, `@Transactional`, Javadoc은 그대로다.
   - 동작이 같다.
     - 없는 사용자는 `BusinessException(UserErrorCode.USER_NOT_FOUND)`다.
     - 사진 경로는 `user.withdraw()` 호출 전에 읽은 값이다.
     - 삭제 순서는 사용자 → 소셜 계정 → 약관 동의다.
     - 이벤트는 삭제가 끝난 뒤 소셜 계정마다 한 번씩 같은 필드로 발행한다.
     - 소셜 계정 목록은 지금처럼 한 번만 조회한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.user.application.UserCommandServiceTest'` → BUILD SUCCESSFUL, 8개 통과, 실패 0
3. `UserCommandService.register(RegisterUserCommand)`가 같은 규칙에 맞게 단계별로 나뉜다. 단계는 같은 클래스 안의 private 메서드로 나누고, 공개 메서드 본문은 단계 메서드 호출만으로 흐름이 읽힌다.
   - 공개 시그니처(`UUID register(RegisterUserCommand)`), `@Override`, `@Transactional`, Javadoc(동시 가입 시 저장이 `DUPLICATED_NICKNAME`을 던진다는 문장 포함)은 그대로다.
   - 동작이 같다.
     - `userRepository.existsByNickname(command.nickname())`이 true면 `BusinessException(UserErrorCode.DUPLICATED_NICKNAME)`이고, 아무것도 저장하지 않는다.
     - 저장 순서는 사용자 → 소셜 계정 → 약관 동의다.
     - 사용자는 `User.create(command.name(), command.email(), Profile.of(nickname, gender, birthDate, profileImageUrl), LocalDate.now())`로 만들어 `userRepository.save`로 한 번 저장한다. 이후 단계는 save가 돌려준 사용자를 쓴다.
     - 소셜 계정은 `UserSocialAccount.of(user, command.provider(), command.providerUserId())` 하나를 `save`로 저장한다.
     - 약관 동의는 `PRIVACY_POLICY`(`termsProperties.privacyPolicyVersion()`), `SERVICE_TERMS`(`termsProperties.serviceTermsVersion()`) 두 건을 이 순서의 리스트로 `saveAll` 한 번에 저장한다.
     - 반환값은 저장된 사용자의 id다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.user.application.UserCommandServiceTest'` → BUILD SUCCESSFUL, 8개 통과, 실패 0
4. `KakaoSocialLoginAdapter.exchangeToken(String)`이 같은 규칙에 맞게 단계별로 나뉜다. 단계는 같은 클래스 안의 private 메서드로 나눈다.
   - 동작이 같다. 요청 폼의 키·값, URL, Content-Type이 그대로이고, 호출 실패(`RestClientException`)·응답 없음·access token 없음은 모두 `BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE)`다. 반환값은 응답의 access token이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.auth.infrastructure.client.KakaoSocialLoginAdapterTest'` → BUILD SUCCESSFUL, 5개 통과, 실패 0
5. 전체 빌드가 통과한다(`snsg-postgres`·`snsg-redis`가 떠 있는 상태에서).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && ./gradlew build` → BUILD SUCCESSFUL, 테스트 실패 0
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153/snsg && grep -ho 'skipped="[0-9]*"' build/test-results/test/*.xml | sort | uniq -c` → `skipped="0"`만 나온다(Redis 테스트가 Docker 부재로 건너뛰지 않았음을 확인)
6. 변경은 대상 두 파일과 이 task.md뿐이다. 테스트 파일은 고치지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153 && git diff --staged --name-only` → 다음 세 경로만
     - `.claude/tasks/SHI-153/task.md`
     - `snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/client/KakaoSocialLoginAdapter.java`
     - `snsg/src/main/java/com/shineunsigan/snsg/user/application/UserCommandService.java`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153 && git status --short` → 스테이징되지 않은 변경이나 untracked 파일이 없다
7. 대상 메서드 밖의 코드는 그대로다. 새 클래스·인터페이스·필드·import를 더하지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153 && git diff --staged -U0 -- snsg/src/main/java/com/shineunsigan/snsg/user/application/UserCommandService.java` → 바뀐 줄은 모두 `register(RegisterUserCommand)` 본문, `withdraw(UUID, WithdrawReason)` 본문, 새 private 메서드 안에 있다. `upload`, `withdrawBySocialAccount`, 두 대상 메서드의 Javadoc·애너테이션·시그니처, 필드, import, 클래스 선언에는 변경이 없다
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-153 && git diff --staged -U0 -- snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/client/KakaoSocialLoginAdapter.java` → 바뀐 줄은 모두 `exchangeToken` 본문과 새 private 메서드 안에 있다. `authorizeUrl`, `fetchUser`, `fetchUserInfo`, `encode`, 상수, 생성자, import에는 변경이 없다

## 비목표
- `UserCommandService`의 `FileStoragePort` 의존(Command 서비스가 `port/out`을 의존하는 문제)은 그대로 둔다.
- 다른 메서드는 규칙에 맞지 않아 보여도 고치지 않는다(`upload`, `withdrawBySocialAccount`, `KakaoSocialLoginAdapter.fetchUserInfo` 등). 눈에 띄면 보고만 한다.
- `UserRepositoryImpl.save`의 닉네임 인덱스 위반 변환, `User.create`의 검증 등 대상 메서드가 부르는 쪽도 고치지 않는다.
- 기존 테스트를 고치거나 새 테스트를 더하지 않는다. 동작이 바뀌지 않는 리팩터링이라 기존 테스트가 회귀 검증이다.
- Javadoc·주석·포맷을 다듬지 않는다. 새 private 메서드에 주석이 꼭 필요하다고 판단되면 넣지 않고 보고한다.
- 문서(`.claude/rules`, `docs/` 등)는 이 단계에서 고치지 않는다.
- 베이스에서 GOAL 1이 실패하면 리팩터링을 시작하지 않고 멈춰 보고한다.

## 제약
- 패키지 구조와 코드 작성 규칙은 `.claude/rules/package-structure.md`를 따른다("코드 작성" 절).
- 테스트는 `.claude/rules/testing.md`를 따른다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 워크트리 안에 로그·스크래치 파일을 남기지 않는다.
- 들여쓰기는 4칸 공백이다.
- 커밋·push·PR은 하지 않는다. `git add`로 스테이징까지만 한다(`.claude/skills/implement/SKILL.md`).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 나누는 방식의 제안이다. 이름과 경계는 구현이 정해도 된다.
  - `withdraw`: `findUser(userId)`(조회 + `USER_NOT_FOUND`) → 사진 경로 보관 → `deleteAggregate(user)`(사용자·소셜 계정·약관 동의 삭제, 소셜 계정 목록 반환) → `publishWithdrawnEvents(userId, socialAccounts, profileImageUrl, reason)`.
    - 사진 경로는 `user.withdraw()`가 프로필을 비식별 값으로 바꾸기 전에 읽어야 한다. 테스트 `withdrawPublishesEventWithOriginalImage`가 이것을 단정한다.
    - 이벤트 발행에 소셜 계정의 provider·providerUserId가 필요하므로, 삭제 단계가 조회한 목록을 발행 단계로 넘긴다. 다시 조회하면 리포지토리 호출 횟수가 바뀐다.
  - `register`: `validateNicknameNotTaken(nickname)` → `saveUser(command)` → `saveSocialAccount(user, command)` → `agreeToTerms(user)` → `return user.getId()`.
    - `saveUser`는 `userRepository.save`가 돌려준 인스턴스를 반환하고, 뒤 단계가 그 인스턴스를 쓴다. 테스트 `registerAndCaptureSavedUser`는 save에 넘긴 인자를 캡처해 이름·이메일을 단정한다.
    - `LocalDate.now()`는 `User.create`의 나이 검증(`UNDERAGE`) 기준일이다. 호출 위치를 바꿔도 같은 요청 안에서 한 번만 평가되면 된다.
    - 동시 가입으로 인덱스를 위반하면 `UserRepositoryImpl.save`(saveAndFlush)가 `DUPLICATED_NICKNAME`을 던진다. 저장을 다른 메서드로 옮겨도 이 동작은 그대로다.
  - `exchangeToken`: `tokenRequestForm(authorizationCode)`(폼 조립) → `requestToken(form)`(POST, `RestClientException` → `INVALID_SOCIAL_CODE`) → `extractAccessToken(response)`(null·access token 없음 → `INVALID_SOCIAL_CODE`).
    - 지금 코드는 null 검사의 `BusinessException`을 `try` 안에서 던지지만, `RestClientException`이 아니라 `catch`에 잡히지 않는다. 검사를 `try` 밖으로 옮겨도 결과 예외는 같다.
- 공개 메서드 `withdraw`는 같은 클래스의 `withdrawBySocialAccount`에서도 호출된다. 자기 호출이라 프록시를 거치지 않고 바깥 트랜잭션에 합류한다. 시그니처와 `@Transactional`을 바꾸면 이 경로의 트랜잭션 경계가 달라진다.
- 새 private 메서드에는 `@Transactional`을 붙이지 않는다. private 메서드는 프록시를 거치지 않아 효과가 없고, 공개 메서드의 트랜잭션 안에서 실행된다.
- 소셜 계정이 여러 개일 때 이벤트가 계정마다 발행되는 경우는 기존 테스트가 단정하지 않는다(계정 1개만 쓴다). 구현이 forEach 구조를 유지해 동작을 보존해야 한다.

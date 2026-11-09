## 배경

- Linear SHI-175 "탈퇴 90일 보관과 비식별 배치". 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖)의 T-05(user)다. 근거 항목:
  - POL-U13: 탈퇴 90일 뒤 JOB-03이 이름·이메일·생년월일·닉네임·사진을 비식별 값으로 바꾸고 사진 파일을 지운다. 닉네임 이력의 nickname도 지운다. 재가입은 새 계정이다.
  - POL-U3: 닉네임 중복은 대소문자를 무시한다. 탈퇴하지 않은 회원의 닉네임, 또는 `reserved_until > now`인 이력(변경 전 14일, 탈퇴 90일)이 있으면 쓸 수 없다.
  - POL-A6: user 탈퇴 = 소셜 계정·약관 soft delete, 탈퇴 행, 닉네임 90일 보호. 그 뒤 커밋되면 이벤트로 전체 세션 삭제·카카오 연결 끊기(구현됨).
  - D4: 원본은 90일 보관한 뒤 스케줄러가 비식별한다. 달력 3개월이 아니라 고정 90일이고, 설정값 하나로 두 기간(닉네임 보호, 보관)을 계산한다.
  - JOB-03(매일 04:00): `anonymized = false AND anonymize_due_at <= now`인 회원을 POL-U13대로 처리하고 `anonymized = true`로 바꾼다. 사진 파일은 커밋 뒤에 지운다. 회원마다 트랜잭션을 나눈다. 한 명이 실패해도 나머지는 진행하고, 실패한 회원은 다음 실행에서 다시 처리된다. 스케줄러는 실행 전에 SecurityContext에 `Auditor.SYSTEM_SCHEDULER`를 넣는다(JPA Auditing이 SecurityContext에서 주체를 읽는다).
  - 이벤트 변경: `UserWithdrawnEvent`에서 `profileImageUrl`을 빼고, `ProfileImageCleanupListener`는 더 이상 이 이벤트를 받지 않는다.
  - 테이블 `user_withdrawals`(User 구성요소, FK O): `user_id` FK·유니크, `reason` VARCHAR(30)(기존 `WithdrawReason`), `anonymize_due_at`(탈퇴 시각 + 90일), `anonymized`(기본 false). 인덱스는 `ix_user_withdrawals_due (anonymized, anonymize_due_at)`. 탈퇴 일시는 `created_at`이다.
  - 명세의 완료 조건:
    - 탈퇴 직후 DB에 원본이 남는다
    - 탈퇴 닉네임으로 가입할 수 없다
    - `anonymize_due_at`이 지난 회원만 비식별되고, `anonymized = true`·사진 파일 삭제·이력 nickname 비움이 함께 일어난다
    - 두 번 돌려도 결과가 같다
    - 한 회원의 실패가 다른 회원 처리를 막지 않는다
- 코드에서 확인한 현재 동작 (베이스 75a02d4)
  - `User.withdraw()`는 즉시 비식별한다. 이름·이메일은 `''`, `Profile.anonymized()`(닉네임 "탈퇴한 사용자", 생년월일 1900-01-01, 기본 이미지, 성별 유지)로 바꾼 뒤 `delete()`한다.
  - 탈퇴는 두 경로가 같은 `UserCommandService.withdraw(userId, reason)`로 들어간다.
    - `UserController.withdraw`(`USER_REQUESTED`)
    - 카카오 웹훅: `HandleSocialUnlinkFacade` → auth `UserAdapter` → `WithdrawUserUseCase.withdrawBySocialAccount`(`SOCIAL_UNLINKED`)
  - 탈퇴하면 소셜 계정·약관이 soft delete되고, 소셜 계정마다 `UserWithdrawnEvent(userId, provider, providerUserId, profileImageUrl, reason)`가 발행된다.
    - auth `UserWithdrawnEventListener`는 `profileImageUrl`을 쓰지 않는다. 세션 삭제와, `USER_REQUESTED`일 때 카카오 끊기만 한다.
    - user `ProfileImageCleanupListener`는 이 이벤트로 사진 파일을 지운다.
  - `UserRepository.isNicknameTaken`(native)은 이력의 reason을 가리지 않고 `reserved_until > now`만 본다. 그래서 WITHDRAWN 이력을 남기기만 하면 가입·프로필 수정·중복 확인이 모두 막힌다. 지금은 탈퇴 때 이력을 남기지 않는다.
  - `UserRepository.isProfileImageUsedByOthers`는 탈퇴하지 않은 회원만 본다.
  - 소셜 계정 조회 `findByProviderAndProviderUserId`(웹훅·로그인의 `FindUserUseCase.findIdBySocialAccount`가 쓴다)는 `IsDeletedFalse`로 삭제되지 않은 행만 본다.
  - 다른 회원의 프로필(탈퇴자 포함)을 읽는 곳은 아직 없다. schedule `ScheduleUserAdapter`는 `exists`만 부른다. 그래서 원본을 보관해도 노출되는 화면이 없다. 마스킹 조회는 T-09다.
- 결정 (사용자 승인, 2026-11-10)
  1. **JOB-03은 100명씩 keyset 페이지로 대상이 없어질 때까지 처리한다.** 커서는 `(anonymize_due_at, user_id)`이고, 정렬과 커서 비교는 DB에서 한다. 실패한 회원은 커서가 지나가므로 같은 실행에서 다시 잡지 않고 다음 실행에서 재시도한다. 페이지 크기는 설정값(기본 100)이다.
     - 근거: 한 번에 100명만 처리하면 하루 탈퇴가 100명을 넘을 때 아무 경고 없이 처리가 밀린다. 커서 없이 "남은 대상이 없을 때까지" 반복하면 계속 실패하는 한 명 때문에 무한 루프가 된다.
  2. **비식별 때 그 회원의 소셜 계정 `provider_user_id`를 `''`로 비운다.**
     - 남겨두면 비식별 뒤에도 어느 카카오 계정이었는지 이어진다.
     - 유니크 인덱스(`uix_user_social_accounts_provider`)는 삭제되지 않은 행에만 걸려 있어 `''`가 여럿이어도 충돌하지 않는다.
     - 명세 POL-U13 목록에 한 줄 더하는 것이다(명세는 저장소 밖이라 이 작업에서 고치지 않는다).
  3. **사진 경로 중복 판정에 탈퇴(삭제) 회원도 포함한다.**
     - T-05 뒤에는 탈퇴 회원이 90일 동안 업로드 경로를 가지고 있다. 그 사이 다른 회원이 그 경로를 가입·프로필 수정에 넣으면, JOB-03이 그 회원이 쓰는 파일을 지운다.
     - `.claude/rules/package-structure.md` 이벤트 절("지울 대상이 사용자가 보낸 값이면 저장할 때, 다른 소유자가 이미 참조하고 있지 않은지 본다")에 따라 저장할 때 막는다.
     - 비식별 뒤에는 경로가 기본 이미지로 바뀌므로, 그 뒤로는 그 경로가 막히지 않는다.
  4. **사진 파일 삭제는 같은 도메인 이벤트로 커밋 뒤에 한다**(이벤트 절 "되돌릴 수 없는 외부 정리").
     - 명세는 "스케줄러가 커밋 뒤 `FileStoragePort.delete`를 직접 부른다"고 적었다. 하지만 스케줄러는 서비스(또는 facade)만 부른다는 규칙(스케줄러 절)과 맞지 않아 규칙을 따른다.
     - 이미 비식별된 회원이나, 비식별 전 경로가 우리 업로드 경로(`ProfileImage.isUploadedPath`)가 아닌 회원(기본 이미지·카카오 사진)이면 삭제 이벤트를 내지 않는다.
  5. **`user_withdrawals`는 `BaseAuditEntity`를 상속한다**(created_by·updated_by가 생긴다). 명세 표에는 Audit 여부가 없다.
     - 근거: 탈퇴 행을 누가 만들었는지(본인 요청이면 userId, 웹훅이면 `SYSTEM`)와 누가 비식별했는지(`SYSTEM_SCHEDULER`)는 도메인 컬럼으로 알 수 없다. package-structure Entity 절의 Audit 판단 기준에 맞는다.
     - 비식별된 행의 `updated_by = SYSTEM_SCHEDULER`를 DB 테스트로 확인하기로 했다.
  6. **설정 키 (OS 환경변수와 겹치지 않는 prefix)**
     - 보관 기간: `nickname.protect-after-withdrawal: 90d`. 명세 설정 블록 그대로, 기존 `NicknameProperties`에 더한다. 탈퇴 닉네임 보호 기간과 `anonymize_due_at` 계산이 같은 값을 쓴다(D4, 설정 절 "두 규칙이 같은 기간을 쓰면 설정 하나에서 읽는다").
     - 페이지 크기 `withdrawal.anonymize-page-size: 100`과 실행 시각 `withdrawal.anonymize-cron: "0 0 4 * * *"`은 새 prefix `withdrawal.*`에 둔다.
       - `user.*`는 OS 환경변수 `USER`와 부딪힌다(설정 절).
       - `nickname.*`는 닉네임 정책이라 비식별 배치의 페이지 크기·시각을 두기에 뜻이 맞지 않는다.
       - `WITHDRAWAL`은 흔한 OS 환경변수 이름이 아니다.
     - cron 키는 `@Scheduled` 자리표시자로 읽으므로 record에 넣지 않고, 그 이유를 yaml 주석으로 남긴다(설정 절 예외).
     - 시간대는 JVM 기본값(Asia/Seoul, `SnsgApplication.main`)을 따른다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-175` (브랜치 `feat/SHI-175-withdrawal-retention`, 베이스 `75a02d4` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리 파일은 고치지 않는다.
- `snsg/.env`는 이미 복사돼 있다. 새 키는 `application.yaml`에 기본값으로 두므로 `.env`를 고치지 않는다.
- develop의 마이그레이션은 V1~V8이다. 새 마이그레이션은 `V9__create_user_withdrawal.sql`이다. 다른 브랜치가 먼저 V9를 머지하면 머지 직전에 번호를 올린다(package-structure 마이그레이션 절).
- 셸은 PowerShell(5.1)이 기본이고 Bash도 쓸 수 있다. PowerShell에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").
  - DB가 꺼져 있으면 테스트가 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다.
  - 테스트와 앱 실행은 다른 워크트리와 동시에 하지 않는다.
  - **마이그레이션을 더하는 작업이다.** 다른 워크트리가 머지 전 마이그레이션을 공유 DB에 적용해 두었으면 Flyway 검증이 어긋날 수 있다. 그때는 멈춰서 보고한다.
- 테스트의 Hikari 풀은 3이다(`snsg/build.gradle`, `.claude/rules/testing.md` DB 연결 수 항목). DB 슬라이스 구성을 더하면 `./gradlew cleanTest build`로 전체를 다시 돌린다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.
- 병렬로 진행 중인 작업: T-09(다른 도메인용 프로필 조회 port/in, user). `FindUserProfilesUseCase`, 삭제 행까지 읽는 조회, 마스킹을 만든다. user 도메인 파일(`UserRepository`·`UserJpaRepository`·`UserRepositoryImpl`·`UserQueryService` 등)이 겹칠 수 있다. 앞 작업이 먼저 머지되면 최신 origin/develop 위로 rebase하고 양쪽 메서드를 모두 살려 충돌을 푼 뒤 GOAL 11을 다시 돌린다.

## GOAL

공통 규칙:
- "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`).
- 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다.
- 아래 검증 명령의 `WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-175`다.

1. **탈퇴 직후 원본이 남는다. (의도된 동작 변경)**
   - 탈퇴(두 경로 모두)는 회원을 삭제 표시(`is_deleted = true`)만 한다. 이름·이메일·닉네임·생년월일·사진 경로·성별은 탈퇴 전 값 그대로 둔다.
   - 소셜 계정·약관 동의는 지금처럼 soft delete한다. 같은 소셜 계정으로 재가입하면 새 계정이 되는 동작은 유지한다.
   - 이미 탈퇴한 회원을 다시 탈퇴시키면 지금처럼 `ALREADY_WITHDRAWN`이다.
   - 기존 테스트 기대가 바뀐다. "테스트를 고쳐 통과시킨 것"이 아니라 이 GOAL이 요구하는 동작 변경이다.
     - `UserTest.withdrawClearsNameAndEmail`: 탈퇴 즉시 이름·이메일 `''`를 단정한다. → 탈퇴 뒤 원본 유지를 단정하도록 바꾼다(이름을 바꿔도 된다).
     - `UserTest.withdraw`: 즉시 비식별을 단정한다면 같은 방식으로 바꾼다.
   - 검증: `UserTest`가 탈퇴 뒤 `isDeleted() == true`, 그리고 이름·이메일·닉네임·생년월일·사진 경로가 탈퇴 전 값과 같음을 단정한다. 두 번째 탈퇴의 `ALREADY_WITHDRAWN` 단정은 그대로 남는다.

2. **탈퇴할 때 탈퇴 행과 WITHDRAWN 닉네임 이력을 남긴다.**
   - 같은 트랜잭션에서 두 행을 저장하고, 두 행은 같은 기준 시각 `now`를 쓴다.
     - `user_withdrawals`: `reason` = 탈퇴 사유, `anonymize_due_at` = `now + nickname.protect-after-withdrawal`, `anonymized = false`
     - `user_nickname_histories`: `reason = WITHDRAWN`, `nickname` = 탈퇴 시점 닉네임, `reserved_until` = `now + nickname.protect-after-withdrawal`
   - 검증: 탈퇴 서비스 테스트(Mockito)가 두 경로(`USER_REQUESTED`, `withdrawBySocialAccount` → `SOCIAL_UNLINKED`) 각각에 대해, 저장된 탈퇴 행의 사유·`anonymized = false`와 WITHDRAWN 이력의 닉네임을 단정한다. 또 두 행의 `anonymize_due_at`과 `reserved_until`이 같고, 둘 다 설정값 기간만큼 뒤임을 단정한다.
   - 검증: `grep -n "protect-after-withdrawal" WT/snsg/src/main/resources/application.yaml` → `protect-after-withdrawal: 90d` 한 줄(`nickname:` 블록 아래)
   - 검증: `ls WT/snsg/src/main/resources/db/migration/` → `V9__create_user_withdrawal.sql`이 있다. 다음 셋이 들어 있다:
     - `user_withdrawals` 생성(`user_id` FK → users, 감사 컬럼 포함 NOT NULL)
     - 삭제되지 않은 행에 대한 `user_id` 부분 유니크 인덱스
     - `ix_user_withdrawals_due (anonymized, anonymize_due_at)`
   - 검증: `user_withdrawals` 엔티티가 `BaseAuditEntity`를 상속한다(`grep -rn "extends BaseAuditEntity" WT/snsg/src/main/java/com/shineunsigan/snsg/user/domain/model/` 결과에 그 엔티티 파일이 있다)

3. **탈퇴한 회원의 닉네임은 보호 기간 동안 다른 사람이 쓸 수 없다.**
   - 해당 경로: 가입, 프로필 수정, 닉네임 사용 가능 여부 조회. 응답은 `DUPLICATED_NICKNAME` 또는 `DUPLICATED`다.
   - 대소문자를 무시한다.
   - 보호 기간이 지나면(`reserved_until <= now`) 다시 쓸 수 있다.
   - 검증: 실제 PostgreSQL 테스트가 아래 순서로 단정한다.
     - `UserRepositoryImplTest`에 새 메서드를 더하거나, 탈퇴 서비스의 `@DataJpaTest` 중첩 클래스를 쓴다.
     - 탈퇴 서비스로 회원을 탈퇴시키고 flush한다.
     - `isNicknameTaken(그 닉네임, now)` → true, 대소문자만 바꾼 닉네임 → true
     - `isNicknameTaken(그 닉네임, reserved_until)` → false

4. **`UserWithdrawnEvent`에 사진 경로가 없고, 탈퇴만으로는 사진 파일이 지워지지 않는다. auth 쪽 수신은 그대로다.**
   - 검증: `grep -n "profileImageUrl" WT/snsg/src/main/java/com/shineunsigan/snsg/user/application/event/UserWithdrawnEvent.java` → 출력 없음
   - 검증: `grep -n "UserWithdrawnEvent" WT/snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/event/ProfileImageCleanupListener.java` → 출력 없음
   - 검증: `cd WT && git diff --staged --name-only -- snsg/src/main/java/com/shineunsigan/snsg/auth` → 출력 없음
   - 이 이벤트의 사진 경로를 단정하던 기존 `UserCommandServiceTest.withdrawPublishesEventWithOriginalImage`는 의도된 동작 변경에 따라 고친다. 이벤트의 userId·provider·providerUserId·reason을 단정하고, 사진 경로 단정은 뺀다.

5. **비식별 대상 선정: `anonymized = false AND anonymize_due_at <= 기준 시각`, keyset 페이지.**
   - 탈퇴 행에서 위 조건에 맞는 회원만 고른다. 순서는 `(anonymize_due_at, user_id)`이고, 정렬과 커서 비교는 쿼리 안에서 한다. 페이지 크기는 `withdrawal.anonymize-page-size`(기본 100)다.
   - 검증: 실제 PostgreSQL repository 테스트. 공유 DB이므로 기준 시각을 과거 고정 값(예: `2020-01-01T12:00`)으로 두고, 넣은 행의 user id가 결과에 있는지 없는지와 넣은 행끼리의 순서로만 단정한다.
     - `anonymize_due_at` = 기준 시각 → 포함
     - 기준 시각 + 1분 → 제외
     - `anonymized = true`이고 기준 시각보다 과거 → 제외
     - 커서 다음 페이지: 넣은 행 셋 이상을 크기 2로 두 번 읽는다. 첫 페이지 마지막을 커서로 넘긴 두 번째 페이지에 첫 페이지 행이 다시 나오지 않고, 넣은 행이 빠짐없이 한 번씩 나온다. `anonymize_due_at`이 같은 행 둘 이상을 넣어 `user_id` 순서로 갈리는 경우를 포함한다.
   - 검증: `grep -n "anonymize-page-size\|anonymize-cron" WT/snsg/src/main/resources/application.yaml` → `withdrawal:` 블록 아래 `anonymize-page-size: 100`, `anonymize-cron: "0 0 4 * * *"` 두 줄. cron 줄 근처에 record에 넣지 않는 이유를 적은 주석이 있다.

6. **비식별(회원 한 명)은 한 트랜잭션에서 아래를 모두 한다. 이미 비식별된 회원이면 아무것도 바꾸지 않는다.**
   - 바꾸는 값:
     - users: 이름 `''`, 이메일 `''`, 닉네임·생년월일·사진은 `Profile.anonymized()` 값(성별 유지), `is_deleted = true` 유지
     - 그 회원의 `user_nickname_histories` 모든 행(CHANGED·WITHDRAWN)의 `nickname` = `''`
     - 그 회원의 `user_social_accounts` 모든 행(삭제된 행 포함)의 `provider_user_id` = `''`
     - `user_withdrawals.anonymized = true`
   - 이미 `anonymized = true`인 회원으로 다시 부르면 예외 없이 끝난다. 어떤 행도 바꾸지 않고(`updated_at` 그대로) 사진 삭제 이벤트도 내지 않는다.
   - 비식별은 탈퇴(삭제)된 회원만 대상으로 한다. 탈퇴하지 않은 회원으로 부르면 바꾸지 않는다. 예외로 끊을지 무시할지는 구현이 정하되, 테스트로 보인다.
   - 검증: 도메인 테스트(`UserTest` 등)가 비식별 뒤 이름·이메일 `''`, 닉네임 "탈퇴한 사용자", 생년월일 1900-01-01, 기본 이미지, 성별 유지를 단정한다.
   - 검증: 실제 PostgreSQL 테스트가 아래를 단정한다. 탈퇴 서비스 테스트의 `@DataJpaTest` 중첩 클래스이고, 커밋을 거치지 않아도 된다. 탈퇴시킨 회원의 탈퇴 행을 기준 시각 이전 due로 둔다.
     - 비식별을 부르고 flush한 뒤 native SELECT로 users(name·email·nickname·birth_date·profile_image_url·gender), 그 회원의 이력 nickname 전부, 소셜 계정 provider_user_id 전부, 탈퇴 행 `anonymized`를 읽어 위 값과 맞는지 본다.
     - 같은 회원으로 한 번 더 부른 뒤, users·탈퇴 행의 `updated_at`이 첫 호출 뒤 값과 같다(멱등).
   - 검증: 비식별 뒤에도 유니크 인덱스와 충돌하지 않는다. 같은 테스트 클래스에서 두 회원을 비식별해 flush가 성공한다(둘 다 닉네임 "탈퇴한 사용자", provider_user_id `''`).
   - 검증(결정 2): `UserSocialAccountRepositoryImplTest`가 비식별된 소셜 계정 행(`is_deleted = true`, `provider_user_id = ''`)이 `findByProviderAndProviderUserId(KAKAO, "")`로 찾히지 않음을 단정한다. 이 조회는 웹훅 탈퇴와 로그인(`FindUserUseCase.findIdBySocialAccount`)이 쓴다.

7. **사진 파일은 비식별이 커밋된 뒤에만, 우리 업로드 경로일 때만 지운다.**
   - 비식별 서비스는 비식별 전 경로가 `ProfileImage.isUploadedPath`를 만족할 때만, 트랜잭션 안에서 이벤트 발행 포트로 삭제 이벤트를 낸다. 같은 도메인 `infrastructure/event` 리스너가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받아 `FileStoragePort.delete`를 부른다.
   - 기본 이미지·카카오 사진 경로이거나 이미 비식별된 회원이면 이벤트를 내지 않는다.
   - 검증: 비식별 서비스 Mockito 테스트가 세 경우를 단정한다.
     - 업로드 경로 회원 → 비식별 전 경로를 담은 이벤트가 한 번 발행된다
     - 기본 이미지 회원·카카오 사진 회원 → 이벤트 발행 `never()`
     - 이미 비식별된 회원 → 이벤트 발행 `never()`
   - 검증: `grep -n "AFTER_COMMIT" WT/snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/event/ProfileImageCleanupListener.java` → 비식별 삭제 이벤트를 받는 메서드가 있다. 다른 리스너 클래스로 둬도 된다. 그 경우 그 클래스를 grep한다.
   - 검증: `grep -rn "FileStoragePort" WT/snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/scheduler/` → 출력 없음. 스케줄러는 파일 저장소를 직접 부르지 않는다.

8. **사진 경로 중복 판정에 탈퇴 회원도 포함한다. (결정 3, 의도된 동작 변경)**
   - 가입·프로필 수정에서 다른 회원의 업로드 경로를 넣으면 `INVALID_PROFILE_IMAGE_URL`이다. 그 회원이 탈퇴(삭제)했어도 비식별 전이면 그렇다.
   - 본인 경로 예외(프로필 수정에서 본인 현재 사진)는 그대로다.
   - 메서드 이름·javadoc이 새 의미와 어긋나면 고친다.
   - 기존 테스트 기대가 바뀐다. "테스트를 고쳐 통과시킨 것"이 아니라 이 GOAL이 요구하는 동작 변경이다.
     - `UserRepositoryImplTest.profileImageOfDeletedUserIsNotUsed`: "탈퇴(삭제)한 회원의 경로는 사용 중이 아니다"를 단정한다. → 사용 중으로 단정하도록 바꾼다(이름·DisplayName도 바꾼다).
   - 검증: 실제 PostgreSQL 테스트(`UserRepositoryImplTest`)가 두 경우를 단정한다. 탈퇴(삭제)한 회원의 업로드 경로에 대해 판정이 `userId` 지정(프로필 수정)과 `null`(가입) 둘 다 true다.
   - 검증: 서비스 테스트가 두 경로를 모두 본다. 판정이 true일 때 가입(`UserCommandServiceTest`)과 프로필 수정(`UserProfileCommandServiceTest`) 각각이 `INVALID_PROFILE_IMAGE_URL`이다. 기존 테스트가 이미 이 분기를 단정하면 그대로 둬도 된다. 대신 그 테스트가 있는지를 보고에 적는다.

9. **JOB-03 스케줄러: 회원마다 따로 트랜잭션, 실패 격리, SYSTEM_SCHEDULER 주체.**
   - user `infrastructure/scheduler`의 `@Component`가 `@Scheduled(cron = "${withdrawal.anonymize-cron}")`으로 돈다.
   - 대상 페이지를 끝까지 읽고, 회원마다 비식별 서비스 메서드(`@Transactional`)를 따로 부른다. 그래서 회원마다 트랜잭션이 나뉜다.
   - 한 회원의 예외는 잡아서 로그로 남기고 다음 회원으로 넘어간다.
   - 실행 동안 SecurityContext의 인증 주체를 `Auditor.SYSTEM_SCHEDULER`로 두고, 끝나면(예외가 나도) `finally`에서 비운다.
   - 클래스 단순명은 다른 도메인의 `@Component`와 겹치지 않는다.
   - 검증: 스케줄러 Mockito 테스트가 아래를 단정한다.
     - 회원 셋 중 가운데 회원의 비식별이 예외를 던져도, 나머지 둘의 비식별이 호출된다
     - 페이지가 둘 이상일 때 두 번째 페이지를 첫 페이지 마지막 행의 커서로 읽고, 빈 페이지에서 멈춘다
     - 비식별을 부르는 동안 `SecurityContextHolder`의 principal 문자열이 `SYSTEM_SCHEDULER`다(호출 시점에 캡처)
     - 실행이 끝나면, 예외가 난 경우를 포함해 `SecurityContextHolder.getContext().getAuthentication()`이 null이다
   - 검증: `grep -rn "@Scheduled" WT/snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/scheduler/` → `withdrawal.anonymize-cron`을 읽는 한 줄

10. **비식별된 행의 감사 값이 `SYSTEM_SCHEDULER`다.**
    - 검증: 실제 PostgreSQL 테스트(GOAL 6과 같은 `@DataJpaTest` 중첩 클래스, `@Import`에 `JpaAuditingConfig` 포함)가 아래를 단정한다. 테스트가 SecurityContext에 principal `"SYSTEM_SCHEDULER"`(`Auditor.SYSTEM_SCHEDULER`)인 인증을 스케줄러와 같은 방식으로 넣고 비식별을 부른 뒤 flush한다. native SELECT로 그 회원의 `users.updated_by`, `user_withdrawals.updated_by`가 `SYSTEM_SCHEDULER`이고, `created_by`는 탈퇴 전 값 그대로임을 본다. 테스트 끝에서 SecurityContext를 비운다.

11. **전체 빌드가 실패 0으로 끝난다.**
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0(`ArchitectureTest`, `OpenApiSpecTest`, `SnsgApplicationTests` 포함)
    - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다.
    - 끝내지 않는 경우:
      - 실패가 줄었지만 0이 아닐 때
      - 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때
      - 컴파일이 깨져 테스트가 돌지 않았을 때
      - 위 1·4·8에 적지 않은 기존 테스트의 단정을 바꿔서 통과시켰을 때(바꿨다면 이유를 보고한다)

12. **앱을 띄우면 JOB-03 스케줄러가 실제로 돈다**(Boot 4 모듈 분리로 "에러 없이 조용히 동작하지 않는" 경우를 잡는다, CLAUDE.md).
    - 사전 조건: 8080 포트를 다른 워크트리가 쓰고 있지 않다. 쓰고 있으면 이 항목을 건너뛰지 말고 멈춰서 보고한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/snsg && ./gradlew bootRun --args='--withdrawal.anonymize-cron=*/20 * * * * *' > <세션 스크래치패드>/bootrun.log 2>&1`을 백그라운드로 띄운다. 기동 뒤 60초 이상 지나 `grep -ci "user_withdrawals" <세션 스크래치패드>/bootrun.log` → 1 이상이면 된다(local 프로파일이 `org.hibernate.SQL: debug`라 0건 조회도 SQL이 찍힌다. `format_sql`로 SQL이 여러 줄에 걸쳐 찍히므로 테이블 이름으로 찾는다).
      - `--args` 인용이 PowerShell·Bash에서 깨지면 같은 값을 환경변수 `WITHDRAWAL_ANONYMIZECRON`으로 넘겨도 된다.
      - 확인 뒤 앱을 멈추고 로그 파일을 지운다(`.claude/rules/temp-files.md`).
      - 공유 DB의 실제 탈퇴 데이터 중 due가 지난 행이 있으면 비식별이 커밋된다. 로컬 개발 데이터라 허용한다.

13. **문서가 새 동작을 설명한다.**
    - `docs/api-guide.md` 탈퇴 절: "비식별 값으로 바뀐다·사진 파일이 지워진다"를 "삭제 표시되고 원본은 90일 보관한 뒤 비식별한다"로 바꾼다. 닉네임은 90일 동안 다른 사람이 쓸 수 없다.
    - `docs/api-guide.md` 닉네임 절(현재 135줄): "탈퇴에는 아직 이력 기록이 들어가지 않아, 지금은 탈퇴한 회원의 닉네임을 바로 다시 쓸 수 있다" 문장을 지운다.
    - `UserController`의 탈퇴 `@Operation` description을 같은 내용으로 고치고, `docs/openapi.yaml`을 재생성한다(`OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`).
    - 검증: `grep -n "바로 다시 쓸 수 있다" /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/docs/api-guide.md` → 출력 없음
    - 검증: `grep -n "90일" /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/docs/api-guide.md` → 탈퇴 절에 한 줄 이상
    - 검증: `grep -n "비식별 값으로 바뀌고 올렸던 사진 파일은 지워진다" /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/docs/openapi.yaml /c/Users/ks/nss/dev/snsg-be/wt-SHI-175/snsg/src/main/java/com/shineunsigan/snsg/user/presentation/UserController.java` → 출력 없음 (2026-11-10 오케스트레이터 정정: 처음 패턴 "올렸던 사진 파일은 지워진다"는 지금도 맞는 프로필 수정 설명까지 잡아 탈퇴 옛 문장으로 좁혔다)
    - 검증: `cd WT && git diff --staged -- docs/openapi.yaml` → 탈퇴 `description`만 바뀐다(경로·스키마·응답 코드 변경 없음)

14. **패키지 규칙을 지킨다. 서비스 크기 기준을 넘으면 나눈다.**
    - 탈퇴·비식별 로직이 든 Command 서비스가 150줄을 넘거나 의존(repository·이벤트 포트. 설정 record는 세지 않는다)이 4개를 넘으면 책임별로 나눈다. 예: 가입은 `UserCommandService`, 탈퇴·비식별은 `UserWithdrawalCommandService`.
      - 나눈 서비스 이름은 `CommandService`로 끝난다.
      - `WithdrawUserUseCase`는 탈퇴를 맡는 서비스가 구현한다.
      - 서비스끼리 주입하지 않는다.
      - User 애그리거트 구성요소가 다섯(users, 소셜 계정, 약관, 닉네임 이력, 탈퇴)이라, 탈퇴 서비스 하나가 다섯 repository를 쓰는 것은 상한 안이다. 그래도 150줄을 넘으면 나눈다.
    - 검증: `wc -l` 결과, 탈퇴·비식별을 담은 각 `*CommandService.java`가 150줄 이하. 넘으면 그 이유를 보고에 적는다.
    - 검증: `ArchitectureTest` 통과(GOAL 11에 포함)

15. **변경은 워크트리 안이고 auth·schedule·place·common을 건드리지 않는다.**
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-175 && git diff --staged --name-only` → 모든 경로가 `snsg/`, `docs/`, `.claude/tasks/SHI-175/` 아래다. `snsg/src/main/java/com/shineunsigan/snsg/{auth,schedule,place,common}/` 경로가 없다. `.claude/rules`, `.claude/skills`, `CLAUDE.md` 경로도 없다.

## 비목표

- 탈퇴 API를 auth로 옮기는 일, 탈퇴 가능 여부 검사(POL-A5), `WithdrawFacade`·`CheckWithdrawalFacade`는 만들지 않는다(T-18). `UserController.withdraw` 핸들러 위치와 경로는 그대로 둔다. description만 GOAL 13대로 고친다.
- 탈퇴자 마스킹 조회(`FindUserProfilesUseCase`, POL-U12)는 만들지 않는다(T-09, 병렬 진행 중). T-09가 만들 파일을 미리 만들지 않는다.
- auth 도메인 파일(`UserWithdrawnEventListener`, `UserAdapter`, `HandleSocialUnlinkFacade` 등)은 고치지 않는다. 이벤트 필드 제거로 auth가 컴파일되지 않으면, auth를 고치지 말고 멈춰서 보고한다(지금 auth는 그 필드를 쓰지 않는다).
- `isNicknameTaken` 쿼리는 고치지 않는다. 이미 reason을 가리지 않고 보호 기간을 본다.
- `ProfileImageReplacedEvent` 수신과 `LocalFileStorageAdapter.delete`의 형식 가드는 그대로 둔다.
- 여러 서버 동시 실행에 대비한 분산 락(ShedLock, advisory lock)을 넣지 않는다. 두 서버가 같은 회원을 동시에 비식별해도 같은 값을 쓰고, 파일 삭제는 `deleteIfExists`라 결과가 같다.
- 수동 실행 API·운영 도구를 만들지 않는다.
- 이미 탈퇴한(베이스 코드로 즉시 비식별된) 기존 회원의 데이터를 옮기는 마이그레이션은 만들지 않는다. 운영 데이터가 없다(명세). 그 회원들은 탈퇴 행이 없어 JOB-03 대상이 아니다.
- 테스트에서 스케줄링을 켜지 않는다. `src/test/resources/config/application.yaml`의 `scheduling.enabled: false`는 그대로 둔다.
- `common/config/SecurityAuditorAware`·`Auditor`는 고치지 않는다. 스케줄러가 SecurityContext에 넣은 principal을 그대로 읽는다.
- `UserRepositoryImplTest.nicknameOfWithdrawnUserIsReusable`는 엔티티 단위 `withdraw()`만 한 뒤 users 테이블의 현재 닉네임을 보지 않는다는 쿼리 동작을 단정한다. 이력은 서비스가 남기므로 그대로 통과하면 고치지 않는다. 실패하면 이유를 보고한다.
- `.claude/rules/`·`.claude/skills/`·`CLAUDE.md`는 이 단계에서 고치지 않는다(refine-docs 단계).
- 명세·ERD(저장소 밖 `planning/`)는 고치지 않는다.
- `snsg/.env`를 고치지 않는다.

## 제약

- 패키지 구조·의존 방향·Service/Facade 규칙·이벤트·스케줄러·일괄 UPDATE·설정·Redis·마이그레이션 규칙은 `.claude/rules/package-structure.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 먼저 읽는다. 테스트는 원래 코드와 별도 커밋 대상이다(스테이징까지만 한다).
- 커밋·브랜치 절차는 `.claude/rules/git-workflow.md`, 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 로그는 세션 스크래치패드에 두고 워크트리에 만들지 않는다.
- 들여쓰기 4칸, Jackson 3, 시간대 등 `CLAUDE.md`를 따른다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 구현 형태 제안 (바꿔도 된다)

- **엔티티**
  - `UserWithdrawal`(`user_withdrawals`, `BaseAuditEntity`)에는 `@ManyToOne(LAZY)` 또는 `@OneToOne`으로 `user`, `reason`, `anonymizeDueAt`, `anonymized`를 둔다. 정적 팩토리 예: `UserWithdrawal.of(user, reason, withdrawnAt, retention)`. 상태 변경은 `markAnonymized()`.
  - `NicknameHistoryReason`에 `WITHDRAWN`을 더한다.
  - `UserNicknameHistory`에 `withdrawn(user, nickname, withdrawnAt, protectPeriod)` 팩토리와 `clearNickname()`을 더한다. `nickname`의 `updatable = false`는 푼다.
  - `UserSocialAccount`에 `clearProviderUserId()`를 더하고 `providerUserId`의 `updatable = false`를 푼다.
  - `User`
    - `withdraw()`: 이미 삭제됐으면 `ALREADY_WITHDRAWN`, 아니면 `delete()`만 한다. 클래스·메서드 주석("탈퇴와 비식별 처리를 한 번에 한다")을 고친다.
    - `anonymize()`: 이름·이메일 `''`, `profile.anonymized()`.
    - `Profile.anonymized()`는 그대로 쓴다.
- **repository** (조회는 삭제되지 않은 행만 본다는 기존 규칙의 예외이므로, 삭제 행까지 보는 메서드는 이름에 드러낸다)
  - `UserRepository`: 탈퇴한 회원까지 찾는 조회. 예: `findIncludingWithdrawnById`. T-09도 비슷한 메서드를 만들 수 있다. 충돌하면 rebase 때 하나로 합친다.
  - `UserSocialAccountRepository`: 삭제 행 포함 `findAllIncludingDeletedByUserId`.
  - `UserNicknameHistoryRepository`: `findAllByUserId`(삭제 안 된 행).
  - `UserWithdrawalRepository`: `save`, `findByUserId`, `findAnonymizeTargets(LocalDateTime now, AnonymizeCursor cursorOrNull, int size)`.
  - 대상 쿼리는 JPQL로 둔다: `where w.anonymized = false and w.isDeleted = false and w.anonymizeDueAt <= :now [and (w.anonymizeDueAt > :cAt or (w.anonymizeDueAt = :cAt and w.user.id > :cId))] order by w.anonymizeDueAt, w.user.id`, `setMaxResults(size)`. 커서 조건은 커서가 있을 때만 조각을 붙인다(package-structure 목록 쿼리 절). UUID 비교는 DB에서만 한다.
  - 엔티티별로 영속성 컨텍스트 dirty checking으로 바꾸면 JPA Auditing이 돌아 `updated_by`가 채워진다. 일괄 UPDATE(`@Modifying`)로 바꾸면 Auditing을 거치지 않으니, 감사 값을 쿼리에 직접 넣어야 한다(일괄 UPDATE 절). 회원 한 명 단위라 dirty checking이 단순하다.
- **서비스**
  - 탈퇴: `withdraw(userId, reason)`에 탈퇴 행 저장과 WITHDRAWN 이력 저장을 더한다. 기준 시각은 메서드 안 `LocalDateTime.now()` 한 번이다. `UserWithdrawnEvent`는 사진 경로 없이 소셜 계정마다 그대로 발행한다.
  - 비식별: `anonymize(UUID userId)`(`@Transactional`) 순서
    1. 탈퇴 행 조회
    2. 이미 비식별됐으면 return
    3. 회원(삭제 포함) 조회
    4. 비식별 전 사진 경로 기억
    5. `user.anonymize()`, 이력 nickname 비움, 소셜 계정 id 비움, `markAnonymized()`
    6. 경로가 `ProfileImage.isUploadedPath`면 `userEventPort`로 삭제 이벤트 발행(예: `UserAnonymizedEvent(userId, profileImageUrl)`, 같은 도메인 `application/event`)
  - 대상 조회는 스케줄러가 부를 메서드(예: `findAnonymizeTargets(now, cursor)`, 읽기 전용 트랜잭션)로 같은 서비스나 `UserQueryService`에 둔다. 스케줄러 규칙("같은 도메인의 Command 서비스를 부르기만 한다")과 ArchUnit에 맞게 고른다. 기준 시각은 실행 시작 때 한 번 정해 모든 페이지에 같은 값을 쓴다.
  - 페이지 크기는 `WithdrawalProperties(int anonymizePageSize)` record(`@ConfigurationProperties(prefix = "withdrawal")`, user `application` 패키지)로 주입한다. cron은 record에 넣지 않는다.
- **스케줄러**: `user/infrastructure/scheduler/UserAnonymizationScheduler`. 실행 흐름:
  ```
  SecurityContext ← UsernamePasswordAuthenticationToken(Auditor.SYSTEM_SCHEDULER, null, List.of())
  try {
      now 고정
      loop {
          page = 대상 조회(now, cursor)
          빈 페이지면 끝
          각 회원: try anonymize(id) catch (RuntimeException) log.warn
          cursor = 페이지 마지막
      }
  } finally {
      SecurityContextHolder.clearContext()
  }
  ```
  - `UsernamePasswordAuthenticationToken`의 3인자 생성자는 `isAuthenticated() == true`라 `SecurityAuditorAware`가 principal을 그대로 쓴다.
  - 회원마다 서비스 프록시를 거쳐 호출하므로 트랜잭션이 나뉜다. 스케줄러 안에서 self-invocation하지 않는다.
- **리스너**: `ProfileImageCleanupListener`에서 `on(UserWithdrawnEvent)`를 지우고 `on(UserAnonymizedEvent)`를 더한다. 클래스 주석의 "탈퇴한 사용자의 사진"을 "비식별한 사용자의 사진"으로 고친다. `UserEventPort`에 발행 메서드를 더하고 `UserEventAdapter`가 구현한다.
- **`isProfileImageUsedByOthers`**: `UserJpaRepository`의 `...AndIsDeletedFalse` 파생 쿼리를 삭제 조건 없는 것(`existsByProfileProfileImageUrl`, `...AndIdNot`)으로 바꾼다. 비식별된 회원은 기본 이미지라 걸리지 않는다. javadoc의 "탈퇴하지 않은 다른 회원"을 "다른 회원(탈퇴 후 보관 중 포함)"으로 고친다. 이름은 그대로 맞으면 두어도 된다.

### 테스트 작성 팁

- 비식별·감사 값 DB 테스트(GOAL 3·6·10)는 탈퇴 서비스 테스트 클래스에 `@Nested` `@DataJpaTest` 중첩 클래스로 둔다. `@AutoConfigureTestDatabase(replace = NONE)`. `@Import`에는 서비스, user repository 구현들, `JpaAuditingConfig`를 넣고, `UserEventPort`는 `@MockitoBean`으로 둔다. Mockito 흐름은 `Flow` 중첩 클래스(`ScheduleCommandServiceTest` 예).
  - 설정 record(`NicknameProperties`, `WithdrawalProperties`)는 `@DataJpaTest`에 올라오지 않는다. 테스트 `@TestConfiguration`에서 값을 직접 빈으로 준다.
  - 새 DB 슬라이스 구성이 생기므로 컨텍스트·연결이 하나 늘어난다. GOAL 11의 `cleanTest build`로 전체를 확인한다.
- 대상 조회 테스트(GOAL 5)는 기존 repository 테스트 구성에 메서드를 더하거나, 탈퇴 repository 테스트를 새로 만든다. 탈퇴 행·회원은 native INSERT 또는 엔티티 저장으로 넣고, due는 2019~2020년 고정 시각을 쓴다. 다른 워크트리의 실제 데이터는 due가 미래라 걸리지 않는다. 그래도 단정은 넣은 user id로만 한다.
- 비식별 뒤 값은 native SELECT로 읽는다. 감사 값(`updated_by`)은 flush 뒤 native로 읽어야 Auditing 결과가 보인다.
- 스케줄러 Mockito 테스트에서 SecurityContext 단정은, mock 서비스의 `doAnswer`에서 `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`을 캡처해서 한다. `@AfterEach`에서 `SecurityContextHolder.clearContext()`를 부른다.

### 알아둘 것

- 보관 기간 동안 탈퇴 회원의 사진 파일은 `/images/profiles/**`로 계속 서빙된다(`ProfileImageResourceConfig`). URL을 아는 사람은 90일 동안 볼 수 있다. D4가 정한 보관이고, 화면에 보이는 경로는 T-09 마스킹이 막는다.
- 비식별 시점(탈퇴 + 90일)은 WITHDRAWN 이력의 `reserved_until`과 같다. 그래서 이력 nickname을 비워도 닉네임 판정 결과는 달라지지 않는다.
- 탈퇴 뒤 같은 카카오로 재가입하면 새 계정이다. 다만 자기 이전 닉네임도 90일 동안 쓸 수 없다(POL-U3 그대로).
- `UserWithdrawnEvent` 필드가 빠지면서 auth `UserWithdrawnEventListener` 주석·코드는 영향이 없다. 명세 3.3이 적은 auth의 user enum(`WithdrawReason`) 참조도 그대로다.

### refine-docs 후보 (이번 단계에서 고치지 않는다)

- `.claude/rules/package-structure.md`
  - 이벤트 절의 예 "`UserWithdrawnEvent`(탈퇴)·`ProfileImageReplacedEvent`(사진 교체) → `ProfileImageCleanupListener`"를 새 이벤트로 바꾼다.
  - 같은 절의 "지울 대상이 사용자가 보낸 값이면 저장할 때 막는다" 예에 "보관 중인 탈퇴 회원도 소유자로 센다"를 더한다.
  - 스케줄러 절에 엔티티를 바꾸는 스케줄러는 SecurityContext에 `Auditor.SYSTEM_SCHEDULER`를 넣고 `finally`에서 비운다는 것, 회원별 트랜잭션·실패 격리·keyset 페이지 예(`UserAnonymizationScheduler`)를 적는다.
  - 설정 절에 `withdrawal.*` prefix를 고른 이유를 적는다.
  - common 절 판단 기록의 `SchedulingConfig` 설명("user(탈퇴 비식별) 스케줄러도 같은 스위치를 쓴다")을 확인한다.
- `.claude/rules/testing.md`: SecurityContext를 쓰는 스케줄러 테스트 요령, 감사 값 DB 테스트에서 principal을 넣는 방법.
- `User` 클래스 주석의 "탈퇴 여부는 isDeleted" 설명에 보관·비식별 단계를 더할지.

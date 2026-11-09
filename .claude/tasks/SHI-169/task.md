## 배경

- Linear SHI-169. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖·읽기 전용)의 T-03 "내 프로필 조회·수정·사진 변경 (user)"을 구현한다. 화면은 MY-01(내 정보)·MY-02(프로필 수정)다. API 번호는 API-U02·U03·U04다.
- 명세 범위: API-U02·U03·U04, POL-U6(성별 수정 불가)·U7(닉네임 변경 제한)·U9(사진 경로 검사)·U10(원자성·불변 필드)·U11(`AgeGroup` VO), 닉네임 변경 시 `CHANGED` 이력(14일)(이력 엔티티와 정적 팩토리는 여기서 처음 만든다), `ProfileImageReplacedEvent`로 이전 사진 파일을 커밋 후 삭제.
- 명세 완료 조건: 생략 필드 불변 / 14일 내 재변경 `NICKNAME_CHANGE_RESTRICTED` / gender를 보내도 성별 불변 / 현재와 같은 닉네임 재전송은 `CHANGED` 이력을 남기지 않음 / 업로드 경로가 아닌 profileImageUrl은 `INVALID_PROFILE_IMAGE_URL` / 닉네임 OK + 이메일 형식 오류면 닉네임도 저장 안 됨 / 연령대 경계값(18·19 → 10대 후반, 20·23·24·26·27·30세) 계산 / 사진 교체 후 이전 파일 없음.
- 명세 요약(근거)
  - API-U02 `GET /api/v1/users/me`: 인증 필요. 응답 `id, name, nickname, gender, birthDate, email, profileImageUrl`(email은 없으면 `""`), `ageGroup {decade: 20, phase: LATE}`(phase `EARLY` 초반 / `MID` 중반 / `LATE` 후반), `nicknameChangeableAt`(닉네임을 다시 바꿀 수 있는 시각, 지금 바꿀 수 있으면 null).
  - API-U03 `PATCH /api/v1/users/me`: 인증 필요. 필드를 생략하면 바꾸지 않는다. 현재 값과 같은 값도 바꾸지 않은 것으로 본다(변경 제한에 세지 않음). 요청 `nickname`(POL-U2·U3·U7), `email`(POL-U8, `""` = 삭제), `profileImageUrl`(API-U04가 준 경로, 바꾸면 이전 업로드 파일은 커밋 후 지운다. 기본·카카오 이미지는 우리 경로가 아니라 지우지 않는다). name·gender·birthDate는 Request에 없다(보내도 무시). 응답은 API-U02와 같은 형태. 같은 요청을 다시 보내면 두 번째는 "바뀐 것 없음"이라 성공한다. 오류 `INVALID_NICKNAME`, `DUPLICATED_NICKNAME`, `NICKNAME_CHANGE_RESTRICTED`, `INVALID_EMAIL`, `INVALID_PROFILE_IMAGE_URL`.
  - API-U04 `POST /api/v1/users/me/profile-image`: 인증 필요. multipart `image`. 응답 `{profileImageUrl}`. 저장만 하고 프로필에 반영하지 않는다(API-U03에서 반영). 가입용 API-A04와 같은 `UploadProfileImageUseCase`(구현 `UploadProfileImageFacade`, 이미 있음)를 쓴다. 오류 `INVALID_PROFILE_IMAGE`, `PROFILE_IMAGE_STORE_FAILED`(구현됨). 반영되지 않은 파일 정리는 하지 않는다(명세 C6: MVP는 방치).
  - POL-U7: 마지막 변경(`CHANGED` 이력의 `created_at`) 후 14일 이내면 거부. 변경하면 이전 닉네임을 14일 보호하는 이력을 남긴다. 가입 직후 첫 변경은 제한 없음. 명세 DDL 설명: "가장 최근 CHANGED 행의 created_at → 14일 변경 제한".
  - POL-U9: 저장하는 경로는 업로드 API가 준 `/images/profiles/` 경로만. 가입은 auth가 검사 중(`CompleteSignUpFacade`), 수정은 user가 같은 검사를 한다. user의 `INVALID_PROFILE_IMAGE_URL`(INVALID, 400)을 새로 만든다. auth에 같은 이름의 코드가 있지만 도메인마다 각자 둔다.
  - POL-U10: 한 요청의 항목 중 하나라도 실패하면 아무것도 저장하지 않는다(한 트랜잭션). 전용 오류 코드는 만들지 않는다.
  - D13(연령대): 만 나이 기준. 십의 자리 + 일의 자리 0~3 초반, 4~6 중반, 7~9 후반. 기준일은 조회 시점의 오늘(Asia/Seoul). 18·19세는 "10대 후반".
  - 오류 코드 표: `NICKNAME_CHANGE_RESTRICTED` 신규 CONFLICT(409). 메시지 문구는 명세에 없다(결정 12).
  - 이벤트 절: `ProfileImageReplacedEvent` 신규, user → user(같은 도메인). 기존 `ProfileImageCleanupListener`가 받아 커밋 후 이전 파일을 지운다. 발행은 `UserEventPort`.
  - 설정 절: `user.nickname-protect-after-change: 14d`. (`nickname-protect-after-withdrawal`은 T-05 몫.) 이번 작업은 키를 `nickname.protect-after-change`로 바꿔 쓴다(결정 4).
- 직전 작업 T-02(PR #52, SHI-168)가 `user_nickname_histories`(V8)와 `UserRepository.isNicknameTaken`(native 쿼리), `GET /api/v1/users/nickname-availability`를 만들었다. T-02는 이력 JPA 엔티티를 만들지 않았고 "엔티티·정적 팩토리는 이력을 쓰는 T-03에서 만든다"고 합의했다.
- 사용자 위임: "추천안대로 진행, 단 규칙과 문서 최신화를 면밀히 검토". 아래 결정은 명세·코드·규칙에서 근거를 찾아 권장안을 고른 것이다.

### 결정

1. **이력 엔티티를 만든다 (권장안 채택).** `user_nickname_histories`를 매핑하는 엔티티를 User 애그리거트의 구성요소로 만든다. `BaseEntity` 상속(V8에 `created_by`·`updated_by`가 없다), `User`로 `@ManyToOne`(FK 있음, `TermsAgreement`와 같은 형태), 정적 팩토리로만 만든다. 컬럼명은 필드명과 같게 둔다. `ddl-auto: validate`가 V8과 대조한다. 마이그레이션은 더하지 않는다(V8에 필요한 컬럼·인덱스가 모두 있다).
2. **이력 사유 enum은 `CHANGED`만 둔다 (권장안 채택).** `WITHDRAWN`은 쓰는 곳(T-05)에서 더한다. 근거: CLAUDE.md Simplicity First, 쓰기 경로가 없는 값을 미리 두지 않는다. DB 컬럼은 VARCHAR라 영향 없다.
3. **변경 제한 기준은 명세대로 "가장 최근 `CHANGED` 이력의 `created_at` + 설정값"이다 (권장안 채택).** 지금 시각이 그 시각 이후(같거나 지남)면 바꿀 수 있다. `nicknameChangeableAt`은 그 시각이 지금보다 뒤일 때만 그 값, 아니면(이력 없음 포함) null. 삭제(`is_deleted=true`)된 이력은 보지 않는다. 판정 계산은 command·query 두 서비스가 함께 쓰므로 domain 한 곳에 둔다(서비스끼리 주입하지 않는다, package-structure.md).
4. **14일은 설정 하나(`nickname.protect-after-change`)에서 읽는다 (오케스트레이터 결정).** 이전 닉네임 보호(`reserved_until = 변경 시각 + 14일`)와 재변경 제한(POL-U7)이 같은 값을 쓴다. 근거: 명세 설정 절에 user 설정은 이 키 하나뿐이고 두 규칙이 같은 14일이다. Redis 절의 "같은 값이 다른 곳에도 쓰이면 한 설정값에서 가져온다"와 같은 취지. 값은 `@ConfigurationProperties(prefix = "nickname")` record로 묶고, 기본값은 `application.yaml`에 고정값 `14d`로 둔다(기존 `auth`·`schedule` 정책값과 같은 패턴. 환경변수가 아니므로 `.env`는 바꾸지 않는다). T-05가 같은 record에 `nickname.protect-after-withdrawal: 90d`를 더한다.
   - **prefix를 명세의 `user`가 아니라 `nickname`으로 쓰는 이유**: Spring Binder는 대상 이름과 정확히 같은 속성이 있으면 그 값으로 바인딩을 시도한다. 리눅스 운영 서버에는 OS 환경변수 `USER`가 항상 있어 `user` 속성으로 잡히므로, `@ConfigurationProperties(prefix = "user")` record 바인딩이 변환 실패로 기동을 막을 위험이 있다. 문제가 난 뒤 대응하지 않고 처음부터 피한다. 명세 설정 절의 `user.*` 키와 달라지므로 기획 문서(`be-spec-mvp.html`)는 오케스트레이터가 고친다. 이 작업에서는 고치지 않는다(저장소 밖).
5. **"현재와 같은 값"은 정확히 같은 문자열이다 (권장안 채택).** 닉네임이 현재 값과 대소문자까지 같으면 바꾸지 않은 것으로 보고 제한·중복 검사·이력·저장을 하지 않는다(14일 안이어도 성공). **대소문자만 다른 닉네임**(`Mint` → `mint`)은 변경이다. 제한(POL-U7)을 적용하고 `CHANGED` 이력을 남기며, 중복 판정에서 본인의 현재 닉네임은 중복으로 보지 않는다(POL-U3 "본인의 현재 닉네임은 중복으로 보지 않는다", API-U01이 본인 닉네임을 대소문자 무시로 사용 가능으로 안내하는 것과 맞춘다). 근거: 저장값은 대소문자를 구분해 화면에 그대로 보이므로 대소문자 변경도 눈에 보이는 변경이다. 같은 값으로 보면 API-U01이 true를 줬는데 PATCH가 조용히 아무것도 바꾸지 않는다.
6. **이메일·사진도 현재 값과 정확히 같으면 바꾸지 않은 것으로 본다.** 같은 사진 경로면 이벤트도 내지 않는다. 이메일 `null`(생략) = 그대로, `""` = 삭제(빈 문자열로 저장).
7. **`profileImageUrl`은 `/images/profiles/`로 시작해야 한다. `""`도 거부한다 (권장안 채택).** 생략(`null`)은 그대로. `""`·공백·다른 주소는 400 `INVALID_PROFILE_IMAGE_URL`. 근거: POL-U9 "저장하는 경로는 업로드 API가 준 경로만", 명세에 사진 삭제(기본 이미지로 되돌리기) 기능이 없다. 나중에 더해도 FE를 깨지 않는다. 검사는 auth `CompleteSignUpFacade`와 같은 접두사 검사다(auth 코드를 import하지 않고 user에 따로 둔다).
8. **검사 순서: 입력 형식 → 변경 제한 → 중복 (권장안 채택).** 형식(닉네임 `INVALID_NICKNAME`, 이메일 `INVALID_EMAIL`, 사진 경로 `INVALID_PROFILE_IMAGE_URL`)을 먼저 모두 본 뒤, 닉네임이 바뀌는 경우에만 `NICKNAME_CHANGE_RESTRICTED`, 그다음 `DUPLICATED_NICKNAME`. 근거: 바꿀 수 없는 기간에 "중복"을 알리면 다른 닉네임을 시도하게 만든다. 형식은 Request의 Bean Validation이 먼저 잡고(400 `INVALID_REQUEST` + `errors`, 가입과 같다) domain이 같은 규칙을 안전망으로 다시 본다(api.md).
9. **원자성(POL-U10)은 "모든 검사를 마친 뒤에 바꾼다" + 한 트랜잭션으로 지킨다.** 어느 검사가 실패해도 엔티티 값·이력 저장·이벤트 발행이 일어나지 않는다. 트랜잭션은 command 서비스 메서드에 건다.
10. **facade를 만들지 않는다.** 프로필 수정은 repository와 이벤트 발행 포트(`UserEventPort`)만 쓰므로 Command 서비스의 일이다(package-structure.md Service와 Facade 절). 파일 삭제는 `ProfileImageReplacedEvent`를 AFTER_COMMIT으로 받는 기존 `ProfileImageCleanupListener`가 `FileStoragePort.delete`로 한다. 이벤트는 command 서비스의 트랜잭션 안에서 발행한다(트랜잭션 밖 발행은 AFTER_COMMIT 리스너에 도착하지 않는다). 사진 경로가 바뀔 때마다 이전 경로로 발행하고, 기본·카카오 이미지처럼 우리 경로가 아닌 것은 기존 `FileStoragePort.delete`가 무시한다. API-U04는 `UserController`가 기존 `UploadProfileImageFacade`를 그대로 부른다(api.md Controller 절).
11. **프로필 수정은 새 Command 서비스로 책임을 나눈다 (권장안 채택).** `UserCommandService`(가입·탈퇴, 123줄, 의존 4 + 설정 1)에 프로필 수정을 더하면 150줄과 의존 4개 기준을 넘는다(package-structure.md "서비스가 커져 … 책임별로 나눈다", "의존이 4개를 넘으면 다시 본다"). 계정 생애주기(가입·탈퇴)와 프로필 관리는 책임이 다르다. 명세 3.4는 `UserCommandService`에 프로필 수정을 적었지만, 이 문서의 기준이 규칙 파일이므로 나눈다. 내 프로필 조회는 명세대로 `UserQueryService`에 둔다. 클래스 이름은 `## 참고` 제안.
12. **`NICKNAME_CHANGE_RESTRICTED` 메시지는 "닉네임은 14일에 한 번만 바꿀 수 있어요." (권장안 채택).** 명세에 문구가 없다. 기존 user 메시지 말투(해요체)를 따른다. 화면은 `nicknameChangeableAt`으로 날짜를 안내할 수 있다. `INVALID_PROFILE_IMAGE_URL` 메시지는 auth의 문구("프로필 사진은 업로드한 사진만 쓸 수 있어요.")를 그대로 쓴다.
13. **기존 `User.updateProfile(Profile)`을 없애고 수정 가능한 값(닉네임·이메일·사진)만 바꾸는 domain 메서드로 바꾼다 (권장안 채택).** 지금 메서드는 `Profile`을 통째로 바꿔 성별·생년월일까지 바뀐다(POL-U6·U10 위반 경로). main 코드에서 쓰는 곳이 없다. 이 메서드만 검증하던 `UserTest.updateProfile`(성별이 바뀌는 것을 단정)은 새 규칙에 맞게 바꾼다.
14. **`isNicknameTaken`은 native 쿼리 그대로 둔다 (권장안 채택).** Surgical Changes. 동작이 맞고 바꿀 이유가 없다. 단 `UserJpaRepository`의 주석 "닉네임 이력은 엔티티로 매핑하지 않았으므로 native 쿼리로"는 이번 변경으로 사실이 아니게 되므로 사실에 맞게 고친다(예: 두 테이블을 `LOWER` 기준으로 한 번에 보는 쿼리라 native로 둔다).
15. **API-U02·U03에서 사용자를 찾지 못하면(탈퇴 포함) 404 `USER_NOT_FOUND`(기존 코드).** API-U04는 명세대로 저장만 하고 사용자 존재를 확인하지 않는다.
16. 세 API는 인증 필요다. `SecurityConfig`는 `anyRequest().authenticated()`라 고치지 않는다.
17. **우리 업로드 경로 형식을 엄격히 본다 (오케스트레이터 결정, 보안 — 검증 단계에서 발견).** 접두사(`/images/profiles/`)만 보면 `/images/profiles/..`, `/images/profiles/`(파일명 없음) 같은 값이 저장되고 삭제 때 `directory.resolve("..")`를 시도한다. 그래서 `LocalFileStorageAdapter.store`가 만드는 형태와 정확히 일치하는 정규식으로 본다: 접두사 + `UUID.randomUUID().toString()`(소문자 16진수 8-4-4-4-12) + `.` + 허용 확장자(`ProfileImage`의 `png`·`jpg`). 대문자 UUID·다른 확장자(`jpeg`, `PNG`, `gif` 등)·하위 경로·`..`는 형식 밖이다. 이 규칙은 user domain 한 곳에 두고 프로필 수정과 가입이 함께 쓴다.
    - 프로필 수정: 결정 7대로 우리 접두사가 아닌 값은 거부하고, 우리 접두사로 시작하지만 형식이 틀린 값도 400 `INVALID_PROFILE_IMAGE_URL`.
    - 가입(`UserCommandService.register`): 지금 user는 `profileImageUrl`을 그대로 받는다. auth `SignUpCommand.toRegisterUserCommand`가 사용자가 비워 두면 소셜(카카오) 사진 주소를 넣기 때문이다. 우리 접두사가 아닌 값(소셜 주소)과 null·빈 값(기본 이미지)을 받는 기존 동작은 그대로 둔다. **우리 접두사로 시작하는 값만** 형식 검사를 해서, 틀리면 `INVALID_PROFILE_IMAGE_URL`.
18. **우리 업로드 경로는 한 회원만 참조한다 (오케스트레이터 결정, 보안).** 다른 사용자의 사진 경로는 프로필에 노출된다. 그 경로를 자기 사진으로 넣은 뒤 사진을 바꾸거나 탈퇴하면, `ProfileImageReplacedEvent`나 탈퇴 정리가 남의 파일을 지운다. 그래서 우리 업로드 경로를 다른 회원(삭제되지 않은 회원, 본인 제외)이 이미 쓰고 있으면 400 `INVALID_PROFILE_IMAGE_URL`로 거부한다. 프로필 수정과 가입 둘 다 적용한다. 판정은 `UserRepository` 메서드 하나로 한다(예: `isProfileImageUsedByOthers(path, userId)`, 가입은 userId 없음). 우리 접두사가 아닌 값(소셜 주소)은 이 판정을 하지 않는다. 본인의 현재 사진과 같은 값은 결정 6대로 바뀐 것이 아니다.
19. **삭제 쪽도 막는다 (오케스트레이터 결정, 보안).** `LocalFileStorageAdapter.delete`는 경로가 결정 17의 형식이 아니면 아무것도 지우지 않는다(`..`, 빈 파일명 차단). 이 파일은 user infrastructure라 범위 안이다. 형식 규칙은 infrastructure가 user domain의 것을 import해 쓸 수 있다(infrastructure → domain 방향은 허용).
20. auth `CompleteSignUpFacade`의 접두사 검사는 그대로 둔다. user가 다시 검사한다. auth main 코드는 고치지 않는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-169` (브랜치 `feat/SHI-169-my-profile`, 베이스 `d67041d`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 워크트리 밖 파일은 고치지 않는다.
- `snsg/.env`는 이미 복사돼 있다.
- 결정 17~20·GOAL 14를 더한 시점(2026-11-09)에 develop이 `3c84a20`(기기별 세션, #53)으로 올라갔다. GOAL 1~13 구현은 이미 스테이징돼 있고 PASS 판정을 받았다. GOAL 1~13의 `git diff d67041d` 검증 문구는 그대로 둔다. GOAL 14는 `3c84a20` 기준이다.
- develop의 마이그레이션은 V1~V8이다. 이번 작업은 마이그레이션을 더하지 않는다.
- spec 작성 시점(2026-11-09)에 Docker 컨테이너 `snsg-postgres`·`snsg-redis`가 떠 있다. 테스트 전에 다시 확인한다(`docker ps`). 꺼져 있어 띄울 수 없으면 구현을 멈추고 보고한다(DB 실패를 코드로 고치지 않는다).
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 테스트는 한 번에 한 워크트리에서만 돌린다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다. 경로의 `$WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-169`이다.

1. 연령대(D13)를 user domain의 값 객체가 계산한다. 입력은 생년월일과 기준일, 결과는 `decade`(정수, 십의 자리 × 10)와 `phase`(user domain enum `EARLY`·`MID`·`LATE`). 만 나이는 `Period.between`으로 센다.
   - 검증: 그 값 객체의 테스트 클래스(순수 JUnit, Spring·mock 없음)가 다음을 단정한다.
     - 만 18세·19세 → 10, `LATE`
     - 20세 → 20 `EARLY`, 23세 → 20 `EARLY`, 24세 → 20 `MID`, 26세 → 20 `MID`, 27세 → 20 `LATE`, 30세 → 30 `EARLY`
     - 생일 경계: 27세 생일 전날은 20 `MID`(만 26세), 생일 당일은 20 `LATE`(만 27세)
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*AgeGroupTest'` → BUILD SUCCESSFUL, 실패 0 (클래스 이름을 바꿨으면 그 이름으로)

2. 닉네임 이력 엔티티가 생긴다(결정 1·2). `BaseEntity` 상속, `User`로 FK 연관, 정적 팩토리로만 생성(public 생성자·setter 없음), `reason`은 user domain enum이며 값은 `CHANGED` 하나. 변경 이력 팩토리는 이전 닉네임과 `reserved_until = 변경 시각 + 설정 기간`을 담는다.
   - 검증: `grep -rn "user_nickname_histories" $WT/snsg/src/main/java` → `@Table(name = "user_nickname_histories")` 한 줄
   - 검증: `grep -rn "WITHDRAWN" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/domain/constants` → 기존 `WithdrawReason`·`NicknameUnavailableReason` 밖의 파일에서 나오지 않는다
   - 검증: `cd $WT && git diff --name-status d67041d -- snsg/src/main/resources/db/migration` → 출력 없음

3. 이력 repository(domain 인터페이스 ← infrastructure 구현)가 이력을 저장하고, 사용자의 **삭제되지 않은 가장 최근 `CHANGED` 이력의 `created_at`**을 찾는다.
   - 검증: 그 구현체의 `@DataJpaTest`(실제 PostgreSQL, `@AutoConfigureTestDatabase(replace = NONE)`, `JpaAuditingConfig` import) 테스트가 다음을 단정한다.
     - 팩토리로 만든 이력을 저장하면 다시 읽었을 때 `user_id`·`nickname`·`reason=CHANGED`·`reserved_until`이 그대로다(엔티티 매핑이 V8과 맞는다)
     - 같은 사용자에게 이력이 여럿이면 가장 최근 `created_at`을 돌려준다
     - 다른 사용자의 이력과 `is_deleted=true`인 이력은 보지 않는다
     - 이력이 없으면 빈 값
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*NicknameHistoryRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0 (클래스 이름을 바꿨으면 그 이름으로)
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0 (기존 `isNicknameTaken` 단정 유지, 결정 14)

4. 닉네임 변경 가능 시각 계산(결정 3)이 domain 한 곳에 있고, command·query 서비스가 그것을 쓴다. 입력은 마지막 변경 시각(없을 수 있음)·설정 기간·지금, 결과는 "지금 바꿀 수 있는가"와 `nicknameChangeableAt`(바꿀 수 있으면 null).
   - 검증: 그 계산을 가진 domain 클래스의 테스트(순수 JUnit)가 다음을 단정한다.
     - 이력 없음 → 바꿀 수 있음, null
     - 마지막 변경 후 13일 23시간 59분 → 바꿀 수 없음, 변경 시각 + 14일
     - 정확히 14일 → 바꿀 수 있음, null
     - 14일이 지남 → 바꿀 수 있음, null
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.user.domain.*'` → BUILD SUCCESSFUL, 실패 0

5. `nickname.protect-after-change: 14d`가 `application.yaml`에 있고, `@ConfigurationProperties(prefix = "nickname")` record(user `application` 패키지)로 `Duration`으로 읽힌다. 이력의 `reserved_until`과 변경 제한이 이 값 하나를 쓴다. 14나 14일을 뜻하는 리터럴(`Duration.ofDays(14)`, `plusDays(14)` 등)이 main 코드에 없다.
   - 검증: `grep -n -A1 "^nickname:" $WT/snsg/src/main/resources/application.yaml` → `nickname:` 다음 줄에 `protect-after-change: 14d`
   - 검증: `grep -rn 'prefix = "user"\|^user:' $WT/snsg/src/main` → 출력 없음
   - 검증: `grep -rnE "ofDays\(14\)|plusDays\(14\)|plusWeeks\(2\)" $WT/snsg/src/main/java` → 출력 없음
   - 검증: `cd $WT && git diff --name-only d67041d -- snsg/src/main/resources` → `snsg/src/main/resources/application.yaml` 한 줄

6. 프로필 수정 command 서비스(결정 5~11)가 명세 API-U03 규칙대로 동작한다. 입력 Command는 `nickname`·`email`·`profileImageUrl` 세 값(각각 null = 생략)과 userId만 가진다. 성별·이름·생년월일을 담는 필드가 없다. 메서드에 `@Transactional`이 있다. 결과는 API-U02와 같은 내용(결정 3의 `nicknameChangeableAt`, 연령대 포함)이다.
   - 검증: 그 서비스의 테스트 클래스(Mockito, repository·`UserEventPort` mock)가 다음을 단정한다.
     - 세 필드 모두 null → 닉네임·이메일·사진 그대로, 이력 저장·이벤트 발행 없음
     - 현재와 정확히 같은 닉네임(마지막 변경 후 14일 이내여도) → 성공, 이력 저장 없음, `NICKNAME_CHANGE_RESTRICTED` 아님
     - 다른 닉네임, 마지막 변경 후 14일 이내 → `BusinessException(NICKNAME_CHANGE_RESTRICTED)`, 이력 저장 없음, 닉네임 그대로
     - 다른 닉네임, 이력 없음(가입 후 첫 변경), 중복 아님 → 닉네임이 바뀌고, 이전 닉네임·`CHANGED`·`reserved_until`(변경 시각 + 14일)인 이력이 저장되고, 결과의 `nicknameChangeableAt`이 null이 아니다(약 지금 + 14일)
     - 다른 닉네임, `isNicknameTaken` 참 → `DUPLICATED_NICKNAME`, 이력 저장 없음
     - 대소문자만 다른 닉네임(결정 5) → `isNicknameTaken`으로 본인 때문에 막히지 않고 변경되며 이력이 저장된다. 14일 이내면 `NICKNAME_CHANGE_RESTRICTED`
     - 유효한 새 닉네임 + 형식이 틀린 이메일 → `INVALID_EMAIL`, 닉네임 그대로, 이력 저장·이벤트 발행 없음(POL-U10)
     - 이메일 `""` → 빈 문자열로 바뀐다. 이메일 null → 그대로
     - `profileImageUrl`이 `/images/profiles/`로 시작하지 않음(외부 주소, `""`) → `INVALID_PROFILE_IMAGE_URL`, 아무것도 바뀌지 않음
     - 다른 업로드 경로로 바꾸면 사진이 바뀌고 이전 경로를 담은 `ProfileImageReplacedEvent`가 한 번 발행된다. 같은 경로면 발행하지 않는다
     - 사용자가 없으면(탈퇴 포함) `USER_NOT_FOUND`
   - 검증: `grep -rn "NICKNAME_CHANGE_RESTRICTED\|INVALID_PROFILE_IMAGE_URL" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/domain/exception/UserErrorCode.java` → 두 줄. `NICKNAME_CHANGE_RESTRICTED`는 `ErrorType.CONFLICT`, `INVALID_PROFILE_IMAGE_URL`은 `ErrorType.INVALID`, 메시지는 결정 12
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.user.application.*'` → BUILD SUCCESSFUL, 실패 0

7. `User`의 수정 메서드는 닉네임·이메일·사진만 바꾸고 성별·생년월일·이름은 바꾸지 않는다. 기존 `updateProfile(Profile)`은 남지 않는다(결정 13). 닉네임 형식(`INVALID_NICKNAME`)·이메일 형식(`INVALID_EMAIL`)을 domain에서도 검사하며, 어느 하나라도 실패하면 아무 값도 바뀌지 않는다.
   - 검증: `grep -rn "updateProfile(Profile" $WT/snsg/src` → 출력 없음
   - 검증: `UserTest`가 수정 후 성별·생년월일·이름이 그대로임과, 유효한 닉네임 + 틀린 이메일이면 `INVALID_EMAIL`이고 닉네임이 그대로임을 단정한다. 기존 다른 `UserTest`·`ProfileTest` 단정은 바꾸지 않는다
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserTest' --tests '*ProfileTest'` → BUILD SUCCESSFUL, 실패 0

8. 이전 사진 파일은 커밋 후 지워진다. `ProfileImageReplacedEvent`(user `application/event`, record, 이전 경로를 담는다)가 생기고, `UserEventPort`로 발행되며, 기존 `ProfileImageCleanupListener`가 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`로 받아 `FileStoragePort.delete(이전 경로)`를 부른다. 기존 `UserWithdrawnEvent` 수신은 그대로다.
   - 검증: `grep -n "ProfileImageReplacedEvent\|UserWithdrawnEvent\|AFTER_COMMIT" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/event/ProfileImageCleanupListener.java` → 두 이벤트 수신 메서드가 각각 `AFTER_COMMIT`
   - 검증: `grep -n "ProfileImageReplacedEvent" $WT/snsg/src/main/java/com/shineunsigan/snsg/user/application/port/out/UserEventPort.java` → 한 줄

9. 내 프로필 조회(API-U02)를 `UserQueryService`가 한다. 결과에 `id, name, nickname, gender, birthDate, email, profileImageUrl, ageGroup(decade, phase), nicknameChangeableAt`이 있고 연령대 기준일은 조회 시점의 오늘이다.
   - 검증: `UserQueryServiceTest`(Mockito)가 다음을 단정한다. 기존 닉네임 사용 가능 여부 테스트의 단정은 바꾸지 않는다.
     - 변경 이력이 없으면 `nicknameChangeableAt` null
     - 마지막 변경이 14일 이내면 변경 시각 + 14일
     - 마지막 변경이 14일보다 전이면 null
     - 사용자가 없으면 `USER_NOT_FOUND`
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserQueryServiceTest'` → BUILD SUCCESSFUL, 실패 0

10. `UserController`에 세 엔드포인트가 생긴다. 반환은 `ResponseEntity<ApiResponse<…Response>>`, 인증 주체는 `@AuthenticationPrincipal UUID userId`.
    - `GET /api/v1/users/me` → 내 프로필 응답
    - `PATCH /api/v1/users/me` → 요청 본문 DTO(`nickname`, `email`, `profileImageUrl` 세 필드만, 모두 선택). Bean Validation: 닉네임은 `Profile.NICKNAME_PATTERN`과 같은 규칙, 이메일은 `User.EMAIL_PATTERN`과 최대 100자. 제약 `message`는 domain ErrorCode와 같은 문구. 응답은 GET과 같은 형태
    - `POST /api/v1/users/me/profile-image` (multipart, 파트 이름 `image`) → `UploadProfileImageFacade`(이미 있음)를 불러 `{profileImageUrl}` 응답. user presentation에 자기 응답 DTO를 둔다(auth DTO를 import하지 않는다)
    - 검증: `UserControllerTest`(`@WebMvcTest`, auth 미import, `addFilters = false`, 서비스·facade mock)가 다음을 단정한다.
      - PATCH 본문에 `gender`·`name`·`birthDate`를 함께 보내도 200이고, 서비스에 넘어간 Command에는 `nickname`·`email`·`profileImageUrl`만 있다(성별 불변, POL-U6·U10)
      - PATCH 닉네임 형식 위반 → 400 `INVALID_REQUEST`, `errors[].field = nickname`. 이메일 형식 위반 → `errors[].field = email`. 서비스는 호출되지 않는다
      - 서비스가 `NICKNAME_CHANGE_RESTRICTED`를 던지면 409와 그 code
      - POST profile-image가 파일 바이트를 facade에 넘기고 200 `data.profileImageUrl`을 돌려준다
    - 검증: auth `SecurityConfigTest`(기존, `@WebMvcTest(controllers = UserController.class)`)가 토큰 없이 `GET /api/v1/users/me`, `PATCH /api/v1/users/me`, `POST /api/v1/users/me/profile-image`를 부르면 401임을 단정한다. 기존 단정은 유지한다
    - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*UserControllerTest' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
    - 검증: `cd $WT && git diff d67041d -- snsg/src/main/java/com/shineunsigan/snsg/auth` → 출력 없음

11. 문서화: 세 엔드포인트에 `@Operation(summary, description)`과 응답 코드별 `@ApiResponses`가 있고, 요청·응답 DTO 필드에 `@Schema(description, example)`이 있다(api.md 문서 절). description은 다음을 적는다. PATCH: 생략하면 그대로, 현재와 같은 값은 바뀐 것으로 보지 않음, 이메일 `""` = 삭제, 사진은 업로드 API가 준 경로만이고 바꾸면 이전 업로드 파일이 지워짐, 성별·이름·생년월일은 바꿀 수 없고 보내도 무시, 하나라도 실패하면 아무것도 저장하지 않음, 같은 요청을 다시 보내도 됨, 14일 제한과 `nicknameChangeableAt`. GET: `nicknameChangeableAt` null의 뜻, `ageGroup` phase 값의 뜻, email 빈 문자열의 뜻. POST: 저장만 하고 프로필 반영은 PATCH로 한다는 것. 오류 응답: 401, 404 `USER_NOT_FOUND`, PATCH의 400(`INVALID_REQUEST`, `INVALID_NICKNAME`, `INVALID_EMAIL`, `INVALID_PROFILE_IMAGE_URL`)·409(`DUPLICATED_NICKNAME`, `NICKNAME_CHANGE_RESTRICTED`), POST의 400 `INVALID_PROFILE_IMAGE`·500 `PROFILE_IMAGE_STORE_FAILED`. `docs/openapi.yaml`을 다시 만든다.
    - 검증: `cd $WT/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'` 실행 후 `cd $WT/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
    - 검증: `grep -n "/api/v1/users/me/profile-image\|nicknameChangeableAt\|NICKNAME_CHANGE_RESTRICTED" $WT/docs/openapi.yaml` → 셋 모두 한 줄 이상
    - 검증: `cd $WT && git diff d67041d -- docs/openapi.yaml | grep -E '^-\s+/api/'` → 출력 없음(기존 경로가 지워지지 않는다)

12. 전체 빌드와 아키텍처 규칙이 통과한다.
    - 검증: `cd $WT/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0. `ArchitectureTest` 포함(Command·Query 서비스가 `EventPort` 외 `port/out`에 의존하지 않는다, facade에 `@Transactional` 없음)
    - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다
    - 끝내지 않는 경우: DB가 꺼져 DB 테스트가 실패하거나 컨텍스트가 안 떴을 때(환경 문제로 보고), 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컴파일이 깨져 테스트가 돌지 않았을 때, Redis Testcontainers 테스트가 skip됐는데 그 사실을 보고하지 않았을 때

13. 범위 밖 파일을 고치지 않는다.
    - 검증: `cd $WT && git status --short` → 모든 경로가 워크트리 안이고, 바뀐 main 코드는 `snsg/src/main/java/com/shineunsigan/snsg/user/**`(GOAL 14의 `user/infrastructure/storage/LocalFileStorageAdapter.java` 포함)와 `snsg/src/main/resources/application.yaml` 뿐이다. 그 밖에는 `snsg/src/test/**`, `docs/openapi.yaml`, `.claude/tasks/SHI-169/task.md`만

14. 사진 경로 보안(결정 17~20). 우리 업로드 경로 형식 규칙이 user domain 한 곳에 있고, 프로필 수정·가입·파일 삭제가 그것을 쓴다. 다른 회원이 쓰는 우리 업로드 경로는 수정·가입에서 거부된다.
    - 검증: 형식 규칙을 가진 domain 클래스의 테스트(순수 JUnit)가 다음을 단정한다.
      - 형식 안: `/images/profiles/<소문자 UUID>.png`, `/images/profiles/<소문자 UUID>.jpg`
      - 형식 밖: `/images/profiles/..`, `/images/profiles/`(빈 파일명), `/images/profiles/../<UUID>.png`, `/images/profiles/a/<UUID>.png`(하위 경로), 다른 확장자(`.jpeg`, `.gif`, 확장자 없음), 대문자 확장자(`.PNG`), 대문자 UUID, UUID가 아닌 파일명, 뒤에 문자가 붙은 값(`.png?x=1` 등)
    - 검증: `UserRepositoryImplTest`(실제 PostgreSQL)가 새 판정 메서드에 대해 다음을 단정한다.
      - 다른 회원(삭제되지 않음)이 그 경로를 쓰면 참
      - 그 경로를 쓰는 회원이 본인뿐이면 거짓
      - 그 경로를 쓰던 회원이 삭제됐으면 거짓
      - 가입용(본인 userId 없음) 호출에서 누군가 쓰면 참, 아무도 안 쓰면 거짓
    - 검증: 프로필 수정 command 서비스 테스트(Mockito)가 다음을 단정한다. 다른 회원이 쓰는 우리 경로면 `INVALID_PROFILE_IMAGE_URL`이고 아무것도 바뀌지 않으며 이벤트가 없다. 접두사는 맞지만 형식이 틀린 경로(`/images/profiles/..`)면 `INVALID_PROFILE_IMAGE_URL`이다.
    - 검증: `UserCommandServiceTest`(Mockito)가 다음을 단정한다. 가입에서 다른 회원이 쓰는 우리 경로면 `INVALID_PROFILE_IMAGE_URL`이고 `save`가 호출되지 않는다. 접두사는 맞지만 형식이 틀리면 `INVALID_PROFILE_IMAGE_URL`이다. 소셜 주소(`https://...`)와 null은 기존대로 가입되고 사용 여부 판정을 부르지 않는다. 기존 단정은 바꾸지 않는다.
    - 검증: `LocalFileStorageAdapter`의 테스트(임시 디렉터리, Spring 없음)가 다음을 단정한다. 형식 안의 경로는 파일을 지운다. 형식 밖의 경로(`/images/profiles/..`, `/images/profiles/`, 다른 확장자, 접두사 아님)는 디렉터리 안팎의 어떤 파일도 지우지 않고 예외도 없다.
    - 검증: `cd $WT && git diff 3c84a20 -- snsg/src/main/java/com/shineunsigan/snsg/auth` → 출력 없음
    - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.user.*'` → BUILD SUCCESSFUL, 실패 0
    - 검증: `cd $WT/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0(GOAL 12와 같은 정지 조건·끝내지 않는 경우)

## 비목표

- 마이그레이션을 더하거나 V1~V8을 고치지 않는다.
- `WITHDRAWN` 이력, 탈퇴 시 이력 기록, `nickname-protect-after-withdrawal` 설정, `UserWithdrawnEvent`의 `profileImageUrl` 제거와 리스너 수신 해제는 T-05 몫이다. `UserCommandService.withdraw`·`User.withdraw`·`UserWithdrawnEvent`는 건드리지 않는다.
- `AgeGroup`의 범위 비교(min·max)와 `FindUserProfilesUseCase`, 탈퇴자 마스킹은 T-09·T-12 몫이다. 이번에 port/in을 만들지 않는다(타 도메인 호출 없음).
- 탈퇴 API의 auth 이동(`DELETE /api/v1/users/me` 핸들러)은 하지 않는다. `UserController.withdraw`는 그대로 둔다.
- `isNicknameTaken` 쿼리를 JPQL로 바꾸지 않는다. 주석만 사실에 맞게 고친다(결정 14).
- 반영되지 않은 업로드 파일 정리 배치(명세 C6)는 만들지 않는다.
- 5MB 초과 업로드의 `MaxUploadSizeExceededException` 처리, `GlobalExceptionHandler`, `JwtAuthenticationFilter`, `SecurityConfig`는 고치지 않는다. `LocalFileStorageAdapter`는 GOAL 14의 `delete` 형식 방어만 고치고 `store`는 그대로 둔다(주석의 "탈퇴한 사용자의 사진" 같은 설명을 사진 교체까지 포함하도록 고치는 것은 허용).
- auth 도메인 main 코드(`CompleteSignUpFacade`, `AuthErrorCode`, `SignUpRequest` 등)는 고치지 않는다. auth 테스트는 `SecurityConfigTest`만 고친다(새 401 단정과 `UserController` 의존이 늘어 필요한 `@MockitoBean` 추가).
- 동시에 같은 사용자가 PATCH를 두 번 보내 둘 다 제한 검사를 통과하는 경합은 다루지 않는다.
- 기존 테스트의 단정을 바꾸지 않는다. 예외: `UserTest.updateProfile`(결정 13), 그리고 `UserController` 의존이 늘어 `UserControllerTest`·`SecurityConfigTest`에 `@MockitoBean`을 더하는 것.
- `docs/api-guide.md`, `.claude/rules/**`, CLAUDE.md는 고치지 않는다. refine-docs 단계 몫이다(`## 참고`).

## 제약

- 패키지 구조·의존 방향·Service/Facade·이벤트·DTO·엔티티 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API·Request 검증·문서화는 `.claude/rules/api.md`를 따른다.
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 읽는다. 테스트는 원래 코드와 별도 커밋 대상이다.
- 브랜치·커밋은 `.claude/rules/git-workflow.md`를 따른다. 커밋하지 않고 스테이징까지만 한다. `docs/openapi.yaml`은 API 코드와 같은 커밋 대상이다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 워크트리 안에 만들지 않는다.
- Spring Boot 4·Jackson 3 함정은 CLAUDE.md를 본다. 시간 값은 오프셋 없는 `LocalDateTime`이다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 제안(바꿔도 된다. 결과가 GOAL을 만족하면 된다)

- 이름 예
  - 엔티티 `UserNicknameHistory`(user `domain/model`), enum `NicknameHistoryReason { CHANGED }`(user `domain/constants`), 팩토리 `UserNicknameHistory.changed(User user, String previousNickname, LocalDateTime changedAt, Duration protectPeriod)`.
  - repository `UserNicknameHistoryRepository`(domain) ← `UserNicknameHistoryRepositoryImpl` + `UserNicknameHistoryJpaRepository`(infrastructure/persistence). 최근 변경 시각 메서드 예: `Optional<LocalDateTime> findLastChangedAt(UUID userId)`. 엔티티 대신 시각을 돌려주면 Mockito 테스트에서 `createdAt`(감사 필드, mock에서는 null)을 흉내 낼 필요가 없다. GOAL 3의 테스트 클래스 이름은 구현체 이름 + `Test`다.
  - 연령대 `AgeGroup`(user `domain/model`, record 가능) + `AgePhase { EARLY, MID, LATE }`(`domain/constants`), 팩토리 `AgeGroup.of(LocalDate birthDate, LocalDate today)`.
  - 변경 가능 시각 계산: `UserNicknameHistory`의 static 메서드(예: `changeableAt(Optional<LocalDateTime> lastChangedAt, Duration period, LocalDateTime now)` → `Optional<LocalDateTime>`)처럼 domain 한 곳. GOAL 4의 테스트는 그 클래스의 테스트에 둔다.
  - 설정 record `NicknameProperties(Duration protectAfterChange)`(user `application`, `@ConfigurationProperties(prefix = "nickname")`). `SnsgApplication`의 `@ConfigurationPropertiesScan`이 등록한다. 설정 record는 서비스 의존 수에 세지 않는다(package-structure.md).
  - 프로필 수정 서비스 `UserProfileCommandService`(이름이 `CommandService`로 끝나야 ArchUnit 규칙이 적용된다). 의존: `UserRepository`, `UserNicknameHistoryRepository`, `UserEventPort`, `NicknameProperties`. 메서드 `updateMyProfile(UpdateMyProfileCommand)` → `MyProfileResult`.
  - 조회 `UserQueryService.getMyProfile(UUID userId)` → `MyProfileResult`. `UserNicknameHistoryRepository`·`NicknameProperties` 의존이 더해진다.
  - `MyProfileResult.of(User user, LocalDateTime nicknameChangeableAt, LocalDate today)`처럼 결과 조립을 Result 쪽 팩토리 하나에 두면 command·query가 같은 형태를 만든다(DTO 변환 메서드는 결과 쪽에 둔다).
  - presentation: `UpdateMyProfileRequest`(`toCommand(userId)`), `MyProfileResponse`(`ageGroup`은 중첩 record `{decade, phase}`), `ProfileImageResponse`(user 쪽 자체 DTO).
  - `User`의 수정 메서드 예: `changeProfile(String nickname, String email, String profileImageUrl)` — null은 그대로, 모든 형식 검사를 먼저 하고 나서 대입한다. `Profile`은 불변 값이므로 새 `Profile`을 만들어 바꾼다(성별·생년월일은 기존 값을 넘긴다). 사진 경로 검사(`/images/profiles/` 접두사)도 domain(`Profile` 또는 `User`)에 두면 POL-U10 순서를 한 메서드에서 지킬 수 있다. 접두사 상수는 infrastructure `LocalFileStorageAdapter.URL_PREFIX`를 import할 수 없으므로 user domain에 따로 둔다(값 상수 중복 허용).
- 흐름 예(command 서비스): 사용자 조회(`USER_NOT_FOUND`) → 입력 형식 검사 → 닉네임이 정확히 같지 않으면: 제한 검사(`findLastChangedAt` + 계산) → 대소문자 무시로 현재 닉네임과 같지 않을 때만 `isNicknameTaken(새 닉네임, now)` → 이력 저장(이전 닉네임) → 엔티티 변경 → 사진이 바뀌었으면 이전 경로로 이벤트 발행 → 결과. 검사를 모두 끝낸 뒤에 저장·변경·발행한다(결정 9).
- 동시에 두 사용자가 같은 닉네임으로 바꾸면 `uix_users_nickname`이 막는다. 변경 뒤 `userRepository.save(user)`(내부 `saveAndFlush`)를 부르면 인덱스 위반이 커밋 시 500이 아니라 그 자리에서 `DUPLICATED_NICKNAME`(409)으로 바뀐다. 이 경로의 DB 테스트는 GOAL이 아니다.
- 이력의 `created_at`은 JPA Auditing이 저장 시각으로 채운다. `reserved_until`은 서비스의 `now` + 기간이다. 둘은 밀리초 단위로 다를 수 있지만 판정에 영향이 없다.
- `UserController`에 의존(프로필 command 서비스, `UploadProfileImageFacade`)이 늘면 `@WebMvcTest(controllers = UserController.class)`를 쓰는 `UserControllerTest`와 auth `SecurityConfigTest`에 `@MockitoBean`을 더해야 컨텍스트가 뜬다.
- Spring Boot의 Jackson 설정은 알 수 없는 JSON 필드를 무시한다(기존 `KakaoUserResponse` 등이 이에 기댄다). 그래서 PATCH에 `gender`를 보내도 400이 아니라 무시된다. GOAL 10의 테스트가 이것을 확인한다.
- `@WebMvcTest`에서 multipart는 `MockMvcRequestBuilders.multipart(...).file(new MockMultipartFile("image", ...))`로 보낸다.
- api.md: 같은 컨트롤러에 공개(닉네임 사용 가능 여부)와 인증 필요 API가 섞이므로 `@SecurityRequirements`를 클래스에 달지 않는다. 이번 세 API는 인증 필요라 달지 않는다.

refine-docs 단계가 점검할 문서 후보

- `docs/api-guide.md`
  - 가입 절의 "보호 이력은 닉네임 변경·탈퇴가 남기는데 두 기능에 아직 이력 기록이 들어가지 않아, 지금은 탈퇴한 회원의 닉네임을 바로 다시 쓸 수 있다" — 닉네임 변경은 이제 이력을 남긴다. 탈퇴 부분(T-05 전)만 과도기로 남도록 고친다.
  - 내 정보(마이페이지) 절 신설: `GET/PATCH /api/v1/users/me`, `POST /api/v1/users/me/profile-image`의 흐름(사진은 업로드 → PATCH로 반영), 생략·같은 값·이메일 `""`, 14일 제한과 `nicknameChangeableAt`, `ageGroup` phase 표, 오류 표, 재시도 가능 여부, 성별·이름·생년월일 읽기 전용(명세 C11: FE는 성별을 읽기 전용으로 보여준다).
  - "화면별로 쓰는 API" 표: "프로필 수정 (닉네임 칸을 벗어날 때)" 행의 "프로필 수정 API는 아직 없다" 문구 삭제, 내 정보 진입(GET), 프로필 수정 사진 변경(POST profile-image), 프로필 수정 제출(PATCH) 행 추가.
  - 탈퇴 절의 "올렸던 프로필 사진 파일은 지워진다"는 그대로(T-05 전).
- `.claude/rules/package-structure.md`
  - 이벤트 절 또는 Service와 Facade 절: 같은 도메인 이벤트(`ProfileImageReplacedEvent` → `ProfileImageCleanupListener`)로 되돌릴 수 없는 외부 정리(파일 삭제)를 커밋 뒤에 하는 패턴이 두 번째 사례가 됐다. 규칙으로 적을 만한지 본다.
  - "서비스가 커지면 책임별로 나눈다"의 사례로 `UserCommandService`(가입·탈퇴)와 프로필 수정 서비스 분리를 적을지 본다.
- `.claude/rules/testing.md`: `SecurityConfigTest`가 user 컨트롤러의 인증 필요 경로까지 보게 된 점(예시 갱신 여부).
- 메모리 `snsg-next-task.md`(저장소 밖)는 오케스트레이터 몫이다.

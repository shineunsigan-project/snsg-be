## 배경
- Linear SHI-206 "프로필 수정에서 바뀌지 않은 비업로드 사진이 거부되는 문제 수정". 앞선 코드 리뷰에서 찾은 버그다.
- 유형: **버그 수정**. 기존 테스트 단정은 유지하고 회귀 테스트를 더한다.
- 재현 상태·값
  - 데이터: 프로필 사진(`users.profile_image_url`)이 우리 업로드 경로가 아닌 활성 사용자. 실제로 생기는 두 경우다.
    - 소셜 사진 주소(가입 때 카카오에서 받은 값, 예: `http://k.kakaocdn.net/dn/abc/img.jpg`). `Profile.of`가 업로드 접두사로 시작하지 않는 값은 그대로 받는다.
    - 기본 이미지 `/images/default-profile.png`(`Profile.DEFAULT_PROFILE_IMAGE_URL`). 소셜 사진이 없으면 채워진다.
  - 요청: `PATCH /api/v1/users/me`에 `profileImageUrl`로 **지금 값 그대로**(위 소셜 주소 또는 `/images/default-profile.png`)를 보낸다. 닉네임·이메일을 함께 보내든 생략하든 같다.
  - 잘못된 결과: 400 `INVALID_PROFILE_IMAGE_URL`(`UserErrorCode.INVALID_PROFILE_IMAGE_URL`). 닉네임·이메일 변경도 함께 저장되지 않는다.
  - 기대 결과: `docs/api-guide.md` 프로필 수정 절 "현재 값과 정확히 같은 값은 바꾸지 않은 것으로 본다"대로, 사진은 바뀌지 않은 것으로 보고 통과한다. 같이 보낸 다른 값은 정상 반영된다.
- 원인(코드에서 확인, 요구사항 서술과 일치)
  - 요청 DTO(`UpdateMyProfileRequest.profileImageUrl`)에는 Bean Validation이 없다. DTO에서 거부되지 않는다.
  - `UserProfileCommandService.validate`(`snsg/src/main/java/com/shineunsigan/snsg/user/application/UserProfileCommandService.java:93`)가 `User.validateProfileChange(nickname, email, profileImageUrl)`를 **현재 값과 비교하지 않고** 부른다. 이 정적 메서드(`User.java` `validateProfileChange`)는 `profileImageUrl != null && !ProfileImage.isUploadedPath(profileImageUrl)`이면 `INVALID_PROFILE_IMAGE_URL`을 던진다. 여기서 거부된다.
  - 같은 검사를 `User.changeProfile`도 다시 부른다(`changeProfile` 첫 줄). 서비스 쪽 호출만 고치면 여기서 다시 거부된다.
  - `Profile.of`의 `validateUploadedImageUrl`은 업로드 접두사로 시작할 때만 형식을 보므로 원인이 아니다.
  - 반면 다른 회원 사용 검사(`validateProfileImageNotUsed`)와 교체 이벤트(`publishImageReplaced`)는 이미 `isChanged(요청값, 현재값)`로 바뀐 경우에만 돈다. 형식 검사만 "바뀌었는가"를 보지 않아 생긴 불일치다.
  - 기존 테스트 `UserProfileCommandServiceTest.sameImage`는 현재 사진이 업로드 경로(`OLD_IMAGE`)인 경우만 봐서 이 버그를 잡지 못했다.

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-206` (브랜치 `fix/SHI-206-unchanged-profile-image`, 베이스 `96f6ceed728623d14937f1720bc2850273d7c524`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리는 읽기만 한다
- 다른 워크트리에서 SHI-204·205·207(common·auth)이 동시에 진행 중이다. DB·Redis·8080 포트를 공유하므로 전체 테스트(`./gradlew test`)는 다른 워크트리와 겹치지 않게 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). GOAL 1·2의 대상 테스트는 Mockito·순수 JUnit이라 DB를 쓰지 않는다
- 전체 테스트는 로컬 Docker 컨테이너 `snsg-postgres`·`snsg-redis`가 떠 있어야 한다. 꺼져 있으면 DB·`@SpringBootTest` 계열이 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 `docker start snsg-postgres snsg-redis` 뒤 다시 돌리거나 멈춰서 보고한다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 기준이다. `&&`는 PowerShell 5.1에서 동작하지 않는다
- 베이스에서 알려진 실패 없음(확인하지 않음, 테스트는 spec 단계에서 돌리지 않았다)

## GOAL
1. 지금 사진이 업로드 경로가 아닌 사용자가 프로필 수정에서 지금 사진 값을 그대로 보내면 거부되지 않는다. application 계층 회귀 테스트(`UserProfileCommandServiceTest`, Mockito)로 재현하고 다음을 단정한다. 사용자의 현재 사진은 (a) 소셜 사진 주소(`http://k.kakaocdn.net/...` 형태의 `http(s)` 주소) (b) `/images/default-profile.png` 두 경우를 모두 본다.
   - 닉네임·이메일을 함께 바꿔 보내면(예: 닉네임 `Lemon`, 이메일 `lemon@example.com`, 사진은 현재 값) 예외 없이 닉네임·이메일이 바뀌고 사진은 현재 값 그대로다
   - `ProfileImageReplacedEvent`를 내지 않는다(`publishProfileImageReplaced` 호출 없음)
   - 다른 회원 사용 검사를 하지 않는다(`isProfileImageUsedByOthers` 호출 없음)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*UserProfileCommandServiceTest'` → BUILD SUCCESSFUL, 실패 0. staged diff에 위 (a)(b) 상태·값을 쓰는 새 테스트가 있다
2. 바뀐 값에 대한 사진 검사는 약해지지 않는다. 지금 사진이 소셜 사진 주소인 사용자가 **현재 값과 다른** 비업로드 값을 보내면 여전히 400 `INVALID_PROFILE_IMAGE_URL`이고 아무 값도 바뀌지 않으며 이벤트도 없다. 같은 회귀 테스트 클래스에 다음 값을 단정한다: 다른 외부 주소(`https://evil.test/me.png`), 빈 문자열 `""`, `/images/default-profile.png`(현재가 소셜 주소일 때 기본 이미지로 되돌리기), 형식이 바뀐 업로드 접두사 경로(`/images/profiles/..`).
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*UserProfileCommandServiceTest' --tests '*UserTest' --tests '*ProfileImageTest' --tests '*UserCommandServiceTest'` → BUILD SUCCESSFUL, 실패 0
3. 기존 테스트 단정을 바꾸지 않는다. 특히 `UserProfileCommandServiceTest`의 `invalidProfileImageUrl`·`imageUsedByOthers`·`malformedUploadedPath`·`sameImage`, `UserTest.changeProfileInvalidImageUrl`, `UserCommandServiceTest`(가입의 사진 검사)는 그대로 통과한다.
   - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/wt-SHI-206 diff --staged -U0 -- 'snsg/src/test' | grep -E '^-[^-]'` → 출력 없음(기존 테스트 줄의 삭제·수정 없음, 추가만). import 줄 정리가 필요해 삭제가 나오면 그 줄이 import뿐인지 verify가 확인한다
4. 변경은 user 도메인과 이 task.md에만 있다. common·auth·다른 도메인·마이그레이션·`docs/`는 고치지 않는다.
   - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/wt-SHI-206 diff --staged --name-only | grep -vE '^snsg/src/(main|test)/java/com/shineunsigan/snsg/user/' | grep -vx '.claude/tasks/SHI-206/task.md'` → 출력 없음
5. 전체 빌드·테스트(ArchUnit 포함)가 통과한다.
   - 검증: `cd snsg && ./gradlew cleanTest test` → BUILD SUCCESSFUL, 실패 0 (다른 워크트리와 겹치지 않을 때 돌린다. 환경 절 참고)

## 비목표
- 가입(`UserCommandService.signUp`, auth `CompleteSignUpFacade`)의 사진 검사는 건드리지 않는다. 가입에는 "현재 값"이 없다
- `ProfileImage.isUploadedPath`·`hasUploadedPathPrefix`의 형식 규칙, `UserRepository.isProfileImageUsedByOthers`, `LocalFileStorageAdapter.delete`는 바꾸지 않는다
- 사진을 지우는(기본 이미지로 되돌리는) 기능을 만들지 않는다. 현재 값과 다른 `/images/default-profile.png`는 계속 거부한다(GOAL 2)
- 닉네임·이메일의 "바뀌지 않음" 처리는 바꾸지 않는다. 이번 버그는 사진만이다
- `UpdateMyProfileRequest`의 `@Schema` 문구, `UserController`의 오류 설명, `docs/api-guide.md`·`docs/openapi.yaml`은 고치지 않는다. api-guide는 이미 "현재 값과 정확히 같은 값은 바꾸지 않은 것으로 본다"고 적고 있어 이번 수정으로 코드가 문서에 맞춰진다. 문서 보강이 필요한지는 refine-docs 단계가 판단한다
- 기존 테스트의 단정을 고치지 않는다(GOAL 3)
- common·auth 파일은 건드리지 않는다(SHI-204·205·207이 고친다)
- 주변 코드의 정리·리팩터링(예: `isChanged` 헬퍼 이동, 검사 순서 재배치)은 이번 수정에 필요한 만큼만 한다

## 제약
- 패키지 구조·계층 규칙은 `.claude/rules/package-structure.md`를 따른다. 특히 "지울 대상이 사용자가 보낸 값(경로·키)에서 왔으면 저장할 때 막는다"(이벤트 절)를 약하게 만들지 않는다. 바뀐 값은 형식 검사와 다른 회원 사용 검사를 모두 그대로 거친다
- 불변식은 엔티티에 둔다(package-structure.md domain 절). `User.changeProfile`이 "모든 값을 먼저 검사하고 나서 바꾼다"는 원자성을 유지한다
- 테스트는 `.claude/rules/testing.md`를 따른다(application은 JUnit + Mockito, domain은 순수 JUnit)
- 들여쓰기 4칸 공백, 기존 코드 스타일을 따른다
- 임시 파일은 세션 스크래치패드에 두고 워크트리에 만들지 않는다(`.claude/rules/temp-files.md`)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 수정 방향 제안(형태는 구현이 정한다)
  - 권장안: "바뀌었는가"를 사진 형식 검사에도 적용한다. 서비스는 이미 `isChanged(command.profileImageUrl(), 현재값)`을 갖고 있으므로, 바뀌지 않았으면 사진을 `null`(바꾸지 않음)로 취급해 `User.validateProfileChange`와 `User.changeProfile`에 넘긴다. 다른 회원 사용 검사·이벤트가 이미 같은 기준으로 돈다. 변경이 서비스 한 파일에 모인다
  - 대안: `User.changeProfile`이 현재 사진과 같은 값을 바꾸지 않은 것으로 보게 하고, 서비스의 사전 형식 검사도 같은 기준으로 맞춘다. 정적 `validateProfileChange`는 현재 값을 모르므로 시그니처나 호출 방식이 바뀐다. 도메인 테스트(`UserTest`)에도 "현재와 같은 비업로드 값은 통과" 단정을 더하게 된다
  - 어느 쪽이든 서비스(`validate`)와 엔티티(`changeProfile`) 두 곳의 검사가 모두 통과해야 한다. 한 곳만 고치면 다른 곳에서 다시 거부된다
- 따라 할 기존 코드
  - 흐름: `UserProfileCommandService.validateProfileImageNotUsed`(`snsg/src/main/java/com/shineunsigan/snsg/user/application/UserProfileCommandService.java:105`) — `isChanged`로 바뀐 경우에만 검사한다. 형식 검사도 같은 기준을 따른다
  - 흐름: `UserProfileCommandService.publishImageReplaced`(같은 파일 `:122`) — 같은 기준으로 이벤트를 낸다
  - 오류: `UserErrorCode.INVALID_PROFILE_IMAGE_URL`(`snsg/src/main/java/com/shineunsigan/snsg/user/domain/exception/UserErrorCode.java:20`) — 새 오류 코드를 만들지 않는다
  - 테스트: `UserProfileCommandServiceTest.sameImage`·`invalidProfileImageUrl`(`snsg/src/test/java/com/shineunsigan/snsg/user/application/UserProfileCommandServiceTest.java:254-291`) — `givenUser()`·`givenLastChangedAt(null)`·`update(...)`·`assertErrorCode(...)` 헬퍼, 실패 뒤 값 유지·이벤트 없음 단정 방식. 기존 `user` 필드는 사진이 `OLD_IMAGE`(업로드 경로)라 재현에는 현재 사진이 소셜 주소·기본 이미지인 사용자를 따로 만들어야 한다(`User.create(..., Profile.of(..., "<소셜 주소>"), LocalDate.now())`, 기본 이미지는 `Profile.of(..., null)`이 채운다)
  - 테스트(도메인, 대안을 고르면): `UserTest.changeProfileInvalidImageUrl`(`snsg/src/test/java/com/shineunsigan/snsg/user/domain/model/UserTest.java:221-232`) — 현재 사진이 `https://kakao.test/me.png`인 사용자. 이 테스트의 값 목록에는 현재 값이 없어 어느 수정안이든 그대로 통과해야 한다
- implement는 회귀 테스트가 수정 전 코드에서 `INVALID_PROFILE_IMAGE_URL`로 실패했는지 확인해 보고한다(GOAL에는 넣지 않았다. verify가 수정 전 코드를 돌릴 수 없다)

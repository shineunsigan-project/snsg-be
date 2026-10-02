# Git workflow

브랜치는 `develop`(기본 브랜치, 상시 통합)과 `main`(운영 환경) 둘만 둔다. 두 브랜치에 직접 커밋하지 않고, 모든 작업은 아래 순서로 진행한다. 팀원에게는 강제하지 않지만 **AI는 이 순서를 반드시 따른다.**

작업은 항상 작업 브랜치에서 한다. 여러 작업을 동시에 진행할 수 있도록, 작업마다 issue와 브랜치를 따로 만들고 한 브랜치에 여러 작업을 섞지 않는다.

1. 작업 시작 전 GitHub issue를 만든다 (`gh issue create`).
2. develop을 최신화하고 브랜치를 만든다: `<prefix>/<issue번호>-<작업-이름>` (예: `feat/12-matching-create`)
   - prefix: `feat`(기능), `fix`(버그), `refactor`(리팩터링), `test`(테스트), `docs`(문서), `chore`(빌드·설정)
   - 작업 이름은 영문 kebab-case
3. 작업을 커밋하고 브랜치를 push한다.
   - **커밋하기 전에 멈춘다.** 변경을 스테이징까지 해두고(새 파일이 Unversioned로 흩어지지 않고 한 묶음으로 보인다) 커밋은 하지 않은 채 사용자에게 알린다. 사용자가 IDE에서 diff를 확인하고 OK하면 커밋한다. 커밋을 여러 개로 나눌 계획이면 어떻게 나눌지도 함께 알린다.
   - 큰 작업이라도 한 커밋에 몰지 않는다. 커밋 하나에는 하나의 논리적 변경만 담고, 커밋 메시지만 보고 무엇이 바뀌었는지 알 수 있어야 한다.
4. PR을 만든다. base는 `develop`이다 (레포 기본 브랜치라 `gh pr create`가 자동으로 잡는다). 본문은 `.github/pull_request_template.md` 형식(summary / change / background or etc / test)을 따른다.
   - 제목은 `<prefix>: <제목>`. prefix는 작업 브랜치의 prefix와 같다 (예: `docs: 커밋 분리 규칙 추가`). squash merge 시 이 제목이 develop의 커밋 메시지가 된다.
   - summary에 `Closes #<issue번호>`를 넣어 머지 시 issue가 자동으로 닫히게 한다.
   - test에는 실제로 실행한 검증(명령어와 결과)만 적는다.
5. squash merge 후 원격·로컬 브랜치를 삭제하고 로컬 develop을 최신화한다. issue가 자동으로 닫혔는지 확인하고, 닫히지 않았으면 닫는다.

## main

- 작업은 develop에 머지하는 것으로 끝난다. dev 서버가 없으므로 develop 머지에 배포는 따르지 않는다.
- main은 운영 환경이다. 릴리즈할 때만 쓴다. `develop` → `main` PR을 만들고, squash가 아니라 merge commit으로 머지해 두 브랜치의 이력을 맞춘다.
- **main에서는 절대 브랜치를 따거나 커밋하지 않는다.** 작업 브랜치는 항상 develop에서 딴다.

# Git workflow

브랜치는 `develop`(기본 브랜치, 상시 통합)과 `main`(운영 환경) 둘만 둔다. 두 브랜치에 직접 커밋하지 않고, 모든 작업은 아래 순서로 진행한다. 팀원에게는 강제하지 않지만 **AI는 이 순서를 반드시 따른다.**

작업은 항상 작업 브랜치에서 한다. 여러 작업을 동시에 진행할 수 있도록, 작업마다 Linear 이슈와 브랜치를 따로 만들고 한 브랜치에 여러 작업을 섞지 않는다.

**파일을 한 줄이라도 고치기 전에 이슈와 워크트리를 먼저 만든다.** 고치고 나서 브랜치를 만들어 옮기지 않는다. 옮기는 과정에서 변경이 섞이거나 빠지고, 그 사이 다른 작업을 시작할 수 없다.

1. 작업 시작 전 Linear 이슈를 만든다. 프로젝트·라벨·본문 템플릿(개요·범위·범위 밖)은 `docs/linear-convention.md`를 따른다. 본문은 세션 스크래치패드에 파일로 써서 넘긴다(`.claude/rules/temp-files.md`).
   ```bash
   linear issue create --team SHI --project "<프로젝트>" -l BE -l <대상> -t "<제목>" --description-file <본문.md>
   ```
2. develop을 최신화하고 **워크트리와 브랜치를 함께 만든다**: `<prefix>/SHI-<번호>-<작업-이름>` (예: `feat/SHI-12-matching-create`). 만든 직후 이슈를 In Progress로 바꾼다. Linear의 PR 자동 전환은 PR이 열린 뒤에만 움직인다.
   ```bash
   git fetch origin
   git worktree add ../wt-SHI-<번호> -b <prefix>/SHI-<번호>-<작업-이름> origin/develop
   cp snsg/.env ../wt-SHI-<번호>/snsg/.env   # gitignore 대상이라 따라오지 않는다
   linear issue update SHI-<번호> -s "In Progress" -a @me
   ```
   - `linear issue start`는 쓰지 않는다. 현재 디렉터리에서 브랜치를 바꿔 워크트리 규칙과 맞지 않는다
   - prefix: `feat`(기능), `fix`(버그), `refactor`(리팩터링), `test`(테스트), `docs`(문서), `chore`(빌드·설정)
   - 작업 이름은 영문 kebab-case
   - **메인 작업 디렉터리(develop)에서는 코드를 고치지 않는다.** 읽기와 조회만 한다
   - 워크트리를 쓰는 이유: 작업마다 디렉터리가 분리되어 stash 없이 여러 작업을 동시에 진행할 수 있다. 리뷰 중인 작업을 그대로 둔 채 급한 수정을 시작할 수 있다
3. 작업을 커밋하고 브랜치를 push한다.
   - **커밋하기 전에 멈춘다.** 변경을 스테이징까지 해두고(새 파일이 Unversioned로 흩어지지 않고 한 묶음으로 보인다) 커밋은 하지 않은 채 사용자에게 알린다. 사용자가 IDE에서 diff를 확인하고 OK하면 커밋한다. 커밋을 여러 개로 나눌 계획이면 어떻게 나눌지도 함께 알린다.
   - 큰 작업이라도 한 커밋에 몰지 않는다. 커밋 하나에는 하나의 논리적 변경만 담고, 커밋 메시지만 보고 무엇이 바뀌었는지 알 수 있어야 한다.
4. PR을 만든다. base는 `develop`이다 (레포 기본 브랜치라 `gh pr create`가 자동으로 잡는다). 본문은 `.github/pull_request_template.md` 형식(summary / change / background or etc / test)을 따른다.
   - 제목은 `<prefix>: <제목>`. prefix는 작업 브랜치의 prefix와 같다 (예: `docs: 커밋 분리 규칙 추가`). squash merge 시 이 제목이 develop의 커밋 메시지가 된다.
   - summary에 `Fixes SHI-<번호>`를 넣어 머지 시 Linear 이슈가 Done이 되게 한다.
   - test에는 실제로 실행한 검증(명령어와 결과)만 적는다.
5. squash merge 후 원격·로컬 브랜치를 삭제하고 로컬 develop을 최신화한다. Linear 이슈가 Done이 됐는지 확인하고, 안 됐으면 바꾼다.
   ```bash
   gh pr merge <번호> --squash --delete-branch
   git worktree remove ../wt-SHI-<번호>
   git fetch --prune
   ```

## 워크트리를 쓸 때 주의

- 워크트리마다 `snsg/.env`를 복사해야 빌드·테스트가 돈다. gitignore 대상이라 따라오지 않는다.
- **작업이 새 환경변수를 더하면 그 값은 머지 전까지 그 워크트리의 `snsg/.env`에만 있다.** 새 워크트리는 메인 디렉터리의 `snsg/.env`를 복사하므로, 머지 뒤 메인 `.env`에 그 값이 없으면 develop을 받은 모든 워크트리에서 테스트 컨텍스트가 뜨지 않는다(기본값 없는 자리표시자 해석 실패). 예: SHI-182의 `ADMIN_JWT_SECRET`.
  - 머지할 때 사용자에게 메인 `snsg/.env`(그리고 develop을 받을 다른 워크트리의 `.env`)에 그 키를 더해야 한다고 알리고, PR의 background에도 적는다. 작업 중 AI는 메인 디렉터리와 다른 워크트리의 `.env`를 고치지 않는다(SHI-182 task.md 비목표).
  - 비밀값(서명 키 등)은 각자 로컬에서 난수로 만들어 파일에 바로 덧붙이고 화면·로그·채팅에 찍지 않는다. 예: Bash `printf 'ADMIN_JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> snsg/.env`. 다른 비밀값(`JWT_SECRET` 등)과 같은 값을 쓰지 않는다.
  - 기본값이 있는 키(`ADMIN_CORS_ALLOWED_ORIGINS`)는 없어도 컨텍스트가 뜨지만, 로컬에서 그 기능을 쓰려면 함께 넣는다.
  - 운영 값은 `.claude/rules/package-structure.md` 설정 절(ECS task definition).
- **DB·Redis·8080 포트는 모든 워크트리가 공유한다.** 두 워크트리에서 앱을 동시에 띄울 수 없고, 한쪽에서 돌린 테스트가 다른 쪽 데이터를 지운다.
  - 테스트는 한 번에 한 워크트리에서만 돌리도록 조율한다.
  - 워크트리를 오가며 마이그레이션이 서로 다르면 로컬 스키마를 지우고 다시 적용해도 된다. 번호 규칙은 `.claude/rules/package-structure.md` 마이그레이션 절.
  - **마이그레이션을 더하는 작업끼리는 병렬로 진행할 수 없다.** 한 워크트리가 머지 전 마이그레이션을 공유 DB에 적용해 두면 다른 워크트리의 Flyway 검증이 어긋난다. 먼저 시작한 작업이 머지된 뒤 develop을 최신화하고 다음 번호로 진행한다(SHI-146은 SHI-147 머지 후 V3로 진행했다). 워크트리별 DB는 두지 않기로 했다.
    - 기존 테이블에 DEFAULT 없는 NOT NULL 컬럼을 더하는 마이그레이션은 마이그레이션이 없는 작업도 깨뜨린다. 공유 DB에 적용된 순간부터, 그 컬럼을 모르는 다른 워크트리가 그 테이블에 INSERT하면(엔티티 저장, 테스트의 native INSERT) NOT NULL 위반으로 실패한다. 예: SHI-184의 V12(`places`의 `created_by`·`updated_by`·`reviewed_by`). 다른 워크트리에서 그 테이블 INSERT가 갑자기 실패하면 이것부터 본다. 그 작업이 머지된 뒤 develop을 받으면 풀린다.
- 워크트리를 지우기 전에 Gradle 데몬이 그 디렉터리를 잡고 있으면 삭제가 실패한다. 앱을 먼저 멈춘다.

## main

- 작업은 develop에 머지하는 것으로 끝난다. dev 서버가 없으므로 develop 머지에 배포는 따르지 않는다.
- main은 운영 환경이다. 릴리즈할 때만 쓴다. `develop` → `main` PR을 만들고, squash가 아니라 merge commit으로 머지해 두 브랜치의 이력을 맞춘다. 릴리즈 PR에는 Linear 이슈 ID를 넣지 않는다.
- **main에서는 절대 브랜치를 따거나 커밋하지 않는다.** 작업 브랜치는 항상 develop에서 딴다.

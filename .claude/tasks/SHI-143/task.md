## 배경

- Linear 이슈 SHI-143 (https://linear.app/shineunsigan/issue/SHI-143). 이번 작업부터 이슈 트래커를 GitHub issue에서 Linear로 바꾼다.
- 할 일은 둘이다.
  1. 팀이 Linear를 쓰는 컨벤션을 FE 개발자에게 공유할 문서 `docs/linear-convention.md`로 만든다. FE 담당자는 이 저장소를 받아 `docs/`를 본다(CLAUDE.md). 그래서 "FE에는 `docs/openapi.yaml`과 `docs/api-guide.md`로만 전달한다"는 CLAUDE.md·`.claude/rules/api.md`의 문장도 이 문서를 포함하도록 고친다.
  2. AI용 규칙·skill과 팀 기여 문서를 GitHub issue 기반에서 Linear 이슈 기반으로 바꾼다.
- 사용자와 합의된 사실은 아래 `## 참고`의 "합의된 사실"에 모았다. **컨벤션 문서와 규칙에는 이것만 적는다.** 추측성 규칙은 넣지 않는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-143` (브랜치 `docs/SHI-143-linear-convention`, 베이스 `84d5403` = origin/develop)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다
- 코드 변경이 없는 문서 작업이다. 빌드·테스트·Docker·DB는 필요 없다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 **Bash 문법**이다. `&&`는 PowerShell 5.1에서 동작하지 않는다
- 이미 깨져 있는 것: 없음

## GOAL

검증 명령은 모두 Bash에서 `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 &&`를 앞에 붙여 실행한다(아래에는 생략하지 않고 적었다).

### 커밋 1: FE 공유 문서 + 문서 안내 문구

1. `docs/linear-convention.md`가 새로 생긴다. 한국어, 이 저장소 문서 문체(짧은 평서문 "~한다", 규칙마다 이유를 함께 적음)로 쓴다. 독자는 FE 개발자다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && test -f docs/linear-convention.md && echo OK` → `OK`
2. 문서에 워크스페이스·팀·이슈 ID 형식이 있다: 워크스페이스 `shineunsigan`, 팀 `Shineunsigan`, 팀 키 `SHI`, 이슈 ID `SHI-<번호>`.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'shineunsigan' 'Shineunsigan' 'SHI-<번호>'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 3줄 모두 `OK`
3. 문서에 CLI 설치·인증 절차가 있다: `npm install -g @schpet/linear-cli`, 바이너리 `linear`, Personal API key 발급 경로(Settings → Account → Security & access → Personal API keys), `linear auth login`, `linear auth whoami`, 그리고 키를 대화·저장소에 남기지 않는다는 문장.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'npm install -g @schpet/linear-cli' 'Personal API keys' 'linear auth login' 'linear auth whoami'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 4줄 모두 `OK`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -rnE 'lin_api_[A-Za-z0-9]' . --exclude-dir=.git` → 출력 없음 (실제 키가 들어가지 않았다)
4. 문서에 이슈 생성 명령 예시가 있다. 합의된 형식 `linear issue create --team SHI --project "<프로젝트>" -l <라벨> ... -t "<제목>" -d "<본문>"`을 따르고 선택 옵션(`--parent`, `--milestone`, `-a @me`, `-s`)을 설명한다. 조회 명령 `linear issue list --team SHI --all-states -A`도 있다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'linear issue create --team SHI' '--parent' '--milestone' 'linear issue list --team SHI --all-states -A'; do grep -qF -- "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 4줄 모두 `OK`
5. 문서에 GitHub 연동 규칙이 있다: 이슈 ID를 브랜치 이름이나 PR 제목/본문에 넣으면 연결된다. 닫는 magic word(`Fixes`/`Closes`/`Resolves SHI-<번호>`)는 머지 시 Done, `Part of`/`Ref`는 머지돼도 Done이 아님, `Related to`는 링크만. 한 PR에 여러 이슈 가능, 한 이슈에 PR이 여럿이면 마지막 PR이 상태에 도달해야 바뀐다. 이 저장소 PR은 summary에 `Fixes SHI-<번호>`를 쓴다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'Fixes SHI-<번호>' 'Closes' 'Resolves' 'Part of' 'Ref' 'Related to'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 6줄 모두 `OK`
6. 문서에 상태 자동 전환표가 있다(팀 SHI 설정): draft open → No action, PR open → In Progress, 리뷰 요청/활동 → In Review, ready for merge → No action, PR merge → Done. 이어서 (a) develop 머지가 곧 Done이다, (b) PR 자동화는 PR이 열린 뒤에만 움직이므로 작업을 시작할 때 이슈를 직접 In Progress로 바꾼다(`linear issue update SHI-<번호> -s "In Progress" -a @me`), (c) develop→main 릴리즈 PR에는 이슈 ID를 넣지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'No action' 'In Progress' 'In Review' 'Done' 'linear issue update SHI-<번호> -s "In Progress" -a @me' '릴리즈'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 6줄 모두 `OK`
7. 문서에 프로젝트·마일스톤 규칙이 있다: 끝이 있는 목표(결과가 분명하거나 완료 예정일이 있는 묶음, 1~3주 크기 권장)에만 프로젝트를 쓴다. 이슈는 프로젝트 하나에만 속한다. 현재 BE·FE 이슈의 기본 프로젝트는 `쉬는시간 MVP`. MVP 이후 작업은 착수할 때 결과 단위 프로젝트로 만든다(예: 사장님 일정 개설, 운영 도구 v1). 프로젝트 안의 단계는 마일스톤으로 나눈다(구체 목록은 아직 정하지 않았다고 적는다).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in '쉬는시간 MVP' '마일스톤' '사장님 일정 개설' '운영 도구 v1'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 4줄 모두 `OK`
8. 문서에 라벨 규칙이 있다: 라벨 그룹은 그룹당 하나만 고른다. 그룹 `파트`=BE/FE/기획, 그룹 `대상`=사용자/어드민/파트너("누구를 위한 기능인가"는 프로젝트가 아니라 라벨로 나눈다), 그룹 `Type`=Feature/Bug/Improvement(선택), 그룹 없는 `보안`. BE 이슈는 `파트/BE` 필수. 저장소가 다르면 브랜치·PR이 달라지므로 BE와 FE 작업은 이슈를 나눈다(한 PR ↔ 한 이슈가 기본). 그리고 **현재 상태를 사실대로** 적는다: 지금 Linear에는 라벨이 그룹 없이 있고 `대상` 라벨은 아직 없으며, 그룹 구성은 Linear 웹에서 할 예정이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in '파트' '대상' '사용자' '어드민' '파트너' 'Type' 'Feature' 'Bug' 'Improvement' '보안' '파트/BE' '기획'; do grep -qF "$p" docs/linear-convention.md && echo "OK $p" || echo "MISSING $p"; done` → 12줄 모두 `OK`
9. 문서에 합의되지 않은 내용이 들어가지 않는다: 화면 명세 ID 표기 규칙(`AUTH-01` 등)이 없다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -nE '[A-Z]{3,}-0[0-9]' docs/linear-convention.md` → 출력 없음
10. CLAUDE.md의 FE 전달 문서 문장이 `docs/linear-convention.md`를 포함한다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -n 'FE에 필요한 내용은' CLAUDE.md | grep -c 'docs/linear-convention.md'` → `1`
11. `.claude/rules/api.md` `## 문서` 절의 "FE에 전하는 것은 ... 두 문서뿐" 문장이 `docs/linear-convention.md`를 포함하도록 고쳐진다. 이어지는 "두 문서만 보고 화면을 만들 수 있어야 한다"는 API 두 문서(`openapi.yaml`, `api-guide.md`)에 대한 요구로 의미가 유지되게 한다(Linear 문서는 화면을 만드는 문서가 아니다).
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -c 'docs/linear-convention.md' .claude/rules/api.md` → `1` 이상
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -n '두 문서뿐' .claude/rules/api.md` → 출력 없음

### 커밋 2: AI용 규칙·skill·기여 문서의 Linear 전환

대상 파일(grep `gh issue|issue번호|issue 번호|Closes #|wt-<|wt-[0-9]|tasks/<|feat/12-|chore/31-`과 문맥상 "issue"로 전수 확인한 결과):

| 파일 | 고칠 곳 (베이스 `84d5403` 기준 줄) |
|---|---|
| `.claude/rules/git-workflow.md` | 5, 7, 9, 10, 13, 14, 25, 27, 30 |
| `.claude/skills/task/SKILL.md` | 8, 16, 32, 40, 54, 62, 69, 87 |
| `.claude/skills/spec/SKILL.md` | 26, 30, 62(예시 `wt-31`, `chore/31-delegation-rule`) |
| `.claude/skills/implement/SKILL.md` | 10 |
| `CLAUDE.md` | 21 (`issue → 브랜치 → PR → merge`) |
| `CONTRIBUTING.md` | 6, 24, 29, 33 |
| `README.md` | 18, 21, 26 (`feat/12-내-기능명`, `<issue번호>`) |

`.claude/rules/temp-files.md`, `.claude/skills/verify/SKILL.md`, `.github/pull_request_template.md`는 grep 결과 해당 표현이 없어 고치지 않는다.

12. `.claude/rules/git-workflow.md`가 Linear 기준으로 바뀐다.
    - 1번 단계: Linear 이슈를 만든다(`linear issue create --team SHI ...`). 프로젝트·라벨 규칙은 본문을 베끼지 말고 `docs/linear-convention.md`를 가리킨다.
    - 2번 단계: 브랜치 `<prefix>/SHI-<번호>-<작업-이름>`(예시도 `SHI-` 형식), 워크트리 `../wt-SHI-<번호>`, `.env` 복사 경로도 같게. 워크트리를 만든 직후 `linear issue update SHI-<번호> -s "In Progress" -a @me`로 이슈를 In Progress로 바꾼다(이유: PR 자동화는 PR이 열린 뒤에만 움직인다). `linear issue start`는 현재 디렉터리에서 브랜치를 바꿔 워크트리 규칙과 맞지 않으므로 쓰지 않는다고 적는다.
    - 4번 단계: summary에 `Fixes SHI-<번호>`를 넣어 머지 시 Linear 이슈가 Done이 되게 한다.
    - 5번 단계: 머지 후 Linear 이슈가 Done이 됐는지 확인하고, 안 됐으면 바꾼다. 워크트리 제거 경로 `../wt-SHI-<번호>`.
    - `## main` 절: develop→main 릴리즈 PR에는 이슈 ID를 넣지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && for p in 'linear issue create --team SHI' '<prefix>/SHI-<번호>-<작업-이름>' '../wt-SHI-<번호>' 'linear issue update SHI-<번호> -s "In Progress" -a @me' 'linear issue start' 'Fixes SHI-<번호>' 'docs/linear-convention.md'; do grep -qF -- "$p" .claude/rules/git-workflow.md && echo "OK $p" || echo "MISSING $p"; done` → 7줄 모두 `OK`
13. task·spec·implement skill의 issue 표현이 Linear 이슈로 바뀐다. task.md 경로는 `.claude/tasks/SHI-<번호>/task.md`, 1단계는 "Linear 이슈 생성"(`gh issue create` → `linear issue create` + In Progress 전환), agent1에 넘기는 것은 "이슈 ID", 브랜치 규칙 인용은 `<prefix>/SHI-<번호>-<작업-이름>`. spec skill `## 환경` 예시는 `wt-SHI-<숫자>`, `<prefix>/SHI-<숫자>-...` 형식으로 바꾼다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -c '.claude/tasks/SHI-<번호>/task.md' .claude/skills/task/SKILL.md .claude/skills/spec/SKILL.md .claude/skills/implement/SKILL.md` → 세 파일 모두 `1` 이상
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -n 'linear issue create' .claude/skills/task/SKILL.md` → 1줄 이상
14. CONTRIBUTING.md·README.md의 브랜치명 규칙과 예시가 `<prefix>/SHI-<번호>-<작업-이름>` 형식이 되고, CONTRIBUTING.md의 PR 규칙은 `Fixes SHI-<번호>`, 작업 흐름은 "Linear 이슈 생성"·"Linear 이슈 Done 확인"으로 바뀐다. CONTRIBUTING.md에서 `docs/linear-convention.md`를 가리킨다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -c 'SHI-<번호>' CONTRIBUTING.md README.md` → 두 파일 모두 `1` 이상
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -c 'docs/linear-convention.md' CONTRIBUTING.md` → `1` 이상
15. CLAUDE.md 21줄의 워크플로 요약이 `Linear 이슈 → 브랜치 → PR → merge`가 된다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -c 'Linear 이슈 → 브랜치 → PR → merge' CLAUDE.md` → `1`
16. 저장소 전체(이 task.md 제외)에 GitHub issue 기반 표현이 남지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -rnE 'gh issue|issue번호|issue 번호|Closes #|wt-<issue|tasks/<issue|wt-[0-9]|feat/12-|chore/31-' CLAUDE.md CONTRIBUTING.md README.md .claude .github docs/linear-convention.md --exclude-dir=tasks` → 출력 없음
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && grep -rniwE 'issue' CLAUDE.md CONTRIBUTING.md README.md .claude .github --exclude-dir=tasks | grep -v 'linear issue'` → 출력 없음 (`issueAccessToken`은 단어 단위 매칭에 걸리지 않는다. `linear issue ...` CLI 명령은 GOAL 12·13이 요구하므로 제외한다)

### 전체

17. 바뀐 파일이 정확히 아래 목록이다. 코드(`snsg/`), `docs/openapi.yaml`, `docs/api-guide.md`, `.github/`, `.claude/rules/temp-files.md`, `.claude/skills/verify/SKILL.md`는 바뀌지 않는다.
    - `docs/linear-convention.md`(신규), `CLAUDE.md`, `.claude/rules/api.md`, `.claude/rules/git-workflow.md`, `.claude/skills/task/SKILL.md`, `.claude/skills/spec/SKILL.md`, `.claude/skills/implement/SKILL.md`, `CONTRIBUTING.md`, `README.md`, `.claude/tasks/SHI-143/task.md`(신규)
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && git add -A && git diff --staged --name-only | sort` → 위 10개 경로와 정확히 일치
18. 커밋 분할 계획을 지킬 수 있게 스테이징만 하고 커밋하지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-143 && git log -1 --format=%h` → `84d5403`

## 비목표

- Linear 쪽 실제 작업(라벨 그룹 구성, `대상` 라벨 생성, 마일스톤 생성, 프로젝트 정리)은 하지 않는다. 사용자가 Linear 웹에서 한다. `linear` CLI로 아무것도 만들거나 바꾸지 않는다
- FE 저장소(`shineunsigan-project/snsg-fr`)의 브랜치명 규칙은 합의되지 않았다. 컨벤션 문서는 "이슈 ID를 브랜치 이름이나 PR 제목/본문에 넣으면 연결된다"는 Linear 동작과 BE 브랜치 형식만 적고, FE 저장소의 브랜치 형식을 정하지 않는다
- 화면 명세 ID(`AUTH-01` 등) 표기 규칙, 구체 마일스톤 목록, 이슈 템플릿, 우선순위·추정치 규칙은 합의되지 않았으므로 쓰지 않는다
- 기존 Linear 프로젝트(`쉬는시간 MVP`, `쉬는시간 확장`, 취소된 `쉬는시간 MVP(삭제)`)를 정리·이름 변경하라는 내용을 쓰지 않는다. 그대로 둔다
- `.github/pull_request_template.md`에 `Fixes SHI-` 자리표시를 추가하지 않는다. 템플릿에는 원래 `Closes #`가 없었다
- `.claude/rules/package-structure.md`의 `issueAccessToken`은 메서드 이름이라 건드리지 않는다
- README.md의 GitHub 클론 URL·"GitHub에서 develop으로 PR 생성"은 그대로 둔다(PR은 계속 GitHub에서 만든다)
- `docs/api-guide.md`, `docs/openapi.yaml`, 코드, 테스트는 건드리지 않는다
- 판단이 갈리는 지점: git-workflow.md·CONTRIBUTING.md에 라벨·프로젝트 규칙이 필요하면 본문을 베끼지 않고 `docs/linear-convention.md`를 가리킨다. 문서끼리 어긋나지 않게 규칙 원본은 그 문서 하나로 둔다

## 제약

- 문체와 문서 관리는 CLAUDE.md "에이전트 문서 관리"를 따른다. 합의된 사실과 코드에서 확인한 사실만 적는다
- FE 전달 문서의 원칙은 `.claude/rules/api.md` `## 문서` 절을 따른다
- 워크플로 순서(이슈 → 워크트리 → 스테이징 후 멈춤 → PR → squash merge)는 `.claude/rules/git-workflow.md`의 기존 구조를 유지하고, 이슈 트래커 관련 부분만 바꾼다
- 임시 파일은 `.claude/rules/temp-files.md`대로 워크트리 밖 스크래치패드에 둔다
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 커밋 분할 (오케스트레이터가 커밋할 때)

1. `docs: Linear 컨벤션 문서를 추가한다` 류 — `docs/linear-convention.md`, `.claude/rules/api.md`, `CLAUDE.md`의 16줄(FE 전달 문서) 변경 hunk
2. `docs: 이슈 트래커를 Linear로 전환한다` 류 — `.claude/rules/git-workflow.md`, `.claude/skills/{task,spec,implement}/SKILL.md`, `CONTRIBUTING.md`, `README.md`, `CLAUDE.md`의 21줄 변경 hunk, `.claude/tasks/SHI-143/task.md`

- `CLAUDE.md`는 두 커밋에 걸친다. `git add -p`(대화형)는 이 환경에서 쓸 수 없으므로, 첫 커밋 전에 `git diff --staged CLAUDE.md`를 패치로 떠서 21줄 hunk를 뺀 패치만 `git apply --cached`로 남기는 식으로 나눈다. 구현 에이전트는 전부 스테이징까지만 한다(GOAL 17·18)
- PR summary에는 `Fixes SHI-143`을 넣는다(새 규칙의 첫 적용)

### 합의된 사실 (컨벤션 문서·규칙에 쓸 수 있는 것은 이것뿐이다)

- Linear 워크스페이스 `shineunsigan`, 팀 `Shineunsigan`, 팀 키 `SHI`. 이슈 ID는 `SHI-<번호>`
- CLI: `@schpet/linear-cli` (`npm install -g @schpet/linear-cli`, 바이너리 `linear`). 인증은 Linear Settings → Account → Security & access → Personal API keys에서 키를 만들고 `linear auth login`. 키는 대화·저장소에 남기지 않는다. 확인은 `linear auth whoami`
- 확인된 명령
  - `linear issue create --team SHI --project "<프로젝트>" -l <라벨> [-l ...] -t "<제목>" -d "<본문>" [--parent SHI-n] [--milestone <이름>] [-a @me] [-s <상태>]`
  - `linear issue update SHI-n -s "In Progress" -a @me`
  - `linear issue list --team SHI --all-states -A`, `linear issue view`
  - `linear milestone list/create`, `linear project list`
  - `linear issue start`는 현재 디렉터리에서 브랜치를 바꾸므로 워크트리 규칙과 맞지 않는다 → AI는 쓰지 않고 `git worktree add`로 브랜치를 만든다
- GitHub 연동: Linear GitHub 앱이 `shineunsigan-project` org에 설치됐다. PR 자동 전환(팀 SHI 설정): draft open → No action, PR open → In Progress, 리뷰 요청/활동 → In Review, ready for merge → No action, PR merge → Done. develop 머지가 곧 Done(작업은 develop 머지로 끝난다는 기존 규칙과 일치). develop→main 릴리즈 PR에는 이슈 ID를 넣지 않는다
- 연결(Linear 공식 문서): 브랜치 이름이나 PR 제목/본문에 이슈 ID. `Fixes`/`Closes`/`Resolves SHI-n`은 머지 시 Done, `Part of`/`Ref SHI-n`은 머지돼도 Done 처리 안 함, `Related to`는 링크만. 한 PR에 여러 이슈 가능, 한 이슈에 PR이 여럿이면 마지막 PR이 상태에 도달해야 바뀐다
- PR 자동화는 PR이 열린 뒤에만 움직이므로, 작업 시작(워크트리 생성) 시 이슈를 직접 In Progress로 바꾼다
- 브랜치 `<prefix>/SHI-<번호>-<작업-이름>`(prefix·kebab 규칙은 기존 그대로), 워크트리 `../wt-SHI-<번호>`, PR 제목 `<prefix>: <제목>` 그대로, PR summary `Fixes SHI-<번호>`, 머지 후 확인 대상은 Linear 이슈 Done
- 프로젝트(Linear 공식 문서 근거: 결과가 분명하거나 완료 예정일이 있는 이슈 묶음, Linear Method는 1~3주 크기 권장, 이슈는 프로젝트 하나에만 속함): 끝이 있는 목표에만 쓴다. 현재 BE·FE 이슈의 기본 프로젝트는 `쉬는시간 MVP`. 기존 프로젝트(`쉬는시간 MVP`, `쉬는시간 확장`, 취소된 `쉬는시간 MVP(삭제)`)는 그대로 둔다. MVP 이후 작업은 착수할 때 결과 단위 프로젝트로 만든다(예: 사장님 일정 개설, 운영 도구 v1). 프로젝트 안의 단계는 마일스톤으로 나눈다(구체 목록은 미정)
- 라벨(라벨 그룹은 그룹당 하나만 선택): 그룹 `파트`=BE/FE/기획, 그룹 `대상`=사용자/어드민/파트너("누구를 위한 기능인가"는 프로젝트가 아니라 라벨 축), 그룹 `Type`=Feature/Bug/Improvement(선택), 그룹 없는 `보안`. 현재 Linear에는 BE·FE·기획·보안(팀 라벨), Bug·Feature·Improvement(워크스페이스 라벨)가 그룹 없이 있고 `대상` 라벨은 아직 없다. 그룹 구성은 Linear 웹에서 한다(CLI `label create`에 그룹 옵션이 없다)
- BE 이슈는 `파트/BE` 필수. 저장소가 다르면(snsg-be/snsg-fr) 브랜치·PR이 달라지므로 BE와 FE 작업은 이슈를 나눈다(한 PR ↔ 한 이슈가 기본)
- 기존 Linear 이슈는 비개발자가 쓴 것이라 모두 삭제했다(휴지통)

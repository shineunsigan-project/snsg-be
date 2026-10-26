# Linear 컨벤션

BE·FE가 함께 쓰는 이슈 관리 규칙이다. 이슈 트래커는 GitHub issue가 아니라 Linear를 쓴다.

## 워크스페이스

| 항목 | 값 |
|---|---|
| 워크스페이스 | `shineunsigan` (https://linear.app/shineunsigan) |
| 팀 | `Shineunsigan` |
| 팀 키 | `SHI`. 이슈 ID는 `SHI-<번호>` 형식이다 |

## CLI

웹에서 써도 되고, 터미널에서는 `@schpet/linear-cli`를 쓴다. Linear 공식 CLI는 없다.

```bash
npm install -g @schpet/linear-cli   # 바이너리 이름은 linear
linear auth login                   # API 키를 묻는다
linear auth whoami                  # 로그인 확인
```

API 키는 Linear Settings → Account → Security & access → Personal API keys에서 만든다. 키는 한 번만 보이고, `linear auth login`에 붙여넣으면 OS 자격 증명 저장소에 들어간다. **키는 채팅·문서·저장소에 남기지 않는다.** 남으면 그 키로 워크스페이스 전체를 읽고 쓸 수 있다.

### 자주 쓰는 명령

```bash
# 이슈 만들기
linear issue create --team SHI --project "쉬는시간 MVP" -l BE -l 사용자 -t "<제목>" -d "<본문>"

# 조회
linear issue list --team SHI --all-states -A   # 상태·담당자 무관 전체
linear issue view SHI-<번호>

# 작업 시작
linear issue update SHI-<번호> -s "In Progress" -a @me
```

`linear issue create`의 선택 옵션:

| 옵션 | 뜻 |
|---|---|
| `--parent SHI-<번호>` | 상위 이슈 |
| `--milestone <이름>` | 프로젝트 마일스톤 (`--project`와 함께) |
| `-a @me` | 담당자를 나로 |
| `-s <상태>` | 처음 상태 |

## 프로젝트

**끝이 있는 목표에만 프로젝트를 만든다.** Linear는 프로젝트를 "결과가 분명하거나 완료 예정일이 있는 이슈 묶음"으로 정의하고, 1~3주 안에 끝낼 크기로 자르라고 권한다. 끝이 없는 묶음을 프로젝트로 만들면 진행률과 완료가 의미를 잃는다.

- **이슈는 프로젝트 하나에만 속한다.** 그래서 "누구를 위한 기능인가"(사용자·어드민·파트너)처럼 여러 목표에 걸치는 축은 프로젝트가 아니라 라벨로 나눈다.
- 지금 BE·FE 이슈의 기본 프로젝트는 `쉬는시간 MVP`다.
- MVP 이후 작업은 착수할 때 결과 단위로 프로젝트를 만든다. 예: `사장님 일정 개설`, `운영 도구 v1`.
- 프로젝트 안의 단계는 **마일스톤**으로 나눈다. `쉬는시간 MVP`의 마일스톤 목록은 아직 정하지 않았다.

## 라벨

라벨 그룹 안에서는 **하나만** 고를 수 있다. 분류 축 하나에 값 하나씩 달기 위해서다.

| 그룹 | 라벨 | 규칙 |
|---|---|---|
| `파트` | BE / FE / 기획 | 필수. BE 이슈는 `파트/BE` |
| `대상` | 사용자 / 어드민 / 파트너 | 누구를 위한 기능인가. MVP 동안은 대부분 `사용자` |
| `Type` | Feature / Bug / Improvement | 선택 |
| (그룹 없음) | 보안 | 다른 라벨과 함께 붙일 수 있다 |

> 현재 상태: Linear에는 BE·FE·기획·보안, Feature·Bug·Improvement 라벨이 그룹 없이 있고 `대상` 라벨은 아직 없다. 그룹 구성은 Linear 웹에서 할 예정이다(CLI로는 라벨 그룹을 만들 수 없다).

## 이슈

- **BE와 FE 작업은 이슈를 나눈다.** 저장소(snsg-be, snsg-fr)가 다르면 브랜치와 PR이 달라지고, 한 PR ↔ 한 이슈가 기본이다. 한 이슈에 PR이 여럿 붙으면 마지막 PR이 머지돼야 상태가 바뀌어 진행 상황이 흐려진다.
- 작업을 시작할 때 이슈를 직접 **In Progress**로 바꾼다(`linear issue update SHI-<번호> -s "In Progress" -a @me`). 아래 자동 전환은 PR이 열린 뒤에만 움직여서, 그 전까지는 아무도 작업 중인지 알 수 없다.

## GitHub 연동

Linear GitHub 앱이 `shineunsigan-project` org에 설치되어 있다. 이슈 ID를 **브랜치 이름**이나 **PR 제목·본문**에 넣으면 PR이 이슈에 연결된다.

### PR 본문의 magic word

| 쓰는 말 | 효과 |
|---|---|
| `Fixes` / `Closes` / `Resolves SHI-<번호>` | 연결. 머지되면 Done |
| `Part of` / `Ref SHI-<번호>` | 연결. 머지돼도 Done으로 바꾸지 않는다 |
| `Related to SHI-<번호>` | 링크만 건다. 상태를 바꾸지 않는다 |

- 한 PR에 이슈 여러 개를 연결할 수 있다.
- 한 이슈에 PR이 여럿이면 마지막 PR이 해당 단계에 도달해야 상태가 바뀐다.
- snsg-be는 브랜치를 `<prefix>/SHI-<번호>-<작업-이름>`으로 짓고, PR summary에 `Fixes SHI-<번호>`를 쓴다.

### 상태 자동 전환 (팀 SHI 설정)

| PR 상황 | 이슈 상태 |
|---|---|
| draft PR open | No action |
| PR open | In Progress |
| 리뷰 요청·활동 | In Review |
| ready for merge | No action |
| PR merge | Done |

- 작업 PR은 develop으로 머지하므로 **develop 머지가 곧 Done**이다. 작업은 develop 머지로 끝난다.
- develop → main 릴리즈 PR에는 이슈 ID를 넣지 않는다. 릴리즈는 이미 Done인 작업을 묶어 옮길 뿐, 새 작업이 아니다.

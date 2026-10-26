# 기여 가이드

## 브랜치 규칙
- `develop`이 기본 브랜치다. 모든 작업 브랜치는 `develop`에서 분기한다.
- `main`은 운영 환경이다. `main` 직접 push 금지 ❌, `main`에서 분기도 금지 ❌
- 브랜치명: `<prefix>/SHI-<번호>-<작업-이름>` (예: `feat/SHI-12-schedule-create`). `SHI-<번호>`는 Linear 이슈 ID다
  - prefix는 아래 커밋 메시지 규칙과 같은 값을 쓴다
  - 작업 이름은 영문 kebab-case

## 커밋 메시지 규칙
feat: 새로운 기능 추가  
fix: 버그 수정  
docs: 문서 수정  
style: 코드 포맷팅 (기능 변경 없음)  
refactor: 코드 리팩토링  
test: 테스트 코드  
chore: 패키지 업데이트 등 기타  

커밋 하나에는 하나의 논리적 변경만 담는다. 큰 작업이라도 한 커밋에 몰지 않는다.

## PR 규칙
- PR 제목은 커밋 규칙과 동일하게 작성 (`<prefix>: <제목>`)
- 본문은 `.github/pull_request_template.md` 형식(summary / change / background or etc / test)을 따른다
- summary에 `Fixes SHI-<번호>`를 넣어 머지 시 Linear 이슈가 Done이 되게 한다
- test에는 실제로 실행한 검증(명령어와 결과)만 적는다
- `develop` 방향 PR은 squash merge, `develop` → `main` 릴리즈 PR은 merge commit으로 머지한다

## 작업 흐름
1. Linear 이슈 생성 (프로젝트·라벨 규칙은 `docs/linear-convention.md`)
2. develop 최신화 후 브랜치 분기, 이슈를 In Progress로
3. 작업 → 커밋 → 브랜치 push
4. PR 생성 (develop 방향)
5. 머지 → 브랜치 삭제 → Linear 이슈 Done 확인

팀원에게 강제하지는 않지만, **AI(Claude Code)는 이 흐름을 반드시 따른다.** 상세 규칙은 `.claude/rules/git-workflow.md`에 있다.

## 그 외
- `.env` 파일 커밋 금지 🚨
- 코드 구조와 컨벤션은 `CLAUDE.md`와 `.claude/rules/`를 따른다

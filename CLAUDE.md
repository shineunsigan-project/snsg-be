[CLAUDE.md](http://CLAUDE.md)

## Project: snsg-be

- Spring Boot 4.0.x, Java 17, Gradle. 모듈: `snsg/`
- 빌드/테스트: `snsg/`에서 `./gradlew build`, `./gradlew test`
- base package: `com.shineunsigan.snsg`
- 들여쓰기는 4칸 공백
- 시간대는 Asia/Seoul. `SnsgApplication.main`에서 JVM 기본 시간대를 고정하고, 시간 값은 오프셋 없는 `LocalDateTime`으로 주고받는다
- 클라이언트는 웹(브라우저)이다. 모바일 앱이 아니다.
- 이 저장소에서는 BE만 작업한다. FE 저장소는 아직 없다(추후 생성). 생기면 FE는 따로 작업하며 여기서 수정하지 않고, FE에 필요한 내용은 `docs/openapi.yaml`과 `docs/api-guide.md`로만 전달한다(`.claude/rules/api.md`의 문서 절)
- 도메인별 DDD 4계층, application·domain ↔ infrastructure는 반드시 DIP. 상세 규칙:
  - `.claude/rules/package-structure.md` (항상 로드)
  - `.claude/rules/api.md` (presentation 작업 시 로드)
  - `.claude/rules/testing.md` (테스트 파일 작업 시 로드. 새 테스트를 만들기 전에 먼저 읽을 것)
  - `.claude/rules/git-workflow.md` (항상 로드. issue → 브랜치 → PR → merge)

## 에이전트 문서 관리

- CLAUDE.md, `.claude/rules/`, `.claude/skills/`는 Claude가 작업 중 수시로 채운다. 사용자에게 따로 묻지 않는다.
  - 새 컨벤션을 사용자와 합의했을 때 → 해당 rules 파일
  - 같은 설명·수정 요청이 반복될 때 → rules
  - 같은 작업 절차가 반복될 때 → skill
- 합의된 내용과 코드에서 확인한 사실만 적는다. 추측성 규칙은 넣지 않는다.
- CLAUDE.md는 짧게 유지한다. 규칙은 rules, 절차는 skills로 보낸다.
- 갱신은 진행 중인 작업 브랜치에 함께 커밋하고, PR의 background에 무엇을 바꿨는지 적는다.

Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

1. Think Before Coding  
 Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.  
If multiple interpretations exist, present them - don't pick silently.  
If a simpler approach exists, say so. Push back when warranted.  
If something is unclear, stop. Name what's confusing. Ask.  
2. Simplicity First  
   Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.  
No abstractions for single-use code.  
No "flexibility" or "configurability" that wasn't requested.  
No error handling for impossible scenarios.  
If you write 200 lines and it could be 50, rewrite it.  
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

3. Surgical Changes  
 Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.  
Don't refactor things that aren't broken.  
Match existing style, even if you'd do it differently.  
If you notice unrelated dead code, mention it - don't delete it.  
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.  
Don't remove pre-existing dead code unless asked.  
The test: Every changed line should trace directly to the user's request.

4. Goal-Driven Execution  
 Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"  
"Fix the bug" → "Write a test that reproduces it, then make it pass"  
"Refactor X" → "Ensure tests pass before and after"  
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]  
 Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
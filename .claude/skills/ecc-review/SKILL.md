---
name: ecc-review
description: verify가 끝난 변경을 ecc 플러그인의 읽기 전용 리뷰어 에이전트로 리뷰하고, 지적을 프로젝트 규칙 기준으로 분류한다. task 파이프라인의 리뷰 단계 계약이며, 현재 변경의 리뷰만 따로 시킬 때도 쓴다.
---

# ecc review

verify는 GOAL만 판정한다(`.claude/skills/verify/SKILL.md` "GOAL에 없는 것을 트집잡지 않는다"). 그래서 GOAL 밖의 버그·보안 문제·설계 품질은 오케스트레이터 재확인과 사용자 diff 승인만 잡는다. 이 단계는 그 자리를 ecc 플러그인(`ecc@ecc`, user scope)의 리뷰어 에이전트로 보강한다. 전체 흐름은 `.claude/skills/task/SKILL.md`에 있다.

**ecc는 범용 플러그인이다. 프로젝트 규칙(CLAUDE.md, `.claude/rules/`)이 항상 이긴다.** ecc 에이전트·스킬의 예시와 권고에는 이 프로젝트 규칙과 정면으로 충돌하는 것이 많다(아래 "자주 나오는 규칙 충돌"). 리뷰어는 지적을 낼 뿐이고, 받아들일지는 오케스트레이터가 규칙 기준으로 가른다.

## 언제 띄우는가

- **verify 전 항목 PASS와 오케스트레이터 재확인이 끝난 뒤, refine-docs보다 먼저.** 리뷰 때문에 코드가 바뀌면 문서 대상도 바뀐다.
- 코드 변경이 없는 작업(문서·skill만 고치는 작업)은 띄우지 않는다.
- 사용자가 리뷰만 따로 시켜도 같은 방식으로 띄운다.

## 어떤 리뷰어를 띄우는가

`git diff --staged --name-only`(comment-analyzer 조건은 javadoc이 기본 문맥 3줄 밖에 있어도 보이도록 `git diff --staged -U20` 본문)를 보고 고른다. 고른 리뷰어는 **한 메시지에서 병렬로** 띄운다. 모두 읽기 전용이라 동시에 돌려도 된다(task SKILL.md "에이전트를 띄우는 방법").

| 조건 (staged 경로) | `subagent_type` | 보는 것 |
|---|---|---|
| 코드 변경이 있으면 항상 | `ecc:code-reviewer` | 확신 80% 이상의 버그·보안·품질 문제. HIGH 이상은 실패 시나리오를 요구한다 |
| `.java` | `ecc:java-reviewer` | Spring·JPA·트랜잭션·예외 처리 |
| `db/migration/`, `*Repository*.java` | `ecc:database-reviewer` | 인덱스, 잠금, 마이그레이션 안전성 |
| `auth/`, `admin/`, `security/`, 파일 업로드, `infrastructure/client/`, `src/main/resources/application*.yaml` | `ecc:security-reviewer` | 인증·인가, 비밀값, 입력 검증, 외부 호출 |
| `infrastructure/client/`, `infrastructure/scheduler/`, `infrastructure/event/` | `ecc:silent-failure-hunter` | 삼킨 예외, 위험한 fallback, 타임아웃 |
| `src/test/` | `ecc:pr-test-analyzer` | 바뀐 동작별 테스트 유무와 품질 |
| `domain/model/` | `ecc:type-design-analyzer` | 엔티티·VO의 불변식 표현과 캡슐화 |
| `presentation/`, 또는 `git diff --staged`에 javadoc(`/**`) 블록이나 그 블록이 달린 메서드의 변경이 있음 | `ecc:comment-analyzer` | 바뀐 동작과 어긋난 주석·API description. description은 FE 계약이고 `OpenApiSpecTest`는 문구가 사실인지 보지 않는다 |

- `comment-analyzer`만 `model: haiku`이고 나머지는 `sonnet`이다. 한 작업에 보통 2~4개다.
- `type-design-analyzer`·`comment-analyzer`는 Bash가 없어 diff를 직접 뽑지 못한다. 프롬프트에 해당 파일의 `git diff --staged` 부분을 넣는다.
- `database-reviewer`에는 고정 프롬프트 뒤에 한 줄을 더한다: "운영은 ECS 서비스라 배포 중 새 태스크가 마이그레이션을 적용한 뒤에도 이전 버전 태스크가 잠시 돌 수 있다. 이전 버전 코드가 실패하는 변경(컬럼 삭제·이름 변경, 이전 코드가 INSERT하는 테이블에 DEFAULT 없는 NOT NULL 컬럼, 이전 코드가 쓰는 enum 값 제거)을 지적한다." 근거: `.github/workflows/deploy.yml`(ECS 서비스 갱신), V12가 `places`에 DEFAULT 없는 NOT NULL 컬럼을 더했다. 출처: ecc `database-migrations`.
  - 마이그레이션이 없는 diff에서 `database-reviewer`가 마이그레이션이 없다는 것만 보고 "지적 없음"으로 끝낼 수 있다(SHI-203 첫 답). 그래서 프롬프트에 **이번 diff의 핵심 대상**을 한 줄 더 적는다. 쿼리 변경이면 바뀐 메서드 이름과 볼 것(keyset 비교 방향, 계산식 정렬, 삭제 조건, 탈 인덱스)이다. SHI-203은 이렇게 다시 요청하자 쿼리를 실제로 검토했다.

## 프롬프트에 반드시 넣는 것

ecc 리뷰어의 기본 동작은 이 저장소와 맞지 않는다. 예: `java-reviewer`는 저장소 루트에서 `build.gradle`을 찾고(이 저장소는 `snsg/build.gradle`), **unstaged** `git diff -- '*.java'`를 읽고(스테이징 뒤라 비어 있다), `./gradlew check`로 테스트를 돌린다(공유 DB). 그래서 아래를 고정 문구로 넣는다.

```
워크트리: <절대 경로>. 이 디렉터리에서만 읽는다.
리뷰 대상은 `git -C <워크트리> diff --staged` 뿐이다. `git diff`(unstaged)와 다른 커밋은 대상이 아니다.
빌드 파일은 `snsg/build.gradle`이다(Spring Boot 4, Java 17, Gradle).

금지: 파일 수정, `git add`·`git checkout`·`git restore`·`git stash`,
`./gradlew`·`mvn`·`npm`·`npx`·`psql`·`docker`·`curl` 실행.
DB·Redis·8080 포트를 다른 작업과 공유하고, 빌드·테스트는 오케스트레이터가 이미 돌렸다.

먼저 읽을 규칙: CLAUDE.md, .claude/rules/package-structure.md, .claude/rules/api.md, .claude/rules/testing.md.
이 규칙과 충돌하는 일반 권고는 보고하지 않는다. 충돌인지 애매하면 지적에 `규칙충돌?`을 붙인다.

이번 작업의 GOAL:
<task.md의 ## GOAL 전문>
이번 작업의 비목표. 여기에 속하는 지적은 보고하지 않는다:
<task.md의 ## 비목표 전문>

출력: 지적마다 `파일:줄 / 심각도(CRITICAL·HIGH·MEDIUM·LOW) / 실패 시나리오(입력·상태 → 잘못된 결과) / 근거`.
지적이 없으면 "지적 없음"이라고만 쓴다.
```

- **implement 보고와 verify 결과는 주지 않는다.** verify와 같은 원칙이다. "테스트를 다 통과했다"를 먼저 읽으면 그것을 전제로 읽는다.

## 끝난 뒤 확인

- **리뷰어가 끝나면 `git status --short`와 `git diff --staged --stat`이 리뷰 전과 같은지 본다.** Bash가 있는 리뷰어는 쓰기가 도구로 막혀 있지 않고 프롬프트로만 막힌다. 달라졌으면 되돌리고 사용자에게 보고한다.

## 합치기와 반박 검증

출처: ecc `/orch-review` 워크플로의 설계(`workflows/README.md`). 워크플로 자체는 쓰지 않는다(아래 "쓰지 않는 것").

- **분류 전에 리뷰어 간 중복을 합친다.** 기준은 `파일` + 지적한 코드 조각이다. 줄 번호·제목은 리뷰어마다 달라 기준이 아니다. 심각도는 가장 높은 것을 남기고, 낸 리뷰어는 모두 적는다.
- 아래 분류표의 1번(규칙 충돌)을 먼저 적용한다. 지적 문장과 rules만 대조하면 되고 코드를 읽지 않는다. 기각될 지적에 검증 비용을 쓰지 않는다.
- **남은 CRITICAL·HIGH는 오케스트레이터가 코드를 읽어 판단하지 않는다.** 오케스트레이터가 구현 맥락을 갖게 되고(task SKILL.md 서두), 확인하지 못한 진짜 문제를 기각하게 된다. `Explore` 검증 에이전트 하나에 묶어 넘긴다.
  - 주는 것: 워크트리 절대 경로, 지적마다 `파일:줄 / 실패 시나리오 / 근거`. 리뷰어 이름·다른 지적·implement 보고·verify 결과는 주지 않는다.
  - 지시: 지적마다 `성립` / `이미 지켜짐(ArchUnit·기존 테스트 파일:줄)` / `반박(반증 파일:줄)` / `판단 불가` 중 하나. 반박과 이미 지켜짐은 코드로 근거를 댈 수 있을 때만 한다. 빌드·테스트·DB는 실행하지 않는다.
  - `이미 지켜짐`은 2번, `반박`은 3번으로 기각한다. `판단 불가`는 기각하지 않고 `미확인`으로 **분류 직후 바로 사용자에게 묻는다**(task SKILL.md "멈춰 묻는 경우").
- MEDIUM·LOW는 검증하지 않고 분류만 한다. 2번 판정이 필요해 보이면 6번으로 내린다.

## 지적 분류 (오케스트레이터)

지적마다 아래 순서로 처음 맞는 칸에 넣는다.

| 순서 | 경우 | 처리 |
|---|---|---|
| 1 | CLAUDE.md·`.claude/rules`와 충돌한다 | **기각.** 예는 아래 "자주 나오는 규칙 충돌" |
| 2 | 검증 에이전트가 ArchUnit(`ArchitectureTest`)이나 기존 테스트가 이미 지킨다고 근거를 댔다 | 기각 |
| 3 | 실패 시나리오가 없거나, 검증 에이전트가 코드로 반박했다 | 기각 |
| 4 | GOAL 위반의 증거다 | agent2에게 **그 항목과 재현 방법만** `SendMessage`로 보낸다. 같은 GOAL 항목의 FAIL로 센다(재작업 루프의 2회 제한) |
| 5 | GOAL 밖이지만 실제 버그·보안 문제다 | **직접 고치게 하지 않는다.** 분류 직후 사용자에게 이번 작업에 넣을지, 후속 이슈로 뺄지 묻는다 |
| 6 | 나머지(취향, 개선 제안) | PR 본문 `background or etc`의 참고나 후속 이슈 후보로 남긴다 |

- **4번으로 재작업하면** verify를 새로 띄우고 재확인을 다시 돈 뒤, 그 지적이 고쳐졌는지만 `Explore` 에이전트 하나로 확인한다(지적의 `파일:줄 / 실패 시나리오`만 준다). verify는 리뷰 지적을 받지 않아 이것을 보지 않는다. "안 고쳐졌다"면 같은 GOAL 항목의 FAIL로 세고 agent2를 재개한다(task SKILL.md "FAIL 횟수").
- **5번에서 사용자가 이번 작업에 넣기로 하면** agent1을 재개해 task.md의 GOAL에 항목을 더하고 사용자 재승인을 받은 뒤, agent2 → verify(새로) → 재확인을 돈다. implement·refine-docs는 GOAL을 고칠 수 없고, 오케스트레이터가 옮겨 적으면 기준이 다듬어진다(task SKILL.md 아티팩트 절).
- 4·5로 재작업한 뒤 **리뷰는 다시 돌리지 않는다.** 다시 돌면 리뷰 → 재작업 → 리뷰가 끝나지 않는다. 사용자가 원하면 그때 돌린다.
- 1로 기각한 지적이 같은 규칙에서 반복되면, 그 규칙의 이유가 문서에 충분히 적혀 있는지 refine-docs에 마찰 기록으로 넘긴다. ecc 문구를 rules에 옮기지 않는다.
- 사용자에게 diff 승인을 받을 때 분류 결과를 함께 보여준다. 기각한 것도 한 줄씩 이유와 함께 보여준다. 기각 판단도 사용자가 확인할 대상이다.

### 자주 나오는 규칙 충돌

ecc 에이전트·스킬 본문에서 확인한 것들이다. 이런 지적은 1번으로 기각한다.

| ecc 권고 | 프로젝트 규칙 |
|---|---|
| `timestamptz`·`Instant` | 시간은 오프셋 없는 `LocalDateTime`(CLAUDE.md) |
| 목록은 `Page<T>` 반환 | keyset 커서 목록(`.claude/rules/api.md` 목록 절) |
| DELETE 204, 검증 오류 422, `{error:{code}}` 봉투 | `ApiResponse` 봉투, 400 `INVALID_REQUEST`(`.claude/rules/api.md`) |
| 새 토큰 필터를 `@Component`로 | 새 필터는 체인 설정에서 `new`로 만들어 체인에만 넣는다. 기존 사용자 필터 `JwtAuthenticationFilter`는 빈이고 `FilterRegistrationBean.setEnabled(false)`로 자동 등록만 끈다. 이 두 방식에서 벗어난 지적만 유효하다(`.claude/rules/package-structure.md` 보안 체인 절) |
| `@PreAuthorize`·서비스 안 역할 확인 | 보안 체인 매처 `hasRole`(같은 절) |
| setter, hard delete, IDENTITY Long id | 의도 메서드, `isDeleted`, UUID v7(`.claude/rules/package-structure.md` domain 절) |
| facade에 `@Transactional` | facade는 트랜잭션을 걸지 않는다(Service와 Facade 절) |
| 잠금 조회에 fetch join | 잠그는 조회에 fetch join 하지 않는다(동시 요청과 잠금 절) |
| Testcontainers, `@MockBean`, 80% 커버리지, 모든 public 메서드 단위 테스트 | 로컬 PostgreSQL·Redis, `@MockitoBean`, 계층별 테스트 기준(`.claude/rules/testing.md`) |
| Redis 키를 패턴으로 지운다 | 테스트 키는 패턴으로 지우지 않는다(`.claude/rules/testing.md`) |
| rate limiting 누락을 HIGH로 | 요구사항에 없는 기능이다. 6번(후속 후보)으로 내린다 |
| 50줄 넘는 함수, 불변 객체로 바꿔라 | 엔티티 상태는 의도 메서드로 바꾼다. 길이만으로는 기각한다 |

## 쓰지 않는 것

아래는 파이프라인 에이전트에 쓰지 않는다. 사람이 참고로 읽는 것은 막지 않는다.

| 대상 | 이유 |
|---|---|
| `ecc:java-build-resolver`, `ecc:tdd-guide`, `ecc:build-error-resolver`, `ecc:code-simplifier`, `ecc:refactor-cleaner` | 쓰기 에이전트다. 한 워크트리에 쓰기 에이전트는 하나다(task SKILL.md). 빌드·테스트도 돌린다 |
| `ecc:doc-updater` | `docs/CODEMAPS/`를 만든다. 문서는 refine-docs가 맡는다 |
| `ecc:planner`, `ecc:architect` | opus 모델이고 spec 단계와 겹친다. spec은 애매하면 사용자에게 묻는 것이 계약이다 |
| 스킬 `ecc:springboot-*`, `ecc:jpa-patterns`, `ecc:java-coding-standards`, `ecc:api-design`, `ecc:hexagonal-architecture`, `ecc:redis-patterns`, `ecc:postgres-patterns`, `ecc:tdd-workflow`, `ecc:verification-loop`, `ecc:security-review` | 예시가 위 "자주 나오는 규칙 충돌"에 해당하거나 Maven·npm을 전제로 한다. 에이전트에 로드하면 규칙과 다른 코드를 쓴다 |
| `/orch-review`, `/orch-*`, ecc `/code-review`, `/review-pr` | 프로젝트 규칙을 넣을 자리가 없고, java-reviewer가 `./gradlew check`를 돌린다. PR 모드는 저장소에 리뷰 파일을 쓰거나 PR에 게시한다 |
| `/plan`, `/prp-*`, `/checkpoint`, `/learn`, `/test-coverage`, `/update-docs`, `/pr` | spec·refine-docs와 겹치거나 저장소에 파일을 쓴다(`.claude/plans/`, 체크포인트 로그). `/pr`은 base가 main이다. `/plan`의 기존 패턴 찾기만 spec에 가져왔다 |
| ecc `rules/`(`common/`, `java/`) | 플러그인으로는 로드되지 않는다. 직접 복사하지 않는다. 80% 커버리지, Testcontainers, 불변 객체, 모든 엔드포인트 rate limiting이 프로젝트 규칙과 충돌하고, 매 세션 컨텍스트에서 rules와 경쟁한다 |
| `ecc:docs-lookup`, `ecc:documentation-lookup` | context7 MCP가 설치돼 있지 않아 동작하지 않는다 |
| `/astra-review`, `/multi-*`, `ecc:council-multi-model` | codex 등 외부 CLI가 없다 |

## ecc 훅

ecc는 user scope 훅도 설치한다(standard 프로필). 파이프라인에 걸리는 것은 GateGuard(Fact-Forcing Gate)와 `--no-verify` 차단이다. 대응은 `.claude/skills/task/SKILL.md` "ecc 훅에 막혔을 때".

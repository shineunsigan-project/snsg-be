## 배경

- Linear SHI-183 "어드민 가입 승인과 이용 중지". SHI-182(어드민 계정·인증, PR #67, develop `7bfb80b`)에 이어, MASTER가 가입 신청을 승인·거절하고 승인된 어드민의 이용을 중지하는 API를 만든다. 다음 작업 SHI-184는 어드민 장소 수정이다.
- 사용자 원문(그대로): "master role은 직접 db에 밀어넣고 다른 admin은 회원가입을 통해 master_role이 수락하는 방식으로 진행한다. 일반사용자는 절대 admin에 접속할 수도 어떠한 정보도 볼 수 없도록 설계되어야 하며 … 인증은 master가 해주는 거라 별다른 인증은 없다." "면밀히 검토후 진행한다."
- 사용자가 고른 안: MASTER 권한은 **승인·거절 + 이용 중지**. 이미 승인된 어드민도 이용 중지할 수 있고, 이용 중지하면 그 어드민의 토큰은 바로 쓸 수 없다.
- SHI-182가 이 작업에 미뤄 둔 것(`.claude/tasks/SHI-182/task.md` 결정 8): 상태는 `PENDING`·`APPROVED`만 만들었고, 거절을 상태로 남길지 행을 지울지(아이디 재사용 여부)는 이 작업이 정한다.

### 코드에서 확인한 사실 (설계의 근거)

- 어드민 필터(`AdminAuthenticationFilter`)는 요청마다 `AdminQueryService.findApprovedRole`로 **삭제되지 않았고 `APPROVED`인** 어드민의 역할을 읽어, 있을 때만 principal(adminId `UUID`)과 권한 `ROLE_<역할>`을 채운다. 아니면 익명 → 보호 경로 401 `UNAUTHENTICATED`. 그래서 새 상태(`REJECTED`·`SUSPENDED`)는 코드 변경 없이 다음 요청부터 401이다.
- 재발급(`AdminReissueTokenFacade.reissue`)은 `isApproved`가 거짓이면 `deleteAll(adminId)` 후 `INVALID_ADMIN_REFRESH_TOKEN`이다. 새 상태도 그대로 막힌다.
- 로그인(`AdminLoginFacade`)은 비밀번호가 맞은 뒤 `AdminCredentialResult.approved`만 보고 아니면 403 `ADMIN_PENDING_APPROVAL`을 낸다. 상태를 구분하지 않으므로 지금 그대로면 거절·중지 계정도 "승인 대기"로 안내된다. **고쳐야 한다.**
- `AdminSecurityConfig`: 공개 경로 셋(`POST signup·login·tokens`) 외 `anyRequest().authenticated()`. **역할(`hasRole`) 검사를 쓰는 곳이 아직 없고, `AccessDeniedHandler`가 어디에도 없다**(`grep -rn accessDenied snsg/src/main` 출력 없음). 그대로 `hasRole("MASTER")`만 걸면 ADMIN의 요청은 Spring 기본 403(우리 봉투가 아님)이 된다. 메서드 보안(`@EnableMethodSecurity`·`@PreAuthorize`)도 쓰지 않는다.
- `CommonErrorCode`에는 FORBIDDEN 값이 없다(`INVALID_REQUEST`, `UNAUTHENTICATED`, `INTERNAL_ERROR`). `ErrorType.FORBIDDEN` → 403은 `GlobalExceptionHandler`가 변환한다.
- 감사: `Admin`은 `BaseAuditEntity`. `SecurityAuditorAware`는 인증된 principal의 `toString()`을 `updated_by`에 넣는다. 어드민 principal은 adminId `UUID`이므로 **MASTER가 상태를 바꾸면 `updated_by` = MASTER의 adminId 문자열**이 남는다(엔티티를 읽어 바꾸는 dirty checking 경로일 때. 일괄 UPDATE는 Auditing을 거치지 않는다).
- `admins.status`는 `VARCHAR(20)`, CHECK 제약 없음(V11 주석에 "상태를 더할 때 마이그레이션이 필요 없도록"). `SUSPENDED`(9자)·`REJECTED`(8자) 모두 들어간다. **마이그레이션은 필요 없다.**
- 아이디 유니크는 `uix_admins_login_id ON admins (login_id) WHERE is_deleted = FALSE`. 삭제되지 않은 행이면 상태와 관계없이 아이디를 막는다.
- 경로 변수·쿼리 파라미터 타입 오류(UUID 아님, enum 밖 값)는 `GlobalExceptionHandler.handleExceptionInternal`이 400 `INVALID_REQUEST`(errors 없음)로 감싼다.
- 목록 API 형식(커서·`{items, nextCursor}`·size 잘라내기)과 쿼리 작성 규칙이 이미 있다(`.claude/rules/api.md` 목록 절, `.claude/rules/package-structure.md` 목록 쿼리 절, 예: `ScheduleCursor`, `ScheduleRepositoryImpl`). 잠금 조회 예: `ScheduleRepository.findByIdForUpdate`.

### 결정 (spec 단계에서 근거로 정함)

1. **상태는 넷: `PENDING`(승인 대기)·`APPROVED`(승인)·`REJECTED`(거절)·`SUSPENDED`(이용 중지).** 상태 전이는 아래 넷뿐이다. 그 밖의 출발 상태에서 부르면 409 `ADMIN_STATUS_CONFLICT`이고 아무것도 바꾸지 않는다(같은 동작을 다시 보낸 경우 포함).

   | 동작 | 출발 | 도착 | 세션 |
   |---|---|---|---|
   | 승인 | `PENDING` | `APPROVED` | - |
   | 거절 | `PENDING` | `REJECTED` | - (승인 전이라 세션이 없다) |
   | 이용 중지 | `APPROVED` | `SUSPENDED` | 그 어드민의 refresh 세션을 모두 지운다(결정 4) |
   | 이용 중지 해제 | `SUSPENDED` | `APPROVED` | - |

   - **거절은 상태(`REJECTED`)로 남기고 행을 지우지 않는다. 거절된 아이디는 다시 쓸 수 없다.** 근거: (a) "아이디 중복 금지" 요구를 그대로 지킨다. 지우면(soft delete) 부분 유니크 인덱스에서 빠져 같은 아이디로 다른 사람이 가입할 수 있다. (b) 목록에서 상태 필터로 거절 이력을 보고 `updated_by`로 누가 거절했는지 남는다. soft delete면 이력을 보려고 `IncludingDeleted` 조회가 따로 필요하다. (c) 거절된 사람이 로그인하면 비밀번호가 맞은 뒤 "거절됨"을 알려줄 수 있다(결정 6). soft delete면 "아이디 또는 비밀번호가 틀렸다"로만 보여 원인을 알 수 없다. 대가: 같은 사람이 다시 신청하려면 다른 아이디를 써야 한다. 거절은 되돌리지 않는다(`REJECTED`는 끝 상태). 잘못 거절했으면 새 아이디로 다시 신청한다.
   - **이용 중지 해제(`SUSPENDED` → `APPROVED`)를 둔다.** 근거: "이용 중지"는 계정을 없애는 것이 아니라 멈추는 것이다. 해제가 없으면 아이디가 막힌 채(결정 1의 유니크) 그 사람은 새 아이디로 다시 가입해야 하고, 아니면 BE가 DB를 직접 고쳐야 한다. 엔드포인트 하나와 전이 하나라 비용이 작다. **[확정(추천안) — 해제를 둔다]** 사용자가 해제를 빼라고 하면 GOAL의 해제 관련 줄(결정 2 표의 `DELETE …/suspension`, GOAL 2·6·8·9·11의 해제 항목)을 지운다.
   - 해제 뒤 이전 refresh 세션이 되살아나지 않는다. 중지할 때 세션을 모두 지우기 때문이다(결정 4). 해제된 어드민은 다시 로그인한다.

2. **API (모두 MASTER 전용, `/api/admin/v1/admins` 아래)**

   | 메서드·경로 | 하는 일 | 성공 |
   |---|---|---|
   | `GET /api/admin/v1/admins?status=&cursor=&size=` | 어드민 목록 | 200 `{items, nextCursor}` |
   | `POST /api/admin/v1/admins/{adminId}/approval` | 승인 | 200, data 없음 |
   | `POST /api/admin/v1/admins/{adminId}/rejection` | 거절 | 200, data 없음 |
   | `POST /api/admin/v1/admins/{adminId}/suspension` | 이용 중지 | 200, data 없음 |
   | `DELETE /api/admin/v1/admins/{adminId}/suspension` | 이용 중지 해제 | 200, data 없음 |

   - URL은 "동사 대신 하위 리소스"(`.claude/rules/api.md` URL 절, 예: `POST /api/v1/schedules/{scheduleId}/cancellation`). 상태를 바꾸는 API는 data를 주지 않는다(일정 참여·취소와 같다). FE는 성공 뒤 목록을 다시 부른다.
   - 목록
     - `status`: 선택. `PENDING`·`APPROVED`·`REJECTED`·`SUSPENDED` 중 하나. 생략하면 모든 상태. 목록 밖 값은 400 `INVALID_REQUEST`(errors 없음).
     - 삭제되지 않은 모든 어드민(MASTER 포함, 요청한 MASTER 자신 포함)을 보여준다. 역할로 구분할 수 있게 `role`을 싣는다.
     - 정렬은 가입 신청 시각(`createdAt`) 오름차순, 같으면 id 오름차순. 커서 페이지는 `.claude/rules/api.md` 목록 절 그대로(keyset, size + 1, URL-safe Base64 불투명 커서, 해석할 수 없으면 400 `INVALID_CURSOR`, size는 생략하면 기본값·범위 밖은 잘라냄). 기본 20·상한 50이고 설정값이다.
     - 항목 필드: `id`, `loginId`, `name`, `team`, `birthDate`, `role`, `status`, `createdAt`(가입 신청 시각, 오프셋 없는 한국 시간). **비밀번호 해시와 감사 컬럼(`createdBy`·`updatedBy`)은 싣지 않는다.**
   - **대상이 MASTER면 네 동작 모두 403 `MASTER_NOT_MODIFIABLE`이다.** 요청한 MASTER 자신도 MASTER이므로 자기 자신을 중지·거절할 수 없다. 근거: MASTER는 DB로만 생기므로(README 절차) 관리도 DB로 한다. API로 열면 MASTER 한 명이 다른 MASTER를 모두 중지하거나, 마지막 MASTER가 자기를 중지해 아무도 승인할 수 없게 될 수 있다.
   - 대상이 없거나 삭제됐으면 404 `ADMIN_NOT_FOUND`. 판정 순서: 대상 조회(404) → MASTER인지(403) → 전이 가능한지(409).
   - **역할 검사는 보안 체인에서 한다.** `AdminSecurityConfig`가 `/api/admin/v1/admins/me`는 인증만, 그 밖의 `/api/admin/v1/admins`·`/api/admin/v1/admins/**`는 `hasRole("MASTER")`로 막는다(`me`를 먼저 둔다. 매처는 처음 맞는 것이 이긴다). 역할은 필터가 요청마다 DB에서 읽은 값이라 토큰과 어긋나지 않는다. 다른 MASTER 전용 경로가 생기면 같은 방식으로 더한다.
   - **ADMIN(승인된 일반 어드민)이 MASTER 전용 경로를 부르면 403 `MASTER_ONLY`**, 우리 봉투(`{isSuccess:false, code:"MASTER_ONLY", message}`)다. 어드민 체인에 `AccessDeniedHandler`를 둬 이 봉투를 쓴다(401 진입점과 같은 방식, 어드민 infrastructure). 토큰이 없거나 승인 상태가 아니면 지금처럼 401 `UNAUTHENTICATED`. 사용자 토큰은 401(어드민 체인에서 인증되지 않는다).

3. **오류 코드(`AdminErrorCode`, FE 계약). SHI-182의 다섯 개에 더한다.**

   | code | 상태 | 언제 |
   |---|---|---|
   | `MASTER_ONLY` | 403 | MASTER 전용 API를 ADMIN이 부름 |
   | `ADMIN_NOT_FOUND` | 404 | 승인·거절·중지·해제: 대상이 없거나 삭제됨 |
   | `MASTER_NOT_MODIFIABLE` | 403 | 승인·거절·중지·해제: 대상이 MASTER(자기 자신 포함) |
   | `ADMIN_STATUS_CONFLICT` | 409 | 승인·거절·중지·해제: 대상의 지금 상태에서 할 수 없는 동작(이미 처리됨, 다른 MASTER가 먼저 처리함 포함) |
   | `INVALID_CURSOR` | 400 | 목록: 해석할 수 없는 커서 |
   | `ADMIN_REJECTED` | 403 | 로그인: 비밀번호는 맞지만 가입이 거절됨 |
   | `ADMIN_SUSPENDED` | 403 | 로그인: 비밀번호는 맞지만 이용 중지됨 |

   문구는 화면에 그대로 보여줄 한국어로 구현이 정한다(기존 `AdminErrorCode` 말투).

4. **이용 중지는 즉시 반영된다. 중지하면 그 어드민의 refresh 세션을 모두 지운다.**
   - access token: 필터가 요청마다 `APPROVED`를 확인하므로 다음 요청부터 401이다(코드 변경 없음, 사실 절).
   - refresh: 재발급도 승인 상태를 다시 보므로 지우지 않아도 재발급은 막힌다. 그래도 지우는 이유는 **해제했을 때 중지 전 세션(최대 12시간)이 되살아나지 않게** 하기 위해서다. 중지 사유가 계정 탈취일 수 있다.
   - 방식: **facade**(`.claude/rules/package-structure.md` Service와 Facade 절 — Redis는 이벤트 발행 포트가 아닌 `port/out`). facade가 Command 서비스의 중지(트랜잭션, 커밋)를 부른 뒤 `AdminRefreshTokenStorePort.deleteAll`을 부른다. 서비스가 예외를 던지면(404·403·409) 세션을 지우지 않는다. facade에 `@Transactional`을 걸지 않는다(ArchUnit). 이벤트(AFTER_COMMIT 리스너)는 쓰지 않는다. 같은 도메인 안의 정리이고, facade로 충분히 트랜잭션 밖에 둘 수 있다.
   - 커밋 뒤 Redis 삭제가 실패하면 응답은 500이지만 중지는 이미 반영돼 있다. 안전망은 필터·재발급의 상태 확인이다(재발급 시도 때 `deleteAll`도 다시 돈다). 이 경우 FE는 목록을 다시 불러 상태를 확인한다.
   - 남는 틈(받아들임): 중지 뒤 30분 안에 해제하면, 중지 전에 받은 access token(최대 30분)은 해제 뒤 다시 통한다. 막으려면 토큰 발급 시각과 상태 변경 시각을 비교해야 하는데 컬럼·토큰 형식이 늘어난다. 요구("중지하면 바로 쓸 수 없다")는 중지 동안에 대한 것이라 충족한다.

5. **동시 처리: 대상 행을 잠근 뒤 판정한다.** Command 서비스의 트랜잭션 안에서 대상 어드민을 `SELECT … FOR UPDATE`(`@Lock(PESSIMISTIC_WRITE)` repository 메서드)로 **처음 읽고**, 그 뒤 엔티티의 전이 메서드가 출발 상태를 본다. 두 MASTER가 같은 대상을 동시에 승인·거절하면 뒤 요청은 앞 커밋을 기다렸다가 바뀐 상태를 보고 409 `ADMIN_STATUS_CONFLICT`다. 근거: `.claude/rules/package-structure.md` 동시 요청과 잠금 절("잠근 뒤 판정"). 조건부 일괄 UPDATE는 Auditing을 거치지 않아 `updated_by`에 MASTER id를 직접 넣어야 하고, 404·403·409를 가르려면 어차피 한 번 더 읽어야 해서 쓰지 않는다. 낙관적 잠금(`@Version`)은 마이그레이션이 필요해 쓰지 않는다. 한 흐름에서 잡는 잠금은 이 행 하나라 교착 순서 문제가 없다.

6. **로그인: 거절·중지 계정도 비밀번호가 맞은 뒤에만 상태를 알려준다.** SHI-182의 `PENDING` 정책(결정 12)과 같다. 판정 순서와 실패 횟수 규칙은 그대로이고(비밀번호가 맞으면 실패 횟수 초기화), 상태별로 `PENDING` → 403 `ADMIN_PENDING_APPROVAL`, `REJECTED` → 403 `ADMIN_REJECTED`, `SUSPENDED` → 403 `ADMIN_SUSPENDED`, `APPROVED`만 발급. 비밀번호를 모르면 계정 존재·상태 어느 것도 알 수 없다. 상태를 숨기고 401로 묶지 않는 이유: 정당한 사용자가 원인(승인 대기·거절·중지)을 알아야 MASTER에게 문의할 수 있고, 비밀번호를 아는 사람에게만 보인다.

7. **감사: `updated_by`에 처리한 MASTER의 adminId가 남는다.** 엔티티를 읽어 바꾸는(dirty checking) 경로이고, 요청의 principal이 MASTER adminId(`UUID`)라 `SecurityAuditorAware`가 그 문자열을 넣는다. 코드 추가는 없고 테스트로 고정한다.

8. **마이그레이션 없음.** 결정 1의 새 값은 기존 `VARCHAR(20)` 컬럼에 들어가고 CHECK 제약이 없다. 목록 정렬용 인덱스도 두지 않는다(어드민은 수십 건 규모).

9. **문서**: `docs/admin-api-guide.md`의 "아직 없는 것" 절을 실제 API 설명으로 바꾸고, `docs/openapi-admin.yaml`을 다시 만든다. 사용자 스펙 `docs/openapi.yaml`은 바뀌지 않는다. README의 MASTER 절차는 바꿀 것이 없다(MASTER는 여전히 DB로만 만든다).

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-183` (브랜치 `feat/SHI-183-admin-approval`, 베이스 `7bfb80b` = origin/develop, SHI-182 #67 머지 포함)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 다른 워크트리는 지금 없다
- `snsg/.env`는 복사돼 있다(`ADMIN_JWT_SECRET` 포함 여부는 확인하지 않았다. 테스트 컨텍스트가 `ADMIN_JWT_SECRET` 자리표시자 해석 실패로 뜨지 않으면 코드를 고치지 말고 멈춰서 보고한다. 값을 화면·로그에 찍지 않는다)
- DB·Redis 테스트는 로컬 Docker 컨테이너(`snsg-postgres`, `snsg-redis`)에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 테스트·`bootRun`은 오케스트레이터가 허락한 시점에만 돌린다. 이 작업은 마이그레이션을 더하지 않는다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. `&&`는 PowerShell에서 동작하지 않는다. 아래 검증 명령은 Bash 기준이고 워크트리 루트(`C:\Users\ks\nss\dev\snsg-be\wt-SHI-183`)에서 시작한다
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다

## GOAL

1. 상태 값이 넷이다(`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`). 마이그레이션 파일을 더하거나 고치지 않는다.
   - 검증: `grep -cE "^\s+(PENDING|APPROVED|REJECTED|SUSPENDED),?\s*$" snsg/src/main/java/com/shineunsigan/snsg/admin/domain/constants/AdminStatus.java` → `4`
   - 검증: `git diff --name-only origin/develop -- snsg/src/main/resources/db/migration; git status --short -- snsg/src/main/resources/db/migration` → 출력 없음

2. 상태 전이 규칙(결정 1·2). 순수 JUnit domain 테스트가 단정한다.
   - 승인 `PENDING`→`APPROVED`, 거절 `PENDING`→`REJECTED`, 이용 중지 `APPROVED`→`SUSPENDED`, 해제 `SUSPENDED`→`APPROVED`
   - 각 동작을 허용된 출발 상태가 아닌 나머지 세 상태에서 부르면 `ADMIN_STATUS_CONFLICT`, 상태는 그대로(같은 동작 반복 포함)
   - 대상의 역할이 `MASTER`면 네 동작 모두 `MASTER_NOT_MODIFIABLE`, 상태는 그대로
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우를 다루는 테스트 포함

3. 상태 변경 흐름(Mockito, application).
   - 승인·거절·중지·해제는 대상을 잠금 조회로 읽는다(잠금 없는 조회를 먼저 부르지 않는다). 대상이 없으면 `ADMIN_NOT_FOUND`
   - 이용 중지 성공 → 그 대상 adminId로 `AdminRefreshTokenStorePort.deleteAll`이 한 번 불린다. 중지가 예외(`ADMIN_NOT_FOUND`·`MASTER_NOT_MODIFIABLE`·`ADMIN_STATUS_CONFLICT`)로 끝나면 `deleteAll`이 불리지 않는다
   - 승인·거절·해제는 세션 저장소를 부르지 않는다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

4. 동시 처리와 감사(실제 PostgreSQL, `@DataJpaTest`·`replace = NONE`, `.claude/rules/testing.md`의 잠금·감사 항목 방식).
   - 같은 `PENDING` 어드민에 승인과 거절을 두 스레드로 동시에 부르면(테스트 트랜잭션 끔) 정확히 하나만 성공하고 다른 하나는 `ADMIN_STATUS_CONFLICT`다. DB에 커밋된 상태는 성공한 쪽의 도착 상태다
   - SecurityContext에 MASTER adminId(`UUID`) principal을 넣고 승인하면, 커밋·flush 뒤 native SELECT한 `updated_by`가 그 adminId 문자열이고 `created_by`는 그대로다
   - 테스트 행은 무작위 아이디로 넣고 `@AfterEach`에서 넣은 id로만 지운다(공유 DB)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

5. 목록 조회(결정 2).
   - repository(실제 PostgreSQL): 상태 필터를 주면 그 상태만, 생략하면 모든 상태가 나온다. 삭제된 행은 나오지 않는다. (`createdAt`, id) 오름차순이고 커서 뒤부터 나온다. size + 1을 읽어 다음 페이지 유무를 정한다. 공유 DB라 넣은 행만 단정한다(`.claude/rules/testing.md` — 예: 과거 고정 `created_at`으로 native INSERT하고 그 직전 커서로 읽어 넣은 행의 포함 여부와 서로의 순서만 본다)
   - 커서: 인코딩한 값을 다시 해석하면 같고, 해석할 수 없는 값(Base64 아님·구분자 없음·날짜/UUID 형식 아님)은 `INVALID_CURSOR`. 문자는 `[A-Za-z0-9_-]`뿐
   - size: 생략하면 기본값, 상한 초과는 상한, 1 미만은 1(설정값에서 읽는다. 테스트에서 값을 바꿔 넣어 확인)
   - 응답 항목에 `id, loginId, name, team, birthDate, role, status, createdAt`이 있고 `passwordHash`·`createdBy`·`updatedBy`가 없다. 마지막 페이지의 `nextCursor`는 `null`
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함
   - 검증: `grep -nE "page-size-(default|max)" snsg/src/main/resources/application.yaml` → 기존 schedule 두 줄과 함께 어드민 목록용 두 줄(20, 50)이 더 나온다

6. 로그인 상태별 응답(결정 6, Mockito).
   - 비밀번호가 맞고 `REJECTED` → `ADMIN_REJECTED`, `SUSPENDED` → `ADMIN_SUSPENDED`, `PENDING` → `ADMIN_PENDING_APPROVAL`(기존). 셋 모두 토큰을 발급하지 않고 실패 횟수를 초기화한다
   - 비밀번호가 틀리면 상태와 관계없이 `INVALID_ADMIN_CREDENTIALS`(상태가 드러나지 않는다)
   - 재발급: 어드민이 `REJECTED`·`SUSPENDED`이면 그 어드민의 세션 모두 삭제 + `INVALID_ADMIN_REFRESH_TOKEN`
   - 기존 `AdminLoginFacadeTest`·`AdminReissueTokenFacadeTest`의 단정은 그대로 통과한다(승인 여부를 나타내는 test fixture 생성 방식만 바뀌는 것은 허용)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

7. 인가 경계. `AdminSecurityConfigTest`(두 보안 설정·두 필터·진입점을 함께 올리는 기존 구성)에 더한다. 토큰 해석 포트와 `AdminQueryService`(역할 조회)·대상 컨트롤러의 서비스·facade만 mock이다.
   - 토큰 없이 `GET /api/admin/v1/admins`, `POST /api/admin/v1/admins/{id}/approval`·`/rejection`·`/suspension`, `DELETE /api/admin/v1/admins/{id}/suspension` → 401 `UNAUTHENTICATED`
   - 사용자 access 토큰으로 위 경로 → 401 `UNAUTHENTICATED`
   - 승인된 `ADMIN` 역할 어드민 토큰으로 위 경로 → **403, 본문 `code` = `MASTER_ONLY`**, 서비스·facade가 불리지 않는다. 같은 토큰으로 `GET /api/admin/v1/admins/me` → 200(기존과 같다)
   - 승인된 `MASTER` 토큰으로 위 경로 → 2xx, 서비스·facade가 **경로의 adminId로** 불린다
   - 어드민 토큰이지만 `findApprovedRole`이 빈 값(거절·중지·승인 대기·없음) → 401 `UNAUTHENTICATED`
   - 기존 `auth/infrastructure/security/SecurityConfigTest`도 통과한다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*' --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

8. API 요청·응답(`@WebMvcTest`, 서비스·facade mock, 필터 끔 — `.claude/rules/testing.md`의 어드민 컨트롤러 테스트 방식).
   - 승인·거절·중지·해제 성공 → 200, `{"isSuccess":true}`, data 없음. `ADMIN_NOT_FOUND` 404, `MASTER_NOT_MODIFIABLE` 403, `ADMIN_STATUS_CONFLICT` 409 전달
   - 경로의 adminId가 UUID가 아니면 400 `INVALID_REQUEST`, 서비스·facade가 불리지 않는다
   - 목록: `status`가 목록 밖 값이면 400 `INVALID_REQUEST`. `status` 생략이면 필터 없이(null) 넘어간다. `INVALID_CURSOR` 400 전달
   - 검증: `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.admin.*'` → BUILD SUCCESSFUL, 실패 0, 위 경우 포함

9. OpenAPI.
   - 사용자 스펙은 바뀌지 않았다. 검증: `git diff --quiet origin/develop -- docs/openapi.yaml; echo $?` → `0`
   - 어드민 스펙의 경로가 기존 넷과 새 넷이다. 검증: `grep -E "^  /" docs/openapi-admin.yaml | sort` → 정확히 다음 여덟 줄(순서 무관): `/api/admin/v1/auth/tokens:`, `/api/admin/v1/auth/signup:`, `/api/admin/v1/auth/login:`, `/api/admin/v1/admins/me:`, `/api/admin/v1/admins:`, `/api/admin/v1/admins/{adminId}/approval:`, `/api/admin/v1/admins/{adminId}/rejection:`, `/api/admin/v1/admins/{adminId}/suspension:` (앞의 공백 둘 포함)
   - 새 코드와 상태 값이 어드민 스펙에 나온다. 검증: `for c in MASTER_ONLY ADMIN_NOT_FOUND MASTER_NOT_MODIFIABLE ADMIN_STATUS_CONFLICT INVALID_CURSOR ADMIN_REJECTED ADMIN_SUSPENDED REJECTED SUSPENDED; do grep -q "$c" docs/openapi-admin.yaml || echo "MISSING $c"; done` → 출력 없음
   - 어드민 스펙에 비밀번호 해시 필드가 없다. 검증: `grep -ci "passwordHash" docs/openapi-admin.yaml` → `0`
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest' --tests '*OpenApiConfigTest'` → BUILD SUCCESSFUL (스펙은 `OPENAPI_UPDATE=true`로 다시 만든 뒤 이 명령이 통과해야 한다)

10. FE 문서 `docs/admin-api-guide.md`.
    - "아직 없는 것" 절의 승인·거절·이용 중지·대기 목록 문장이 사라지고, MASTER 전용 API 절이 생긴다: 다섯 엔드포인트, 목록의 `status`·`cursor`·`size`·정렬·항목 필드·빈 결과, 상태 네 값의 뜻과 화면 표시, 상태 전이 표(해제 포함, 거절은 되돌릴 수 없고 거절된 아이디는 다시 쓸 수 없다는 것), MASTER는 대상이 될 수 없다는 것(자기 자신 포함), 이용 중지하면 그 어드민은 다음 요청부터 401이고 재발급도 안 되며 해제해도 다시 로그인해야 한다는 것, MASTER 메뉴는 `me`의 `role`이 `MASTER`일 때만 그리되 실제 검사는 BE(ADMIN이 부르면 403 `MASTER_ONLY`)
    - 로그인 절과 에러 표에 `ADMIN_REJECTED`·`ADMIN_SUSPENDED`(비밀번호가 맞은 뒤에만), 에러 표에 결정 3의 나머지 코드(HTTP·code·조건·화면에서 할 일)와 예시 JSON
    - 화면별 API 표에 가입 승인 관리 화면(목록 → 승인·거절·중지·해제 → 목록 다시 부르기)과 각 호출의 "다시 보내도 되나"(성공한 상태 변경을 다시 보내면 409 `ADMIN_STATUS_CONFLICT`이니 응답 전까지 버튼을 막고, 409면 목록을 다시 부른다)
    - 로컬 개발 절의 "승인 API가 아직 없어 BE가 DB에서 직접 승인한다"를 "MASTER 계정으로 승인한다(MASTER 계정은 BE에 요청)"로 바꾼다
    - 검증: `for s in "/api/admin/v1/admins/{adminId}/approval" "/api/admin/v1/admins/{adminId}/rejection" "/api/admin/v1/admins/{adminId}/suspension" MASTER_ONLY ADMIN_NOT_FOUND MASTER_NOT_MODIFIABLE ADMIN_STATUS_CONFLICT INVALID_CURSOR ADMIN_REJECTED ADMIN_SUSPENDED REJECTED SUSPENDED nextCursor; do grep -qF -- "$s" docs/admin-api-guide.md || echo "MISSING $s"; done` → 출력 없음
    - 검증: `grep -cE "GET /api/admin/v1/admins([^/]|$)" docs/admin-api-guide.md` → 1 이상(`/admins/me`가 아닌 목록 경로)
    - 검증: `grep -nE "아직 없어|다음 작업에서 생긴다" docs/admin-api-guide.md` → 승인·거절·이용 중지에 관한 줄이 없다(장소 수정 등 다른 API가 나중에 생긴다는 문장은 남아도 된다)

11. 전체 빌드가 통과한다(`ArchitectureTest` 포함: 새 facade에 `@Transactional`이 없고, Command·Query 서비스가 이벤트 포트 아닌 `port/out`에 의존하지 않고, admin이 다른 도메인을 import하지 않는다).
    - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
    - 검증: `grep -rlE "import com\.shineunsigan\.snsg\.(auth|user|schedule|place)\." snsg/src/main/java/com/shineunsigan/snsg/admin` → 출력 없음, `grep -rl "import com.shineunsigan.snsg.admin." snsg/src/main/java | grep -v "/snsg/admin/"` → 출력 없음

12. 범위 밖 파일을 고치지 않는다.
    - 검증: `git diff --staged --name-only | grep -vE '^(snsg/src/main/java/com/shineunsigan/snsg/admin/|snsg/src/test/java/com/shineunsigan/snsg/admin/|snsg/src/main/resources/application\.yaml$|docs/(admin-api-guide\.md|openapi-admin\.yaml)$|\.claude/tasks/SHI-183/task\.md$)'` → 출력 없음
    - 검증: `git diff --staged --name-only | grep -E '(^|/)\.env$'` → 출력 없음

## 비목표

- 거절 취소(`REJECTED` → 다른 상태), 어드민 삭제·탈퇴, 역할 변경(ADMIN ↔ MASTER), MASTER 생성·관리 API, 비밀번호 변경·초기화, 어드민 정보 수정, 어드민 단건 조회 API는 만들지 않는다
- 상태 변경 이력 테이블, 거절·중지 사유 입력은 만들지 않는다(요구 없음. 누가 마지막으로 바꿨는지는 `updated_by`)
- 중지 전에 발급된 access token을 해제 후까지 막는 장치(토큰 발급 시각 비교 등)는 두지 않는다(결정 4의 남는 틈)
- 가입 남용 방지(횟수 제한·캡차), IP 제한은 하지 않는다
- 어드민 장소 수정(SHI-184), `places`의 `BaseAuditEntity` 전환은 하지 않는다
- user·auth·schedule·place 도메인 코드, `common/**`(`GlobalExceptionHandler`·`ErrorType`·`CommonErrorCode`·`SecurityAuditorAware`·`CorsConfig`·`OpenApiConfig` 포함), 기존 마이그레이션, `README.md`, `docs/openapi.yaml`, `docs/api-guide.md`, `snsg/build.gradle`, `snsg/.env.example`은 고치지 않는다
- 기존 테스트의 단정을 고치지 않는다. `AdminLoginFacadeTest`·`AdminReissueTokenFacadeTest`·`AdminSecurityConfigTest`·`AdminControllerTest`는 컨텍스트·fixture를 맞추는 데 필요한 변경(`@MockitoBean`·`@Import` 추가, 승인 여부 fixture를 상태 값으로 바꾸기)만 허용한다
- `CLAUDE.md`, `.claude/rules/*`, `.claude/skills/*`는 고치지 않는다(문서 최신화 단계 몫)
- 판단이 갈리는 경우
  - 사용자가 해제를 빼기로 하면(결정 1의 질문) 해제 전이·엔드포인트·테스트·문서를 만들지 않고, GOAL 9의 경로는 그대로 여덟 줄이다(`/suspension`은 중지 때문에 남는다)
  - `hasRole` 매처로 `me`와 MASTER 경로를 나누다 기존 `AdminSecurityConfigTest`의 단정이 깨지면 단정을 고치지 말고 멈춰서 보고한다
  - 403 봉투를 `AccessDeniedHandler`가 아니라 다른 방법(예: 서비스에서 역할 확인)으로 내고 싶어지면, GOAL 7의 결과(403 `MASTER_ONLY`, 서비스·facade 미호출)가 같아야 한다. 같게 만들 수 없으면 멈추고 보고한다
  - 잠금 조회에 연관을 fetch join하지 않는다(어드민은 연관이 없다)

## 제약

- 패키지·계층·Service와 Facade·포트·DTO·Entity·삭제·잠금·목록 쿼리·설정·보안 체인 규칙은 `.claude/rules/package-structure.md`를 따른다(특히 "Service와 Facade", "동시 요청과 잠금", "목록 쿼리 (JPQL)", "보안 체인 (Spring Security)")
- API·인증 주체·목록·응답·문서 규칙은 `.claude/rules/api.md`를 따른다(목록 절, 어드민 경로 인증 주체, 문서 절의 스펙 재생성과 같은 커밋)
- 테스트는 `.claude/rules/testing.md`를 따른다(잠금 동시성 테스트, 감사 컬럼 테스트, 공유 DB에서 넣은 행만 단정, `AdminSecurityConfigTest` 구성, 어드민 컨트롤러 테스트 방식, BCrypt 해시 리터럴 금지). 테스트는 원래 코드와 별도 커밋이다. DB 슬라이스 구성이 늘면 `./gradlew cleanTest build`로 전체를 다시 돌린다
- `docs/openapi-admin.yaml`은 API를 바꾼 코드와 같은 커밋에 넣는다
- 비밀값(`.env` 값, 비밀번호, 해시)을 보고·로그·문서·커밋에 남기지 않는다
- 임시 파일은 세션 스크래치패드에 둔다(`.claude/rules/temp-files.md`). 워크트리에 남기지 않는다
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 제안(구현이 바꿔도 된다)
  - domain: `Admin`에 `approve()`·`reject()`·`suspend()`·`unsuspend()`(또는 `resume()`). 공통으로 MASTER 확인 → 출발 상태 확인. `isApproved()`는 그대로 둔다
  - `AdminRepository.findByIdForUpdate(UUID)`(`AdminJpaRepository`에 `@Lock(PESSIMISTIC_WRITE)` + `isDeleted = false` JPQL, 내장 `findById`와 다른 이름), 목록 `findAll(AdminStatus statusOrNull, AdminCursor cursorOrNull, int limit)`(JPQL 고정 조각 이어 붙이기, `setMaxResults(size + 1)`)
  - 커서: `admin/domain/model/AdminCursor(LocalDateTime createdAt, UUID id)` — `ScheduleCursor`와 같은 형식. 다른 도메인 클래스는 import할 수 없으므로 같은 모양을 admin에 따로 둔다(도메인 간 중복 허용)
  - application: `AdminStatusCommandService`(또는 `AdminCommandService`에 메서드 추가 — 150줄·의존 4개 기준을 넘지 않으면 기존 서비스에 둬도 된다)의 `approve`·`reject`·`suspend`·`unsuspend(UUID adminId)`, `SuspendAdminFacade`(서비스 `suspend` → `deleteAll`), `AdminQueryService.list(...)`, 결과 `AdminSummaryResult`·`AdminPageResult`
  - `AdminCredentialResult`의 `boolean approved`를 `AdminStatus status`로 바꾸고 로그인 facade가 상태별로 코드를 고른다(`default` 없는 switch). 이 record는 application 밖으로 나가지 않는다
  - presentation: `AdminController`에 목록·승인·거절·중지·해제를 더한다(`@Tag(name = "어드민")`). 목록은 쿼리 파라미터만이라 Request DTO 없이 `@RequestParam(required = false) AdminStatus status, String cursor, Integer size`. 응답 `AdminListResponse`(`items`, `nextCursor`), `AdminSummaryResponse`
  - 보안: `AdminSecurityConfig`의 매처 순서 — 공개 셋 → `.requestMatchers(HttpMethod.GET, "/api/admin/v1/admins/me").authenticated()` → `.requestMatchers("/api/admin/v1/admins", "/api/admin/v1/admins/**").hasRole("MASTER")` → `anyRequest().authenticated()`. 순서가 중요하다는 주석. `exceptionHandling`에 `accessDeniedHandler(new/주입 AdminAccessDeniedHandler)` — `AdminAuthenticationEntryPoint`와 같은 봉투 쓰기로 `ApiResponse.error(AdminErrorCode.MASTER_ONLY)`. `@Component`로 두면 `AdminSecurityConfigTest`·`AdminAuthControllerTest` 등 슬라이스의 `@Import`에 더해야 한다
  - 설정 예: `admin.page-size-default: 20`, `admin.page-size-max: 50`을 `AdminProperties`에 더한다(prefix `admin`, 기존 record)
  - 동시성 테스트: 클래스(또는 `@Nested`)에 `@Transactional(propagation = NOT_SUPPORTED)`, `CountDownLatch` 둘. 각 스레드에서 SecurityContext에 MASTER principal을 넣고 끝나면 `clearContext()`(SecurityContext는 스레드마다 따로다). 테스트 행은 native INSERT 또는 `Admin.signUp`으로 무작위 아이디(`a` + 무작위 소문자·숫자)
- 목록 repository 테스트에서 `created_at`을 과거 고정 값으로 두려면 native INSERT가 필요하다(`@CreatedDate`가 덮는다). 이때 `password_hash`는 해시 리터럴이 아닌 `"stored-hash"` 같은 문자열을 쓴다
- 문서 최신화 단계(agent4)에 넘길 후보: `.claude/rules/api.md` 어드민 경로 절(MASTER 전용 경로와 `hasRole`·403 `MASTER_ONLY` 봉투, 공개·인증·MASTER 세 층), `.claude/rules/package-structure.md` 보안 체인 절(상태 넷과 전이, 중지 시 세션 삭제를 facade로 한 이유, 잠금 조회), `.claude/rules/testing.md`의 `AdminSecurityConfigTest` 항목(역할별 403 단정)

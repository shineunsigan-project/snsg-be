## 배경

- Linear SHI-178 "참여자 명단". 기능 명세(저장소 밖 `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-14를 구현한다. 관련 명세 항목은 API-S08(`GET /api/v1/schedules/{scheduleId}/participants`), POL-S14(명단 조회 권한, 결정 D3), POL-U12(탈퇴 표시), POL-S16(삭제된 일정 404), 오류 코드 장의 `PARTICIPANT_LIST_FORBIDDEN`(FORBIDDEN)이다.
- 명세 완료 조건: 참여하지 않은 사람과 참여를 취소한 사람은 403 / 개설자 먼저, 그 뒤 created_at 순 / isHost·isMe가 정확하다 / 이름·이메일·생년월일 필드가 없다.
- 이미 머지된 선행 작업
  - T-12 (#60): 일정 상세. `GetScheduleDetailFacade`, `ScheduleQueryService.findDetail`, `Schedule.viewerRole`·`confirmedUserIds`, `ScheduleRepository.findById`(삭제되지 않은 일정, 잠금 없음), `UserPort.findProfiles` → `Map<UUID, ScheduleUserProfileResult>`(입력의 서로 다른 id마다 항목이 정확히 하나. 탈퇴자·행이 없는 id도 가린 값), `ScheduleDetailResponse.AgeGroup`.
  - T-09 (#58): user `FindUserProfilesUseCase`. 이름·이메일·생년월일은 담지 않는다.
  - T-13 (#54): 참여·참여 취소. 참여할 때마다 새 행을 만든다(`Schedule.join` 주석 "취소 후 다시 참여해도 이전 행을 되살리지 않고 새 행을 만든다", 명세 schedule_participants 절 "참여 일시 = created_at … 참여할 때마다 새 행을 만든다").
- 사용자 지시: "면밀히 검토하면서 추천안대로 진행". 아래 결정은 spec 단계에서 근거를 찾아 정한 추천안이다.

### 결정

1. **403 판정 대상은 개설자와 확정(CONFIRMED·삭제 안 됨) 참여자다.** 기존 `Schedule.viewerRole`이 `HOST`·`PARTICIPANT`를 주는 사람과 같다. 참여를 취소한 사람(행이 CANCELED만 있음)과 참여한 적 없는 사람은 403 `PARTICIPANT_LIST_FORBIDDEN`. 근거: POL-S14 "확정 참여자(MEMBER)와 개설자만", D3, T-12의 viewerRole 판정과 같은 기준을 쓰라는 명세 순서표("상세와 같은 프로필 조회·권한 판정").
2. **일정 상태로는 막지 않는다.** 취소(CANCELED)·완료(COMPLETED)·모집 마감 일정도 개설자·확정 참여자는 명단을 본다. 근거: POL-S14·D3에 상태 조건이 없다. T-15 완료 조건이 "일정을 취소해도 참여자 행은 CONFIRMED 유지"라 취소된 일정에서도 확정 참여자가 그대로 정해진다. 반대로 상태 조건을 넣을 근거는 명세에 없다.
3. **판정 순서는 SCHEDULE_NOT_FOUND(404) → PARTICIPANT_LIST_FORBIDDEN(403)이다.** 없거나 삭제된 일정은 권한과 관계없이 404(POL-S16 "모든 조회에서 404"). 일정 존재는 상세가 공개라 403/404 차이로 새는 정보가 없다.
4. **오류 코드는 새로 `ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN(ErrorType.FORBIDDEN, …)`을 더한다.** 기존 코드 중 `NOT_PARTICIPANT`는 CONFLICT(409)이고 참여 취소 전용 의미라 쓰지 않는다. 명세 오류 코드 장이 이 이름과 타입을 정해 두었다. 메시지는 화면 명세 문구가 저장소에 없어 "참여한 일정만 참여자 명단을 볼 수 있어요."로 둔다(구현이 문구를 다듬어도 된다. code가 FE 분기 기준이다).
5. **정렬: 개설자 행 먼저, 그 뒤 참여 행의 created_at 오름차순, 같으면 행 id 오름차순.** 재참여한 사람은 새 행의 created_at(다시 참여한 시각) 자리에 온다. 취소된 옛 행은 명단에 없다. 개설자는 created_at과 관계없이 맨 앞이다(개설자 행은 일정과 함께 만들어져 실제로도 가장 이르지만, 명세가 "개설자 먼저"를 따로 적었으므로 역할로 정한다).
   - 정렬은 schedule 도메인(애그리거트 루트가 확정 참여 행을 정렬해 돌려주는 메서드)에서 한다. 명단은 정원(최대 24명) 안이라 페이지가 없다. `.claude/rules/package-structure.md` 목록 쿼리 절의 "정렬은 DB에서만"은 keyset 페이지 경계의 중복·누락을 막는 규칙이고, 이 명단에는 페이지·커서가 없어 해당하지 않는다. id는 created_at이 같을 때 순서를 매 요청 같게 하려는 보조 키일 뿐이다(같은 일정의 참여는 행 잠금으로 직렬화돼 created_at이 같을 일이 앱 경로로는 없다).
6. **응답 `data`는 항목 배열이다(`ApiResponse<List<…>>`).** 커서 페이지(`{items, nextCursor}`)로 감싸지 않는다. 근거: 정원 상한이 있어 페이지가 필요 없고, 페이지 없는 기존 목록(`GET /api/v1/activity-categories`, `GET /api/v1/place-candidates`)이 배열을 준다.
7. **항목 필드는 명세 그대로 `nickname, profileImageUrl, gender, ageGroup, isHost, isMe, withdrawn`이다.** 사용자 id는 내보내지 않는다(명세에 없다). `gender`는 사용자 정보를 찾을 수 없는 경우 null, `ageGroup`은 탈퇴자·찾을 수 없는 경우 null(T-12와 같은 계약). `isMe`는 항목의 사용자 id가 보는 사람 id와 같을 때 true.
8. **보는 사람의 활성 여부(`UserPort.isActive`)는 확인하지 않는다.** 상세(T-12)와 같게, 유효한 토큰의 userId로 권한을 정한다. POL-S14에 활성 조건이 없고, 탈퇴는 남은 일정이 있으면 막히며(T-18) 탈퇴자의 access token은 짧게 산다.
9. **SecurityConfig는 고치지 않는다.** `GET /api/v1/schedules/{scheduleId}`의 permitAll 패턴은 한 단계 경로 변수라 `/{scheduleId}/participants`에 맞지 않고, 이 경로는 `anyRequest().authenticated()`에 걸려 토큰 없이 부르면 401이다(`.claude/rules/api.md` 인증 주체 절). 이것을 `SecurityConfigTest`로 단정만 더한다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-178` (브랜치 `feat/SHI-178-schedule-participants`, 베이스 `58ff8d6`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다
- `snsg/.env`는 복사돼 있다
- DB·Redis 테스트는 로컬 Docker 컨테이너(`snsg-postgres`, `snsg-redis`)에 붙는다. 꺼져 있으면 실패한다(skip이 아니다). 그때는 코드를 고치지 말고 멈춰서 보고한다
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 다른 워크트리가 테스트 중일 수 있으니 테스트는 오케스트레이터가 정한 시점에만 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의")
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. `&&`는 PowerShell에서 동작하지 않는다. 아래 검증 명령은 Bash 기준이고 워크트리 루트(`C:\Users\ks\nss\dev\snsg-be\wt-SHI-178`)에서 시작한다
- 이번 작업은 마이그레이션을 더하지 않는다(V1~V9 그대로)
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다

## GOAL

1. `GET /api/v1/schedules/{scheduleId}/participants`가 인증된 개설자·확정 참여자에게 200과 명단을 준다. `data`는 배열이고 항목 필드는 정확히 `nickname, profileImageUrl, gender, ageGroup, isHost, isMe, withdrawn`이다. `ageGroup`은 상세의 연령대와 같은 정보(decade, phase)다. 이름·이메일·생년월일·사용자 id 필드는 없다.
   - 검증: 8번 `OpenApiSpecTest` 통과로 응답 스키마가 `docs/openapi.yaml`과 일치함을 확인한다
   - 검증: `grep -n "/api/v1/schedules/{scheduleId}/participants:" -A 40 docs/openapi.yaml | grep -n "get:"` → 한 줄 이상(GET 연산이 있다)
   - 검증: 명단 항목 스키마(구현이 정한 이름)의 `properties`를 `docs/openapi.yaml`에서 읽어 위 일곱 필드만 있고 `name`·`email`·`birthDate`·`userId`·`id`가 없음을 확인한다

2. 명단 권한·정렬은 schedule 도메인에 있고, domain 단위 테스트(Spring·mock 없이)가 아래를 단정한다.
   - 개설자와 확정(CONFIRMED) 멤버는 통과한다. 참여를 취소한 사람(CANCELED 행만 있음), 참여한 적 없는 사람은 `BusinessException(ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN)`
   - 일정 상태가 `CANCELED`·`COMPLETED`여도 개설자·확정 멤버는 통과한다
   - 명단 순서: 개설자가 맨 앞(개설자 행의 created_at을 멤버보다 늦게 둬도 맨 앞), 그 뒤 멤버를 created_at 오름차순. 취소된 행과 삭제된 행은 빠진다. 참여 취소 후 다시 참여한 사람은 한 번만 나오고 새 행의 created_at 자리에 온다
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.domain*'` → BUILD SUCCESSFUL, 실패 0, 위 경우를 다루는 테스트가 실행됨

3. 명단 흐름(facade)을 Mockito 테스트가 단정한다.
   - `UserPort.findProfiles`를 요청당 **한 번**, 명단의 모든 사용자 id로 부른다
   - 항목 순서는 도메인이 정한 순서 그대로다
   - `isHost`는 개설자 항목만 true, `isMe`는 보는 사람 항목만 true(보는 사람이 개설자면 개설자 항목이 둘 다 true)
   - 탈퇴자 항목은 user가 준 가린 값(nickname "탈퇴한 사용자", 기본 이미지, ageGroup null, 성별 유지, `withdrawn = true`)을 그대로 쓴다
   - 일정이 없으면 `SCHEDULE_NOT_FOUND`, 권한이 없으면 `PARTICIPANT_LIST_FORBIDDEN`이 나고, 두 경우 모두 `UserPort`를 부르지 않는다
   - facade에는 `@Transactional`이 없다(ArchUnit이 검사한다)
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*schedule.application*'` → BUILD SUCCESSFUL, 실패 0

4. 없는 일정과 삭제된(`is_deleted = true`) 일정은 404 `SCHEDULE_NOT_FOUND`다. 일정 읽기는 T-12의 삭제 조건을 명시한 잠금 없는 조회(`ScheduleRepository.findById`)를 쓴다(이미 `ScheduleRepositoryImplTest`가 삭제된 행을 돌려주지 않음을 단정한다). 권한이 없으면 403 `PARTICIPANT_LIST_FORBIDDEN`이다.
   - 검증: `cd snsg && grep -n "PARTICIPANT_LIST_FORBIDDEN(ErrorType.FORBIDDEN" src/main/java/com/shineunsigan/snsg/schedule/domain/exception/ScheduleErrorCode.java` → 한 줄
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0

5. 컨트롤러가 인증 주체의 userId를 facade에 넘기고 배열 응답을 준다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest'` → BUILD SUCCESSFUL, 실패 0. 인증한 userId가 facade에 넘어가는 것과 `$.data[0]`에 `isHost`·`isMe`·`withdrawn`가 나가고 `name`·`email`·`birthDate`가 없는 것을 단정하는 테스트 포함

6. 명단은 인증이 필요하다. `SecurityConfigTest`가 `GET /api/v1/schedules/{무작위 UUID}/participants`를 토큰 없이 부르면 401임을 단정하고, 기존 단정(상세 공개, `/participating`·`/hosted`·`POST /api/v1/schedules` 401 등)은 그대로 통과한다. `SecurityConfig.java`는 바뀌지 않는다.
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `git diff --staged --name-only | grep SecurityConfig.java` → `SecurityConfigTest.java`만(프로덕션 `SecurityConfig.java` 없음)

7. 패키지 규칙과 전체 빌드를 통과한다.
   - schedule의 application·domain·presentation이 user 패키지를 import하지 않는다
   - 검증: `grep -rln "com.shineunsigan.snsg.user" snsg/src/main/java/com/shineunsigan/snsg/schedule` → `schedule/infrastructure/client/` 아래 파일만
   - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0(`ArchitectureTest` 포함)

8. 문서가 코드와 맞는다.
   - `docs/openapi.yaml`을 `OPENAPI_UPDATE=true`로 다시 만들어 명단 API가 들어 있다. 명단 엔드포인트의 springdoc 설명에 부르는 화면(상세에서 명단 펼칠 때, viewerRole이 HOST·PARTICIPANT일 때만), 정렬, 401·403·404·400(UUID 형식) 처리, 재시도 안전을 적는다. 인증이 필요하므로 `@SecurityRequirements`를 달지 않는다
   - `docs/api-guide.md`에 "### 참여자 명단" 절이 있다. api.md 문서 절의 항목(부르는 화면과 시점, 인증 필요, 응답 필드마다 의미·null 여부(`gender`·`ageGroup`), 탈퇴자 표시, 정렬 기준과 재참여 시 위치, 빈 경우가 없다는 것(개설자는 항상 있다), 날 수 있는 에러마다 HTTP·code·조건·화면 처리·예시 JSON(403 `PARTICIPANT_LIST_FORBIDDEN`, 404 `SCHEDULE_NOT_FOUND`, 401), 재시도 안전)을 담는다
   - `docs/api-guide.md` "화면별로 쓰는 API" 표에 명단 행이 있다
   - 검증: `cd snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'` 뒤 `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `grep -n "### 참여자 명단" docs/api-guide.md` → 한 줄
   - 검증: `grep -n "PARTICIPANT_LIST_FORBIDDEN" docs/api-guide.md docs/openapi.yaml` → 두 파일 모두 한 줄 이상
   - 검증: `grep -n 'GET /api/v1/schedules/{scheduleId}/participants`' docs/api-guide.md` → 한 줄 이상(절 머리 또는 화면별 표에 GET 명단 경로가 있다)

9. 워크트리 밖과 무관한 파일을 고치지 않는다. 변경은 스테이징까지만 하고 커밋하지 않는다.
   - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/wt-SHI-178 status --short` → 모든 경로가 `snsg/src/`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/` 아래. `snsg/src/main/resources/db/migration/` 아래 변경 없음
   - 검증: `git -C C:/Users/ks/nss/dev/snsg-be/snsg-be status --short` → 출력 없음(메인 디렉터리 무변경)

- 정지 조건: 위 검증이 모두 기대 결과일 때 끝낸다
- 끝내지 않는 경우: 테스트를 지우거나 기존 단정을 완화해 통과시켰을 때, 컴파일이 깨져 테스트가 돌지 않았을 때, `OpenApiSpecTest`를 `OPENAPI_UPDATE` 없이 다시 돌려 확인하지 않았을 때, 실패가 줄었지만 0이 아닐 때

## 비목표

- `SecurityConfig.java`를 고치지 않는다(결정 9). 만약 테스트에서 명단이 토큰 없이 401이 아니면 SecurityConfig를 고치지 말고 멈춰서 보고한다
- 일정 상세(`GetScheduleDetailFacade`, `ScheduleQueryService.findDetail`, `ScheduleDetailResponse`)의 동작과 응답을 바꾸지 않는다. 공용으로 쓸 메서드를 더하는 것은 괜찮지만 기존 메서드의 결과를 바꾸지 않는다
- `Schedule.viewerRole`·`availableActions`·`join`·`leave`·`confirmedUserIds`의 동작과 기존 테스트의 단정을 바꾸지 않는다. `Schedule.participants`의 매핑(`@OrderBy` 추가 등)도 바꾸지 않는다
- 페이지·커서를 두지 않는다(결정 6)
- 보는 사람의 활성 여부(`UserPort.isActive`)를 확인하지 않는다(결정 8)
- user 도메인 코드(`FindUserProfilesUseCase`, `UserProfileResult` 등)와 `ScheduleUserAdapter`의 변환을 고치지 않는다. 이미 필요한 값이 다 온다
- 일정 전체 취소(T-15), 일정 소통(TALK), 탈퇴 가능 검사(T-18), auth 흐름은 건드리지 않는다
- 명세 파일(`planning/be-spec-mvp.html`)을 고치지 않는다
- 마이그레이션을 더하거나 고치지 않는다. 명단은 기존 컬럼(schedule_participants.created_at, role, status, is_deleted)만 쓴다
- repository에 명단 전용 쿼리를 더하지 않는다. 일정을 `findById`로 읽고 참여 행은 트랜잭션 안에서 지연 로딩으로 읽는다(T-12와 같다)

## 제약

- 패키지 구조·Service와 Facade(타 도메인 호출은 facade, facade에 트랜잭션 없음, 비즈니스 규칙은 엔티티)·Port·도메인 간 참조·삭제 조회 규칙은 `.claude/rules/package-structure.md`를 따른다
- API·인증 주체(`@AuthenticationPrincipal UUID`)·응답 봉투·문서 작성은 `.claude/rules/api.md`를 따른다
- 테스트 계층·방식·DB 공유 주의는 `.claude/rules/testing.md`를 따른다. 컨트롤러 의존이 늘면 `SecurityConfigTest`와 `ScheduleControllerTest`에 새 facade `@MockitoBean`을 함께 더한다
- 테스트는 원래 코드와 별도 커밋이 되도록 스테이징 계획을 보고한다(`.claude/rules/git-workflow.md`, `testing.md`). `docs/openapi.yaml`은 API 코드와 같은 커밋에 둔다(api.md)
- 임시 파일은 세션 스크래치패드에 두고 워크트리에 남기지 않는다(`.claude/rules/temp-files.md`)
- 들여쓰기 4칸, Jackson 3(CLAUDE.md)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현 형태에 대한 제안이다. 결과(GOAL)를 지키면 바꿔도 된다.

- 흐름: `ScheduleController` `@GetMapping("/{scheduleId}/participants")` → `ListParticipantsFacade`(트랜잭션 없음, 명세가 정한 이름) → `ScheduleQueryService`(readOnly 트랜잭션 안에서 `findById` → 없으면 `SCHEDULE_NOT_FOUND` → 도메인 권한 검사 → 정렬된 확정 참여 행을 (userId, isHost) 같은 Result 목록으로 바꾼다. 참여 행은 지연 로딩이라 트랜잭션 밖으로 엔티티를 넘기지 않는다) → `UserPort.findProfiles(전체 id)` 한 번 → 순서대로 항목 Result를 만들고 `isMe`를 붙인다.
- 도메인 메서드 제안(`Schedule`): 권한 검사(예: `validateParticipantListViewable(UUID viewerId)`, 내부에서 `viewerRole`이 `HOST`·`PARTICIPANT`인지 본다)와 정렬된 확정 행(예: `confirmedParticipantsForList()`; `isConfirmed()` 행만, HOST 역할 먼저, 그 뒤 `createdAt`, 같으면 `id`). 기존 `isConfirmed()` 기준을 재사용한다.
- domain 테스트에서 created_at은 Auditing이 채우므로 `ReflectionTestUtils.setField(participant, "createdAt", …)`로 넣는다(기존 `ScheduleTest`가 상태 필드를 같은 방식으로 넣는다). 개설자 행은 `getParticipants()`에서 꺼낸다.
- 이름 제안: `ParticipantListItemResult`(application), `ScheduleParticipantResponse`(presentation, `@Schema(name = …)`로 스키마 이름이 다른 응답과 겹치지 않게). 연령대 응답은 `ScheduleDetailResponse.AgeGroup`을 재사용하거나 같은 모양으로 둔다. 같은 `@Schema(name)`이 두 번 정의되지 않게 한다.
- boolean 컴포넌트 `isHost`·`isMe`는 record 컴포넌트 이름 그대로 JSON 키가 된다(`ApiResponse.isSuccess`와 같다). JSON 키가 `host`·`me`로 바뀌지 않는지 컨트롤러 테스트로 본다.
- `ScheduleUserProfileResult`는 이미 nickname·profileImageUrl·gender·ageGroup·withdrawn을 갖는다. facade는 `profiles.get(id)`를 따로 null 확인하지 않는다(`UserPort.findProfiles` 계약, `.claude/rules/package-structure.md` Service와 Facade 절의 `GetScheduleDetailFacade` 설명과 같다). 이 facade도 같은 계약에 기대므로 package-structure.md의 그 문단에 `ListParticipantsFacade`를 함께 적는 것은 refine-docs 단계 몫이다.
- api-guide "일정 상세" 절에 "viewerRole이 HOST·PARTICIPANT일 때만 명단을 펼칠 수 있다(그 밖은 403)" 한 줄을 더하면 FE가 호출 조건을 찾기 쉽다.
- 403 화면 처리 제안: 정상 흐름에서는 상세의 viewerRole로 막으므로 나지 않는다. 났으면(그 사이 참여 취소 등) 상세를 다시 불러 그린다.
- `testing.md`의 `SecurityConfigTest` 설명 목록(인증 필요 경로 열거)에 명단 경로를 더하는 것은 refine-docs 단계 몫이다.

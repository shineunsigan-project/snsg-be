## 배경

- Linear SHI-148. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 T-08 "활동 카테고리와 목록 API (schedule 신규)"를 구현한다.
- 활동(보드게임·방탈출·산책)은 일정을 분류하는 기준 데이터이고 schedule 도메인이 소유한다. 이번 작업에서 schedule 패키지를 처음 만든다.
- 이 API는 일정 개설 1단계(화면 CRT-01 활동 선택)와 일정 목록의 활동 필터(화면 LIST-03)에서 쓴다. 활동 ID와 활동의 장소 유형은 이후 작업(T-11 장소 후보 API, T-10 일정 개설)에서 쓴다.
- 활동이 고를 수 있는 장소 유형(place_type)은 schedule이 소유하는 enum이다. place 도메인의 `PlaceType`(develop에 있음, 값 BOARD_GAME_CAFE·ESCAPE_ROOM·PARK·UNCLASSIFIED)과 같은 이름의 값을 갖지만 import하지 않는다. 노출되지 않는 UNCLASSIFIED는 schedule 쪽에 없다. 두 enum 사이의 변환(default 없는 switch)은 place를 호출하는 adapter가 생기는 T-11에서 만든다.
- 사용자와 합의한 결정
  - 초기 3행은 모두 활성(`is_active = true`)이다. 순서(`display_order`)는 보드게임 1, 방탈출 2, 산책 3이다.
  - 마이그레이션 번호는 develop의 다음 번호인 V6이다(`.claude/rules/package-structure.md` 마이그레이션 절). 예전의 "명세 표대로 V7, 공유 DB 충돌 감수" 합의는 폐기됐다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-148` (브랜치 `feat/SHI-148-activity-category`, 베이스 `87b3c36`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리 `wt-SHI-153`은 고치지 않는다. `wt-SHI-153`은 마이그레이션이 없는 리팩터링이다.
- `snsg/.env`는 이미 복사돼 있다.
- develop의 마이그레이션은 V1~V5다: `V1__create_user.sql`, `V2__create_region.sql`, `V3__extend_user_profile.sql`, `V4__create_place.sql`, `V5__insert_reviewed_places.sql`. place 도메인(`Region`, `Place`, `PlaceType`, `FindPlaceUseCase` 등)이 이미 있다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다.
- DB(PostgreSQL)·Redis·8080 포트를 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). `@DataJpaTest`, `SnsgApplicationTests`, `OpenApiSpecTest`가 이 로컬 DB에 Flyway를 적용한다. DB가 꺼져 있으면 이 테스트들이 실패한다.
- 베이스에서 빌드·테스트를 미리 돌려보지는 않았다. 이미 깨져 있는 테스트는 알려진 것이 없다.

## GOAL

1. 마이그레이션 `V6__create_activity_category.sql`이 `activity_categories` 테이블과 초기 3행을 만든다.
   - 컬럼: BaseEntity 공통 컬럼(`id`, `created_at`, `updated_at`, `is_deleted`), `code` VARCHAR(30), `name` VARCHAR(20), `place_type` VARCHAR(20), `display_order` INT, `is_active` BOOLEAN. 모두 NOT NULL이다.
   - 초기 3행의 값(code / name / place_type / display_order / is_active)은 다음과 같다.
     - `BOARD_GAME` / 보드게임 / `BOARD_GAME_CAFE` / 1 / true
     - `ESCAPE_ROOM` / 방탈출 / `ESCAPE_ROOM` / 2 / true
     - `WALK` / 산책 / `PARK` / 3 / true
   - 초기 3행의 id는 고정 UUID v7 리터럴이다. 생성 함수(`gen_random_uuid()` 등)를 쓰지 않는다.
   - 검증: `ls /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg/src/main/resources/db/migration` → `V1__create_user.sql`, `V2__create_region.sql`, `V3__extend_user_profile.sql`, `V4__create_place.sql`, `V5__insert_reviewed_places.sql`, `V6__create_activity_category.sql` 여섯 개
   - 검증: `grep -oiE "'[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'" /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg/src/main/resources/db/migration/V6__create_activity_category.sql | sort -u | wc -l` → `3`
   - 검증: `grep -ci "gen_random_uuid\|uuid_generate" /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg/src/main/resources/db/migration/V6__create_activity_category.sql` → `0`
   - 검증: GOAL 3의 repository 테스트가 마이그레이션으로 들어간 3행을 위 순서와 code로 읽어낸다.
2. `code`는 삭제되지 않은 행끼리만 유니크하다(PostgreSQL 부분 유니크 인덱스, `WHERE is_deleted = false`).
   - 검증: 같은 code로 삭제되지 않은 행을 하나 더 넣으면 DB가 거부하고, 삭제된(`is_deleted = true`) 행으로 넣으면 허용하는 `@DataJpaTest`(실제 PostgreSQL)가 있고 통과한다.
3. 활동 목록 조회는 `is_active = true`이고 `is_deleted = false`인 행만 `display_order` 오름차순으로 돌려준다.
   - 검증: 다음을 확인하는 `@DataJpaTest`가 있고 통과한다.
     - 비활성 행과 삭제된 행을 직접 넣어도 결과에서 빠진다.
     - 마이그레이션의 3행은 BOARD_GAME, ESCAPE_ROOM, WALK 순서로 나온다.
4. schedule이 소유하는 장소 유형 enum이 있고, 값은 `BOARD_GAME_CAFE`, `ESCAPE_ROOM`, `PARK` 셋뿐이다. schedule 패키지는 place 패키지를 import하지 않는다.
   - 검증: `grep -rn "com.shineunsigan.snsg.place" /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg/src/main/java/com/shineunsigan/snsg/schedule` → 출력 없음
   - 검증: `grep -rn "UNCLASSIFIED" /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg/src/main/java/com/shineunsigan/snsg/schedule` → 출력 없음
   - 검증: GOAL 3의 테스트가 마이그레이션 3행을 엔티티로 읽어낸다(`ddl-auto: validate`와 enum 매핑이 맞아야 읽힌다).
5. `GET /api/v1/activity-categories`는 토큰 없이 호출할 수 있다.
   - 응답은 `{isSuccess: true, data: [...]}`이다. `data`의 각 원소는 `id`, `code`, `name` 세 필드만 가진다. 장소 유형(`placeType`)은 응답에 없다.
   - 검증: 앱을 띄운 상태에서 `curl -s -w '\n%{http_code}' http://localhost:8080/api/v1/activity-categories` 실행. 기대 결과는 다음과 같다.
     - HTTP 상태가 `200`이다.
     - `data`가 보드게임(BOARD_GAME), 방탈출(ESCAPE_ROOM), 산책(WALK) 순서의 3개 원소다.
     - 각 원소의 키는 `id`, `code`, `name`뿐이다.
   - 앱 기동: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg && ./gradlew bootRun` (확인 후 반드시 멈춘다. 8080은 모든 워크트리가 공유한다)
6. FE용 문서를 갱신한다.
   - `docs/openapi.yaml`에 이 엔드포인트가 들어간다. 커밋된 스펙과 코드가 일치한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg && ./gradlew test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL
   - 검증: `grep -n "activity-categories" /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/docs/openapi.yaml /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/docs/api-guide.md` → 두 파일 모두 한 줄 이상
   - `docs/api-guide.md`에 다음 내용이 들어간다.
     - 활동 목록 절: 인증 불필요, 활성 활동만 서버가 정한 순서로 온다는 것, 응답 필드(`id`, `code`, `name`)의 뜻과 null 여부, 응답이 빈 배열일 수 있다는 것, 다시 보내도 된다는 것
     - "화면별로 쓰는 API" 표: 일정 개설 활동 선택 화면과 일정 목록 활동 필터에서 이 API를 쓴다는 것
7. 전체 빌드와 테스트가 통과한다. 도메인 의존 규칙을 검사하는 ArchUnit 테스트도 포함된다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg && ./gradlew build` → BUILD SUCCESSFUL, 테스트 실패 0
   - 검증: 같은 실행의 테스트 결과에서 GOAL 2·3의 테스트가 skipped가 아니라 실제로 실행됐다.
8. 범위 밖 파일을 고치지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-148 && git status --short`. 기대 결과는 다음과 같다.
     - `snsg/.env`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md`, 기존 마이그레이션(V1~V5), `user/`·`place/` 패키지 파일이 없다.
     - `auth/` 패키지는 `SecurityConfig.java`만 있다.
     - `.claude/tasks/SHI-148/task.md` 외의 임시 파일이 없다.

## 비목표

- 장소 후보 API(T-11), 일정 개설(T-10), 일정 목록 필터(T-16)는 만들지 않는다. 활동을 다른 도메인에 노출하는 port/in(UseCase), place를 부르는 port/out·adapter, schedule enum ↔ place `PlaceType` 변환도 만들지 않는다.
- `schedules` 등 다른 schedule 테이블은 만들지 않는다. place 도메인(regions, places, `PlaceType` 등)은 건드리지 않는다.
- 새 오류 코드(`ScheduleErrorCode`, INVALID_CATEGORY 등)를 만들지 않는다. 이 API에는 실패 분기가 없다. INVALID_CATEGORY는 T-11에서 만든다.
- 활동의 하위 분류(`parent_id`)는 만들지 않는다.
- 활동을 생성·수정·숨기는 API, 엔티티 정적 팩토리, 상태 변경 메서드는 만들지 않는다. 데이터는 마이그레이션과 DB 직접 수정으로만 바뀐다(`.claude/rules/package-structure.md` domain 절의 쓰기 경로 없는 엔티티).
- `.env`, Flyway 설정(`outOfOrder` 등), 기존 마이그레이션 V1~V5는 바꾸지 않는다.
- 로컬 DB를 사용자 확인 없이 초기화하지 않는다. Flyway 검증 오류(예: "Detected applied migration not resolved locally", "Detected resolved migration not applied to database", 체크섬 불일치)를 만나면 그 자리에서 멈추고 오류 메시지 원문을 보고한다.
- 기존 테스트의 단정, 기존 코드의 경고와 스타일은 건드리지 않는다. `api-guide.md`의 기존 절(지역 등)은 고치지 않는다. "일정·알림 API는 아직 없다" 문장만 활동 API를 반영해 필요하면 고친다.

## 제약

- 패키지 구조·의존 방향·Entity·삭제·마이그레이션·코드 작성(메서드 단일 책임) 규칙은 `.claude/rules/package-structure.md`를 따른다.
- API·Controller·SecurityConfig 공개 경로·springdoc 문서화·`docs/` 갱신은 `.claude/rules/api.md`를 따른다. 공개 경로는 HTTP 메서드(GET)와 정확한 경로로만 연다.
- 테스트 범위와 방식은 `.claude/rules/testing.md`를 따른다. 테스트는 원래 코드와 별도 커밋이 되도록 나눠 스테이징 계획을 보고한다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다.
- 커밋은 하지 않는다. 스테이징까지만 한다(`.claude/rules/git-workflow.md` 3번).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 문서 근거
- 명세 `be-spec-mvp.html`에서 볼 곳
  - schedule 도메인 `activity_categories` 표와 그 아래 확장성 설명
  - 장소·활동 API의 `GET /api/v1/activity-categories`(API-S01): 공개, 활성 활동을 display_order 순으로 `[{id, code, name}]`, place_type은 내보내지 않는다
  - DDD 4계층 검토의 "장소 유형 enum은 도메인마다 따로 둔다": place의 PlaceType과 같은 값이어도 import하지 않고 schedule의 adapter에서 default 없는 switch로 바꾼다. UNCLASSIFIED는 schedule 쪽에 없다
  - 서비스 표: `ActivityCategoryQueryService`는 Controller가 직접 부른다
  - 결정 D9: 보드게임 → BOARD_GAME_CAFE, 방탈출 → ESCAPE_ROOM, 산책 → PARK
  - 작업 목록 T-08: 완료 조건은 "활성 활동만 순서대로, place_type은 응답에 없음"
  - 마이그레이션 순서 표의 활동 행은 `V?`(가칭)다. 개발 중에는 develop의 다음 번호를 쓴다고 명세에도 적혀 있다
- `erd.md` 4.3절 activity_categories
  - 조회 조건에 `is_active`와 `is_deleted`를 둘 다 건다
  - 공통 컬럼은 BaseEntity 기준이다
- 엔티티는 `BaseEntity`를 상속한다(Audit 아님). 행을 바꾸는 주체가 DB 직접 수정뿐이라 "누가 바꿨나"를 남길 수 없다. places를 Audit 없이 둔 명세의 논리와 같다.
- 명세 개정 D23은 places(폐업·원천 값·검수 초기 데이터)에 관한 것이라 이번 작업에 직접 영향은 없다. 다만 V5가 고정 UUID v7 초기 데이터의 선례다.

### 구현 형태 제안 (바꿔도 됨)
- 패키지: `com.shineunsigan.snsg.schedule`
  - domain/model: `ActivityCategory` (`place.domain.model.Region`처럼 정적 팩토리 없이 조회 전용)
  - domain/constants: 장소 유형 enum. 예: `ActivityPlaceType`. 엔티티에는 `@Enumerated(EnumType.STRING)`으로 매핑한다
  - domain/repository: `ActivityCategoryRepository`
  - infrastructure/persistence: `ActivityCategoryJpaRepository`, `ActivityCategoryRepositoryImpl`
  - application: `ActivityCategoryQueryService`(읽기 전용)
  - application/dto/result: `ActivityCategoryResult`
  - presentation: `ActivityCategoryController` (`place.presentation.RegionController`가 같은 형태의 공개 목록 API다)
  - presentation/dto/response: `ActivityCategoryResponse`
- `code`는 enum이 아니라 문자열이다. 활동 추가를 배포 없이 데이터로 끝내기 위해 테이블로 둔 것이다.
- 부분 유니크 인덱스 예: `CREATE UNIQUE INDEX uix_activity_categories_code ON activity_categories (code) WHERE is_deleted = FALSE;`
- 고정 UUID v7 예: V5처럼 시각 부분을 작성일로 두고 끝자리만 바꾼다. 버전 자리(세 번째 묶음 첫 글자)는 `7`, variant 자리(네 번째 묶음 첫 글자)는 `8`~`b`여야 GOAL 1의 grep에 걸린다.
- `created_at`·`updated_at`은 V2·V5처럼 `NOW() AT TIME ZONE 'Asia/Seoul'`로 넣는다(시간대는 CLAUDE.md).
- 테스트용 행은 엔티티 팩토리를 만들지 말고 `EntityManager`의 native query로 넣는다. 예시: `snsg/src/test/java/com/shineunsigan/snsg/place/infrastructure/persistence/RegionRepositoryImplTest.java`. 테스트 행의 code는 마이그레이션 3행과 겹치지 않게 한다.
- 공개 경로는 `SecurityConfig`의 `GET /api/v1/regions` 줄 옆에 더한다.
- 공개 엔드포인트 메서드에는 `@SecurityRequirements`를 단다(api.md).
- 스펙 재생성: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-148/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`
- `api-guide.md`의 활동 목록 절은 "지역" 절과 같은 형식으로 쓰면 된다.

### 머지 시 충돌 예상
- 다른 브랜치가 먼저 V6을 머지하면 머지 직전에 이 브랜치의 번호를 올린다(`.claude/rules/package-structure.md` 마이그레이션 절).
- `docs/openapi.yaml`, `docs/api-guide.md`("화면별로 쓰는 API" 표), `SecurityConfig`의 permitAll 줄은 다른 브랜치도 고칠 수 있다. 뒤에 머지하는 쪽이 develop을 받아 충돌을 풀고 스펙을 다시 만든다.

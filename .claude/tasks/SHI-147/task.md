## 배경
- Linear SHI-147 "지역 마스터와 지역 목록 API". 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 작업 T-06(지역 마스터와 지역 목록 API)이다.
- place 도메인을 새로 만들고, 서비스 지역 기준 데이터(regions)와 두 가지 조회를 만든다.
  - 지역 목록 API(명세 API-P01, `GET /api/v1/regions`): 일정 목록 화면의 지역 필터(화면 LIST-03)와 장소 선택 화면의 지역 칩(화면 CRT-02)이 부른다.
  - 다른 도메인용 지역 조회 입구(port/in): 이후 schedule 도메인이 쓴다. 쓰임새는 지역 코드 검증(INVALID_REGION 판정)과 일정 카드·상세의 지역 이름 채우기다.
- 뒤 작업 T-07(장소 마스터와 노출 장소 조회)이 이 테이블의 활성 여부를 본다.
- 사용자와 합의한 결정
  - 초기 3행: 11440 서울특별시 마포구(display_order 1), 11410 서울특별시 서대문구(2), 11200 서울특별시 성동구(3). 셋 다 활성.
  - 마이그레이션 번호는 V2(develop의 다음 번호)다. 머지 직전에 다른 브랜치가 먼저 머지됐으면 그때 번호를 올린다.
  - 테스트는 한 번에 한 워크트리에서만 돌린다. 조율은 오케스트레이터가 한다.

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-147` (브랜치 `feat/SHI-147-region-master`, 베이스 `13d3745`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 기획 문서 `C:\Users\ks\nss\dev\snsg-be\planning\`도 읽기만 한다.
- `snsg/.env`는 복사돼 있다. Docker 컨테이너 `snsg-postgres`와 `snsg-redis`는 떠 있다.
  - 꺼져 있으면 `docker start snsg-postgres snsg-redis`로 켠다.
  - 꺼진 채로 돌리면 DB 연결 테스트가 대거 실패한다.
- 베이스 기준 `./gradlew build`는 테스트 92개, 실패 0이다(인계 문서 기준).
- **DB·Redis·8080 포트는 모든 워크트리가 공유한다.** 지금 같은 베이스에서 `wt-SHI-146`(가입 프로필 확장)과 `wt-SHI-148`(활동 카테고리)이 함께 진행 중이고, 둘 다 마이그레이션을 더할 수 있다.
  - 로컬 DB에 다른 워크트리의 V2가 이미 적용돼 있으면 이 워크트리의 Flyway가 설명·체크섬 불일치로 실패한다.
  - **DB 충돌로 보이는 실패가 나면 멈추고 보고한다.** 증상: Flyway validate 오류, 모르는 테이블이나 버전, `flyway_schema_history` 불일치.
  - 이때 스스로 스키마를 지우거나, 다른 워크트리의 데이터나 `flyway_schema_history`를 고치지 않는다. 스키마 재적용은 오케스트레이터가 판단한다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 기준이다. PowerShell에서는 `&&`가 동작하지 않는다.

## GOAL
1. 새 마이그레이션 V2가 regions 테이블을 만들고 초기 3행을 넣는다.
   - 테이블 구성: code CHAR(5) PK, sido_name VARCHAR(20), sigungu_name VARCHAR(20), display_order INT, is_active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP. 모두 NOT NULL이다.
   - `id`(UUID)와 `is_deleted` 컬럼은 없다. BaseEntity를 상속하지 않는 예외 테이블이라는 명세 결정이다.
   - 검증: `ls /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg/src/main/resources/db/migration` → `V1__create_user.sql`과 `V2__`로 시작하는 파일 하나, 이 둘뿐
   - 검증(앱 기동 또는 테스트로 마이그레이션이 적용된 뒤): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && docker exec snsg-postgres psql -U "$(grep '^DB_USERNAME=' .env | cut -d= -f2)" -d snsg -c "select code, sido_name, sigungu_name, display_order, is_active from regions order by display_order"` → 정확히 3행: `11440 | 서울특별시 | 마포구 | 1 | t`, `11410 | 서울특별시 | 서대문구 | 2 | t`, `11200 | 서울특별시 | 성동구 | 3 | t`
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && docker exec snsg-postgres psql -U "$(grep '^DB_USERNAME=' .env | cut -d= -f2)" -d snsg -c "select column_name, data_type, is_nullable from information_schema.columns where table_name = 'regions' order by ordinal_position"` → 위 7개 컬럼만 있고 모두 `is_nullable = NO`
2. 지역 목록 API `GET /api/v1/regions`는 토큰 없이 200을 주고, 활성 지역만 display_order 오름차순으로 `{code, name}` 배열을 준다. name은 시군구 이름이다.
   - 검증(오케스트레이터가 8080을 비워 준 뒤 이 워크트리에서 앱을 띄운다): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew bootRun`. 기동 후 다른 셸에서 `curl -s -w '\n%{http_code}\n' http://localhost:8080/api/v1/regions` → 본문 `{"isSuccess":true,"data":[{"code":"11440","name":"마포구"},{"code":"11410","name":"서대문구"},{"code":"11200","name":"성동구"}]}`, 상태 `200`. 확인이 끝나면 앱을 멈춘다.
3. 지역 목록 API는 비활성 지역을 빼고, 활성 지역을 display_order 순으로 정렬한다. 행을 넣은 순서와 무관하다. 이를 실제 PostgreSQL에 붙은 테스트가 단정한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.place.*'` → BUILD SUCCESSFUL, 실패 0, skipped 0
   - 그리고 diff의 테스트 가운데 다음을 단정하는 테스트가 있다. 비활성 행을 섞어 넣었을 때 목록에서 빠진다. display_order가 넣은 순서와 다를 때 display_order 순으로 나온다.
4. place 도메인은 다른 도메인용 port/in으로 두 가지 조회를 제공한다.
   - 결과에는 코드, 시도 이름, 시군구 이름, 활성 여부가 담긴다.
   - 지역 코드로 단건 조회
     - 활성 지역이면 결과를 준다(활성 여부 true).
     - 비활성 지역도 결과를 준다(활성 여부 false). 활성 여부 판정은 호출하는 쪽(schedule)이 한다.
     - 없는 코드는 빈 Optional이다.
   - 전체 목록 조회: 비활성 지역을 포함한 모든 지역을 준다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew test --tests 'com.shineunsigan.snsg.place.*'` → BUILD SUCCESSFUL, 실패 0, skipped 0
   - 그리고 diff의 테스트 가운데 다음 네 경우를 각각 단정하는 테스트가 있다. 활성 코드 → 결과와 활성 true. 비활성 코드 → 결과와 활성 false. 없는 코드 → empty. 전체 목록에 비활성 지역 포함.
5. API 문서가 지역 목록 API를 담는다.
   - `docs/openapi.yaml`은 코드에서 다시 생성한 것과 같다.
   - `docs/api-guide.md`는 지역 목록 API를 설명한다. 담을 내용: 부르는 화면(일정 목록의 지역 필터, 장소 선택의 지역 칩), 인증 불필요, 정렬(display_order), 비활성 지역 제외, 응답 필드, 같은 요청을 다시 보내도 되는지.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew test --tests '*OpenApiSpecTest'` → 통과
   - 검증: `grep -n "/api/v1/regions" /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/docs/openapi.yaml /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/docs/api-guide.md` → 두 파일 모두 한 줄 이상
6. 전체 빌드와 패키지 의존 규칙이 통과한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew build` → BUILD SUCCESSFUL, 실패 0. 테스트 수는 베이스 92개 이상이다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && ./gradlew test --tests '*ArchitectureTest'` → 7개 통과
7. 변경 범위가 제한된다.
   - 변경은 워크트리 안의 다음에만 있다: place 도메인 신규 파일, 새 마이그레이션 V2, 공개 경로 등록을 위한 `auth/infrastructure/security/SecurityConfig.java`, `docs/openapi.yaml`, `docs/api-guide.md`, place 테스트.
   - user 도메인, common, 기존 마이그레이션 `V1__create_user.sql`, 기존 테스트는 바뀌지 않는다.
   - 검증: `git -C /c/Users/ks/nss/dev/snsg-be/wt-SHI-147 status --short` → 위 범위 밖의 경로가 없음. `.claude/tasks/SHI-147/task.md`는 예외다.
   - 검증: `git -C /c/Users/ks/nss/dev/snsg-be/wt-SHI-147 diff --staged --stat -- snsg/src/main/java/com/shineunsigan/snsg/user snsg/src/main/java/com/shineunsigan/snsg/common snsg/src/main/resources/db/migration/V1__create_user.sql` → 출력 없음

## 비목표
- 캐시를 두지 않는다. 지역 목록과 port/in 조회 모두 매번 DB를 읽는다.
- schedule 도메인, 그리고 schedule 쪽 `RegionPort`·adapter·`INVALID_REGION` 오류 코드는 만들지 않는다. T-10(일정 개설)과 이후 작업이 만든다.
- places 테이블, 장소 조회, T-07(장소 마스터와 노출 장소 조회)의 범위는 건드리지 않는다.
- 지역을 추가·수정·활성 전환하는 API나 쓰기 서비스를 만들지 않는다. 지역은 마이그레이션의 행으로만 관리한다.
- 지역 목록 API에 시도 이름, 활성 여부, display_order를 내보내지 않는다. 응답은 `{code, name}`뿐이다.
- 이 작업에는 오류 응답이 없다. place 오류 코드 enum을 미리 만들지 않는다.
- 공개 경로는 `GET /api/v1/regions` 하나만 연다. `/api/v1/**` 같은 prefix로 열지 않는다(`.claude/rules/api.md`).
- 기획 문서(`planning/`)는 고치지 않는다. 명세의 마이그레이션 표에는 V5로 적혀 있지만, 고칠 필요가 있으면 보고만 한다.
- 마이그레이션 번호 분기
  - 작업 중에는 V2로 둔다.
  - 머지 직전에 develop에 다른 V2가 생겼으면 번호 조정은 오케스트레이터가 한다. implement는 스스로 번호를 바꾸지 않는다.
- DB 충돌 분기: `## 환경`에 적은 대로 멈추고 보고한다. 스키마 정리, 다른 워크트리 데이터 삭제, `flyway_schema_history` 수정은 하지 않는다.
- `ArchitectureTest`는 도메인을 자동 인식하므로 고치지 않는다.
- 기존 경고와 다른 도메인의 곁다리 개선은 하지 않는다.

## 제약
- 패키지 구조, Entity 규칙, 마이그레이션, 의존 방향은 `.claude/rules/package-structure.md`를 따른다.
  - regions는 같은 문서 Entity 절이 말하는 "다른 키 전략이 꼭 필요한 테이블"이다.
- 컨트롤러, 응답 봉투, 공개 경로 등록, springdoc 애노테이션, 문서 갱신은 `.claude/rules/api.md`를 따른다.
- 테스트 범위와 방식은 `.claude/rules/testing.md`를 따른다. 실제 PostgreSQL 사용, 테스트는 별도 커밋 대상이다.
- 들여쓰기 4칸, Jackson 3, Asia/Seoul `LocalDateTime`은 `CLAUDE.md`를 따른다.
- 커밋하지 않는다. `git add`로 스테이징까지만 한다(`.claude/skills/implement/SKILL.md`).
- 임시 파일은 스크래치패드에 두고 워크트리에 남기지 않는다(`.claude/rules/temp-files.md`).
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 명세가 정한 이름이다. schedule이 이 이름으로 부를 예정이라 되도록 그대로 쓴다.
  - port/in `FindRegionUseCase`
  - 이를 구현하는 `RegionQueryService`
  - 결과 필드 `code`, `sidoName`, `sigunguName`, `active`
  - 명세에는 schedule 쪽 `RegionPort`의 메서드가 `find`, `findAll`로 적혀 있다.
- 제안 구성(바꿔도 된다)
  - `place/domain/model/Region`(@Entity, BaseEntity 미상속, 정적 팩토리)
  - `place/domain/repository/RegionRepository` ← `place/infrastructure/persistence/RegionRepositoryImpl` + `RegionJpaRepository`
  - `place/application/RegionQueryService`(읽기 전용 트랜잭션)
  - `place/application/port/in/FindRegionUseCase`
  - `place/application/dto/result/RegionResult`
  - `place/presentation/RegionController`, `place/presentation/dto/response/RegionResponse`
  - 마이그레이션 파일 이름은 `V2__create_region.sql`을 제안한다.
- regions에 created_at, updated_at이 있다.
  - BaseEntity를 상속하지 않으므로 엔티티에서 직접 매핑한다.
  - 이 작업에는 쓰기 경로가 없다. 초기 행의 값은 마이그레이션 INSERT에서 현재 시각으로 채우면 된다.
- 테스트 제안
  - 활성 필터와 정렬 쿼리, port/in의 단건·전체 조회 결과는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`로 확인한다. 예시: `user/infrastructure/persistence/UserRepositoryImplTest`.
  - 테스트 데이터는 초기 3행의 코드(11440, 11410, 11200)와 겹치지 않게 넣는다. 예: 99001, 99002. 테스트 트랜잭션은 롤백된다.
  - Query 서비스가 repository를 그대로 위임만 한다면 서비스 단위 테스트는 쓰지 않는다(`testing.md`). 위 네 경우의 단정은 repository·persistence 테스트에서 할 수 있다. 변환에 판단이 들어가면 그 부분은 테스트한다.
  - 공개 경로를 확인하는 기존 자동 테스트는 없다. 다른 도메인 컨트롤러 테스트는 auth를 import하지 않는다(`testing.md`). 그래서 공개 여부는 GOAL 2의 실제 기동 + curl로 확인한다.
- 공개 경로 등록 위치: `snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java`의 `requestMatchers(HttpMethod.GET, ...)` 줄들. 메서드에는 `@SecurityRequirements`를 달아 전역 bearer 요구를 지운다(`api.md`).
- openapi 재생성: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-147/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`
- 근거 문서 위치(`planning/be-spec-mvp.html`)
  - 데이터 모델의 place 도메인 regions 표와 아래 설명(키 전략, is_deleted 없음, 초기 데이터)
  - 장소·활동 API 절의 지역 목록 API 정의
  - 작업 목록의 T-06 블록(범위·완료 조건)
  - 일정 카드 목록 API의 regionName 설명("RegionPort.findAll(비활성 포함)")
  - 마이그레이션 순서 표 아래 주석(번호는 머지 순서로 확정)
  - ERD는 `planning/erd.md`의 regions 절
- 지역을 다시 활성화할 때 장소 적재 배치(JOB-04)를 돌려야 한다는 운영 메모는 명세 regions 표에 있다. 이번 구현과는 무관하다.

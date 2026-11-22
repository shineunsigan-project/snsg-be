## 배경

- Linear SHI-202 "홈 다가오는 내 일정 API". 유형: **기능 추가**(새 엔드포인트. 기존 API 동작은 바꾸지 않는다).
- FE에 홈 탭이 새로 생긴다(하단 메뉴 홈/일정/일정 개설/내 정보). 홈 상단의 "다가오는 내 일정" 배너에 일정 1건을 그린다.
- 지금 FE가 배너를 그리려면 참여한 목록·개설한 목록 2번 + 상세 + 참여자 명단으로 4번 불러야 한다. 이것을 1번으로 줄이는 것이 목적이다.
- 사용자와 합의한 것
  - 로그인한 사용자 전용
  - 대상: 내가 멤버로 참여 확정한 일정 또는 내가 개설한 일정 중, 취소되지 않았고 시작 전인 일정. 그중 시작이 가장 빠른 **1건만** 준다
  - 응답: 목록 카드 필드 + 장소 이름 + 소요 시간(durationType) + 참여자 프로필 사진 몇 장(배너에 동그라미 3개 정도 겹쳐 그린다)
  - 해당 일정이 없으면 오류가 아니라 200 빈 값. FE는 빈 값이면 쉬는시간 소개 배너를 보여준다
  - 홈 전용 묶음 API는 만들지 않는다. 이 API는 "다가오는 내 일정 1건"만 맡는다
- spec이 정한 권장안(승인 단계에서 사용자가 확인한다)
  - URL `GET /api/v1/schedules/next`. 형제인 `/participating`·`/hosted`처럼 "내 것"은 토큰의 사용자로 정해진다
  - 빈 값 형식: 200 `{"isSuccess":true}`(`data` 키가 빠진다). `ApiResponse`가 `@JsonInclude(NON_NULL)`이라 `data`가 null이면 JSON에서 빠지고, `docs/api-guide.md` 응답 봉투 절의 "성공이어도 돌려줄 값이 없으면 빠진다"와 같다
  - 시작 전 기준: 일정 상태가 예정(SCHEDULED)이고 시작 시각 > 요청 시각(지금). 시작 시각이 지금과 정확히 같으면 시작한 것으로 보고 뺀다. 모집 중 목록(`findRecruiting`의 `s.startAt > :now`)과 같은 경계다. 완료(COMPLETED)·취소(CANCELED)는 빠진다. 정원이 찬 일정(모집 마감)은 포함한다
  - 사진: 참여자 명단 순서(개설자 먼저 → 참여 일시 → 행 id, `Schedule.confirmedParticipantsInListOrder`)의 **앞 3명**의 프로필 사진 경로. 본인도 빼지 않는다(명단과 같은 순서를 그대로 쓴다). 확정 인원이 3명보다 적으면 그 수만큼 온다(개설자가 늘 들어 있어 최소 1장). 탈퇴한 사람은 user가 가린 기본 이미지 경로가 그대로 온다. FE는 "+N"이 필요하면 `confirmedCount`로 계산한다
  - 같은 시작 시각이 둘 이상이면 id 순(DB uuid 순서)으로 앞의 것. 12시간 겹침 규칙 때문에 정상 흐름에서는 생기지 않는다

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-202` (브랜치 `feat/SHI-202-home-next-schedule`, 베이스 `bc8a66d`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 다른 워크트리는 읽기만 한다
- **spec 단계 시점에 Docker가 꺼져 있다.** DB(`snsg-postgres`)·Redis(`snsg-redis`)가 필요한 테스트(`@DataJpaTest`, `@SpringBootTest` 계열 `OpenApiSpecTest`, 전체 `./gradlew test`)는 컨테이너가 꺼져 있으면 실패한다(skip이 아니다). 테스트 전에 `docker ps`로 확인하고 없으면 `docker start snsg-postgres snsg-redis`. 켤 수 없으면 코드를 고치지 말고 멈춰서 보고한다
- DB·Redis·8080 포트는 모든 워크트리가 공유한다. 테스트는 한 번에 한 워크트리에서만 돌린다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의")
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다(`&&`는 PowerShell 5.1에서 동작하지 않는다)
- 이번 작업은 마이그레이션·새 환경변수가 없다

## GOAL

1. `GET /api/v1/schedules/next`는 토큰이 없으면 401 `UNAUTHENTICATED`이고, 유효한 토큰이면 facade(또는 서비스)가 그 토큰의 userId로 불린다. 경로가 일정 상세(`GET /api/v1/schedules/{scheduleId}`, 공개)로 해석되지 않는다.
   - 검증: `auth/infrastructure/security/SecurityConfigTest`에 두 경우(토큰 없음 → 401 본문 `code` = `UNAUTHENTICATED`, 유효 토큰 → 200이고 그 userId로 호출됨)를 단정하는 테스트가 있다. `cd snsg && ./gradlew cleanTest test --tests '*SecurityConfigTest'` → BUILD SUCCESSFUL, 실패 0
2. 대상 일정을 고르는 조회가 합의한 조건을 지킨다. 실제 PostgreSQL에서 아래를 각각 단정하는 테스트가 있다.
   - 포함: 내가 개설한 일정(HOST 행), 내가 멤버로 참여 확정한 일정, 정원이 차 모집 마감된 시작 전 일정
   - 제외: 취소(CANCELED)·완료(COMPLETED) 일정, 시작 시각이 기준 시각과 같거나 이전인 일정, 내가 참여를 취소한 일정, 삭제된 일정·삭제된 참여 행, 다른 사용자의 일정
   - 여럿이면 시작 시각이 가장 빠른 1건이고, 대상이 없으면 빈 결과(예외 아님)
   - 기준 시각은 테스트가 고정 시각으로 넘긴다(`.claude/rules/testing.md` 공유 DB 항목)
   - 검증: 위 경우들을 단정하는 테스트가 `ScheduleRepositoryImplTest`(또는 조회를 둔 repository의 테스트)에 있다. `cd snsg && ./gradlew cleanTest test --tests '*ScheduleRepositoryImplTest'` → BUILD SUCCESSFUL, 실패 0
3. 응답 `data`의 필드와 값
   - 목록 카드와 같은 필드·이름·뜻: `id`, `category`(`id`, `name`), `title`, `regionName`, `startAt`, `confirmedCount`, `capacity`, `remainingSeats`, `displayStatus`, `createdAt`
   - 더하는 필드: 장소 이름(`placeName`, 개설 시점 사본 `PlaceSnapshot.placeName`), 소요 시간(`durationType`, `SHORT`·`NORMAL`·`LONG`), 참여자 사진 경로 배열(`participantProfileImageUrls`)
   - 사진 배열은 명단 순서 앞 3명이고(확정 인원이 적으면 그 수), 개설자가 첫 번째다. 탈퇴자는 user가 가린 경로 그대로다
   - user 프로필은 요청당 `UserPort.findProfiles` 한 번(사진에 쓰는 사람의 id만), 지역 이름은 요청당 한 번 부른다. 대상 일정이 없으면 user·place 포트를 부르지 않는다
   - 지역 목록에 없는 코드면 `regionName`은 빈 문자열이다(목록·상세와 같다)
   - 검증: facade(Mockito) 테스트에 위 흐름(일정 있음: 사진 순서·3장 자르기·포트 호출 횟수와 인자 / 일정 없음: 빈 결과·포트 미호출)을 단정하는 테스트가 있고, 사진 순서·장수를 schedule domain에서 정하면 그 domain 테스트에도 단정이 있다. `cd snsg && ./gradlew cleanTest test --tests 'com.shineunsigan.snsg.schedule.*'` → BUILD SUCCESSFUL, 실패 0
4. 대상 일정이 없으면 200이고 본문은 `{"isSuccess":true}`(`data` 키 없음)다.
   - 검증: `ScheduleControllerTest`에 대상 없음 → 200, `jsonPath("$.isSuccess").value(true)`, `jsonPath("$.data").doesNotExist()`를 단정하는 테스트와, 대상 있음 → 200이고 Result와 이름이 다른 FE 계약 키(`placeName`, `participantProfileImageUrls` 중 Result와 이름이 다른 것)를 `jsonPath`로 단정하는 테스트가 있다(`.claude/rules/testing.md` presentation 항목). `cd snsg && ./gradlew cleanTest test --tests '*ScheduleControllerTest'` → BUILD SUCCESSFUL, 실패 0
5. 패키지 의존 규칙을 지킨다(QueryService는 repository만, facade에 `@Transactional` 없음, 다른 도메인 참조는 adapter만).
   - 검증: `cd snsg && ./gradlew cleanTest test --tests '*ArchitectureTest'` → BUILD SUCCESSFUL, 실패 0
6. `docs/openapi.yaml`을 코드에서 다시 만들어 새 경로가 들어 있다. 새 경로의 description에 인증 필요, 대상 조건(멤버 확정·개설, 취소되지 않음, 시작 전, 가장 빠른 1건), 없으면 `data`가 빠진다는 것, 사진 장수·순서가 적혀 있다. 401 응답이 문서에 있다.
   - 검증: `cd snsg && OPENAPI_UPDATE=true ./gradlew cleanTest test --tests '*OpenApiSpecTest'` 실행 뒤 `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0. `grep -n '/api/v1/schedules/next:' docs/openapi.yaml` → 한 줄. `docs/openapi-admin.yaml`은 diff가 없다(`git diff --staged --stat -- docs/openapi-admin.yaml` → 출력 없음)
7. `docs/api-guide.md`에 새 API 절이 있고(부르는 화면, 인증, 대상 조건, 정렬, 빈 값 응답 예시 `{"isSuccess":true}`, 응답 예시, 필드 뜻, 401 처리, 재시도 안전), "화면별로 쓰는 API" 표에 홈 행이 있다(빈 값이면 쉬는시간 소개 배너, 배너를 누르면 상세로 가는 것은 `id`로 `GET /api/v1/schedules/{scheduleId}`).
   - 검증: `grep -n '/api/v1/schedules/next' docs/api-guide.md` → 새 절과 "화면별로 쓰는 API" 표(1008행 근처 `## 화면별로 쓰는 API` 아래) 양쪽에 나온다. `grep -n '^| 홈' docs/api-guide.md` → 한 줄 이상
8. 전체 빌드가 통과한다.
   - 검증: `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
9. 범위 밖 파일을 고치지 않는다.
   - 검증: `git diff --staged --name-only` → 모든 경로가 `snsg/src/main/java/com/shineunsigan/snsg/schedule/`, `snsg/src/test/java/com/shineunsigan/snsg/schedule/`, `snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java`, `snsg/src/test/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfigTest.java`, `docs/openapi.yaml`, `docs/api-guide.md`, `.claude/tasks/SHI-202/task.md` 중 하나다. `snsg/src/main/resources/db/migration/` 아래 경로가 없다

## 비목표

- 검색, 모집 중 목록 정렬은 하지 않는다(SHI-203에서 별도 진행)
- 홈 전용 묶음 API(배너 + 다른 홈 섹션)를 만들지 않는다
- 기존 엔드포인트(모집 중·참여한·개설한 목록, 상세, 참여자 명단)의 동작·응답을 바꾸지 않는다. `findRecruiting`·`findParticipating`·`findHosted`·`findPage`·`appendPeriod`의 쿼리를 고치지 않는다
- 기존 테스트의 단정을 바꾸지 않는다. 생성자·시그니처 변경에 따른 컴파일 맞춤과, `ScheduleController`·`SecurityConfigTest`에 의존(facade)이 늘어 필요한 `@MockitoBean` 추가만 허용한다(`.claude/rules/testing.md` SecurityConfigTest 항목)
- 응답에 보는 사람의 역할(개설자인지 멤버인지), 상세의 `availableActions`, 참여자 닉네임·연령대, 장소 주소·좌표는 넣지 않는다(합의 범위 밖). 필요하면 FE가 상세를 부른다
- 시작했지만 완료 전인 일정(진행 중)은 대상이 아니다. 합의가 "시작 전"이다
- 사진 장수(3)를 설정값으로 빼지 않는다(요청하지 않은 설정). 이름 있는 상수로 둔다
- 마이그레이션·인덱스를 더하지 않는다. 느려지면 그때 따로 본다
- user·place 도메인 파일, `common/` 파일은 고치지 않는다
- `docs/openapi-admin.yaml`, `docs/admin-api-guide.md`는 고치지 않는다
- 메인 작업 디렉터리의 `bash.exe.stackdump` 등 untracked 파일은 무시한다

## 제약

- 패키지 구조·의존·Service/Facade 기준은 `.claude/rules/package-structure.md`를 따른다(QueryService는 repository만 의존, 다른 도메인 값은 facade가 포트를 요청당 한 번 불러 붙인다, 삭제 조건 명시, 정렬은 DB에서)
- API·문서 작성은 `.claude/rules/api.md`를 따른다(springdoc 애노테이션, null이 될 수 있는 필드의 조건, `OPENAPI_UPDATE`로 `docs/openapi.yaml` 재생성, `docs/openapi.yaml`은 API 코드와 같은 커밋)
- 테스트는 `.claude/rules/testing.md`를 따른다(공유 DB라 넣은 행만 단정, 고정 기준 시각, 테스트는 원래 코드와 별도 커밋)
- `SecurityConfig`에서 새 경로의 인증 매처는 `/api/v1/schedules/{scheduleId}` permitAll보다 **앞에** 둔다. `{scheduleId}` 패턴이 `/next`와도 맞아 순서가 바뀌면 공개 경로가 된다(기존 `/participating`·`/hosted` 주석과 같은 이유)
- 시각은 `LocalDateTime.now()`를 요청마다 한 번 잡아 조회 조건과 `displayStatus`에 함께 쓴다(`ScheduleQueryService` 기존 방식)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- 따라 할 기존 코드
  - 흐름: `ListParticipantsFacade`(`schedule/application/ListParticipantsFacade.java:29-37`) — QueryService가 schedule 데이터만 읽고, facade가 `UserPort.findProfiles`를 한 번 불러 명단 순서대로 붙인다. `GetScheduleDetailFacade`(`schedule/application/GetScheduleDetailFacade.java:39-44,63-65`) — 지역 이름은 `RegionPort.find(regionCode)` 한 번, 없으면 `""`. 둘 다 `@Transactional` 없음. 새 facade 이름 제안: `GetNextScheduleFacade`
  - 이름·계약: `UserPort.findProfiles`(`schedule/application/port/out/UserPort.java`) — "서로 다른 id마다 항목이 정확히 하나"라 맵에서 꺼낸 값의 존재를 따로 확인하지 않는다(`.claude/rules/package-structure.md` facade 절)
  - 데이터 접근: `ScheduleRepositoryImpl.findParticipating`(`schedule/infrastructure/persistence/ScheduleRepositoryImpl.java:99-115`) — `ScheduleParticipant p join p.schedule s`, `p.status = CONFIRMED`, `p.isDeleted = false`, `s.isDeleted = false`. 개설자도 HOST·CONFIRMED 참여 행을 가지므로 role 조건을 빼면 "개설 또는 멤버 확정"이 된다(확정 행은 일정당 사용자 하나, 부분 유니크 인덱스라 중복 없음). 조건이 고정이라 `ScheduleJpaRepository`의 `@Query` 하나(정렬 `s.startAt, s.id`, 1건)로 둬도 된다. 모집 중 경계 `s.status = SCHEDULED and s.startAt > :now`는 `findRecruiting`(같은 파일 74-78행)
  - 데이터 접근(명단 순서): `Schedule.confirmedParticipantsInListOrder`(`schedule/domain/model/Schedule.java:310-315`, 비교자 60-63행) — 참여 행은 지연 로딩이라 QueryService 트랜잭션 안에서 결과로 바꾼다(`ScheduleQueryService.findParticipants`, `schedule/application/ScheduleQueryService.java:97-104`)
  - 카드 변환: `ScheduleQueryService.toCard`(`ScheduleQueryService.java:150-156`)와 `findCategoryNames`(141-148행) — 활동 이름, `remainingSeats = capacity − confirmedCount`, `displayStatus(now, completionAfter)`. 응답은 `ScheduleCardResponse`(`schedule/presentation/dto/response/ScheduleCardResponse.java`)의 `@Schema` 문구를 따른다. 중첩 record에는 `@Schema(name = ...)`로 고유 이름을 준다(`ScheduleCardCategory`처럼). `durationType` 문구는 `ScheduleDetailResponse`(40행)
  - 컨트롤러: `ScheduleController.listParticipating`(`schedule/presentation/ScheduleController.java:256-291`) — 인증 필요 GET, `@AuthenticationPrincipal UUID userId`, `@ApiResponses` 200·401
  - 보안: `SecurityConfig`(`auth/infrastructure/security/SecurityConfig.java:46-52`) — `/participating`·`/hosted`를 `{scheduleId}` permitAll 앞에서 `authenticated()`로 막는 방식과 주석
  - 테스트: `ScheduleRepositoryImplTest`(`schedule/infrastructure/persistence/ScheduleRepositoryImplTest.java`, native INSERT 헬퍼 787·815행 근처) — 무작위 사용자 id로 넣은 행만 단정. `ListParticipantsFacadeTest`(`schedule/application/ListParticipantsFacadeTest.java`) — `@ExtendWith(MockitoExtension.class)` facade 흐름. `SecurityConfigTest`(`auth/infrastructure/security/SecurityConfigTest.java:220` 근처 `/participating` 401 테스트)
- **SHI-203(모집 중 목록 정렬)과 겹칠 수 있는 파일**: `ScheduleController.java`, `ScheduleQueryService.java`, `ScheduleRepository.java`, `ScheduleRepositoryImpl.java`(또는 `ScheduleJpaRepository.java`), `ScheduleRepositoryImplTest.java`, `ScheduleControllerTest.java`, `docs/openapi.yaml`, `docs/api-guide.md`. 겹침을 줄이려면 기존 메서드를 건드리지 않고 새 메서드만 더하고, 가능하면 고정 조회는 `ScheduleJpaRepository`의 `@Query`로 둔다. `docs/openapi.yaml`은 생성 파일이라 나중에 머지되는 쪽이 develop을 받은 뒤 `OPENAPI_UPDATE=true`로 다시 만든다. 이 파일 목록과 처리 방법을 PR background에 적는다
- 응답 예시(제안, 필드 이름은 구현이 정해도 된다. 단 FE 계약이 되므로 문서와 같아야 한다)

  ```json
  {
    "isSuccess": true,
    "data": {
      "id": "01a1c3d4-0000-7000-8000-000000000001",
      "category": { "id": "01a19cc6-3d80-7000-8000-000000000001", "name": "보드게임" },
      "title": "보드게임 같이 해요",
      "regionName": "마포구",
      "startAt": "2026-11-14T19:30:00",
      "confirmedCount": 4, "capacity": 6, "remainingSeats": 2,
      "displayStatus": "RECRUITING",
      "createdAt": "2026-11-10T09:12:34.567",
      "placeName": "보드게임카페 홍대점",
      "durationType": "NORMAL",
      "participantProfileImageUrls": ["/images/profiles/a.png", "/images/default-profile.png", "/images/profiles/c.png"]
    }
  }
  ```

- `displayStatus`는 대상 조건상 `RECRUITING`·`RECRUITMENT_CLOSED`만 나온다. 문서에 그렇게 적는다
- 빈 값(`data` 없음)은 OpenAPI 스키마로 표현되지 않으므로 description에 적는다(`.claude/rules/api.md` null 필드 항목과 같은 이유)

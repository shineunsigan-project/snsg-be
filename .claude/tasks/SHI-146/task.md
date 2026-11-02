## 배경

- Linear SHI-146. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`)의 작업 목록에 있는 가입 프로필 확장 작업이다. 가입할 때 이름·이메일을 받고, 만 18세 제한을 두고, 닉네임 규칙을 바꾼다(`.`과 `_` 허용, 대소문자 무시 중복). 프로필 사진에서는 WebP를 뺀다.
- 명세가 erd.md·requirements.md보다 우선한다. 명세의 근거는 다음과 같다.
  - 데이터 모델 장 users 표: name VARCHAR(20), email VARCHAR(100). 모두 NOT NULL이고, 이메일이 없으면 `''`. 기존 행이 있으니 `DEFAULT ''`로 채운 뒤 기본값을 지운다. 닉네임 인덱스는 `LOWER(nickname)` 부분 유니크로 교체한다.
  - 마이그레이션 순서 표의 `V2__extend_user_profile.sql`은 가칭이다. develop에 `V2__create_region.sql`(SHI-147, #42)이 먼저 머지돼 다음 번호인 `V3__extend_user_profile.sql`을 쓴다(package-structure.md 마이그레이션 절).
  - 회원 정책 표
    - 이름: 필수, 2~20자, 한글·영문만, 공백·숫자 불가, 가입 후 수정 불가
    - 닉네임 형식: `^[가-힣a-zA-Z0-9._]{2,10}$`
    - 성별: FEMALE·MALE 중 하나(성별 결정. 기타 없음, enum 변경 없음)
    - 생년월일: 만 18세 이상(가입일 기준). 미래 날짜는 INVALID_BIRTH_DATE, 18세 미만은 UNDERAGE
    - 이메일: 선택 입력. 이메일 형식, 최대 100자, 빈 문자열이면 없음
    - 프로필 사진: JPG·PNG, 5MB 이하, 바이트로 판별
  - 가입 API(`POST /api/v1/auth/signup`): 요청 필드에 name(필수)·email(선택)을 더한다. 이메일은 사용자가 입력한다. 카카오 응답에는 이메일이 없고, 가입 세션 조회 응답은 바뀌지 않는다.
  - 오류 코드 장: INVALID_NAME·INVALID_EMAIL·UNDERAGE를 새로 만들고 타입은 INVALID(400)다. DUPLICATED_NICKNAME은 CONFLICT(409)다.
- 사용자와 합의한 결정(이번 작업에서 정함)
  1. 범위를 나누지 않고 한 번에 진행한다.
  2. 형식 검증은 요청 DTO(Bean Validation)와 도메인 양쪽에서 한다.
     - 요청 DTO에서 걸리면 400 `INVALID_REQUEST`를 낸다. 공통 응답 봉투에 실패 전용 `errors` 배열(`[{field, message}]`)을 더해 틀린 필드를 보여준다. 필드 오류가 없으면 `errors`는 JSON에 나가지 않는다. 모든 API에 적용된다.
     - JSON 변환 단계의 실패(`gender: "OTHER"`, 없는 날짜 `2010-02-30`, 타입 오류)는 `errors` 없이 400 `INVALID_REQUEST`만 낸다.
     - 도메인의 INVALID_NAME·INVALID_NICKNAME·INVALID_EMAIL·INVALID_BIRTH_DATE는 불변식 안전망으로 남긴다.
     - UNDERAGE와 DUPLICATED_NICKNAME은 도메인에서만 낸다.
  3. 문구
     - 이름: "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요."
     - 닉네임: "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요."
     - 이메일: "이메일 형식이 올바르지 않아요."
     - 생년월일: "생년월일이 올바르지 않습니다."(기존 유지)
     - 성별 누락: "성별을 선택해주세요."
     - UNDERAGE: "만 18세 이상만 가입할 수 있어요."
     - INVALID_PROFILE_IMAGE: "JPG, PNG 형식의 사진만 올릴 수 있어요."
     - `CommonErrorCode.INVALID_REQUEST`: "요청 형식이 올바르지 않습니다."(기존 유지)
     - `errors[].message`는 도메인 코드와 같은 문구를 쓴다. 이름·닉네임·생년월일이 비었을 때도 각 필드의 형식 오류 문구를 쓴다.
  4. 이메일 형식은 `local@domain.tld`(최상위 도메인 필수) 모양의 자체 정규식으로 판별한다. 요청 DTO와 도메인이 같은 정규식을 쓴다.
  5. 이름·이메일의 앞뒤 공백은 다듬지 않고 그대로 거부한다. 공백만 있는 이메일도 형식 오류이고, 빈 문자열 `''`만 "없음"이다. 이메일은 입력한 대소문자 그대로 저장한다.
  6. 만 나이는 `Period.between` 방식으로 계산한다. 2월 29일생은 평년에는 3월 1일에 만 18세가 된다.
  7. 탈퇴하면 즉시 name·email도 비식별한다. 값은 둘 다 `''`다.
  8. 동시 가입 경합으로 닉네임 유니크 인덱스를 위반하면(대소문자만 다른 경우 포함) DUPLICATED_NICKNAME(409)으로 바꾼다.
  9. 마지막 로그인(last_login_at)은 범위에서 완전히 뺀다. 컬럼, 로그인 기록 UseCase, auth의 호출, 비동기 설정이 모두 없다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-146` (브랜치 `feat/SHI-146-signup-profile-extension`, 베이스 `b8ee0d9`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`와 기획 폴더 `C:\Users\ks\nss\dev\snsg-be\planning`은 읽기만 한다
- 베이스가 `ee02813`(SHI-147 지역 마스터 머지 후)로 바뀌었다. 이전 베이스 `b8ee0d9`에서는 92개 통과였다. 구현 전에 새 베이스에서 `cd snsg && ./gradlew test`를 돌려 테스트 수를 기록한다(이하 N)
- `snsg/.env`는 복사돼 있다. Docker 컨테이너 `snsg-postgres`·`snsg-redis`가 떠 있어야 DB·Redis 테스트가 돈다. 꺼져 있으면 `docker start snsg-postgres snsg-redis`
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의")
- 로컬 DB에는 V1, V2(create region)가 적용돼 있다. V3는 새 파일이라 기동하거나 테스트하면 그대로 적용된다
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다. PowerShell에는 `&&`가 없다

## GOAL

1. V3 마이그레이션이 users에 `name VARCHAR(20) NOT NULL`, `email VARCHAR(100) NOT NULL`을 더한다. 기존 행은 `''`로 채우고 기본값은 남기지 않는다. `uix_users_nickname`은 `LOWER(nickname)`에 `WHERE is_deleted = FALSE` 부분 유니크로 바뀐다. last_login_at 컬럼은 없다.
   - 검증: `cat snsg/src/main/resources/db/migration/V3__extend_user_profile.sql` → 위 내용만 있다. `last_login` 문자열이 없고 `DROP DEFAULT`(또는 같은 효과)가 있다
   - 검증: `cd snsg && ./gradlew test` → BUILD SUCCESSFUL. `ddl-auto: validate`가 엔티티와 스키마를 맞춰 보므로 컨텍스트가 뜨는 테스트가 통과하면 둘이 맞는다
2. 가입하면 이름과 이메일이 저장된다. 이메일을 생략하거나 `''`로 보내면 `''`로 저장된다.
   - 검증: `cd snsg && ./gradlew test --tests '*UserCommandServiceTest' --tests '*ProfileTest' --tests '*UserTest' --tests '*UserAdapterTest'` → 통과. 저장된 name·email 값을 단정하는 테스트가 diff에 있다. 이메일이 생략됐을 때와 `''`일 때가 모두 있다
3. 도메인 불변식이 다음을 지킨다.
   - 이름이 `^[가-힣a-zA-Z]{2,20}$`가 아니면(null, 공백 포함, 숫자 포함, 앞뒤 공백, 1자, 21자) `BusinessException(INVALID_NAME)`
   - 닉네임이 `^[가-힣a-zA-Z0-9._]{2,10}$`가 아니면 `INVALID_NICKNAME`. `.`과 `_`가 들어간 닉네임(예: `min.t_o`)은 통과한다
   - 이메일이 `''`가 아닌데 `local@domain.tld` 형식이 아니거나 100자를 넘으면 `INVALID_EMAIL`. 거부 예: `a@b`, `" a@b.com"`, `"   "`. 통과 예: `User@Example.com`이고, 대소문자를 바꾸지 않고 저장한다
   - 생년월일이 미래면 `INVALID_BIRTH_DATE`
   - 가입일 기준 만 18세 미만이면 `UNDERAGE`. 18세 생일 당일은 통과, 그 전날은 거부. 2월 29일생은 평년 2월 28일에 거부, 3월 1일에 통과
   - 검증: `cd snsg && ./gradlew test --tests '*ProfileTest' --tests '*UserTest'` → 통과. 위 경우마다 단정이 diff에 있다
4. 가입 요청 DTO(`SignUpRequest`)가 name·nickname·email·gender·birthDate의 형식을 검사한다. 걸리면 400 `INVALID_REQUEST`와 함께 틀린 필드마다 `errors[{field, message}]`가 나간다. 문구는 배경의 결정 3과 같다.
   - 이름·닉네임·생년월일이 비었을 때도 각 필드의 형식 오류 문구를 쓴다
   - 성별이 비었을 때는 "성별을 선택해주세요."
   - 미래 생년월일은 birthDate 필드 오류다
   - 예: `{"isSuccess":false,"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.","errors":[{"field":"nickname","message":"닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요."}]}`
   - 검증: `cd snsg && ./gradlew test --tests '*AuthControllerTest'` → 통과. diff에 다음 단정이 있다
     - 잘못된 name·nickname·email 각각에 대해 `code`, `errors[].field`, `errors[].message`
     - gender 누락에 대해 "성별을 선택해주세요."
     - 두 필드가 동시에 틀리면 `errors`에 둘 다 담긴다
5. `errors`는 필드 검증 오류가 있을 때만 나간다. 성공 응답과 다른 실패 응답(BusinessException, JSON 변환 실패, 404·405 등)에는 `errors` 키가 없다.
   - `gender: "OTHER"`(FEMALE·MALE 밖의 성별)나 `birthDate: "2010-02-30"`이면 400 `INVALID_REQUEST`이고 `errors`가 없다
   - 검증: `cd snsg && ./gradlew test --tests '*AuthControllerTest'` → 통과. `gender: "OTHER"` 요청에 대해 400, `code`가 `INVALID_REQUEST`, `$.errors`가 없다는 단정이 diff에 있다. BusinessException 응답(예: 409 DUPLICATED_NICKNAME)에 `$.errors`가 없다는 단정도 있다
6. 닉네임 중복 판정은 대소문자를 무시한다.
   - 탈퇴하지 않은 회원이 `Mint`를 쓰고 있으면 `mint`로 가입할 때 `DUPLICATED_NICKNAME`이 난다
   - 탈퇴한 회원의 닉네임은 중복으로 보지 않는다
   - 검증: `cd snsg && ./gradlew test --tests '*UserRepositoryImplTest'` → 통과. 실제 PostgreSQL에서 대소문자만 다른 닉네임이 중복으로 판정되는 단정이 diff에 있다
7. DB 인덱스가 대소문자만 다른 닉네임 저장을 막는다. 애플리케이션 판정을 통과한 뒤 인덱스 위반이 나도(동시 가입 경합) 가입 응답은 409 `DUPLICATED_NICKNAME`이다. 500이 아니다.
   - 검증: `cd snsg && ./gradlew test --tests '*UserRepositoryImplTest' --tests '*UserCommandServiceTest'` → 통과. diff에 다음이 있다
     - 중복 판정 없이 `Mint`, `mint` 두 사용자를 직접 저장하면 인덱스 위반이 나는 실제 PostgreSQL 테스트
     - 인덱스 위반을 `BusinessException(DUPLICATED_NICKNAME)`으로 바꾸는지 단정하는 테스트
8. 프로필 사진은 JPG·PNG만 받는다. WebP(RIFF 시그니처)는 `INVALID_PROFILE_IMAGE`이고, 문구는 "JPG, PNG 형식의 사진만 올릴 수 있어요."다.
   - 검증: `cd snsg && ./gradlew test` → 통과. WebP 바이트가 `INVALID_PROFILE_IMAGE`로 거부되는 단정이 diff에 있다
   - 검증: `git grep -n -i webp -- snsg/src/main docs` → 출력 없음
9. 탈퇴하면 name·email이 즉시 `''`가 된다. 기존 비식별(닉네임·생년월일·사진)과 성별 유지는 그대로다.
   - 검증: `cd snsg && ./gradlew test --tests '*UserTest'` → 통과. 탈퇴 후 name·email이 `''`라는 단정이 diff에 있다
10. 마지막 로그인 관련 코드가 없다.
    - 검증: `git grep -n -i -E "last_?login|recordLogin|RecordLoginUseCase|EnableAsync|@Async" -- snsg docs` → 출력 없음
11. `docs/openapi.yaml`이 코드와 같다. `docs/api-guide.md`가 이번 변경을 반영한다.
    - 가입 요청 필드에 name·email이 있다
    - 공통 실패 봉투에 `errors` 배열이 있다. 언제 나가는지와 예시 JSON을 적는다
    - 가입 오류 표가 결정 2에 맞다
      - 형식 오류는 `INVALID_REQUEST`와 `errors`
      - `UNDERAGE`(400)와 `DUPLICATED_NICKNAME`(409)는 따로 적는다
      - 닉네임·사진 형식 설명이 새 규칙(`.`·`_` 허용, JPG·PNG)이다
    - 검증: `cd snsg && ./gradlew test --tests '*OpenApiSpecTest'` → 통과(`OPENAPI_UPDATE` 없이)
    - 검증: `git grep -n -E "UNDERAGE|errors" -- docs/api-guide.md` → 두 단어 모두 나온다
12. 전체 빌드가 통과하고, 테스트가 줄지 않는다.
    - 검증: `cd snsg && ./gradlew build` → BUILD SUCCESSFUL. `snsg/build/test-results/test/*.xml` 합계가 tests N개(새 베이스 기준) 이상, failures 0, errors 0, skipped 0
13. 워크트리 밖 파일과 기획 문서를 고치지 않는다. 임시 파일이 남지 않는다.
    - 검증: `git status --short` → 모든 경로가 `snsg/`, `docs/`, `.claude/` 아래다. `.log`·스크래치 파일이 없다. `C:\Users\ks\nss\dev\snsg-be\planning`은 변경 없음

- 정지 조건: 12번이 통과하고 1~11번의 검증이 모두 기대 결과일 때 끝낸다.
- 끝내지 않는 경우
  - 기존 테스트를 지우거나 단정을 느슨하게 해서 통과시켰을 때(결정으로 바뀐 문구·형식 때문에 고친 단정은 예외)
  - 컴파일이 깨져 테스트가 돌지 않았을 때
  - Docker가 꺼져 DB 테스트가 skip되거나 연결 실패로 깨졌을 때

## 비목표

- 마지막 로그인(last_login_at)은 만들지 않는다. 컬럼, user의 로그인 기록 UseCase, auth `UserPort`·`UserAdapter`·`SocialLoginUseCase`의 호출, `@EnableAsync`·실행기 설정이 모두 없다. 명세에 아직 그 내용이 보여도 무시한다
- 성별 "기타", 성별 변경 횟수 컬럼, 가입 세션 변경은 하지 않는다(범위 밖. 가입 세션 15분은 이미 머지됨)
- 닉네임 보호 이력(user_nickname_histories, 변경 전 14일·탈퇴 90일 보호)과 닉네임 중복 확인 API는 다른 작업의 범위다. 만들지 않는다
- 프로필 조회·수정 API, 탈퇴 90일 보관(지금의 즉시 비식별 방식 유지), 연령대 계산은 하지 않는다
- 기기별 세션(sessionId), 토큰 재발급·로그아웃 로직은 건드리지 않는다
- 가입 세션 조회 API 응답(카카오 닉네임·사진)은 바꾸지 않는다. 카카오에서 이메일을 받아오지 않는다
- `CommonErrorCode.INVALID_REQUEST`의 문구를 바꾸지 않는다
- JSON 변환 실패에는 `errors`를 담지 않는다. Jackson 예외에서 필드 경로를 꺼내지 않는다
- 기획 문서(`be-spec-mvp.html`·PDF·erd.md·erdcloud.sql·ERDCloud)는 고치지 않는다. 오케스트레이터가 처리한다
- 이번 결정과 무관한 기존 코드·주석·포맷은 고치지 않는다. 예: `UserAdapter`의 풀네임 타입 표기, `Profile`의 "기능명세서 POL-005" 주석 중 닉네임 정규식과 무관한 부분
- 마이그레이션 V3는 아직 머지 전이라 이번 작업 안에서는 고쳐도 된다. 머지된 V1·V2는 고치지 않는다

## 제약

- 패키지 구조·의존 방향·도메인 간 참조·코드 작성(메서드는 한 가지 일만 하고, 단계는 같은 클래스의 private 메서드로 나눈다)은 `.claude/rules/package-structure.md`를 따른다. ArchUnit(`ArchitectureTest`)이 통과해야 한다
- API·응답 봉투·문서는 `.claude/rules/api.md`를 따른다. API를 바꿨으므로 `OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`로 `docs/openapi.yaml`을 다시 만든다
- 테스트는 `.claude/rules/testing.md`를 따른다. DB 인덱스·커스텀 쿼리 테스트는 실제 PostgreSQL로 돈다
- auth는 user 도메인 상수를 import할 수 없다. 그래서 정규식·문구가 `SignUpRequest`와 user 도메인 두 곳에 적힌다. 값 상수 중복은 허용된다(`.claude/rules/package-structure.md` 도메인 간 참조)
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 스테이징 전에 워크트리에서 치운다
- Jackson 3를 쓴다. 애노테이션만 `com.fasterxml.jackson.annotation`에 있다(CLAUDE.md)
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

- **기획 문서 수정 필요(오케스트레이터 처리)**
  - 명세 가입 API의 오류 목록: INVALID_NAME 등 형식 오류는 요청 경로에서 `INVALID_REQUEST`와 `errors`로 나간다. 도메인 코드는 안전망이다
  - 공통 봉투에 `errors` 배열 추가
  - 문구(결정 3)
  - 이메일 정규식·공백·대소문자 결정, 만 나이 2월 29일 처리, 인덱스 위반 409
  - 기획 문서에서 마지막 로그인 삭제: 오케스트레이터 처리
- 현재 코드에서 바뀌는 지점(구현이 정한다)
  - `SignUpRequest`: name·email 추가. nickname·name·email에 `@Pattern`/`@Size`, gender `@NotNull`, birthDate `@NotNull @Past`. 문구는 각 제약의 `message`로 준다. 이름·닉네임이 null일 때도 형식 문구를 쓰려면 `@NotNull(message = 같은 문구)`를 함께 다는 방법이 있다
  - `SignUpCommand`·auth `RegisterUserCommand`·`UserAdapter.register`·user `RegisterUserCommand`: name·email 전달
  - `Profile`(또는 `User`): name·email 필드와 검증. 지금 `Profile` 주석은 "남에게 보여줄 내 정보"다. name·email은 비공개이므로 둘 중 어디에 둘지는 구현이 정한다. 어디에 두든 탈퇴 비식별(GOAL 9)이 함께 일어나야 한다
  - 만 18세 판정의 "오늘"은 `LocalDate.now()`(JVM 기본 시간대가 Asia/Seoul로 고정됨)다. 경계 테스트를 하려면 기준일을 인자로 받는 메서드나 `Clock` 주입을 쓸 수 있다
  - `UserJpaRepository.existsByProfileNicknameAndIsDeletedFalse`는 대소문자를 구분한다. `LOWER` 비교 커스텀 쿼리(`@Query`)로 바꾼다
  - 인덱스 위반을 409로 바꾸려면 위반이 `register` 안에서 드러나야 한다. 예: `saveAndFlush`. 바꾸는 위치는 user infrastructure(`UserRepositoryImpl`)나 `UserCommandService`이고, 구현이 정한다. 다른 유니크 인덱스(소셜 계정) 위반까지 DUPLICATED_NICKNAME으로 바꾸지 않도록 제약 이름(`uix_users_nickname`)으로 구분하는 방법이 있다
  - `errors`: `ApiResponse`에 `errors` 필드(`@JsonInclude(NON_NULL)`라 null이면 빠진다)를 더한다. `GlobalExceptionHandler`에서 `MethodArgumentNotValidException`의 필드 오류만 담는다. `handleExceptionInternal`은 지금 모든 Spring MVC 예외를 `INVALID_REQUEST`로 감싼다. `HttpMessageNotReadableException`(JSON 변환 실패)은 `errors` 없이 둔다
  - `api-guide.md` 30행 부근 "요청 형식 오류의 메시지는 서버 로케일에 따라 달라진다"는 문장은 `errors`와 함께 다시 쓴다
  - `ProfileImage.WEBP`, `UserErrorCode.INVALID_PROFILE_IMAGE` 문구, `AuthController`의 사진 업로드 설명(115행 부근)에 WebP가 적혀 있다
  - 테스트는 대상과 같은 패키지, 같은 이름 + `Test`로 두고, 원래 코드와 별도 커밋으로 올린다(커밋 분할은 오케스트레이터가 한다)
- 명세 근거 중 확인 필요 항목: 영문 이름 띄어쓰기 허용 여부(명세 확인 필요 목록의 C7)는 기획 확인 전 기본 동작이 "공백 불가"다. 이번 작업은 그 기본 동작을 따른다

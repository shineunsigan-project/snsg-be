## 배경
- Linear SHI-211 "프로필 사진을 S3·CloudFront로 저장·제공". 사용자 요구: "s3, cloudfront를 추가하고 코드 수정해줘. 면밀히 검토하면서 진행한다. 규칙을 어기진 않는지 자세히 확인한다."
- 지금은 `user/infrastructure/storage/LocalFileStorageAdapter`가 컨테이너 디스크(`FILE_STORAGE_DIRECTORY=/data/files`, ECS Fargate, 볼륨 없음)에 `<UUID>.<ext>`로 쓰고 `ProfileImageResourceConfig`가 `/images/profiles/**`로 내려준다. 배포·재시작마다 파일이 사라진다. adapter javadoc에 "운영 배포 전에 S3 어댑터로 바꾼다"고 적혀 있다.
- 유형: **기능 추가**(저장소 어댑터·리다이렉트 경로 추가). 기존 테스트 단정은 바꾸지 않는다.
- 사용자와 합의한 결정
  1. **DB·API·검증·FE 계약은 그대로 둔다.** DB와 응답의 사진 값은 계속 `/images/profiles/<소문자 UUID>.<png|jpg>`다. 파일만 S3에 두고, 서버의 `GET /images/profiles/{파일명}`이 형식을 확인한 뒤 CloudFront 주소로 302 리다이렉트한다. (FE `toApiImageUrl`은 `/`로 시작하는 값에 API 서버 주소를 붙이므로 브라우저가 서버로 요청하고 리다이렉트를 따라간다.)
  2. **저장소는 설정으로 고른다.** local 프로파일은 기존 디스크 저장(`LocalFileStorageAdapter`·`ProfileImageResourceConfig`)을 그대로 쓰고, 그 밖의 프로파일(dev·prod) 기본값은 S3다. S3 모드에서 버킷·CloudFront 주소 설정이 빠지면 기동이 실패한다.
  3. **자격 증명은 IAM 권한자가 만든 ECS task role로 받는다.** 코드는 AWS SDK 기본 자격 증명 체인(`DefaultCredentialsProvider`)만 쓰고, 키 설정을 두지 않는다.

## 환경
- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-211` (브랜치 `feat/SHI-211-profile-image-s3`, 베이스 develop `af16b32`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다.
- 워크트리 `snsg/.env`에는 이미 운영 목록 키가 다 있다(`SPRING_PROFILES_ACTIVE`·`ADMIN_CORS_ALLOWED_ORIGINS` 제외, 둘 다 기본값 있음). OS 환경변수 `SPRING_PROFILES_ACTIVE`는 비어 있어 테스트는 기본 프로파일 `local`로 돈다(`application.yaml`의 `spring.profiles.default: local`). 이번 작업은 로컬 `.env`에 새 키를 요구하지 않아야 한다(GOAL 6).
- **spec 작성 시점에 Docker 데몬이 꺼져 있었다**(`docker ps` → `failed to connect to the docker API`). DB·Redis 테스트와 `@SpringBootTest` 계열은 컨테이너가 없으면 실패한다(skip 아님). 전체 build 전에 Docker Desktop을 켜고 `docker start snsg-postgres snsg-redis`로 띄운다. 띄울 수 없으면 코드를 고치지 말고 멈춰 보고한다.
- DB·Redis·8080 포트는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의"). 이번 작업은 마이그레이션이 없다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. 아래 검증 명령은 Bash 문법이다.
- 실제 AWS(S3·CloudFront)는 코드·테스트 어디에서도 부르지 않는다. 이 머신의 AWS 자격 증명에 기대는 테스트를 만들지 않는다.

## GOAL

1. **S3 저장 어댑터**: S3 모드의 `FileStoragePort.store(content, extension)`는 키 `images/profiles/<소문자 UUID>.<extension>`로 S3에 PutObject하고, 지금과 같은 형식의 경로 `/images/profiles/<UUID>.<extension>`(`ProfileImage.isUploadedPath`가 참)을 돌려준다.
   - PutObject 요청에는 설정의 버킷, 위 키, Content-Type(`png` → `image/png`, `jpg` → `image/jpeg`), Cache-Control `public, max-age=86400`이 실린다. (처음 값 `public, max-age=31536000, immutable`을 GOAL 14의 사용자 결정 "캐시 기간을 짧게"로 바꾼 것이다. 이 항목의 단정도 새 값으로 맞춘다.)
   - S3 호출이 SDK 예외(`SdkException` 계열)로 실패하면 `BusinessException(UserErrorCode.PROFILE_IMAGE_STORE_FAILED)`를 던진다. 새 ErrorCode를 만들지 않는다.
   - 검증: staged diff의 S3 어댑터 단위 테스트(S3Client는 Mockito mock)가 위 버킷·키·Content-Type·Cache-Control·반환 경로 형식과 실패 시 ErrorCode를 단정한다. `cd snsg && ./gradlew cleanTest test --tests '*S3*'` → BUILD SUCCESSFUL, 실패 0, 실행 테스트 1개 이상
2. **S3 삭제 어댑터**: `delete(path)`는 `ProfileImage.isUploadedPath(path)`가 참일 때만 키 `images/profiles/<파일명>`으로 DeleteObject한다. 형식 밖의 값(`LocalFileStorageAdapterTest`의 형식 밖 목록과 같은 값: `/images/profiles/..`, `/images/profiles/`, `/images/profiles/a.png`, `.gif` 확장자, `/images/profiles/../<파일명>`, `/images/other/<파일명>`, 외부 `https://` 주소, `null`)이면 S3를 전혀 부르지 않는다. 삭제가 SDK 예외로 실패하면 예외를 던지지 않고 warn 로그만 남긴다.
   - 검증: 같은 테스트가 정상 경로의 DeleteObject 버킷·키, 형식 밖 값마다 S3Client와 상호작용 없음(`verifyNoInteractions`), 삭제 실패 시 예외 없음을 단정한다. `cd snsg && ./gradlew cleanTest test --tests '*S3*'` → 실패 0
3. **리다이렉트 엔드포인트**(S3 모드에서만 있음): `GET /images/profiles/{fileName}`
   - `"/images/profiles/" + fileName`이 `ProfileImage.isUploadedPath`를 통과하면 302이고, `Location`은 `<FILE_STORAGE_CLOUDFRONT_URL>/images/profiles/<fileName>`(슬래시 하나로 이어짐)이며 `Cache-Control: public, max-age=86400`이다. 파일이 S3에 있는지는 확인하지 않는다(없으면 CloudFront가 403·404를 준다).
   - 형식 밖 파일명(`a.png`, 대문자 UUID `0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9.png`, `<소문자 UUID>.gif`, `<소문자 UUID>.png.png`)은 404이고 `Location` 헤더가 없다.
   - **계층**(`.claude/rules/api.md` Controller 절 "사용자용 Controller는 같은 도메인의 application에 의존한다")
     - 컨트롤러는 user `presentation`에 두고 **기존 `UserQueryService`의 새 메서드** 하나만 부른다(사용자 결정: 조회 서비스는 Entity·애그리거트 단위로 이름 짓는다. `ProfileImage`는 Entity가 아니라 enum이라 별도 QueryService를 만들지 않는다).
     - 파일명 형식 판정(`ProfileImage.isUploadedPath`)과 CloudFront 주소 조립은 이 새 메서드가 한다. 형식 밖이면 빈 `Optional`을 돌려주거나 `ErrorType.NOT_FOUND` `BusinessException`을 던진다(구현 선택). 컨트롤러는 이를 404로 바꾼다.
     - 컨트롤러는 user `domain`·`infrastructure`를 import하지 않고, CloudFront 주소 record도 직접 읽지 않는다.
   - **사진 요청마다 DB 커넥션·트랜잭션을 잡지 않는다.** 이 경로는 사진을 볼 때마다 불린다.
     - 새 메서드에만 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 달아 클래스 수준 `@Transactional(readOnly = true)`를 끈다. 다른 메서드와 클래스 수준 설정은 바꾸지 않는다.
     - 새 메서드는 repository를 부르지 않는다. 설정 record와 `ProfileImage`만 쓴다.
     - `UserQueryService`의 생성자 의존에는 CloudFront 주소 record 하나만 더해진다. repository·`port/out`·다른 서비스는 더하지 않는다.
   - `ArchitectureTest`가 통과한다.
   - 검증: `grep -c 'Propagation.NOT_SUPPORTED' snsg/src/main/java/com/shineunsigan/snsg/user/application/UserQueryService.java` → `1`. 파일을 읽어 그 애노테이션이 새 리다이렉트용 메서드에 붙어 있고, 클래스 수준 `@Transactional(readOnly = true)`가 그대로인지 확인한다.
   - 검증: `git diff --staged -U0 -- snsg/src/main/java/com/shineunsigan/snsg/user/application/UserQueryService.java | grep -E '^\+\s*private final'` → CloudFront 주소 record 필드 한 줄만
   - 검증: `git diff --staged -- 'snsg/src/main/java/com/shineunsigan/snsg/user/presentation/*Redirect*' | grep -E '^\+import com\.shineunsigan\.snsg\.user\.(domain|infrastructure)\.|^\+import .*CdnProperties'` → 출력 없음 (컨트롤러 파일 이름에 `Redirect`가 들어가지 않으면 그 파일로 바꿔 본다)
   - 검증: 테스트 두 가지가 staged diff에 있다.
     - `UserQueryServiceTest`에 더한 Mockito 단위 테스트가 형식 판정(통과 값 1개, 위 형식 밖 값들)과 주소 조립(`https://cdn.example.test` + `/images/profiles/<파일명>`)을 단정한다. 새 테스트는 repository mock과 상호작용이 없다.
     - `@WebMvcTest` 컨트롤러 테스트(`UserQueryService`는 `@MockitoBean`)가 302·Location·Cache-Control과, 형식 밖일 때 404·Location 없음을 단정한다. 필터는 `@AutoConfigureMockMvc(addFilters = false)`로 끄고, S3 모드는 테스트 속성으로 켠다.
     - `cd snsg && ./gradlew cleanTest test --tests '*UserQueryServiceTest' --tests '*Redirect*' --tests '*ArchitectureTest'` → 실패 0
4. **모드별 빈이 겹치지 않는다**(같은 `/images/profiles/**` 경로를 두 쪽이 잡지 않는다).
   - local 모드: `FileStoragePort` 빈은 `LocalFileStorageAdapter` 하나이고, `ProfileImageResourceConfig`(디스크 정적 리소스 핸들러)가 등록되며, 리다이렉트 컨트롤러·S3 어댑터·`S3Client` 빈은 없다.
   - S3 모드: `FileStoragePort` 빈은 S3 어댑터 하나이고, 리다이렉트 컨트롤러가 있으며, `ProfileImageResourceConfig`·`LocalFileStorageAdapter` 빈은 없다.
   - S3 모드에서 버킷 또는 CloudFront 주소가 없거나 빈 값이면 컨텍스트 기동이 실패한다.
     - **판정 위치**: 설정 record 자체도 `UserQueryService`도 아니다. **S3 모드에서만 만들어지는 infrastructure 빈**(S3 config, 예: `S3StorageConfig`)이 만들어질 때 버킷과 CloudFront 주소 둘 다 빈 값을 거부한다. infrastructure는 application의 CloudFront record를 import할 수 있다.
       - `UserQueryService`는 모든 모드에서 만들어지므로, 여기서 판정하면 local이 뜨지 않는다.
       - 리다이렉트 컨트롤러는 record를 직접 읽지 않으므로(GOAL 3), 컨트롤러에서 판정하지 않는다.
     - 이유: `SnsgApplication`의 `@ConfigurationPropertiesScan`이 base package 전체를 스캔한다. 그래서 `@ConfigurationProperties`를 단 record(`user/application`의 CloudFront 주소 record 포함)는 **local에서도 전역 등록·바인딩된다.**
     - local에서는 두 키가 빈 값(`${...:}` 빈 기본값)으로 바인딩된다. 그 record를 읽는 빈이 local에 없으므로 문제가 없어야 한다. record에 `@Validated`·`@NotBlank`를 달아 바인딩 단계에서 실패시키지 않는다. 달면 local 컨텍스트가 뜨지 않는다.
   - **`FileStorageProperties`(`file-storage.directory: ${FILE_STORAGE_DIRECTORY}`, 기본값 없음)는 그대로 둔다.** 이 record도 스캔으로 모든 모드에서 바인딩된다. S3 모드에서 환경변수가 없으면 글자 그대로의 `${FILE_STORAGE_DIRECTORY}`가 바인딩되지만, 그 값을 읽는 빈(`LocalFileStorageAdapter`·`ProfileImageResourceConfig`)이 없어 기동과 동작에 영향이 없어야 한다.
   - 검증: staged diff에 아래 경우를 단정하는 테스트가 있다.
     - (a) local 빈 구성
     - (b) S3 빈 구성
     - (c) S3 모드에서 버킷 빈 값 → 기동 실패
     - (d) S3 모드에서 CloudFront 주소 빈 값 → 기동 실패
     - (e) local 모드에서 버킷·CloudFront 주소가 빈 값이어도 기동 성공
     - (f) S3 모드에서 `file-storage.directory`를 주지 않거나 `${FILE_STORAGE_DIRECTORY}` 글자 그대로를 줘도 기동 성공이고, `LocalFileStorageAdapter`·`ProfileImageResourceConfig` 빈이 없음
     - 방식은 Spring Boot 앱 전체 없이 `ApplicationContextRunner`/`WebApplicationContextRunner`로 저장소 관련 설정 클래스와 record만 올리는 것을 권장한다. 실제 AWS 호출은 없다. `S3Client` 생성만으로는 네트워크를 쓰지 않는다.
     - `cd snsg && ./gradlew cleanTest test --tests '*Storage*'` → 실패 0, 기존 `LocalFileStorageAdapterTest` 포함
5. **미해결 자리표시자가 글자 그대로 바인딩되는 함정을 막는다**(SHI-190, `.claude/rules/git-workflow.md`). `application*.yaml`에 기본값 없는 `${FILE_STORAGE_S3_BUCKET}`·`${FILE_STORAGE_CLOUDFRONT_URL}` 자리표시자가 없다. 두 키는 빈 기본값(`${...:}`)으로 두고 비어 있으면 기동 실패(GOAL 4)로 처리하거나, yaml에 두지 않고 relaxed binding으로 읽는다.
   - 검증: `grep -nE '\$\{FILE_STORAGE_(S3_BUCKET|CLOUDFRONT_URL)\}' snsg/src/main/resources/application*.yaml` → 출력 없음
6. **설정과 운영 목록**
   - 저장소 모드는 base `application.yaml`에서 `s3`, `application-local.yaml`에서 `local`이다. 운영 환경변수로 모드를 줄 필요가 없다.
   - 새 환경변수는 둘이다: `FILE_STORAGE_S3_BUCKET`(버킷 이름), `FILE_STORAGE_CLOUDFRONT_URL`(예 `https://dxxxx.cloudfront.net`, 끝 슬래시 없음). `snsg/.env.example`에 값 없이 추가한다. AWS 키(`AWS_ACCESS_KEY_ID` 등)나 리전 환경변수는 추가하지 않는다. 리전은 설정 파일에 `ap-northeast-2`로 둔다.
   - 로컬 `snsg/.env`에는 새 키가 없어도 테스트 컨텍스트가 뜬다.
   - 검증: `grep -cE '^FILE_STORAGE_(S3_BUCKET|CLOUDFRONT_URL)=$' snsg/.env.example` → `2`
   - 검증: `grep -nE '^AWS_' snsg/.env.example` → 출력 없음
   - 검증: `git diff --staged --name-only | grep -x 'snsg/.env'` → 출력 없음 (`.env`는 gitignore 대상이기도 하다. 워크트리 `.env`를 고치지 않는다)
7. **자격 증명은 기본 체인만 쓴다.** 코드에 정적 키 공급자나 키 설정이 없다.
   - 검증: `git diff --staged -U0 -- snsg/src/main | grep -E '^\+' | grep -nE 'StaticCredentialsProvider|AwsBasicCredentials|accessKey|secretKey|AWS_ACCESS_KEY|AWS_SECRET'` → 출력 없음
8. **의존성**: AWS SDK v2 S3는 BOM으로 버전을 고정해 `snsg/build.gradle`에 추가한다. Spring Cloud AWS는 쓰지 않는다. `S3Client` 빈은 우리 config가 직접 만든다(Boot 4 자동 설정 모듈 분리 함정을 피한다, CLAUDE.md).
   - 검증: `grep -nE "software\.amazon\.awssdk" snsg/build.gradle` → `bom`과 `s3` 줄이 있음
   - 검증: `grep -n "awspring" snsg/build.gradle` → 출력 없음
9. **보안 체인·문서·FE 계약을 유지한다.**
   - `SecurityConfig`의 `.requestMatchers("/images/**").permitAll()`은 그대로다.
     - 검증: `grep -c 'requestMatchers("/images/\*\*").permitAll()' snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security/SecurityConfig.java` → `1`
   - 리다이렉트 컨트롤러는 `@Hidden`(선례 `KakaoWebhookController`)이라 스펙에 나오지 않는다. `docs/openapi.yaml`·`docs/openapi-admin.yaml`은 바뀌지 않는다.
     - 검증: `git diff --staged --name-only -- docs/openapi.yaml docs/openapi-admin.yaml` → 출력 없음
     - 검증: `cd snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → 실패 0
   - `ProfileImage`·`Profile`·`User`·`CompleteSignUpFacade`·`UserRepository` 계열의 경로 형식·검증 코드는 바뀌지 않는다.
     - 검증: `git diff --staged --name-only | grep -E '/(ProfileImage|Profile|User|CompleteSignUpFacade|UserRepository|UserRepositoryImpl|UserJpaRepository|UserCommandService|UserProfileCommandService|UserWithdrawalCommandService)\.java$'` → 출력 없음
   - `docs/api-guide.md`에는 사진 경로를 요청하면 서버가 302로 CDN 주소를 준다(`<img>`는 따라가므로 FE가 바꿀 것은 없다)는 안내 한두 문장만 더한다. 그 밖의 절은 고치지 않는다.
     - 검증: `git diff --staged --numstat -- docs/api-guide.md` → 추가 1~4줄, 삭제 0~1줄
10. **전체 빌드**: Docker 컨테이너가 떠 있는 상태에서 `cd snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0
    - 정지 조건: 위 명령이 실패 0으로 끝나면 끝낸다.
    - 끝내지 않는 경우: 실패가 줄었지만 0이 아닐 때, 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컨테이너가 없어 DB 테스트가 실패했을 때(이때는 멈춰 보고한다)
11. **변경 범위**: staged 경로가 모두 아래 안에 있다.
    - `snsg/build.gradle`, `snsg/.env.example`, `snsg/src/main/resources/application.yaml`, `snsg/src/main/resources/application-local.yaml`
    - `snsg/src/main/java/com/shineunsigan/snsg/user/` 아래 `infrastructure/storage/`, `infrastructure/event/ProfileImageCleanupListener.java`(주석만), `application/`(설정 record 추가, `port/out/FileStoragePort.java` 주석), `presentation/`(리다이렉트 컨트롤러), `domain/exception/UserErrorCode.java`(404용 코드를 더할 때만)
    - `snsg/src/test/java/com/shineunsigan/snsg/user/` 아래 새 테스트
    - `docs/api-guide.md`, `.claude/tasks/SHI-211/task.md`
    - 검증: `git diff --staged --name-only` → 위 목록 밖 경로 없음
    - GOAL 12~14도 이 목록 안에서 끝난다(`infrastructure/storage/`, `application.yaml`, user 테스트). common·`LocalFileStorageAdapter`·auth 파일은 GOAL 12~14 때문에 바뀌지 않는다.

> GOAL 12~14는 ecc 리뷰가 찾은 GOAL 밖 문제를 사용자가 이번 작업에 넣기로 한 것이다("권장안대로 진행").

12. **업로드 실패 원인을 로그로 남긴다.**
    - 지금은 `S3FileStorageAdapter.store`가 `SdkException`을 잡아 `BusinessException(PROFILE_IMAGE_STORE_FAILED)`로 바꿀 때 원인이 어디에도 남지 않는다. `BusinessException`은 cause를 받지 않고, `GlobalExceptionHandler.handleBusinessException`은 로그를 남기지 않는다.
    - 던지기 전에 `error` 수준으로 한 번 로그를 남긴다. 로그에는 버킷·키(`images/profiles/<파일명>`)·예외(스택 포함)가 있고, 파일 내용 바이트와 자격 증명은 없다.
    - 수준 근거: 같은 클래스의 `delete` 실패는 이미 끝난 수정·탈퇴의 뒷정리라 사용자 영향이 없어 `warn`이다. 업로드 실패는 사용자에게 500을 주고 기능이 막힌 상태라, 운영 알림이 걸리는 `error`로 구분한다.
    - 검증: `S3FileStorageAdapterTest`가 `@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})`(선례 `PlaceCommandServiceTest`·`ImportPlacesFacadeTest`)로 PutObject 실패 때 다음을 단정한다.
      - 출력에 `ERROR`, 버킷 이름, `images/profiles/`로 시작하는 키, 던진 예외의 클래스 이름이 있다.
      - 출력에 테스트가 넘긴 파일 내용 문자열이 없다.
      - 여전히 `PROFILE_IMAGE_STORE_FAILED`가 던져진다.
      - 실행: `cd snsg && ./gradlew cleanTest test --tests '*S3FileStorageAdapterTest'` → 실패 0
13. **S3 호출에 시간 초과를 건다**(`.claude/rules/package-structure.md` 외부 API 연동 절 "시간 초과는 config 빈이 건다").
    - `S3StorageConfig`가 만드는 `S3Client`에 다음 셋을 건다.
      - HTTP 클라이언트(`UrlConnectionHttpClient.builder()`)의 연결 시간 초과·응답(소켓) 시간 초과
      - 클라이언트 override 설정의 호출 전체 시간 초과(`apiCallTimeout`, 재시도 포함)
    - 값은 `file-storage.s3` 아래 설정(`S3StorageProperties`의 `Duration` 필드)에서 읽고, `application.yaml`에 기본값을 둔다. 연결 2s, 응답 5s, 호출 전체 10s다. 새 환경변수는 없다.
    - 검증: `grep -nE 'connect-timeout|socket-timeout|api-call-timeout' snsg/src/main/resources/application.yaml` → 세 키가 `file-storage.s3` 아래에 있음
    - 검증: `S3StorageConfigTest`(ApplicationContextRunner)의 S3 모드 경우가 만들어진 `S3Client`의 `serviceClientConfiguration().overrideConfiguration().apiCallTimeout()`이 설정 값과 같은지 단정한다. HTTP 클라이언트의 연결·응답 시간 초과는 공개 API로 꺼내 볼 수 없어 테스트하지 않는다. 시간 초과가 실제로 끊는지도 라이브러리 동작이라 테스트하지 않는다(testing.md, `SemasClientConfig` 선례). 실행: `cd snsg && ./gradlew cleanTest test --tests '*S3StorageConfigTest'` → 실패 0
    - 검증: `.env.example` 변경은 GOAL 6의 두 키뿐이다. `git diff --staged -U0 -- snsg/.env.example | grep -E '^\+[A-Z0-9_]+='` → `FILE_STORAGE_S3_BUCKET=`, `FILE_STORAGE_CLOUDFRONT_URL=` 두 줄만
14. **S3 객체 캐시를 하루로 줄인다**(사용자 결정 "캐시 기간을 짧게"). 지운 얼굴 사진이 CloudFront·브라우저에 남는 기간을 줄이기 위해서다.
    - PutObject의 Cache-Control은 `public, max-age=86400`이다. `immutable`은 뺀다.
    - 근거: `immutable`은 max-age 동안 새로고침에도 다시 확인하지 말라는 뜻이다. 지운 사진을 빨리 걷어내려는 목적과 반대이고, 하루짜리 캐시에서 얻는 이득은 작다.
    - 검증: `grep -n 'max-age' snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/storage/S3FileStorageAdapter.java` → `public, max-age=86400`만 있음(`31536000`·`immutable` 없음)
    - 검증: GOAL 1의 `S3FileStorageAdapterTest`가 새 값을 단정한다(위 GOAL 12 실행 명령에 포함)

## 비목표
- CloudFront 주소 끝 슬래시를 기동 때 거부하는 것은 후속 이슈로 뺀다. 이번에는 GOAL 3의 "슬래시 하나로 이어짐" 단정만 지킨다.
- 업로드 실패 로그(GOAL 12) 때문에 `common`의 `BusinessException`(cause 받기)·`GlobalExceptionHandler`(로그 남기기), `LocalFileStorageAdapter.store`, auth의 Kakao adapter 로그를 고치지 않는다.
- 시간 초과(GOAL 13)를 환경변수로 열지 않는다. 재시도 정책(횟수·백오프)은 SDK 기본값을 그대로 둔다.
- DB에 저장하는 값, API 응답 값, 요청 검증(업로드 경로 형식·다른 회원 사용 여부), 가입 흐름(auth)은 바꾸지 않는다. 절대 CloudFront URL을 DB·응답에 넣지 않는다.
- 기존 DB의 `/images/profiles/...` 행(파일은 이미 사라짐)을 마이그레이션하지 않는다. 리다이렉트 뒤 CloudFront가 404·403을 주는 것은 지금 404와 같은 결과로 받아들인다. 마이그레이션 파일을 만들지 않는다.
- 기본 이미지 `/images/default-profile.png`는 지금처럼 jar 정적 리소스로 서버가 준다. S3로 옮기지 않는다.
- 이미 쓰고 있는 `FILE_STORAGE_DIRECTORY` 키·`file-storage.directory` 설정은 지우지 않는다(local이 쓴다). `.env.example`에 "local 전용" 주석 한 줄을 다는 것은 허용한다.
- 기존 테스트의 단정을 고치지 않는다. 생성자·시그니처 변경에 따른 컴파일 맞춤만 허용한다(`LocalFileStorageAdapterTest` 등).
- `SecurityConfig`, `CorsConfig`, `OpenApiConfig`, auth·schedule·place·admin 도메인 파일은 건드리지 않는다.
- `.claude/rules/`, `CLAUDE.md`, `.claude/skills/`는 고치지 않는다. 규칙 문서 갱신은 refine-docs 단계가 한다.
- S3 버킷·CloudFront 배포·IAM task role·ECS task definition은 저장소 밖 작업이라 코드에서 만들거나 고치지 않는다(아래 참고에 값만 적는다). 워크트리·메인 디렉터리의 `snsg/.env`를 고치지 않는다.
- 사진 업로드를 서명 URL(presigned)로 바꾸거나 CloudFront 서명 URL을 쓰지 않는다. 업로드는 지금처럼 서버가 받아 S3에 쓴다.
- 리다이렉트에서 S3 객체 존재 여부를 확인하지 않는다(요청마다 S3 호출이 생긴다).
- 판단이 갈릴 때
  - S3 모드 판정 키를 `@ConditionalOnProperty`로 읽으면 애노테이션이 읽는 값이라 record에 넣지 않고 yaml 주석으로 이유를 남긴다(`.claude/rules/package-structure.md` 설정 절의 예외).
  - `@WebMvcTest` 슬라이스에 `ProfileImageResourceConfig`가 조건 때문에 빠지거나 반대로 S3 쪽 빈이 올라와 기존 컨트롤러 테스트 컨텍스트가 깨지면, 기존 테스트를 고치지 말고 조건·설정 위치를 고친다. 그래도 안 되면 멈춰 보고한다.

## 제약
- 패키지 구조·의존 방향·설정·외부 연동은 `.claude/rules/package-structure.md`를 따른다. 특히 다음 절이다.
  - 의존 방향(presentation은 infrastructure를 import하지 않음, 공유 설정 record는 application)
  - Port(adapter 빈 이름이 다른 도메인과 겹치지 않게)
  - 설정(`@ConfigurationProperties`, prefix, `.env.example`, task definition)
  - 이벤트(삭제는 기존 `ProfileImageCleanupListener` AFTER_COMMIT 경로 그대로)
  - "지울 대상이 사용자가 보낸 값에서 왔으면" 항목(삭제 adapter도 형식 밖 값이면 아무것도 지우지 않는다)
- API·문서는 `.claude/rules/api.md`를 따른다(문서 절. 스펙 재생성이 필요하면 `cd snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'`. 이번에는 스펙이 바뀌지 않아야 한다).
- 테스트는 `.claude/rules/testing.md`를 따른다. 대상과 같은 패키지에 대상 이름 + `Test`로 두고, 실제 AWS를 부르지 않는다. 설정만 바꾼 부분은 테스트하지 않지만, 모드별 빈 선택·기동 실패는 우리가 정한 판단이라 GOAL 4대로 테스트한다.
- 임시 파일은 `.claude/rules/temp-files.md`대로 세션 스크래치패드에만 두고, 스테이징 전에 워크트리에서 치운다.
- 커밋하지 않는다. `git add`로 스테이징까지만 한다(`.claude/skills/implement/SKILL.md`). 테스트는 원래 코드와 별도 커밋이 되도록 보고 때 나눌 계획을 함께 적는다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고
- 이름 제안(바꿔도 된다)
  - S3 어댑터: `user/infrastructure/storage/S3FileStorageAdapter`. 다른 도메인에 같은 이름의 빈이 없다.
  - S3 설정·클라이언트: `user/infrastructure/storage/S3StorageConfig`(`S3Client` 빈, 리전 `ap-northeast-2`, `DefaultCredentialsProvider`), 설정 record `S3StorageProperties(String bucket, String region)`.
  - CloudFront 주소 record: `user/application/ProfileImageCdnProperties(String cloudfrontUrl)`. `UserQueryService`(application)와 S3 config(infrastructure, 빈 값 판정)가 읽으므로 application에 둔다(package-structure.md 의존 방향 절의 "같은 설정값" 규칙). presentation은 이 record를 직접 읽지 않는다.
  - `UserQueryService` 새 메서드 이름 예: `resolveProfileImageCdnUrl(String fileName)` → `Optional<String>`.
  - 리다이렉트 컨트롤러: `user/presentation/ProfileImageRedirectController`(`@Hidden`).
  - 설정 키: `file-storage.type: s3`(base) / `local`(`application-local.yaml`), `file-storage.s3.bucket: ${FILE_STORAGE_S3_BUCKET:}`, `file-storage.s3.region: ap-northeast-2`, `file-storage.cloudfront-url: ${FILE_STORAGE_CLOUDFRONT_URL:}`. `FileStorageProperties(String directory)`의 prefix `file-storage`와 겹치지 않게 record를 나누면 prefix를 하위 키로 준다(`file-storage.s3` 등). 같은 prefix 아래 서로 다른 record가 있어도 바인딩은 되지만, 모르는 키 무시에 기대게 된다.
  - 테스트: `S3FileStorageAdapterTest`, `ProfileImageRedirectControllerTest`, 모드 선택은 설정 클래스 이름 + `Test`(예: `S3StorageConfigTest`).
- **SHI-190 함정**: `@ConfigurationProperties`는 해석하지 못한 `${X}`를 글자 그대로 넣는다. 그래서 `@NotBlank`만으로는 운영에서 키가 빠진 것을 잡지 못한다. 빈 기본값 `${X:}` + 비어 있으면 실패, 또는 yaml에 두지 않고 relaxed binding이 GOAL 5를 만족한다. 선례는 `oauth.kakao.admin-key: ${KAKAO_ADMIN_KEY:}`다(그쪽은 실패 대신 거부를 택했다).
- **record는 모든 모드에서 바인딩된다고 보고 짠다.** `@ConfigurationPropertiesScan`(`SnsgApplication`)이 base package 아래 `@ConfigurationProperties` record를 모두 등록한다(`FileStorageProperties`도 그래서 운영에서 바인딩된다. `ProfileImageResourceConfig`의 `@EnableConfigurationProperties`는 스캔이 없는 `@WebMvcTest` 슬라이스용이다).
  - 그래서 record에는 검증을 달지 않는다.
  - "비어 있으면 기동 실패"는 S3 모드 조건이 붙은 빈의 생성자(또는 `@Bean` 메서드)가 `null`·공백을 보고 예외를 던져 만든다. 예외 메시지에는 키 이름(`FILE_STORAGE_S3_BUCKET` 등)을 넣는다. 값은 넣지 않는다.
  - 이렇게 하면 local은 빈 값이 바인딩돼도 그 빈이 없어 뜬다.
  - application 계층에는 S3 모드 조건이 붙은 빈을 만들지 않는다. `UserQueryService`는 모든 모드에서 그대로 만들어지고, local에서는 이 메서드를 부를 컨트롤러가 없다.
- **`UserQueryService` 의존·크기 점검** (package-structure.md Service와 Facade 절)
  - 지금 생성자 의존: `UserRepository`, `UserSocialAccountRepository`, `UserNicknameHistoryRepository`, `NicknameProperties`.
  - CloudFront record를 더해도 "설정(`@ConfigurationProperties` record)은 의존으로 세지 않는다"에 해당해, 의존 수는 repository 3개 그대로다(상한 4 이하).
  - 파일은 지금 122줄이다. 메서드 하나(주석 포함 15~20줄)를 더해도 150줄 기준 안이다. 넘으면 멈춰 보고한다.
- **기존 테스트 영향**
  - `UserQueryServiceTest`는 `@InjectMocks` 생성자 주입이다. 새 record 타입의 필드가 없으면 Mockito가 `null`을 넘겨, 기존 테스트는 고치지 않아도 컴파일·통과한다. 새 테스트를 위해 record 필드(예 `new ProfileImageCdnProperties("https://cdn.example.test")`)를 더하는 것은 허용한다. 기존 단정은 바꾸지 않는다.
  - `UserControllerTest`·`SecurityConfigTest`·`AdminSecurityConfigTest`는 `UserQueryService`를 `@MockitoBean`으로 대신하므로 생성자 변경의 영향이 없다.
  - 새 리다이렉트 컨트롤러는 `@WebMvcTest(controllers = ...)`로 대상을 지정한 기존 슬라이스에는 올라오지 않는다. 대상을 지정하지 않은 슬라이스가 있어도 local 프로파일에서는 S3 조건으로 빠진다.
- **refine-docs에 넘길 규칙 후보**: "DB를 읽지 않는 조회 메서드(설정·domain 규칙만으로 답하는 것)는 QueryService에 두되, 그 메서드에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 달아 클래스의 readOnly 트랜잭션(커넥션 획득)을 끈다. 예: `UserQueryService`의 프로필 사진 CDN 주소 해석(SHI-211)."
  - 함께 넘길 결정: "조회 서비스는 Entity·애그리거트 단위로 이름 짓는다(enum·값 객체 이름으로 QueryService를 만들지 않는다)."
- 운영 task definition의 `FILE_STORAGE_DIRECTORY`는 S3 모드에서 쓰이지 않는다. `application.yaml`의 `file-storage.directory: ${FILE_STORAGE_DIRECTORY}`(기본값 없음)는 지우지 않는다. 대신 S3 모드에서 그 record가 쓰이지 않음을 GOAL 4 (f)로 보인다.
  - 운영 task definition에서 `FILE_STORAGE_DIRECTORY`를 빼도 기동은 되어야 한다. 빼는 것은 저장소 밖 작업이고 이번 범위가 아니다.
- `ProfileImageResourceConfig`는 `WebMvcConfigurer`라 `@WebMvcTest` 슬라이스에도 올라온다(파일 주석). 조건을 달아도 테스트는 local 프로파일이라 지금처럼 올라온다.
- 따라 할 기존 코드
  - 흐름·안전장치: `LocalFileStorageAdapter.delete`(`snsg/src/main/java/com/shineunsigan/snsg/user/infrastructure/storage/LocalFileStorageAdapter.java:56`). 형식 검사 → 파일명만 떼어 우리 위치에서 찾고, 실패는 warn 로그만 남긴다. S3 쪽도 같은 순서로 한다. 로그에는 경로와 예외만 남기고 자격 증명·서명 헤더를 찍지 않는다(SDK 예외 메시지에는 키가 없다).
  - 오류: `UserErrorCode.PROFILE_IMAGE_STORE_FAILED`(`snsg/src/main/java/com/shineunsigan/snsg/user/domain/exception/UserErrorCode.java:21`). 저장 실패는 `ErrorType.INTERNAL`이다.
  - 조건부 빈: `common/config/SchedulingConfig`·`OpenApiConfig.adminApi`의 `@ConditionalOnProperty`
  - 설정 record 위치: `user/application/NicknameProperties`(application에 둔 record), `user/infrastructure/storage/FileStorageProperties`(infrastructure 전용)
  - 숨긴 컨트롤러: `auth/presentation/KakaoWebhookController`(`@Hidden`, 문서에 나오지 않는 공개 경로)
  - 테스트: `LocalFileStorageAdapterTest`(형식 밖 경로 목록, `@ParameterizedTest @ValueSource`), 컨트롤러 슬라이스는 `UserControllerTest`(`@AutoConfigureMockMvc(addFilters = false)`). 외부 SDK adapter를 Mockito로 대신하는 선례는 없다(외부 HTTP는 `MockRestServiceServer`). SDK가 HTTP를 직접 열어 `MockRestServiceServer`를 쓸 수 없으므로 `S3Client` 인터페이스를 mock으로 둔다. testing.md의 "외부 API adapter의 요청·응답 변환을 테스트한다"에 해당한다.
- 리다이렉트 응답 코드는 302(`HttpStatus.FOUND`)다. 301은 브라우저가 영구 캐시해, 나중에 커스텀 도메인으로 바꿀 때 되돌릴 수 없다. 302에 `max-age=86400`을 걸어 하루 동안만 브라우저가 리다이렉트를 재사용한다. S3 객체도 하루(`public, max-age=86400`)다. 처음에는 파일명이 UUID라 1년 immutable로 정했으나, 지운 얼굴 사진이 CDN에 남는 기간을 줄이려고 사용자가 짧게 바꿨다(GOAL 14).
- CloudFront 캐시 정책은 관리형 `CachingOptimized`(기본 TTL 1일, 최대 1년, 원본 `Cache-Control`이 있으면 그 값을 따른다)를 전제로 한다. 그래서 GOAL 14로 바뀌는 저장소 밖 설정은 없다. 오케스트레이터가 배포의 캐시 정책을 확인한다. 이미 올라간 객체는 예전 헤더를 그대로 갖는다(dev 버킷은 아직 비어 있어 해당 없음).
- 시간 초과 값(GOAL 13) 근거
  - 서버와 버킷은 같은 리전(`ap-northeast-2`)이고 객체는 5MB 이하라, 정상 PutObject·DeleteObject는 1초 안에 끝난다.
  - 연결 2s: 같은 리전 TLS 연결에 충분하고, 네트워크 장애를 빨리 드러낸다.
  - 응답 5s: 5MB 업로드 뒤 응답까지의 여유다.
  - 호출 전체 10s: SDK 기본 재시도(최대 3회 시도)를 포함한 상한이다. 사진 삭제는 커밋 뒤 요청 스레드에서 도는 AFTER_COMMIT 리스너(`ProfileImageCleanupListener`)라, S3가 느리면 프로필 수정·탈퇴 응답이 늦어진다. 이 경로의 지연 상한이 10초다.
  - 업로드 요청도 같은 상한을 받는다.
- 업로드 실패 로그(GOAL 12)는 `error`, 삭제 실패는 `warn`이다. SDK 예외 메시지에는 요청 ID·상태 코드가 실리고 서명 키는 실리지 않는다. 그래도 요청 헤더나 객체 내용을 직접 찍지 않는다.
- 404 응답 형식(우리 봉투인지 빈 본문인지)은 구현이 정한다. 새 ErrorCode가 필요하면 user `UserErrorCode`에 `ErrorType.NOT_FOUND`로 더하는 것은 허용한다. 다만 공개 문서에 오류 코드로 적지 않는다(`@Hidden` 경로).
- 저장소 밖 작업 값(오케스트레이터가 할 일, PR background에도 적는다)
  - S3 버킷: 리전 `ap-northeast-2`, 퍼블릭 액세스 차단 켬, 객체 키 `images/profiles/<uuid>.<png|jpg>`. ACL 없이 버킷 소유자 강제.
  - CloudFront: 오리진은 이 버킷(OAC), origin path 없음. URL 경로가 S3 키와 같다: `https://<배포>.cloudfront.net/images/profiles/<uuid>.png`. 뷰어 프로토콜은 HTTPS로 리다이렉트한다. 버킷 정책은 `cloudfront.amazonaws.com`에 `s3:GetObject`를 주고 `AWS:SourceArn`을 그 배포로 한정한다.
  - IAM task role(IAM 권한자가 만든다): `s3:PutObject`·`s3:DeleteObject`, 대상 `arn:aws:s3:::<버킷>/images/profiles/*`. 신뢰 주체는 `ecs-tasks.amazonaws.com`.
  - ECS task definition `snsg-be-web-task`: `taskRoleArn`, 환경변수 `FILE_STORAGE_S3_BUCKET=<버킷>`, `FILE_STORAGE_CLOUDFRONT_URL=https://<배포>.cloudfront.net`(끝 슬래시 없음). `FILE_STORAGE_DIRECTORY`는 S3 모드에서 쓰이지 않는다.
  - **배포 순서**: 위 값과 task role을 넣은 task definition이 먼저 있어야 한다. 새 이미지를 배포하면 `deploy.yml`이 최신 task definition의 이미지만 바꾸므로, 값이 없으면 기동이 실패한다(의도한 동작).
- 머지 뒤 메인 `snsg/.env`에 더할 키는 없다(local은 디스크 모드라 새 키가 필요 없다).

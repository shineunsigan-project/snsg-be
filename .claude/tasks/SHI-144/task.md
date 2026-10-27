## 배경

- Linear 이슈 SHI-144 "도메인 의존 규칙을 ArchUnit 테스트로 강제".
- 설계 목표(사용자 합의): 애그리거트 루트 ↔ 구성요소 관계가 아닌 한, 나중에 도메인을 서비스로 분리해도 수정 범위가 infrastructure에만 생기게 한다. 지금은 이 규칙을 리뷰로만 지킨다. 테스트가 지키게 한다.
- 근거 문서: 저장소 밖 `C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`의 3.3절("더 좋은 방법 — 규칙을 사람이 아니라 테스트가 지키게 한다"), 결정 D16(가입 세션 15분, `auth.signup-session-ttl`).
- 할 일은 넷이다.
  1. 현재 유일한 위반을 없앤다. `auth/presentation/AuthCookieFactory`가 `auth.infrastructure.jwt.JwtProperties`를 import해 refresh 쿠키 수명을 정한다. 토큰 수명 설정을 auth application의 `@ConfigurationProperties` record로 옮긴다(선례 `auth/application/KakaoWebhookProperties`). 가입 세션 수명(쿠키 max-age 30분 하드코딩, Redis TTL 30분 하드코딩)도 같은 record의 설정값 하나로 묶고 15분으로 줄인다.
  2. ArchUnit 테스트를 추가한다.
  3. `.claude/rules/package-structure.md`의 "도메인 간 참조" 절 모호함(허용 대상에 enum이 없는데 바로 아래에서 enum을 adapter에서 변환하라고 함)을 고치고, 새 규칙을 적는다.
  4. `.claude/skills/spec/SKILL.md`에 "GOAL에는 결과와 합의한 결정만 쓴다"는 규칙을 더한다(이 task.md를 쓰는 과정에서 사용자와 합의).

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-144` (브랜치 `refactor/SHI-144-architecture-rules`, 베이스 `ae19f4b` = origin/develop). `snsg/.env` 복사돼 있다.
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 저장소 밖 `planning/`도 읽기만 한다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. **아래 검증 명령은 모두 Bash 문법이다.** `&&`는 PowerShell 5.1에서 동작하지 않는다.
- DB·Redis: Docker 컨테이너 `snsg-postgres`(5432)·`snsg-redis`(6379)가 떠 있다. 확인: `netstat -an | grep -E ':(5432|6379) .*LISTEN'` → 두 포트 모두 LISTENING. 꺼져 있으면 테스트나 설정을 고쳐 우회하지 말고 멈춰서 보고한다.
- 이미 깨져 있는 것: 없음. 베이스에서 `./gradlew test` → 85개 전부 통과, 실패 0 (2026-10-27 spec 작성 시 확인).
- 8080 포트와 DB·Redis는 모든 워크트리가 공유한다(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의").

## GOAL

검증 명령은 모두 Bash에서 실행한다. 아래 "설정 record"는 GOAL 2의 record를 가리킨다(이름은 구현이 정한다. 제안은 `## 참고`).

### 설정과 동작

1. 같은 도메인의 presentation·application·domain이 infrastructure를 참조하지 않는다(main 코드).
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -rn 'com.shineunsigan.snsg.[a-z]*.infrastructure' snsg/src/main/java/com/shineunsigan/snsg/*/presentation snsg/src/main/java/com/shineunsigan/snsg/*/application snsg/src/main/java/com/shineunsigan/snsg/*/domain` → 출력 없음 (FQN 사용까지 잡도록 `import`를 빼고 찾는다)
2. access token 수명, refresh token 수명, 가입 세션 수명을 `snsg/src/main/java/com/shineunsigan/snsg/auth/application/` 바로 아래의 `@ConfigurationProperties(prefix = "auth")` record 하나에서 읽는다. JWT 서명 키(`jwt.secret`)는 `auth/infrastructure`의 설정에 남고, 위 record에는 secret이 없다. (합의 결정: prefix `auth`, 가입 세션 키 `auth.signup-session-ttl`. 토큰 수명 키 이름은 `auth.` 아래면 된다.)
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -ln 'ConfigurationProperties(prefix = "auth")' snsg/src/main/java/com/shineunsigan/snsg/auth/application/*.java` → 파일 하나
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -il 'secret' $(grep -ln 'ConfigurationProperties(prefix = "auth")' snsg/src/main/java/com/shineunsigan/snsg/auth/application/*.java)` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -rln 'ConfigurationProperties(prefix = "jwt")' snsg/src/main/java` → `auth/infrastructure/` 아래 파일 하나
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n 'Validity\|Ttl\|Duration' $(grep -rln 'ConfigurationProperties(prefix = "jwt")' snsg/src/main/java)` → 출력 없음 (jwt 설정에는 수명이 남지 않는다)
3. `application.yaml`: `jwt:` 아래에는 `secret: ${JWT_SECRET}`만 남는다. `auth:` 아래에 access 수명 `30m`, refresh 수명 `14d`, `signup-session-ttl: 15m`이 있다. access·refresh 값은 바뀌지 않는다. `snsg/.env`와 프로파일별 yaml(`application-local/dev/prod.yaml`)은 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n -A4 '^jwt:\|^auth:' snsg/src/main/resources/application.yaml` → `jwt:` 아래 `secret` 한 줄, `auth:` 아래 `30m`·`14d`·`signup-session-ttl: 15m` 세 줄
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --short -- snsg/src/main/resources snsg/.env` → `snsg/src/main/resources/application.yaml`만
4. 가입 세션 Redis TTL(`auth/infrastructure/redis/RedisSignUpSessionAdapter`)과 가입 쿠키 max-age(`auth/presentation/AuthCookieFactory`)가 모두 GOAL 2의 가입 세션 설정값에서 나온다. 30분 하드코딩 상수는 main 코드에 없다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -rn 'ofMinutes\|ofSeconds(1800)' snsg/src/main/java` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && R=$(basename $(grep -ln 'ConfigurationProperties(prefix = "auth")' snsg/src/main/java/com/shineunsigan/snsg/auth/application/*.java) .java) && grep -c "$R" snsg/src/main/java/com/shineunsigan/snsg/auth/presentation/AuthCookieFactory.java snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/redis/RedisSignUpSessionAdapter.java` → 두 파일 모두 1 이상
5. 기존 테스트의 단정과 개수가 바뀌지 않는다(설정 이동은 동작 변화가 없다). 컴파일을 맞추려고 기존 테스트의 생성자 호출·빈 타입·import를 바꾸는 것은 허용한다. 기존 테스트 파일을 지우거나 이름을 바꾸지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git diff HEAD -U0 -- snsg/src/test/java | grep -E '^[-+].*(assert|andExpect|@Test|@DisplayName)'` → 출력 없음
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git diff HEAD --name-status -- snsg/src/test/java | grep -v '^M'` → 출력 없음 (새 ArchUnit 테스트 파일은 GOAL 7이 따로 본다. 스테이징 전이면 `git status`의 `??`로 보인다)
6. FE 문서: `docs/api-guide.md`에서 가입 세션의 30분 언급(오류 표 `INVALID_SIGNUP_TOKEN` 행, "가입" 절 두 곳)이 15분이 된다. access token의 30분 언급 두 곳(refresh를 쿠키로 주는 이유 문단, 로그아웃 뒤 access token 문장)은 남는다. `docs/openapi.yaml`에는 가입 세션 수명 언급이 없으므로 바뀌지 않는다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n '30분' docs/api-guide.md` → 정확히 2줄, 둘 다 access token 문장
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n '15분' docs/api-guide.md` → 3줄, 모두 가입 세션 문장
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --short -- docs` → `docs/api-guide.md`만

### ArchUnit

7. `snsg/build.gradle`에 `com.tngtech.archunit:archunit-junit5`가 `testImplementation`으로 더해지고, ArchUnit 테스트가 아래 7개 규칙을 **규칙마다 별도 테스트로** 검사한다(테스트 클래스·메서드 이름은 자유. 단 클래스 이름에 `Architecture`를 넣으면 아래 명령을 그대로 쓸 수 있다. 다르게 지으면 implement가 바꾼 명령을 보고한다).
   - 공통 조건(합의 결정)
     - 프로덕션 클래스만 검사한다. 테스트 클래스는 검사하지 않는다.
     - base package `com.shineunsigan.snsg` 안의 클래스와 그 안으로 향하는 의존만 본다. 라이브러리 패키지(`org.springframework.data.domain` 등)가 계층 이름과 같아도 걸리지 않는다.
     - 도메인 목록을 하드코딩하지 않는다. base package 바로 아래 패키지 중 `common`을 뺀 것이 도메인이다. base package 바로 아래 클래스(`SnsgApplication`)는 규칙 대상이 아니다. 도메인이 늘어도 테스트를 고치지 않는다.
   - 규칙 (같은 도메인 = 같은 최상위 패키지)
     | # | 규칙 |
     |---|---|
     | R1 | `<d>.domain..`은 같은 도메인의 `application`·`presentation`·`infrastructure`에 의존하지 않는다 |
     | R2 | `<d>.application..`은 같은 도메인의 `presentation`·`infrastructure`에 의존하지 않는다 |
     | R3 | `<d>.presentation..`은 같은 도메인의 `infrastructure`에 의존하지 않는다 |
     | R4 | 도메인 A의 클래스가 다른 도메인 B에 의존하면, 그 클래스는 `A.infrastructure..`에 있다 |
     | R5 | 도메인 A → 다른 도메인 B 의존의 대상은 `B.application.port.in..`, `B.application.dto..`, `B.application.event..`, `B.domain.constants..` 중 하나다 |
     | R6 | 도메인 사이에 순환이 없다. 순환 검사 대상은 도메인뿐이다(`common` 위반이 순환으로 겹쳐 잡히지 않는다) |
     | R7 | `common..`은 어떤 도메인에도 의존하지 않는다 |
   - 현재 코드가 7개 모두 통과한다.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n "testImplementation 'com.tngtech.archunit:archunit-junit5" snsg/build.gradle` → 한 줄
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144/snsg && rm -rf build/test-results/test && ./gradlew test --tests '*Architecture*' --console=plain` → BUILD SUCCESSFUL
   - 검증(위 명령 직후): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144/snsg && cat build/test-results/test/*.xml | grep -o '<testsuite[^>]*' | grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"'` → tests 합 7, failures·errors 0
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n '"auth"\|"user"\|snsg\.auth\|snsg\.user' snsg/src/test/java/com/shineunsigan/snsg/*Architecture*.java` → 출력 없음 (도메인 이름 하드코딩 없음)
8. **규칙마다 위반 클래스를 임시로 main에 넣으면 그 규칙의 테스트 하나만 실패한다. 새 최상위 패키지에 넣은 위반 클래스도 도메인으로 잡혀 실패한다.** 7개 규칙 각각 + 새 최상위 패키지 1건, 최소 8건이다.
   - 판정 방법: 검증 에이전트는 파일을 만들 수 없다. 이 항목은 **(a) implement 보고에 건별로 "넣은 위반 클래스 경로, 참조한 대상 FQN, 실패한 테스트 이름(하나), 나머지 통과"가 있고 (b) 위반 클래스가 워크트리에 남아 있지 않으면** PASS다. 오케스트레이터가 사용자 보고 전에 권장 세트(`## 참고`)로 직접 재실행한다.
   - 검증(b): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --short --untracked-files=all | grep -ci probe; ls snsg/src/main/java/com/shineunsigan/snsg` → `0`, 목록은 `SnsgApplication.java`, `auth`, `common`, `user`뿐
   - 검증(b): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --short --untracked-files=all -- snsg/src/main | grep -E '^(\?\?|A)'` → 한 줄, GOAL 2의 설정 record 파일

### 전체 빌드

9. 전체 빌드·테스트가 통과한다. 기존 85개 전부와 ArchUnit 7개가 포함되고 실패 0.
   - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144/snsg && rm -rf build/test-results/test && ./gradlew build --console=plain` → BUILD SUCCESSFUL
   - 검증(위 명령 직후): `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144/snsg && cat build/test-results/test/*.xml | grep -o '<testsuite[^>]*' | grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' | awk -F'"' '{split($0,k,"=");a[k[1]]+=$2} END {for(x in a) print x, a[x]}'` → tests 92 이상, failures 0, errors 0, skipped 0

### 규칙 문서

10. `.claude/rules/package-structure.md`
    - "의존 방향" 절: `presentation은 infrastructure를 import하지 않는다.` 문장이 있다. 같은 도메인의 presentation과 infrastructure가 같은 설정값을 읽어야 하면 그 설정 record를 application에 둔다는 내용이 있다.
    - "도메인 간 참조" 절: import 허용 대상이 넷이다 — 상대 도메인의 `application/port/in`(UseCase), `application/dto`(UseCase의 Command·Result), `application/event`, `domain/constants`(계약에 실린 enum).
    - enum 항목: 상대 도메인 enum은 **infrastructure의 adapter·리스너에서만** 참조하고 그 자리에서 자기 enum(또는 필요한 값)으로 바꾼다. application·domain·presentation은 참조하지 않는다. 계약 전용 enum을 따로 만들지 않는다. 예외: 계약에 내보내면 안 되는 값이 있는 enum만 그 도메인에 계약용 enum을 둔다. 기존 하위 항목(`default` 없는 switch, 값만 가진 상수 중복 허용)은 남는다.
    - 이 규칙들(계층 방향, infrastructure 금지, 도메인 간 참조 허용 대상, 도메인 간 순환 금지, common의 도메인 import 금지)을 ArchUnit 테스트가 검사한다고 적혀 있다.
    - "Port" 절 예시의 설정 키가 바뀐 키(`auth.` 아래)로 갱신된다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && for p in 'presentation은 infrastructure를 import하지 않는다.' 'application/port/in' 'application/dto' 'application/event' 'domain/constants' 'ArchUnit' '계약 전용 enum' 'adapter·리스너에서만' 'auth.access-token'; do grep -qF "$p" .claude/rules/package-structure.md && echo "OK $p" || echo "MISSING $p"; done` → 9줄 모두 `OK` (`auth.access-token`은 토큰 수명 키를 다르게 지었으면 그 키로 바꿔 확인한다)
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -nF -e 'jwt.access-token-validity' -e '상대 도메인 것을 import하지 않고' .claude/rules/package-structure.md` → 출력 없음
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n 'application.*설정 record\|설정 record.*application' .claude/rules/package-structure.md` → 1줄 이상
11. `.claude/rules/testing.md`에 ArchUnit 테스트 항목이 있다: 테스트 위치(클래스 이름), 프로덕션 클래스만 검사한다는 것, 도메인이 base package 바로 아래 패키지(`common` 제외)로 자동 인식되어 도메인을 추가해도 테스트를 고치지 않는다는 것.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && grep -n 'ArchUnit' .claude/rules/testing.md` → 1줄 이상, 그 항목에 "프로덕션"과 "common"이 나온다
12. `.claude/skills/spec/SKILL.md`에 GOAL 작성 규칙이 더해진다. 내용:
    - GOAL에는 결과(무엇이 참이면 끝인가)와 사용자와 합의한 결정만 쓴다. 구현 에이전트가 바꿔도 되는 이름·파일 구성·테스트 메서드 이름·정확한 변경 파일 목록은 `## 참고`로 내린다.
    - 이유: 검증은 GOAL에 적힌 결과로 해석 차이를 잡고, 적히지 않은 설계 품질은 오케스트레이터 재확인과 사용자 diff 승인이 잡는다. 구현 세부를 GOAL에 박으면 승인 대상이 spec 에이전트가 정한 세부가 되고, 같은 결과를 다른 형태로 이뤄도 FAIL이 나 재작업이 돈다.
    - "프롬프트 정밀도" 절(경로·브랜치·베이스 커밋·검증 명령은 정밀하게)과 충돌하지 않는다: 정밀하게 적는 대상은 환경과 검증 방법이고, 이 규칙이 GOAL에서 빼는 것은 구현의 형태다. 이 구분이 문서에 드러난다. 위치는 "그 밖의 GOAL 작성 규칙" 절이 자연스럽다.
    - 기존 문장은 지우지 않는다.
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && for p in '결과와 사용자와 합의한 결정만' '테스트 메서드 이름' '재작업' '프롬프트 정밀도'; do grep -qF "$p" .claude/skills/spec/SKILL.md && echo "OK $p" || echo "MISSING $p"; done` → 4줄 모두 `OK`
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git diff HEAD --numstat -- .claude/skills/spec/SKILL.md` → 둘째 열(삭제 줄 수) 0

### 범위

13. 변경이 아래 허용 범위 밖으로 나가지 않는다.
    - 허용: `snsg/build.gradle`, `snsg/src/main/resources/application.yaml`, `snsg/src/main/java/com/shineunsigan/snsg/auth/**`, `snsg/src/test/java/com/shineunsigan/snsg/` 바로 아래 ArchUnit 테스트 파일, `snsg/src/test/java/com/shineunsigan/snsg/auth/**`(기존 테스트 컴파일 맞춤만), `docs/api-guide.md`, `.claude/rules/package-structure.md`, `.claude/rules/testing.md`, `.claude/skills/spec/SKILL.md`, `.claude/tasks/SHI-144/task.md`
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --porcelain --untracked-files=all | cut -c4- | grep -vE '^(snsg/build\.gradle|snsg/src/main/resources/application\.yaml|snsg/src/main/java/com/shineunsigan/snsg/auth/.*|snsg/src/test/java/com/shineunsigan/snsg/[^/]*Architecture[^/]*\.java|snsg/src/test/java/com/shineunsigan/snsg/auth/.*|docs/api-guide\.md|\.claude/rules/package-structure\.md|\.claude/rules/testing\.md|\.claude/skills/spec/SKILL\.md|\.claude/tasks/SHI-144/task\.md)$'` → 출력 없음
    - 검증: `cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144 && git status --short -- snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/client snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/event snsg/src/main/java/com/shineunsigan/snsg/auth/presentation/dto docs/openapi.yaml` → 출력 없음 (계약 enum 구조·openapi·Swagger 설명 미변경)

## 커밋 계획

implement는 전부 스테이징까지만 한다. 커밋 분할은 오케스트레이터가 사용자 승인 후 아래 순서로 한다. **각 커밋은 단독으로 컴파일되고 그 시점의 테스트가 통과한다.** 파일 열거는 예상이다(구현에 따라 달라질 수 있다).

| # | 메시지 (안) | 예상 파일 | 단독 통과 근거 |
|---|---|---|---|
| ① | `refactor: 토큰 수명 설정을 auth application으로 옮긴다` | 설정 record(access·refresh만), `JwtProperties`, `JwtTokenAdapter`, `RedisRefreshTokenStoreAdapter`, `AuthCookieFactory`(refresh 부분), `application.yaml`(access·refresh 키), `JwtTokenAdapterTest`, `AuthControllerTest` | 동작 변화 없음(값 30m·14d 그대로). 생성자·빈 타입이 바뀐 테스트를 같은 커밋에서 맞춰야 컴파일된다 |
| ② | `feat: 가입 세션 수명을 설정값 하나로 묶고 15분으로 줄인다` | 설정 record(+가입 세션), `RedisSignUpSessionAdapter`, `AuthCookieFactory`(가입 쿠키 부분), `application.yaml`(`auth.signup-session-ttl: 15m`), `docs/api-guide.md` | ① 위에서 값 하나와 사용처만 더한다 |
| ③ | `test: 도메인 의존 규칙을 ArchUnit 테스트로 검사한다` | `snsg/build.gradle`, ArchUnit 테스트 | ① 뒤라 현재 코드가 통과한다. ① 앞에 두면 R3가 실패한다 |
| ④ | `docs: 도메인 간 참조 규칙을 정리하고 ArchUnit 검사를 명시한다` | `package-structure.md`, `testing.md`, 이 `task.md` | 문서만 |
| ⑤ | `docs: spec의 GOAL에는 결과와 합의한 결정만 쓴다` | `.claude/skills/spec/SKILL.md` | 문서만 |

- 사용자 제안(①수명 이동+15분 ②ArchUnit ③문서)에서 ①을 둘로 나눴다. 수명 이동은 동작이 같은 리팩터링이고 15분은 FE에 공개된 동작 변경이다. 섞으면 "동작 변화 없음"을 리뷰에서 확인할 수 없고, 15분만 되돌릴 때 리팩터링까지 되돌아간다.
- testing.md의 "테스트는 원래 코드와 별도 커밋"과의 관계: ①의 기존 테스트 수정은 컴파일 맞춤이라 ①에 둔다(분리하면 ① 단독으로 컴파일이 깨진다). 새 테스트(ArchUnit)는 ③으로 따로 간다.
- 설정 record·`AuthCookieFactory`·`application.yaml`은 ①과 ②에 걸친다. 오케스트레이터가 `git add -p`로 나눈다.

## 비목표

- refresh token 전달 방식(HttpOnly 쿠키 유지), 쿠키 속성(`Secure`, `SameSite=None`, `Path`), 기기별 세션 등 다른 auth 기능은 건드리지 않는다.
- user 계약에 실린 enum(`Gender`, `SocialProvider`, `WithdrawReason`)의 구조는 바꾸지 않는다. 지금 구조(adapter·리스너에서만 참조)가 합의된 형태다. `UserAdapter`·`UserWithdrawnEventListener`는 고치지 않는다.
- Gender `OTHER` 등 프로필 확장은 하지 않는다.
- access token 수명(30m)·refresh 수명(14d) 값은 바꾸지 않는다. `TokenResponse`의 "30분" Swagger 설명과 `docs/openapi.yaml`은 access token 이야기라 고치지 않는다.
- `JWT_SECRET` 환경변수와 `jwt.secret` 키는 그대로 둔다. `.env`·프로파일별 yaml은 고치지 않는다.
- 기획 문서 3.3의 "허용 방향 목록(auth → user, auth → schedule, schedule → user, schedule → place)"을 테스트에 하드코딩하지 않는다. 도메인 하드코딩 금지 결정과 충돌하므로 순환 금지까지만 검사한다.
- package-structure.md "common" 절의 "domain·application은 `common.exception`, `common.entity`만 import한다", infrastructure → presentation 방향은 이번에 테스트로 검사하지 않는다.
- 가입 세션 15분에 대한 새 테스트는 쓰지 않는다. 설정값만 바꾸는 변경이다(`.claude/rules/testing.md` "쓰지 않는 테스트").
- 기존 경고, 다른 파일의 스타일은 건드리지 않는다. user 도메인 코드는 고치지 않는다.
- 저장소 밖 `planning/` 문서(erd.md의 "5분" 등)는 고치지 않는다.

## 제약

- 패키지 구조는 `.claude/rules/package-structure.md`를 따른다(설정 record는 의존으로 세지 않는다, 설정값은 `@Value` 대신 record로 묶는다).
- 테스트는 `.claude/rules/testing.md`를 따른다.
- API 문서 갱신은 `.claude/rules/api.md`의 "문서" 절을 따른다.
- Redis TTL과 쿠키 수명처럼 같은 값은 한 설정값에서 가져온다(`.claude/rules/package-structure.md` "Redis").
- 임시 파일(위반 클래스, 로그)은 `.claude/rules/temp-files.md`를 따른다. 위반 클래스는 돌린 직후 지우고, 로그는 스크래치패드에 둔다. 스테이징 전에 워크트리에 남은 것이 없어야 한다.
- 메인 작업 디렉터리와 저장소 밖 파일은 고치지 않는다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

구현이 바꿔도 되는 제안이다. GOAL의 결과를 지키면 다르게 해도 된다.

- **설정 record 이름**: `AuthProperties`(`auth/application/AuthProperties.java`), 컴포넌트 `Duration accessTokenValidity`, `Duration refreshTokenValidity`, `Duration signupSessionTtl`. 키는 `auth.access-token-validity`, `auth.refresh-token-validity`, `auth.signup-session-ttl`. `@ConfigurationPropertiesScan`이 `SnsgApplication`에 이미 있어 따로 등록하지 않아도 된다. `JwtProperties`는 `record JwtProperties(String secret)`로 줄인다.
- **예상 변경 파일**
  ```
  .claude/rules/package-structure.md
  .claude/rules/testing.md
  .claude/skills/spec/SKILL.md
  .claude/tasks/SHI-144/task.md
  docs/api-guide.md
  snsg/build.gradle
  snsg/src/main/java/com/shineunsigan/snsg/auth/application/AuthProperties.java
  snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/jwt/JwtProperties.java
  snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/jwt/JwtTokenAdapter.java
  snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/redis/RedisRefreshTokenStoreAdapter.java
  snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/redis/RedisSignUpSessionAdapter.java
  snsg/src/main/java/com/shineunsigan/snsg/auth/presentation/AuthCookieFactory.java
  snsg/src/main/resources/application.yaml
  snsg/src/test/java/com/shineunsigan/snsg/ArchitectureTest.java
  snsg/src/test/java/com/shineunsigan/snsg/auth/infrastructure/jwt/JwtTokenAdapterTest.java
  snsg/src/test/java/com/shineunsigan/snsg/auth/presentation/AuthControllerTest.java
  ```
- **기존 테스트 맞춤**: `JwtTokenAdapterTest`는 `new JwtProperties(...)` 호출부, `AuthControllerTest`는 `@MockitoBean JwtProperties`를 새 record로 바꾸면 된다. `@WebMvcTest`에는 `@ConfigurationPropertiesScan`이 적용되지 않는다. 지금도 `JwtProperties`를 mock으로 넣어 수명이 null인 채 돌고, 쿠키 max-age 값은 이 테스트가 검증하지 않는다.
- **ArchUnit 버전**: Spring Boot BOM이 관리하지 않으므로 버전을 적는다. `1.4.2`가 Maven Central에 있음을 확인했다.
- **ArchUnit 테스트 이름 제안**: 클래스 `com.shineunsigan.snsg.ArchitectureTest`. 메서드 R1 `domainDoesNotDependOnOuterLayers`, R2 `applicationDoesNotDependOnPresentationOrInfrastructure`, R3 `presentationDoesNotDependOnInfrastructure`, R4 `onlyInfrastructureDependsOnOtherDomains`, R5 `otherDomainDependenciesTargetOnlyContracts`, R6 `noCyclesBetweenDomains`, R7 `commonDoesNotDependOnDomains`.
- **ArchUnit 구현 메모**
  - 방식은 `@AnalyzeClasses(importOptions = DoNotIncludeTests)` + `@ArchTest`, 또는 Jupiter `@Test` 안에서 `ClassFileImporter`로 가져와 `rule.check(...)` 중 무엇이든 된다. `@ArchTest`가 Gradle `--tests` 필터에 잡히지 않으면 Jupiter 방식으로 바꾼다.
  - 계층 이름을 `..domain..` 같은 전역 패턴으로 쓰면 라이브러리 패키지가 오탐된다. base package를 붙인다.
  - 도메인을 동적으로 정하려면 R4·R5는 커스텀 `ArchCondition`(의존 출발·도착 패키지에서 base package 다음 세그먼트를 꺼내 비교)이 필요하다. R6은 `slices().matching("com.shineunsigan.snsg.(*)..")`에서 common을 빼는 방식(`SliceAssignment` 등)으로 한다.
  - ArchUnit 1.x는 `that()`이 아무 클래스도 고르지 못하면 실패한다(`archRule.failOnEmptyShould`). 끄지 말고 대상을 고친다.
  - 현재 도메인 간 참조는 `auth.infrastructure.client.UserAdapter`(user의 `port.in`, `dto.command.RegisterUserCommand`, `domain.constants.Gender·SocialProvider`, FQN 사용 포함)와 `auth.infrastructure.event.UserWithdrawnEventListener`(user의 `event`, `domain.constants`)뿐이고 모두 허용 대상이다.
- **GOAL 8 권장 위반 클래스 세트** (경로는 `snsg/src/main/java/com/shineunsigan/snsg/` 아래. 내용은 `package <경로 패키지>;` + `class ArchProbe { <대상 FQN> ref; }`)
  | 규칙 | 경로 | 대상 FQN |
  |---|---|---|
  | R3 | `user/presentation/ArchProbe.java` | `com.shineunsigan.snsg.user.infrastructure.storage.FileStorageProperties` |
  | R2 | `user/application/ArchProbe.java` | `com.shineunsigan.snsg.user.infrastructure.storage.FileStorageProperties` |
  | R1 | `user/domain/model/ArchProbe.java` | `com.shineunsigan.snsg.user.application.UserQueryService` |
  | R4 | `auth/application/ArchProbe.java` | `com.shineunsigan.snsg.user.application.port.in.FindUserUseCase` |
  | R5 | `auth/infrastructure/client/ArchProbe.java` | `com.shineunsigan.snsg.user.domain.model.User` |
  | R6 | `user/infrastructure/event/ArchProbe.java` | `com.shineunsigan.snsg.auth.domain.constants.SocialProvider` (허용 대상이지만 기존 auth → user와 합쳐 순환) |
  | R7 | `common/ArchProbe.java` | `com.shineunsigan.snsg.user.domain.constants.Gender` |
  | 새 패키지 → R4 | `probe/application/ArchProbe.java` | `com.shineunsigan.snsg.user.application.port.in.FindUserUseCase` |
  - 실행 예(R3 건. 나머지는 `P`·`PKG`·`T`만 바꾼다):
    ```bash
    cd /c/Users/ks/nss/dev/snsg-be/wt-SHI-144/snsg && P=src/main/java/com/shineunsigan/snsg/user/presentation/ArchProbe.java && PKG=com.shineunsigan.snsg.user.presentation && T=com.shineunsigan.snsg.user.infrastructure.storage.FileStorageProperties && mkdir -p "$(dirname $P)" && printf 'package %s;\n\nclass ArchProbe {\n    %s ref;\n}\n' "$PKG" "$T" > "$P" && rm -rf build/test-results/test; ./gradlew test --tests '*Architecture*' --console=plain -q; rm -f "$P"; rmdir -p --ignore-fail-on-non-empty "$(dirname $P)" 2>/dev/null; X=$(ls build/test-results/test/TEST-*Architecture*.xml) && grep -A1 '<testcase' "$X" | grep -B1 '<failure' | grep -o 'testcase name="[^"]*"'; grep -c 'ArchProbe' "$X"
    ```
    → 실패 테스트 이름 한 줄, 마지막 숫자 1 이상. XML이 없으면 컴파일 실패이므로 위반 클래스를 고쳐 다시 돌린다.
- **api-guide.md 위치(베이스 기준)**: 가입 세션 30분은 50·112·113행, access token 30분은 73·139행.

## 배경

- Linear SHI-170. 기능 명세(`C:\Users\ks\nss\dev\snsg-be\planning\be-spec-mvp.html`, 저장소 밖·읽기 전용)의 T-04 "기기별 세션 (auth)"를 구현한다. 화면은 MY-03(로그아웃, 미접속 14일 자동 로그아웃)이다.
- 지금은 refresh token을 사용자당 하나(`refresh:{userId}`)만 보관한다. 다른 기기에서 로그인하면 앞 기기의 refresh가 덮어써져 로그아웃되고, 로그아웃하면 모든 기기가 함께 끊긴다. 이를 기기(세션) 단위로 바꾼다.
- 명세 범위: POL-A1~A3, Redis 키 구조 변경, refresh token claim에 sessionId(`TokenPort` 확장), `RefreshTokenStorePort`를 (userId, sessionId) 단위로, 로그아웃은 해당 세션만, `RevokeAuthFacade`는 sessions 집합으로 전체 삭제.
- 명세 완료 조건: 두 기기 로그인 후 한쪽 로그아웃해도 다른 쪽 재발급 성공 / 재발급하면 이전 refresh 거부 / 탈퇴 이벤트 후 모든 세션 재발급 실패 / sessionId 없는(구형) refresh token 거부 / 모든 키에 TTL.
- 명세 규칙(요약)
  - POL-A1: 로그인·가입 완료 때 sessionId(난수)를 만들어 refresh token의 claim에 담고 `refresh:{userId}:{sessionId}`에 해시를 저장한다. 재발급은 refresh token에서 (userId, sessionId)를 읽어 같은 sessionId로 토큰만 교체한다. access token은 지금처럼 userId(subject)만 담는다. sessionId 생성은 `TokenIssuer`가 한다(명세 "새로 만드는 application 클래스" 표).
  - POL-A2: 14일 동안 재발급이 없으면 Redis TTL로 세션이 사라진다.
  - POL-A3: 로그아웃은 refresh 쿠키의 sessionId 세션만 지우고 `sessions:{userId}`에서도 뺀다. 다른 기기는 유지.
  - Redis(명세 5.4): `refresh:{userId}:{sessionId}` = refresh token SHA-256 해시, TTL 14일(`auth.refresh-token-validity`). `sessions:{userId}` = sessionId SET. 탈퇴 시 전체 삭제용, 로그아웃은 SREM. 만료로 사라진 세션 id가 남아 있어도 삭제 대상이 없을 뿐이라 무해하다. TTL 14일, 세션 저장 때마다 갱신.
  - API-A05 재발급: 공개. 저장된 해시와 다르면(이미 교체됨, 로그아웃됨, 만료됨) 401 `INVALID_REFRESH_TOKEN`. 사용자 활성 확인(안전망, 구현됨)은 유지.
  - API-A06 로그아웃: 인증 필요. refresh 쿠키의 세션만 삭제. 쿠키가 없거나 이미 지워진 세션이어도 200(재시도 안전).
  - 오류 코드 표: auth는 기존 코드(`INVALID_ACCESS_TOKEN`, `INVALID_REFRESH_TOKEN`, …) 유지. 새 코드 없음.
  - 설정 절: `auth.refresh-token-validity: 14d` 그대로. 새 설정 없음.
- 사용자 위임: "추천안대로 진행, 단 규칙과 문서 최신화를 면밀히 검토." 아래 결정은 모두 (권장안 채택)이다.

### 결정

1. **sessionId는 `UUID.randomUUID()` 문자열(122비트 난수)이고 `TokenIssuer`가 새 세션을 발급할 때 만든다.** 로그인(`SocialLoginFacade`)·가입 완료(`CompleteSignUpFacade`)는 새 sessionId, 재발급은 refresh에서 꺼낸 sessionId를 그대로 쓴다. 근거: 명세 POL-A1·3.4 표. UUID v7(시간순)은 추측 가능성이 v4보다 커서 쓰지 않는다. (권장안 채택)
2. **refresh token에만 sessionId claim을 넣는다. access token은 subject(userId)만 그대로.** claim 이름 제안은 `sid`(OIDC의 세션 ID 표준 claim 이름). 근거: POL-A1, api.md "토큰에는 userId만 담는다"(access 기준으로 유지, refresh 예외는 refine-docs가 문서화). (권장안 채택)
3. **sessionId claim이 없는 refresh token(구형 토큰, 또는 access token을 refresh 자리에 넣은 경우)은 재발급에서 401 `INVALID_REFRESH_TOKEN`.** 근거: 기존 `ReissueTokenFacade`가 파싱 실패를 모두 `INVALID_REFRESH_TOKEN`으로 바꾸고, api.md "검증 실패는 한 코드로 묶는다". 새 오류 코드를 만들지 않는다(명세 오류 표: auth 기존 코드 유지). (권장안 채택)
4. **재발급(회전):** (userId, sessionId)로 저장된 해시와 대조 → 다르거나 없으면 401 `INVALID_REFRESH_TOKEN` → 사용자 활성 확인 → 같은 sessionId로 access·refresh를 새로 발급하고 그 세션 키를 새 해시로 덮어쓴다. 이전 refresh는 해시가 달라져 거부된다. (명세 그대로)
5. **재발급 시 사용자가 활성이 아니면(탈퇴) 그 사용자의 세션 전체를 지우고 401 `INVALID_REFRESH_TOKEN`.** 근거: 기존 동작(`delete(userId)` = 사용자 전체)의 의미를 유지한다. 이 경로는 탈퇴 이벤트가 유실됐을 때의 안전망(package-structure.md 이벤트 절)이라 탈퇴 이벤트와 같은 결과(전체 삭제)를 내는 것이 맞다. (권장안 채택)
6. **로그아웃은 인증 필요를 유지하고(`SecurityConfig` 그대로), refresh 쿠키(선택)를 함께 받는다.**
   - 쿠키가 있고, 파싱되고(서명·만료·sessionId 유효), 그 userId가 access token의 principal과 같으면 그 세션만 지운다(`refresh:{userId}:{sessionId}` 삭제 + `sessions:{userId}`에서 SREM).
   - 쿠키가 없거나 비었거나, 파싱에 실패하거나(위조·만료·구형), userId가 principal과 다르면 **아무것도 지우지 않는다.** 어느 경우든 200 + refresh 쿠키 만료(max-age 0). 예외를 던지지 않는다.
   - 근거: API-A06 "쿠키가 없거나 이미 지워진 세션이어도 200". principal과 다른 사용자의 쿠키로 남의 세션을 지우지 않는다. 만료된 refresh의 Redis 키는 TTL이 같아 이미 사라졌으므로 지울 것이 없다. (권장안 채택)
7. **세션 저장(로그인·가입·재발급 공통)은 Redis 트랜잭션(MULTI/EXEC) 하나로 묶는다:** `refresh:{userId}:{sessionId}` SET(TTL = `auth.refresh-token-validity`) + `sessions:{userId}` SADD + `sessions:{userId}` EXPIRE(같은 값). 근거: 셋을 따로 보내면 SADD 직후 프로세스가 죽을 때 새로 생긴 `sessions:{userId}`가 TTL 없이 남는다(package-structure.md Redis "TTL을 반드시 건다" 위반). 비용은 `SessionCallback` 하나다. TTL 값은 refresh 만료·쿠키 max-age와 같은 `AuthProperties.refreshTokenValidity` 하나에서 읽는다(같은 절 "한 설정값에서"). (권장안 채택)
8. **로그아웃 삭제(DEL + SREM)는 트랜잭션으로 묶지 않는다.** 근거: 둘 사이에 실패하면 SET에 id만 남는데, 명세 5.4가 "남아 있어도 무해"라 정했고 SET에는 TTL이 걸려 있다. (권장안 채택)
9. **탈퇴 전체 삭제는 `SMEMBERS sessions:{userId}` 후, 그 id들의 refresh 키와 `sessions:{userId}`를 DEL 한 번으로 지운다.** MULTI/WATCH는 쓰지 않는다. 근거: DEL 한 명령은 원자적이다. SMEMBERS와 DEL 사이에 새 세션이 생기는 경합은 ① 탈퇴한 사용자는 소셜 계정이 지워져 새로 로그인할 수 없고 ② 진행 중이던 재발급이 세션을 다시 저장해도 다음 재발급에서 사용자 활성 확인(안전망, 결정 5)에 막힌다. (권장안 채택)
10. **기존 `refresh:{userId}` 키는 마이그레이션하지 않는다.** 근거: 운영 데이터가 없다(오케스트레이터 확인). 로컬 Redis의 구형 키는 TTL(14일)로 사라지고, 구형 refresh token은 sessionId가 없어 결정 3으로 거부되어 재로그인하게 된다. (권장안 채택)
11. **Redis 어댑터 테스트는 기존 `RedisRefreshTokenStoreAdapterTest` 방식(`@SpringBootTest` + `.env`의 로컬 Redis)을 그대로 따른다. Testcontainers를 도입하지 않는다.** 근거: testing.md는 Redis adapter를 Testcontainers로 테스트한다고 적지만, 저장소에는 그 의존성이 없고 두 Redis 테스트 모두 `@SpringBootTest`로 로컬 Redis에 붙는다. 의존성 도입은 이 작업의 범위가 아니다. 문서와 코드의 어긋남은 refine-docs가 처리한다(`## 참고`). (권장안 채택)
12. **로그아웃 메서드는 지금 위치(`ReissueTokenFacade.logout`)에 둔다.** 행위별 facade로 떼는 리팩터링은 하지 않는다(CLAUDE.md Surgical Changes). (권장안 채택)
13. **access 해석(`TokenPort.parseUserId`)은 sessionId claim이 있는 토큰(= refresh token)을 `INVALID_ACCESS_TOKEN`으로 거부한다.** (오케스트레이터 결정, 권장안 채택)
    - 문제: 지금은 access와 refresh가 같은 키로 서명되고 claim도 같아, refresh token을 `Authorization: Bearer`로 보내면 인증 필터를 통과한다. refresh 수명이 14일이라 access(30분)보다 훨씬 오래 쓰일 수 있다.
    - 방법: 이번 작업이 refresh에만 sessionId claim을 넣으므로, 이 claim의 유무로 토큰 종류가 갈린다. access 해석에서 claim이 있으면 거부하면 된다. `JwtAuthenticationFilter`는 `parseUserId`가 던지는 `BusinessException`을 잡아 익명으로 통과시키고, 보호 경로는 Security가 401로 막는다(기존 동작). 그래서 필터·`security/**`를 고치지 않고 막힌다. 오류 코드는 `parseUserId`가 지금 쓰는 `INVALID_ACCESS_TOKEN` 그대로다(api.md "검증 실패는 한 코드로 묶는다").
    - 남는 틈: 이전 형식 refresh(sessionId 없음, 배포 전에 발급된 것)는 claim이 없어 access 해석을 통과한다. 하지만 JWT 만료가 발급 후 14일이라 그 뒤에는 저절로 거부되고(Redis 키도 같은 14일 TTL로 사라진다), 운영 데이터(발급된 운영 토큰)가 없으므로 무해하다. 별도 차단 코드를 만들지 않는다.

## 환경

- 워크트리: `C:\Users\ks\nss\dev\snsg-be\wt-SHI-170` (브랜치 `feat/SHI-170-device-session`, 베이스 `d67041d`)
- 메인 작업 디렉터리 `C:\Users\ks\nss\dev\snsg-be\snsg-be`는 읽기만 한다. 워크트리 밖 파일은 고치지 않는다.
- **같은 시각 `C:\Users\ks\nss\dev\snsg-be\wt-SHI-169`(user 도메인 프로필 작업, SHI-169)가 진행 중이다.** 그 워크트리의 파일은 읽지도 고치지도 않는다. DB(PostgreSQL)·Redis·8080 포트를 공유하므로(`.claude/rules/git-workflow.md` "워크트리를 쓸 때 주의") 테스트·앱 실행은 오케스트레이터와 조율해 한 번에 한 워크트리에서만 돌린다. 이번 작업은 마이그레이션을 더하지 않으므로 Flyway 충돌은 없다.
- `snsg/.env`는 이미 복사돼 있다.
- spec 작성 시점(2026-11-09)에 Docker 컨테이너 `snsg-postgres`, `snsg-redis`가 떠 있다. Redis 어댑터 테스트(`RedisRefreshTokenStoreAdapterTest`, `RedisSignUpSessionAdapterTest`)와 `SnsgApplicationTests`, `OpenApiSpecTest`, `@DataJpaTest`들은 이 로컬 컨테이너에 붙는다. 컨테이너가 꺼져 있으면 실패한다. 그때는 코드를 고치지 말고 멈춰서 보고한다.
- 베이스에서 이미 깨져 있는 테스트는 알려진 것이 없다.
- 셸은 PowerShell이 기본이고 Bash도 쓸 수 있다. PowerShell 5.1에서는 `&&`가 동작하지 않는다. 아래 검증 명령은 Bash 기준이다. `jq`는 없다.
- 운영 데이터가 없다. 기존 refresh 키 이관은 하지 않는다(결정 10).

## GOAL

공통: 아래 "테스트 클래스"는 대상 클래스와 같은 패키지, 같은 이름 + `Test`다(`.claude/rules/testing.md`). 각 항목의 "검증"에 적은 경우마다 그 클래스에 테스트 메서드가 하나 이상 있어야 한다. 검증 에이전트는 테스트 코드를 읽어 그 경우가 실제로 단정되는지 확인한다. 경로의 `$WT`는 `/c/Users/ks/nss/dev/snsg-be/wt-SHI-170`이다.

1. `TokenPort`가 refresh token을 (userId, sessionId)로 발급하고, refresh token에서 (userId, sessionId)를 함께 꺼내는 메서드를 가진다. sessionId claim이 없는 토큰은 refresh로 해석하지 않고 예외를 던진다. access token 발급은 그대로이고 access token에는 sessionId claim이 없다. `parseUserId`(인증 필터용)는 sessionId claim이 있는 토큰(refresh)을 `BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN)`으로 거부한다(결정 13). 그 밖의 `parseUserId` 동작(위조·만료·형식 오류 거부)은 그대로다.
   - 검증: `JwtTokenAdapterTest`가 다음을 단정한다. 기존 테스트의 단정은 유지한다(refresh 발급 시그니처 변경에 따른 호출 수정은 허용).
     - (userId, sessionId)로 발급한 refresh token을 해석하면 같은 userId와 sessionId가 나온다
     - access token을 refresh 해석 메서드에 넣으면 예외
     - 같은 서명 키로 subject만 담아 만든 토큰(구형 refresh token 흉내, `Jwts.builder()`로 테스트에서 직접 생성)을 refresh 해석 메서드에 넣으면 예외
     - 발급한 access token의 claim에 sessionId claim이 없다(테스트가 같은 키로 claim을 열어 확인)
     - (userId, sessionId)로 발급한 refresh token을 `parseUserId`에 넣으면 `BusinessException`이고 오류 코드가 `INVALID_ACCESS_TOKEN`
     - 발급한 access token은 `parseUserId`로 여전히 같은 userId가 나온다(기존 테스트 유지)
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*JwtTokenAdapterTest'` → BUILD SUCCESSFUL, 실패 0

2. `RefreshTokenStorePort`가 (userId, sessionId) 단위로 저장·대조·삭제하고, 사용자의 세션 전체를 지우는 메서드를 가진다. Redis 키는 `refresh:{userId}:{sessionId}`(값은 토큰 SHA-256 hex 해시)와 `sessions:{userId}`(sessionId SET)다. 저장할 때마다 두 키 모두 TTL을 `auth.refresh-token-validity`로 다시 건다(결정 7). 로그아웃용 삭제는 그 세션 키를 지우고 SET에서 뺀다. 전체 삭제는 SET의 모든 세션 키와 SET을 지운다. `refresh:{userId}`(세션 없는) 키는 더 이상 만들지 않는다.
   - 검증: `RedisRefreshTokenStoreAdapterTest`(기존 방식, 로컬 Redis, `@AfterEach`에서 테스트가 만든 키를 모두 지운다)가 다음을 단정한다.
     - 같은 사용자의 두 세션(A, B)을 저장하면 각자의 토큰만 각자의 세션에서 일치한다(A 토큰은 B 세션에서 불일치)
     - 같은 세션에 새 토큰을 저장하면 이전 토큰은 불일치, 새 토큰은 일치
     - 저장된 적 없는 세션은 불일치
     - 세션 A를 로그아웃 삭제하면 A는 불일치, B는 여전히 일치. `sessions:{userId}`에 A가 없고 B가 있다
     - 전체 삭제하면 A·B 모두 불일치이고 `refresh:{userId}:A`, `refresh:{userId}:B`, `sessions:{userId}` 키가 모두 없다
     - 저장 후 `refresh:{userId}:{sessionId}` 값이 토큰 원문이 아닌 64자 hex이고, 그 키와 `sessions:{userId}` 모두 TTL이 양수다
     - `sessions:{userId}`의 TTL을 테스트가 짧게(예: 60초) 줄여둔 뒤 다른 세션을 저장하면 TTL이 다시 `auth.refresh-token-validity` 근처(60초보다 훨씬 큰 값)로 늘어난다
     - 저장 후 `refresh:{userId}` 키(세션 없는 구형 키)는 존재하지 않는다
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*RedisRefreshTokenStoreAdapterTest'` → BUILD SUCCESSFUL, 실패 0, skipped 0

3. 로그인·가입 완료는 매번 새 sessionId로 세션을 만든다. 발급한 refresh token에 담긴 sessionId와 저장한 세션의 sessionId가 같다.
   - 검증: 다음을 단정하는 Mockito 테스트가 있다(`TokenIssuerTest` 또는 `SocialLoginFacadeTest`, 위치는 `## 참고` 제안).
     - 같은 사용자를 두 번 로그인(발급)시키면 서로 다른 sessionId로 refresh token이 발급되고, 각각 그 sessionId로 저장된다
     - 저장 호출의 (userId, sessionId, refresh token)이 refresh 발급 호출의 (userId, sessionId)·반환값과 같다
   - 검증: `SocialLoginFacadeTest`, `CompleteSignUpFacadeTest`의 기존 테스트가 통과한다(포트 시그니처 변경에 따른 stub·verify 수정은 허용, 성공·실패 분기의 단정은 유지).
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*SocialLoginFacadeTest' --tests '*CompleteSignUpFacadeTest' --tests '*TokenIssuerTest'` → BUILD SUCCESSFUL, 실패 0 (`TokenIssuerTest`를 만들지 않았으면 그 `--tests` 인자는 빼고 돌린다)

4. 재발급은 결정 3·4·5대로 동작한다.
   - 검증: `ReissueTokenFacadeTest`(Mockito)가 다음을 단정한다.
     - refresh에서 (userId, sessionId)를 꺼내고, 그 세션과 일치하고 사용자가 활성이면 같은 sessionId로 새 refresh를 발급해 그 (userId, sessionId)로 저장하고 새 access token을 돌려준다
     - 세션과 불일치(이미 교체됨·로그아웃됨·만료됨)면 `INVALID_REFRESH_TOKEN`이고 토큰을 발급하지 않는다
     - 일치하지만 사용자가 활성이 아니면 `INVALID_REFRESH_TOKEN`이고 그 사용자의 세션 **전체 삭제**가 호출되며 토큰을 발급하지 않는다
     - refresh 해석이 실패하면(위조·만료·sessionId 없는 구형 토큰) `INVALID_REFRESH_TOKEN`이고 저장소를 조회하지 않는다
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*ReissueTokenFacadeTest'` → BUILD SUCCESSFUL, 실패 0

5. 로그아웃은 결정 6대로 refresh 쿠키의 세션만 지운다.
   - 검증: `ReissueTokenFacadeTest`가 다음을 단정한다.
     - refresh가 유효하고 그 userId가 로그아웃 요청자와 같으면 그 (userId, sessionId) 세션 삭제만 호출되고 전체 삭제는 호출되지 않는다
     - refresh가 null 또는 빈 문자열이면 예외 없이 끝나고 저장소 삭제가 호출되지 않는다
     - refresh 해석이 실패하면(위조·만료·구형) 예외 없이 끝나고 저장소 삭제가 호출되지 않는다
     - refresh의 userId가 요청자와 다르면 예외 없이 끝나고 저장소 삭제가 호출되지 않는다
   - 검증: `AuthControllerTest`(`@WebMvcTest`, 기존 방식)가 다음을 단정한다. 기존 `logout` 테스트는 바뀐 호출에 맞게 고쳐도 된다.
     - principal과 refresh 쿠키를 주고 `DELETE /api/v1/auth/tokens` → 200, refresh 쿠키 max-age 0, facade 로그아웃이 (principal userId, 쿠키 값)으로 호출된다
     - principal만 주고 쿠키 없이 호출 → 200, refresh 쿠키 max-age 0 (401·400이 아니다)
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*ReissueTokenFacadeTest' --tests '*AuthControllerTest'` → BUILD SUCCESSFUL, 실패 0

6. 탈퇴(`RevokeAuthUseCase.revoke`)는 사용자의 세션 전체를 지운다. 소셜 연동 해제 분기는 그대로다.
   - 검증: `RevokeAuthFacadeTest`가 세 경우(연동 해제함, 연동 해제 안 함, 연동 해제 실패) 모두 세션 전체 삭제 호출을 단정하고, 기존의 연동 해제 단정은 유지한다.
   - 검증: `cd $WT/snsg && ./gradlew cleanTest test --tests '*RevokeAuthFacadeTest'` → BUILD SUCCESSFUL, 실패 0

7. "사용자당 하나만 유효하다"는 낡은 설명이 main 코드에 남지 않는다(포트·어댑터·`TokenIssuer`·`ReissueTokenFacade`의 Javadoc·주석을 기기별 세션 기준으로 고친다).
   - 검증: `grep -rn "사용자당 하나" $WT/snsg/src/main` → 출력 없음

8. API 문서: 재발급·로그아웃 `@Operation` description이 기기별 세션 동작을 적는다. 로그아웃은 refresh 쿠키가 가리키는 이 기기의 세션만 끝나고 다른 기기의 로그인은 유지된다는 것, 쿠키를 쓰므로 credentials를 포함해 호출한다는 것, 쿠키가 없거나 이미 끝난 세션이어도 200(재시도 안전)이라는 것. 재발급은 같은 기기의 이전 refresh가 무효가 된다는 것. `docs/openapi.yaml`을 다시 만든다.
   - 검증: `cd $WT/snsg && OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'` 실행 후 `cd $WT/snsg && ./gradlew cleanTest test --tests '*OpenApiSpecTest'` → BUILD SUCCESSFUL, 실패 0
   - 검증: `grep -n "다른 기기" $WT/docs/openapi.yaml` → 한 줄 이상(로그아웃 description)
   - 검증: `cd $WT && git diff d67041d --stat -- docs/openapi.yaml` → 바뀐 줄이 auth 토큰 엔드포인트 description 부근뿐(`git diff d67041d -- docs/openapi.yaml`로 확인, 다른 엔드포인트 정의가 지워지지 않는다)

9. 전체 빌드와 아키텍처 규칙이 통과한다.
   - 검증: `cd $WT/snsg && ./gradlew cleanTest build` → BUILD SUCCESSFUL, 실패 0. `ArchitectureTest` 포함
   - 정지 조건: 위 명령이 실패 0으로 끝나면 종료한다
   - 끝내지 않는 경우: DB·Redis 컨테이너가 꺼져 테스트가 실패하거나 컨텍스트가 안 떴을 때(환경 문제로 보고), 테스트를 지우거나 `@Disabled`로 실패를 없앴을 때, 컴파일이 깨져 테스트가 돌지 않았을 때

10. 범위 밖 파일을 고치지 않는다.
    - 검증: `cd $WT && git status --short` → 모든 경로가 워크트리 안이고, 바뀐 main 코드는 `snsg/src/main/java/com/shineunsigan/snsg/auth/**` 뿐이다(`auth/infrastructure/security/**` 제외). 그 밖에는 `snsg/src/test/**`, `docs/openapi.yaml`, `.claude/tasks/SHI-170/task.md`만
    - 검증: `cd $WT && git status --short -- snsg/src/main/resources snsg/build.gradle snsg/src/main/java/com/shineunsigan/snsg/auth/infrastructure/security snsg/src/main/java/com/shineunsigan/snsg/user` → 출력 없음

## 비목표

- `JwtAuthenticationFilter`, `SecurityConfig`, `JwtAuthenticationEntryPoint`는 고치지 않는다. refresh를 Bearer로 보내는 문제는 `JwtTokenAdapter.parseUserId`에서만 막는다(결정 13).
- 이전 형식 refresh(sessionId 없음)를 access 해석에서 따로 거부하는 코드를 만들지 않는다(결정 13의 "남는 틈").
- 동시에 두 번 들어온 같은 refresh의 재발급(대조 후 저장 사이 경합)을 Lua·WATCH로 원자화하지 않는다. 기존과 같은 수준이고, FE에는 "재발급은 한 번에 하나만" 규칙이 이미 있다.
- 기존 `refresh:{userId}` 키 이관·삭제 코드를 만들지 않는다(결정 10).
- 새 오류 코드, 새 설정 키, `.env` 변경, 마이그레이션을 더하지 않는다.
- 탈퇴 API를 auth로 옮기는 것(API-A08), 탈퇴 가능 여부(API-A07), `UserWithdrawnEvent` 필드 변경은 T-05 등 다른 작업이다. user 도메인 파일과 `UserWithdrawnEventListener`는 건드리지 않는다.
- 로그아웃을 별도 facade로 떼지 않는다(결정 12). `AuthCookieFactory`, `AuthProperties`는 고치지 않는다.
- Testcontainers 의존성을 추가하지 않는다(결정 11).
- 콜백 리다이렉트·쿠키 이름·Path·max-age, 재발급·가입 응답 본문 형태는 바꾸지 않는다.
- 기존 테스트의 단정은 바꾸지 않는다. 포트·메서드 시그니처 변경에 따른 stub·verify 호출 수정, 그리고 GOAL 4·5·6이 명시한 단정(전체 삭제·세션 삭제)으로의 교체만 허용한다.
- `docs/api-guide.md`, `.claude/rules/**`, `CLAUDE.md`는 이 단계에서 고치지 않는다. refine-docs 단계 몫이다(`## 참고`).

## 제약

- 패키지 구조·의존 방향·DTO·Redis 규약은 `.claude/rules/package-structure.md`를 따른다(특히 "인프라 규약 > Redis", "Port").
- API·문서화는 `.claude/rules/api.md`를 따른다(토큰과 쿠키, 문서 절).
- 테스트는 `.claude/rules/testing.md`를 따른다. 새 테스트를 만들기 전에 읽는다. Redis 어댑터 테스트 방식은 결정 11. 테스트는 원래 코드와 별도 커밋 대상이다.
- 브랜치·커밋은 `.claude/rules/git-workflow.md`를 따른다. 커밋하지 않고 스테이징까지만 한다. `docs/openapi.yaml`은 API 코드와 같은 커밋 대상이다.
- 임시 파일은 `.claude/rules/temp-files.md`를 따른다. 워크트리 안에 만들지 않는다.
- Spring Boot 4·Jackson 3 함정은 CLAUDE.md를 본다.
- 요청에 없는 수정은 하지 않는다. 각 GOAL 항목이 요구하는 것만 건드린다.

## 참고

### 구현 제안(바꿔도 된다. 결과가 GOAL을 만족하면 된다)

- `TokenPort`: `String issueRefreshToken(UUID userId, String sessionId)`, `RefreshTokenClaims parseRefreshToken(String token)`. `RefreshTokenClaims(UUID userId, String sessionId)` record는 auth `application/dto/result`에 둔다(포트 반환 타입이라 application에 둔다). 해석 실패는 지금처럼 `BusinessException`을 던지고 `ReissueTokenFacade`가 `INVALID_REFRESH_TOKEN`으로 바꾼다. sessionId claim이 없거나 빈 값이면 실패로 본다.
- JWT claim 이름 `sid`. `JwtTokenAdapter`의 private `issue`에 claim을 선택적으로 넣는 형태로 두면 access 경로는 그대로다.
- 결정 13의 테스트 위치: `JwtTokenAdapterTest`(adapter 단위)로 충분하다. 기존 `SecurityConfigTest`는 `TokenPort`를 mock하므로, 거기서 refresh를 보내도 mock이 정한 결과만 확인될 뿐 실제 판정은 검증되지 않는다. "`parseUserId`가 `BusinessException`을 던지면 필터가 익명으로 통과시키고 보호 경로는 401"이라는 연결은 이미 필터와 `SecurityConfigTest`(mock이 예외를 던지는 경우)가 지키는 기존 동작이다. 그래서 새로 생긴 판단(sid가 있으면 거부)만 adapter에서 단정하면 경로 전체가 덮인다. 실제 JWT 어댑터를 올린 `@SpringBootTest` 보안 테스트는 만들지 않는다(testing.md: `@SpringBootTest`는 꼭 필요할 때만).
- `RefreshTokenStorePort`: `save(UUID userId, String sessionId, String refreshToken)`, `matches(UUID userId, String sessionId, String refreshToken)`, `delete(UUID userId, String sessionId)`, `deleteAll(UUID userId)`.
- `RedisRefreshTokenStoreAdapter.save`의 MULTI/EXEC 예:
  ```java
  redisTemplate.execute(new SessionCallback<List<Object>>() {
      @Override
      public List<Object> execute(RedisOperations operations) {
          operations.multi();
          operations.opsForValue().set(refreshKey(userId, sessionId), hash(refreshToken), refreshTokenValidity);
          operations.opsForSet().add(sessionsKey(userId), sessionId);
          operations.expire(sessionsKey(userId), refreshTokenValidity);
          return operations.exec();
      }
  });
  ```
  `deleteAll`: `members = opsForSet().members(sessionsKey)` → 키 목록(`refresh:{u}:{s}` 전부 + `sessions:{u}`)을 만들어 `redisTemplate.delete(keys)` 한 번.
- `TokenIssuer`: `issue(UUID userId)`(새 세션: `UUID.randomUUID().toString()`)와 `reissue(UUID userId, String sessionId)`(같은 세션) 두 메서드, 또는 sessionId를 받는 private 하나로 합치기. 지금 `TokenIssuer`는 테스트 클래스가 없다. sessionId 생성이 새 판단이므로 `TokenIssuerTest`(Mockito, `TokenPort`·`RefreshTokenStorePort` mock, `ArgumentCaptor`로 sessionId 비교)를 만드는 것을 제안한다.
- `ReissueTokenFacade.logout(UUID userId, String refreshToken)`: 쿠키 값이 비었으면 return, 해석은 try/catch로 감싸 실패 시 return, userId 불일치 시 return, 일치 시 `delete(userId, sessionId)`.
- `AuthController.logout`: `@CookieValue(value = AuthCookieFactory.REFRESH_TOKEN, required = false) String refreshToken`을 더해 그대로 넘긴다. 응답은 지금처럼 쿠키 만료 + `ApiResponse.success()`.
- `RevokeAuthFacade.revoke`: `delete(userId)` → `deleteAll(userId)`.
- `RedisRefreshTokenStoreAdapterTest`는 공유 Redis에 붙으므로 고정 UUID 사용자와 고정 sessionId를 쓰고 `@AfterEach`에서 `refresh:{u}:*`가 아니라 만든 키를 이름으로 지운다(KEYS 패턴 스캔은 쓰지 않는다). TTL 단정은 `getExpire(key, TimeUnit.SECONDS)`.
- 명세 완료 조건 대응: "두 기기 로그인 후 한쪽 로그아웃해도 다른 쪽 재발급 성공" = GOAL 2(세션 A 삭제 후 B 일치) + GOAL 4(일치하면 재발급) + GOAL 5. "재발급하면 이전 refresh 거부" = GOAL 2(같은 세션 덮어쓰기) + GOAL 4. "탈퇴 이벤트 후 모든 세션 재발급 실패" = GOAL 6 + GOAL 2(전체 삭제). "구형 refresh 거부" = GOAL 1 + GOAL 4. "모든 키에 TTL" = GOAL 2. 실제 카카오 로그인이 필요해 curl E2E는 GOAL에 넣지 않았다.

### FE 영향 (refine-docs가 `docs/api-guide.md`를 점검할 때 쓴다)

- **응답 형태 변화 없음.** 요청·응답 필드, 쿠키 이름·Path·수명, 오류 코드 모두 그대로다.
- 동작 변화
  - 다른 기기(브라우저)에서 로그인해도 기존 기기가 로그아웃되지 않는다. api-guide의 `INVALID_REFRESH_TOKEN` 설명 "다른 기기에서 로그인함"(오류 표, "401을 받았을 때" 4번)은 더는 원인이 아니다. 원인은 만료(14일 미접속), 로그아웃, 탈퇴, 같은 기기에서 재발급이 겹친 경우다.
  - 로그아웃은 그 기기의 세션만 끝낸다. 다른 기기의 로그인은 유지된다. 로그아웃 요청은 refresh 쿠키를 읽으므로 credentials를 포함해야 한다(이미 api-guide 공통 규칙). 쿠키 없이 불러도 200이지만 그 경우 서버 세션은 지워지지 않고 14일 뒤 만료된다.
  - "재발급은 한 번에 하나만" 규칙은 같은 기기(같은 쿠키) 안에서 그대로 유효하다.
  - 탈퇴하면 모든 기기의 세션이 끊긴다. api-guide 탈퇴 절의 "refresh token이 즉시 무효" 문장은 "모든 기기의 로그인이 끊긴다"로 보강할 수 있다.
  - 배포 직후 기존(구형) refresh 쿠키는 재발급에서 401 `INVALID_REFRESH_TOKEN`이 나 한 번 다시 로그인하게 된다. 운영 데이터가 없어 FE 안내는 필요 없다.

### refine-docs 단계에 넘길 것(규칙·문서 최신화 후보)

- `.claude/rules/api.md` "토큰과 쿠키": "토큰에는 userId만 담는다", "access와 refresh는 구조가 같고 만료만 다르다"가 이번 변경과 어긋난다. access는 userId만, refresh는 userId + sessionId(기기 세션 식별)라는 것과 이유(기기별 로그아웃·회전)를 적는다. access 해석이 sessionId claim이 있는 토큰을 거부하므로 refresh를 Bearer로 쓸 수 없다는 것(결정 13)도 적는다. "구분은 저장 위치로 한다" 문장도 함께 고친다. "검증 실패는 한 코드로 묶는다"에 sessionId 없는 구형 refresh도 같은 코드라는 것을 더할 수 있다.
- `.claude/rules/package-structure.md` Redis 절의 키 예 `refresh:{userId}` → `refresh:{userId}:{sessionId}`, `sessions:{userId}`. 여러 키를 함께 쓰며 TTL을 지켜야 할 때 MULTI/EXEC로 묶은 근거(결정 7)와, 부작용이 무해해 묶지 않은 경우(결정 8·9)를 규약으로 남길지 검토한다. "이벤트 > 안전망" 예시(탈퇴 이벤트로 refresh token을 지우되 재발급 시 사용자 확인)는 "세션 전체"로 표현만 맞춘다.
- `.claude/rules/testing.md` Redis 항목은 Testcontainers를 쓴다고 적지만, 저장소에는 그 의존성이 없고 Redis 어댑터 테스트는 `@SpringBootTest` + 로컬 Redis(`.env`)다(결정 11). 이 어긋남을 실제 방식에 맞추거나 후보로 보고한다. `.claude/skills/spec/SKILL.md`의 예시 문장(`UserRedisAdapterTest`는 Docker가 꺼져 있으면 skip)도 같은 전제라 함께 본다.
- `docs/api-guide.md`: 위 "FE 영향"의 오류 표·401 처리·로그아웃·탈퇴 절. 화면별 표의 "설정 · 로그아웃" 행은 그대로 맞다.
- 저장소 밖 기획 명세는 오케스트레이터가 처리한다: T-04 카드를 완료로 표시하고, 결정 5(비활성 사용자 재발급 시 전체 삭제)·6(쿠키 userId 불일치·해석 실패 시 지우지 않음)·7(저장 MULTI/EXEC)을 POL-A1~A3·5.4에 반영할지 검토.

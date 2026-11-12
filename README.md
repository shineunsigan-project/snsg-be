# snsg-be

## 🚀 개발 환경 셋팅

### 1. 레포 클론
git clone https://github.com/shineunsigan-project/snsg-be.git

cd snsg-be

### 2. 빌드·실행
- Java 17, Gradle wrapper. 모듈은 `snsg/`
- 빌드: `cd snsg && ./gradlew build`
- 테스트: `cd snsg && ./gradlew test`
- 실행: `cd snsg && ./gradlew bootRun` → `http://localhost:8080`

### 3. 브랜치 규칙
- 작업 시작 전 반드시 develop에서 분기
  - git checkout develop
  - git pull origin develop
  - git checkout -b feat/SHI-12-내-기능명
- 브랜치명은 `<prefix>/SHI-<번호>-<작업-이름>` (`SHI-<번호>`는 Linear 이슈 ID)

### 4. 작업 완료 후
- git add .
- git commit -m "feat: 기능 설명"
- git push origin feat/SHI-12-내-기능명
→ GitHub에서 develop으로 PR 생성

### 5. main 직접 push 금지 ❌ (main은 운영 환경이고, main에서 분기도 금지)
### 6. .env 파일 커밋 금지 🚨

## 어드민 MASTER 계정 만들기

어드민(운영 도구)의 MASTER 계정은 API로 만들지 않는다. 운영자가 DB에 직접 넣는다. 다른 어드민은 가입 신청(`POST /api/admin/v1/auth/signup`) 후 MASTER가 승인한다. FE용 설명은 `docs/admin-api-guide.md`에 있다.

### 1. 비밀번호 해시 만들기
비밀번호는 가입 규칙과 같게 정한다(8~64자, 영문·숫자 각 1자 이상, 공백 없는 ASCII). BCrypt(cost 10) 해시를 `htpasswd`로 만든다.
비밀번호는 **명령줄 인자로 넘기지 않는다**(셸 기록에 남는다). 아래 명령이 대화형으로 두 번 묻는다.

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 10 <아이디>
```

출력은 `<아이디>:<해시>` 한 줄이다. `:` 뒤가 해시다(`$2y$10$`로 시작한다. 앱이 그대로 받는다).
해시와 비밀번호는 저장소·채팅·이슈에 남기지 않는다.

### 2. DB에 넣기
값은 자리표시자다. 아이디는 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자, 팀 코드는 `docs/admin-api-guide.md`의 소속팀 표에서 고른다.
앱은 오프셋 없는 한국 시간을 쓰므로 시각은 `now() AT TIME ZONE 'Asia/Seoul'`로 넣는다(DB 시간대가 UTC여도 맞는다).

```sql
INSERT INTO admins (id, login_id, password_hash, name, team, birth_date, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES (gen_random_uuid(), '<아이디>', '<htpasswd 출력의 : 뒤>', '<이름>', '<팀 코드>', '<YYYY-MM-DD>',
        'MASTER', 'APPROVED',
        now() AT TIME ZONE 'Asia/Seoul', now() AT TIME ZONE 'Asia/Seoul', 'SYSTEM', 'SYSTEM', false);
```

### 3. 환경 변수
어드민 인증에는 아래 두 값이 필요하다. 로컬은 `snsg/.env`, 운영은 **ECS 태스크 정의의 환경 변수에 더해야 한다**(저장소 밖에서 관리한다). `ADMIN_JWT_SECRET`이 없으면 앱이 뜨지 않는다.

| 변수 | 값 |
| --- | --- |
| `ADMIN_JWT_SECRET` | 어드민 토큰 서명 키. **`JWT_SECRET`과 다른 값**, 32바이트 이상 난수(예: `openssl rand -base64 48`) |
| `ADMIN_CORS_ALLOWED_ORIGINS` | 어드민 앱 출처(쉼표로 여럿). 사용자 앱 출처를 넣지 않는다. 비우면 브라우저에서 어드민 API를 부를 수 없다 |

기여 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 본다.

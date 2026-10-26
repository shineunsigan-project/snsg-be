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

기여 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 본다.

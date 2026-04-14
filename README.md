## 🚀 개발 환경 셋팅

### 1. 레포 클론
git clone https://github.com/shineunsigan-project/swineunshigan-admin-backend.git
cd

### 2. 브랜치 규칙
- 작업 시작 전 반드시 develop에서 분기
  - git checkout develop
  - git pull origin develop
  - git checkout -b feature/내-기능명

### 3. 작업 완료 후
 - git add .
 - git commit -m "feat: 기능 설명"
 - git push origin feature/내-기능명

→ GitHub에서 develop으로 PR 생성

### 4. main 직접 push 금지 ❌

### 5. .gitignore 설정

 - .gitignore 란?
   - Git에 올리지 말아야 할 파일들을 지정하는 파일이에요.

📁 내 로컬 프로젝트

├── .env              ← API 키, DB 비밀번호 등 (절대 올리면 안 됨 🚨)

├── node_modules/     ← 자동 생성되는 패키지 폴더 (용량 너무 큼)

├── .DS_Store         ← Mac에서 자동 생성되는 파일 (불필요)

├── build/            ← 빌드 결과물 (자동 생성됨)

└── app.js            ← 실제 코드 (올려야 함 ✅)

----

# swineunshigan-admin-backend
쉬는시간 서비스 관리자 전용 페이지 개발(서버 중심)

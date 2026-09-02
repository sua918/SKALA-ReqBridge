# ReqBridge

Spring Boot와 Vue로 구성된 모노레포입니다. 로컬 데이터베이스는 Docker Compose로 실행합니다.

## 기술 스택

- Java 21 LTS, Spring Boot 4.1.1, Gradle
- Vue 3, Vite 8, Node.js 24 LTS, npm
- PostgreSQL 17, Spring Data JPA

## 프로젝트 구조

```text
.
├─ frontend/          # Vue 애플리케이션
├─ backend/           # Spring Boot 애플리케이션
├─ docs/              # 프로젝트 문서
├─ docker-compose.yml # PostgreSQL
├─ .env.example
├─ .editorconfig
├─ .gitattributes
└─ .gitignore
```

## 사전 준비

- Java 21
- Node.js 24 및 npm 11 이상
- Docker와 Docker Compose

## 로컬 실행

1. 환경 변수 파일을 생성합니다.

   ```bash
   cp .env.example .env
   ```

   Windows PowerShell에서는 다음 명령을 사용합니다.

   ```powershell
   Copy-Item .env.example .env
   ```

2. PostgreSQL을 실행합니다.

   ```bash
   docker compose up -d
   ```

3. 백엔드를 실행합니다.

   ```bash
   cd backend
   ./gradlew bootRun
   ```

   Windows PowerShell에서는 `./gradlew.bat bootRun`을 사용합니다.

4. 새 터미널에서 프런트엔드를 실행합니다.

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## 접속 정보

| 구성 요소  | 주소                  |
| ---------- | --------------------- |
| Frontend   | http://localhost:5173 |
| Backend    | http://localhost:8080 |
| PostgreSQL | localhost:5432        |

백엔드 상태는 `GET http://localhost:8080/api/health`에서 확인할 수 있습니다. 프런트엔드 개발 서버의 `/api` 요청은 백엔드로 전달됩니다.

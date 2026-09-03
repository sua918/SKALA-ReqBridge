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

## Supabase PostgreSQL 사용

Supabase를 사용할 때도 Spring Data JPA는 PostgreSQL JDBC로 직접 연결합니다. Hibernate의 prepared statement와 호환되도록 Supabase Dashboard의 `Connect` 화면에서 Session pooler(포트 `5432`) JDBC 정보를 사용하거나, 실행 환경이 IPv6를 지원하면 direct connection을 사용합니다. Transaction pooler(포트 `6543`)는 사용하지 않습니다.

비밀 값은 저장소에 저장하지 말고 실행 환경에 설정합니다.

```bash
export SPRING_PROFILES_ACTIVE=supabase
export SUPABASE_DB_URL='jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require'
export SUPABASE_DB_USERNAME='postgres.<project-ref>'
export SUPABASE_DB_PASSWORD='<database-password>'

cd backend
bash gradlew bootRun
```

Supabase에서는 먼저 SQL Editor에서 `backend/src/main/resources/db/migration/V1__initial_schema.sql`을 한 번 실행합니다. `supabase` 프로필은 Flyway를 끄고 Hibernate `validate`만 수행하므로, 수동 SQL과 자동 마이그레이션이 중복 실행되지 않습니다. 업무 테이블은 기본 노출 스키마인 `public`이 아니라 `app`에 생성됩니다. 자세한 절차는 `docs/backend/supabase-connection.md`를 참고합니다.

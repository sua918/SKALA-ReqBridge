# Spring Boot와 Supabase PostgreSQL 연결

## 적용 방식

프론트엔드는 Supabase에 직접 접근하지 않는다. Vue → Spring Boot → JPA/JDBC → Supabase PostgreSQL 순서로 연결한다. Supabase에는 사용자가 SQL을 직접 적용하므로 `supabase` 프로필에서 Flyway는 비활성화하고 Hibernate는 `ddl-auto=validate`로 스키마 일치 여부만 검사한다.

## 1. Supabase에 스키마 적용

1. Supabase Dashboard에서 프로젝트를 만든다.
2. SQL Editor를 연다.
3. `backend/src/main/resources/db/migration/V1__initial_schema.sql` 전체를 붙여 넣고 한 번 실행한다.
4. Table Editor 또는 SQL Editor에서 `app` 스키마와 업무 테이블 9개가 생성됐는지 확인한다.

이 SQL은 초기 빈 스키마용이며 enum과 테이블을 생성하므로 같은 스키마에 반복 실행하지 않는다. 이미 운영 데이터가 있는 DB에는 V1을 재실행하지 말고 별도의 증분 마이그레이션을 작성한다.

## 2. JDBC 연결 정보 준비

Supabase Dashboard의 **Connect** 화면에서 연결 정보를 복사한다.

- 장시간 실행되는 Spring Boot 서버가 IPv6로 접속 가능하면 Direct connection `5432`를 우선 사용할 수 있다.
- IPv4 환경이면 Shared Pooler의 Session mode `5432`를 사용한다.
- Transaction mode `6543`은 prepared statement를 지원하지 않으므로 이 설정에서는 사용하지 않는다.

예시:

```bash
export SPRING_PROFILES_ACTIVE=supabase
export SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require'
export SUPABASE_DB_USERNAME='postgres.<project-ref>'
export SUPABASE_DB_PASSWORD='<database-password>'
export SUPABASE_DB_POOL_MAX_SIZE=5
```

현재 전달받은 Supabase 프로젝트의 Direct JDBC URL은 다음과 같이 설정한다. 비밀번호 자리만 Dashboard에서 설정한 DB 비밀번호로 환경 변수에 입력한다.

```bash
export SPRING_PROFILES_ACTIVE=supabase
export SUPABASE_DB_URL='jdbc:postgresql://db.bqhajlllofjhavefuvdq.supabase.co:5432/postgres?sslmode=require'
export SUPABASE_DB_USERNAME='postgres'
export SUPABASE_DB_PASSWORD='<YOUR-PASSWORD>'
```

`https://bqhajlllofjhavefuvdq.supabase.co`와 `sb_publishable_...` 값은 Supabase Data API/클라이언트용이다. Spring Boot의 PostgreSQL JDBC 연결에는 사용하지 않는다. 프론트가 DB에 직접 연결하지 않는 현재 구조에서는 publishable key도 백엔드 DB 설정에 넣지 않는다.

2026-09-02 현재 개발 환경에서 위 Direct host는 IPv6 주소만 확인됐고 `5432` 연결에 응답하지 않았다. 이 환경에서는 Dashboard의 **Connect → Session pooler**에 표시되는 host와 `postgres.bqhajlllofjhavefuvdq` 사용자명을 사용하는 것을 권장한다.

```bash
export SUPABASE_DB_URL='jdbc:postgresql://<dashboard-session-pooler-host>:5432/postgres?sslmode=require'
export SUPABASE_DB_USERNAME='postgres.bqhajlllofjhavefuvdq'
export SUPABASE_DB_PASSWORD='<YOUR-PASSWORD>'
```

Direct connection을 쓰는 경우 Dashboard가 제공한 사용자명과 host를 그대로 사용한다. 비밀번호에 특수문자가 있어도 별도 환경 변수 값으로 전달하므로 JDBC URL에 직접 URL 인코딩할 필요가 없다. 비밀번호와 전체 접속 문자열을 Git에 커밋하지 않는다.

## 3. 애플리케이션 실행

```bash
cd backend
bash gradlew bootRun
```

시작 로그에서 다음을 확인한다.

- 활성 프로필이 `supabase`
- HikariCP가 PostgreSQL 연결 생성
- Hibernate schema validation 성공
- 서버가 `8080` 포트에서 시작

그 후 다음 요청으로 확인한다.

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/projects
```

## 4. 자주 발생하는 오류

- `relation app.project does not exist`: V1 SQL을 실행하지 않았거나 다른 Supabase 프로젝트에 연결했다.
- `password authentication failed`: Connect 화면의 pooler 사용자명과 프로젝트 DB 비밀번호를 다시 확인한다.
- 연결 timeout 또는 host 해석 실패: Direct connection의 IPv6 접근 가능 여부를 확인하고, 불가능하면 Session pooler `5432`로 바꾼다.
- `prepared statement ...` 오류: Transaction pooler `6543`을 사용 중인지 확인한다.
- 권한 오류: 기본 `postgres` 계정이 아닌 별도 DB role이라면 `app` 스키마 USAGE와 테이블/시퀀스 권한을 부여한다.

Supabase Data API에 `app` 스키마를 노출할 필요는 없다. 현재 구조에서는 Spring Boot만 DB 자격 증명을 보유한다.

## 5. 제공된 SQL 파일 주의사항

Desktop의 `ReqBridge.sql`은 collaboration plan 1.2와 일치하지 않아 현재 상태로는 실행하지 않는다. 주요 차이는 다음과 같다.

- `DocumentSourceType`에 현재 미지원인 FILE 포함
- RequirementStatus가 5단계이며 plan/API의 OPEN·IN_REVIEW·CONFIRMED와 불일치
- Requirement의 confirmed_text 누락
- Analysis의 재시도 입력 보존용 input_snapshot 누락
- 답변 판정 사유를 Analysis.result.assessment 대신 Clarification 중복 컬럼에 저장

Supabase에는 저장소의 `backend/src/main/resources/db/migration/V1__initial_schema.sql`을 적용해야 현재 JPA Entity와 Hibernate validation이 일치한다. 팀이 Desktop SQL을 최종본으로 확정하려면 API·plan·JPA를 함께 바꾸는 별도 계약 변경이 필요하다.

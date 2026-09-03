# PDF 업로드 적용·인계 (API 0.4.0)

기준: 2026-09-03 협업 계획 1.3, 저장소 `docs/api/ReqBridge_API_Specification.md` 0.4.0, 제공된 Supabase 초기 SQL + RequirementStatus 변경 SQL.
수정 범위는 `backend/`뿐이다. 공용 API 문서·contract·Workflow·프론트는 수정하지 않았다.

## 구현된 범위

- 기존 `POST /api/projects/{projectId}/documents` JSON/TEXT 등록 유지. FILE은 이 경로에서 400.
- `POST /api/projects/{projectId}/documents/upload`: multipart `title` 하나 + `file` 하나. 성공 201, Location `/api/documents/{id}`.
- application/pdf MIME + PDF header + PDFBox parser 검증. 비어 있는 파일, 손상/암호화 PDF, 텍스트가 없거나 공백뿐인 PDF, 10 MiB 초과, 추출 텍스트 100000 코드 포인트 초과는 400.
- title은 기존과 동일한 공백 집합·200 코드 포인트 규칙. 임의 필드/sourceType/여러 파일/중복 title은 400.
- PDF 원본은 Storage, 추출 텍스트는 `app.document.content`. 원본 이름은 metadata에만 보관하고 object key는 `documents/{projectId}/{uuid}.pdf`.
- 외부 Document 응답은 기존 6개 필드만 반환한다. 목록 DTO와 `DocumentSnapshot`에 metadata를 추가하지 않았다.
- 프로젝트 확인 → PDF 검증·추출 → Storage upload → 별도 DB 트랜잭션 저장/커밋. DB 실패 시 방금 업로드한 object만 best-effort 삭제한다.
- JSON 미정의 필드 거절 회귀 실패도 확인하여 공통 Jackson 설정을 복구했다. Jackson 3 숫자 enum 입력 거절 설정과 회귀 테스트를 추가했다.
- OCR, DOCX, 원본 다운로드·수정·삭제 API, 자동 분석 접수, Preview, 실제 AI는 이번 변경에 추가하지 않았다.

## 1. 기존 Supabase DB에 적용할 SQL

이미 실행한 초기 SQL은 V1 구조이며, 본문으로 전달한 RequirementStatus 변경 SQL은 V2다. V1/V2를 다시 실행하거나 테이블을 삭제할 필요가 없다. V2 파일은 작업 시작부터 untracked 상태였으며 이번에 내용을 바꾸지 않았다. 새 브랜치 공유 시 기존 V2 파일도 누락되지 않도록 확인한다.

먼저 SQL Editor에서 확인한다.

```sql
SELECT t.typname, e.enumlabel
FROM pg_type t
JOIN pg_namespace n ON n.oid = t.typnamespace
JOIN pg_enum e ON e.enumtypid = t.oid
WHERE n.nspname = 'app'
  AND t.typname IN ('requirement_status', 'document_source_type')
ORDER BY t.typname, e.enumsortorder;
```

`requirement_status`가 EXTRACTED / AMBIGUOUS / CLARIFYING / IN_REVIEW / CONFIRMED이면 V2까지 적용된 상태다.

개발 서버를 잠시 중단하고 다음 **두 SQL을 각각 실행**한다. 운영 데이터가 있다면 사전에 백업한다. V4는 한 번만 적용하며, 중간 실패/이미 적용 여부가 불명확하면 컬럼·제약을 먼저 확인한다.

### 첫 번째 실행 — V3

파일: `backend/src/main/resources/db/migration/V3__document_file_source.sql`

```sql
ALTER TYPE app.document_source_type ADD VALUE IF NOT EXISTS 'FILE';
```

이 실행의 성공/커밋 후 다음을 실행한다. 같은 트랜잭션에서 바로 새 enum 값을 쓰면 PostgreSQL의 unsafe use of new value 오류가 발생할 수 있다.

### 두 번째 실행 — V4

파일: `backend/src/main/resources/db/migration/V4__document_file_metadata.sql`

```sql
BEGIN;

ALTER TABLE app.document
    ADD COLUMN storage_path TEXT,
    ADD COLUMN original_filename TEXT,
    ADD COLUMN mime_type VARCHAR(100),
    ADD COLUMN file_size_bytes BIGINT;

ALTER TABLE app.document ADD CONSTRAINT ck_document_file_metadata CHECK (
    (source_type = 'TEXT' AND storage_path IS NULL AND original_filename IS NULL
        AND mime_type IS NULL AND file_size_bytes IS NULL)
    OR
    (source_type = 'FILE' AND storage_path IS NOT NULL AND btrim(storage_path) <> ''
        AND original_filename IS NOT NULL AND btrim(original_filename) <> ''
        AND mime_type IS NOT NULL AND mime_type = 'application/pdf'
        AND file_size_bytes IS NOT NULL AND file_size_bytes BETWEEN 1 AND 10485760)
);

COMMENT ON COLUMN app.document.storage_path IS 'Private Storage object key; not a public URL';
COMMENT ON COLUMN app.document.original_filename IS 'Original client filename; never used as object key';
COMMENT ON COLUMN app.document.content IS 'TEXT: original text. FILE: validated text extracted from PDF';

COMMIT;
```

기존 TEXT row는 원문·ID를 유지하고 새 컬럼만 null이 된다. FILE row는 네 metadata 모두 필수이며 `IS NOT NULL`을 명시해 SQL CHECK의 NULL 허용 함정을 막았다. Analysis 이하의 스키마는 이번 PDF 변경으로 수정하지 않는다.

적용 확인:

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'app' AND table_name = 'document'
ORDER BY ordinal_position;

SELECT pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'app.document'::regclass
  AND conname = 'ck_document_file_metadata';
```

`supabase` 프로필에서는 Flyway가 꺼져 있어 서버 재시작만으로 적용되지 않는다. 새 로컬 개발 DB의 기본 프로필에서는 V1→V2→V3→V4를 Flyway가 순서대로 적용한다. Supabase를 수동 적용 방식에서 Flyway 방식으로 전환하는 작업은 이번 범위에 넣지 않았다.

## 2. dbdiagram.io

`backend/docs/db/ReqBridge_API_0.4.dbml` 전체를 편집기에 붙여 넣는다. 9개 테이블, 12개 enum, 15개 FK 관계와 `app` 스키마를 표현한다. DBML의 최신 parser(`dbmlv2`)로 검증한다.

부분 UNIQUE 인덱스 4개는 테이블 Note에 조건을 적었다. DBML에서 SQL로 다시 export한 내용을 운영 마이그레이션으로 사용하지 않는다. 실제 제약 적용은 V1~V4 SQL이 기준이다. Storage는 PostgreSQL 외부 리소스이므로 가짜 Storage 테이블/FK를 추가하지 않았다. Preview는 조회·조합 기능이라 별도 보고서 테이블도 없다.

## 3. Supabase Storage 설정

1. Supabase 프로젝트의 Storage에서 전용 bucket `reqbridge-documents`를 만든다.
2. **Public bucket은 끈다.** 파일 제한은 10485760 bytes 이상으로 설정하고 허용 MIME은 `application/pdf`로 제한한다. 전역 Storage 제한도 이 크기를 허용해야 한다.
3. 프론트의 직접 업로드 정책이나 공개 읽기 정책을 추가하지 않는다. 백엔드만 서버용 service_role credential로 접근한다.
4. 기존 PostgreSQL Session Pooler 환경변수는 유지하고 아래 세 개를 백엔드 실행 환경에 추가한다.

```text
SUPABASE_URL=https://bqhajlllofjhavefuvdq.supabase.co
SUPABASE_STORAGE_BUCKET=reqbridge-documents
SUPABASE_SERVICE_ROLE_KEY=<서버 전용 service_role JWT>
```

현재 client는 service_role JWT를 Authorization Bearer 및 apikey 헤더로 전송한다. 기존에 공유한 `sb_publishable_...` 키나 DB 비밀번호를 이 자리에 넣지 않는다. 실제 키는 채팅·소스·Git·프론트 환경변수에 넣지 말고 IDE 또는 배포 secret 환경변수에 설정한다. Spring Boot는 `.env` 파일을 자동 로딩하지 않는다. 이름 예시는 `backend/.env.example`에 있다.

Storage 설정이 없어도 TEXT 기능은 부팅·동작할 수 있지만 PDF 업로드는 안전한 500으로 실패한다. 설정 검증을 위해 실제 키나 Supabase bucket을 자동 생성·변경하지 않았다.

## 4. 요청 예시

프로젝트 ID가 1이고 서버가 8080 포트인 경우:

```bash
curl -i -X POST 'http://localhost:8080/api/projects/1/documents/upload' \
  -F 'title=고객 요구사항' \
  -F 'file=@/absolute/path/requirements.pdf;type=application/pdf'
```

응답의 Document ID로 기존 문서 상세/목록을 조회할 수 있다. 이후 분석은 별도 `POST /api/documents/{documentId}/analyses`다. 업로드가 자동으로 분석을 생성하지 않는다.

파일은 10 MiB(10485760 bytes)까지 허용한다. 기존 `max-request-size=12MB`는 title·boundary 등 multipart 부가 데이터 때문에 10 MiB 파일이 거절되지 않도록 유지했다. **파일 제한은 API·Spring max-file-size·Service·DB 모두 10 MiB**다. 전체 요청 제한 12MB는 파일 크기 상향이 아니다.

## 5. 검증 및 팀원 인계

- 전체 Gradle 테스트 81개(선택적 PostgreSQL 통합 테스트 5개 포함)가 실패·건너뜀 없이 통과했다. 실제 PostgreSQL 마이그레이션과 Hibernate validate, 실제 HTTP/Tomcat 경계값을 검증했다.
- TEXT 기존 데이터가 V3/V4 적용 후 원문·ID를 유지함을 별도 업그레이드 DB에서 확인했다.
- 10 MiB 파일 성공, 10 MiB+1 byte 거절, Storage 실패 시 row 미생성, 실제 FK 위반 시 row 롤백·cleanup 호출, metadata 비노출, FILE→CoreRequirementPort의 기존 Snapshot 연결을 검증했다.
- Storage client는 로컬 HTTP 서버로 요청 경로·헤더·원본 byte·upsert=false·DELETE·오류 비노출을 검증했다. 실제 Supabase Storage 업로드는 사용자 credential/bucket 설정 후 별도 확인해야 한다.
- 기본 `bash gradlew test`는 외부 DB 없이 76개의 단위·HTTP slice 테스트를 수행하고 PostgreSQL 테스트 5개는 건너뛴다. 실제 PostgreSQL 통합 테스트는 환경변수로 명시적으로 켠다. 반드시 폐기 가능한 전용 DB만 사용한다.

```bash
REQBRIDGE_TEST_POSTGRES_URL='jdbc:postgresql://localhost:5432/YOUR_DISPOSABLE_TEST_DB' \
REQBRIDGE_TEST_POSTGRES_USER='YOUR_TEST_DB_USER' \
REQBRIDGE_TEST_POSTGRES_PASSWORD='YOUR_TEST_DB_PASSWORD' \
bash gradlew test --rerun-tasks
```

### 남은 Workflow 연결 (신형섭 담당)

현재 `analysis/MockWorkflowAnalyzer.java`의 `analyze`는 sourceType이 TEXT이고 고정된 샘플 원문과 **완전히 동일할 때만** 동작한다. FILE guard를 허용하는 것 외에도 PDF 추출의 줄바꿈·공백을 감안한 Mock 시나리오 선택 규칙이 필요하다. 저장한 원문을 Core에서 임의 변형하지 않고 Workflow의 시나리오 판정에서 처리해야 한다.

이 파일 및 Workflow 테스트는 소유권에 따라 수정하지 않았다. 새 File Port나 PDF 전용 분석 Pipeline은 필요 없다. `DocumentSnapshot.sourceType`은 기존 String 그대로 FILE을 반환하고 `content`로 추출 텍스트가 전달되는 것까지 실제 Core로 확인했다. **PDF 업로드부터 Workflow 승인까지의 전체 E2E가 완료됐다는 뜻은 아니다.**

### 운영상 한계

Storage와 PostgreSQL 사이에는 분산 트랜잭션이 없다. cleanup 실패·업로드 응답 유실·프로세스 강제 종료 시 orphan object가 남을 수 있으며 로그의 uploadId로 운영자가 DB와 대조해야 한다. 자동 재시도/오브젝트 덮어쓰기는 하지 않는다. 인증 없는 내부 데모 범위이며 공개 배포에는 접근 제어·업로드 rate limit·parser 자원 격리 등이 추가로 필요하다.

구현 참고: [PDFBox 3.0](https://pdfbox.apache.org/3.0/getting-started.html), [Supabase Storage REST](https://supabase.com/docs/reference/self-hosting-storage/upload-a-new-object), [표준 업로드](https://supabase.com/docs/guides/storage/uploads/standard-uploads), [DBML 문법](https://dbml.dbdiagram.io/docs/). Supabase는 큰 파일에 resumable upload를 권장하지만, 이번 10 MiB 내부 데모는 명세 범위 내 표준 서버 업로드로 구현했다.

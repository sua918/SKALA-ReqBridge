# Backend Core 적용 기록

## 적용 범위

- 브랜치: `feat/backend-core`
- 기본 패키지: `com.sua.reqbridge`
- 소유 경로: `project`, `document`, `requirement`, 공통 DB 설정과 마이그레이션
- 원본 SQL: `/Users/hyeongjunhan/Downloads/ReqBridge.sql`

원본 SQL은 초기 ERD의 테이블과 FK를 확인하는 입력으로 사용했다. 실제 V1 마이그레이션에는 협업 계획의 P0 보완 규칙을 함께 반영했다.

## 스키마 결정

- 로컬 프로필에서는 Flyway가 `app` 스키마를 생성·관리한다.
- 사용자가 SQL Editor로 테이블을 구성하는 Supabase 프로필에서는 Flyway를 끄고 Hibernate `validate`만 수행한다.
- Hibernate는 `ddl-auto=validate`만 사용하고 테이블을 생성하거나 변경하지 않는다.
- 시간 타입은 UTC 처리를 위해 `TIMESTAMPTZ`를 사용한다.
- 원본 `JSON` 응답은 조회·인덱스 확장성을 위해 `JSONB`로 저장한다.
- `Requirement`에 `documentId`, `contentVersion`, `approvedRevisionId`, `confirmedText`, JPA 낙관적 잠금용 `lockVersion`을 추가했다.
- Analysis 종류와 선택 참조, 입력 content version, error code를 추가했다.
- 수정안과 답변은 `revision_clarification` 다대다 근거 테이블로 연결한다.
- 문서/요구사항의 활성 작업, 번호 중복, 한 요구사항의 PROPOSED 수정안 하나 제한을 DB 제약으로 보호한다.
- 복합 FK로 Requirement와 최초 Analysis의 document가 같도록 하고, Clarification과 AmbiguityIssue의 requirement가 같도록 강제한다.

## Supabase 연결

`supabase` 프로필은 다음 환경 변수를 요구한다.

| 환경 변수 | 내용 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `supabase` |
| `SUPABASE_DB_URL` | Session pooler `5432` 또는 direct JDBC URL, `sslmode=require` 포함 |
| `SUPABASE_DB_USERNAME` | Dashboard가 제공한 DB 사용자명 |
| `SUPABASE_DB_PASSWORD` | 프로젝트 DB 비밀번호 |
| `SUPABASE_DB_POOL_MAX_SIZE` | 선택, 기본값 5 |

Hibernate/JPA는 prepared statement를 사용하므로 Supavisor transaction pooler `6543`은 사용하지 않는다. 비밀번호는 properties나 Git 추적 파일에 기록하지 않는다.

## 구현된 내부 기능

- Project 생성·단건·목록 서비스
- TEXT Document 및 PDF→FILE Document 생성·단건·프로젝트별 목록·비관적 잠금
- Requirement 일괄 생성·단건·문서별 목록·비관적 잠금
- `contentVersion` 예상값 검증과 증가
- `EXTRACTED`·`AMBIGUOUS`·`CLARIFYING`·`IN_REVIEW` 상태 전이와 승인 전용 `CONFIRMED`
- 승인 revision ID와 확정 본문을 함께 보존하는 `CONFIRMED` 전환
- 확정 요구사항 재개방 방지

## 구현된 P1 기본 API

- `POST /api/projects`, `GET /api/projects`, `GET /api/projects/{projectId}`
- `POST /api/projects/{projectId}/documents`, `GET /api/projects/{projectId}/documents`
- `GET /api/documents/{documentId}`
- `POST /api/projects/{projectId}/documents/upload` (PDF multipart)
- `GET /api/documents/{documentId}/requirements`, `GET /api/requirements/{requirementId}`
- 성공·목록·오류 공통 래퍼와 `201 Location`
- 요청의 미정의 필드, 잘못된 enum, 문자열·ID 범위 검증
- 프로젝트·문서 ID 내림차순 및 요구사항 순번 오름차순 조회

## 공용 계약 연결

- `CoreRequirementPort` 구현 어댑터와 contract snapshot 매핑을 제공한다.
- Core 리소스·상태 예외는 공용 `ResourceNotFoundException`, `StateConflictException`으로 연결한다.
- Requirement Entity·Controller·DB는 공용 5단계 `RequirementStatus`를 동일하게 사용한다.
- 세분화된 업무 오류 코드는 Workflow가 생성하고 공통 HTTP 처리기가 보존한다. Preview Controller는 Core/Report 소유이며 WorkflowPreviewPort로 연결한다.
- `docs/api/openapi.yaml`은 아직 제공되지 않아 Markdown API 명세를 기준으로 한다.

외부 API에서 Requirement 상태를 직접 바꾸거나 확정 상태를 우회하는 기능은 노출하지 않는다.

## CRUD와 변경 이력 범위

현재 공식 API 0.4.0의 Core 계약은 Project·TEXT/PDF Document 생성/조회와 Requirement 조회다. P1에서 Requirement 수동 생성·원문 수정·상태 PATCH·삭제를 금지하므로, 일반적인 의미의 PUT/PATCH/DELETE를 임의로 추가하지 않았다.

Requirement의 Core 변경 이력 책임은 다음 데이터 보존으로 구현했다.

- 현재 업무 버전 `contentVersion`과 별도 JPA 잠금 버전 `lockVersion`
- 승인된 Revision ID, 확정 본문, 확정 시각
- 최초 Analysis ID와 고객 원문 보존
- 예상 업무 버전이 맞을 때만 증가·상태 전이·확정하는 도메인 메서드

수정안·질문·분석 실행의 전체 이력은 기존 `requirement_revision`, `clarification`, `analysis` 테이블에 남는다. 이 영역의 Java Entity/Service와 외부 Workflow API는 신형섭 소유이므로 Core에 별도 History 테이블이나 중복 API를 만들지 않았다.

## History·예외 처리 보강 (2026-09-03)

기준 커밋은 `d4d47b4` (Workflow P1 병합), 작업 브랜치는 `feat/backend-core`다. 이미 병합된 승인·거절·재생성·재시도/조회 API를 재작성하지 않고 아래 Core 경계와 검증을 추가했다.

- 최초 문서 ID·Analysis ID·순번·원문을 JPA `updatable=false`로 지정한다. 직접 SQL까지 막는 DB 트리거를 추가한 것은 아니다.
- `CONFIRMED`의 버전 증가·상태 변경·확정본 재기록을 `REQUIREMENT_CONFIRMED`로 차단한다. 동일 승인 재전송의 멱등 처리는 기존 Workflow에서 처리한다.
- 예상 버전 불일치는 `CONTENT_VERSION_CONFLICT`다. 잘못된 예상 버전 범위는 입력 오류이고, 저장 버전이 JSON 안전 정수 상한에 도달하면 증가시키지 않는다.
- `RequirementCoreService.confirmRequirement`는 `MANDATORY` 트랜잭션으로 호출자의 승인 트랜잭션에만 참여한다. 독립 호출로 확정본만 커밋할 수 없으며 Workflow와 공용 Port 서명은 변경하지 않는다.
- 공통 `GlobalApiExceptionHandler`의 우선순위를 명시해 기능별 fallback advice의 원인 예외 매핑이 공통 타입/필드 오류를 가리지 않도록 한다. 팀원 소유 예외 처리기는 수정하지 않는다.
- 업무별 409 코드 보존, 잘못된 ID의 `fieldErrors`, 누락된 parameter/part의 400, DB 잠금·낙관적 동시성 오류의 안전한 409를 검증한다. 알 수 없는 서버 오류는 내부 내용을 숨긴 500이다.

History는 다음 기존 API로 조회한다. 별도 `/history` API나 응답 필드를 추가하지 않는다.

| 조회 | 내용 |
| --- | --- |
| `GET /api/requirements/{id}` | 원문·현재 버전·상태·승인 수정안 ID·확정 본문 |
| `GET /api/requirements/{id}/workflow` | 모든 문제·질문/답변 회차·수정안·거절 사유·근거 답변 ID |
| `GET /api/documents/{id}/analyses` | 분석/답변/재생성 작업과 결과·실패·재시도 연결 |

로컬 PostgreSQL 17.10에서 실제 HTTP/비동기 Mock/JPA를 연결해 답변→추가 질문→거절→재생성→승인, 반복 조회/재전송, 실패 재시도 입력 보존, 트랜잭션 롤백, 복합 FK 차단, 동시 버전 증가를 검증했다. 테스트 범위/재실행 명령은 `core-test-notes.md`를 참고한다.

이번 변경에는 DB DDL·마이그레이션이 없으므로 새 Supabase SQL 적용은 필요 없다. 기존 V1~V4가 반영된 DB를 전제로 하며 실제 Supabase/Storage 연결 상태는 이번 로컬 테스트로 보증하지 않는다.

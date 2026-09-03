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
- TEXT Document 생성·단건·프로젝트별 목록·비관적 잠금
- Requirement 일괄 생성·단건·문서별 목록·비관적 잠금
- `contentVersion` 예상값 검증과 증가
- `EXTRACTED`·`AMBIGUOUS`·`CLARIFYING`·`IN_REVIEW` 상태 전이와 승인 전용 `CONFIRMED`
- 승인 revision ID와 확정 본문을 함께 보존하는 `CONFIRMED` 전환
- 확정 요구사항 재개방 방지

## 구현된 P1 기본 API

- `POST /api/projects`, `GET /api/projects`, `GET /api/projects/{projectId}`
- `POST /api/projects/{projectId}/documents`, `GET /api/projects/{projectId}/documents`
- `GET /api/documents/{documentId}`
- `GET /api/documents/{documentId}/requirements`, `GET /api/requirements/{requirementId}`
- 성공·목록·오류 공통 래퍼와 `201 Location`
- 요청의 미정의 필드, 잘못된 enum, 문자열·ID 범위 검증
- 프로젝트·문서 ID 내림차순 및 요구사항 순번 오름차순 조회

## 공용 계약 연결

- `CoreRequirementPort` 구현 어댑터와 contract snapshot 매핑을 제공한다.
- Core 리소스·상태 예외는 공용 `ResourceNotFoundException`, `StateConflictException`으로 연결한다.
- Requirement Entity·Controller·DB는 공용 5단계 `RequirementStatus`를 동일하게 사용한다.
- 세분화된 Workflow 오류 코드와 Preview Controller는 Workflow 구현 범위에서 연결한다.
- `docs/api/openapi.yaml`은 아직 제공되지 않아 Markdown API 명세를 기준으로 한다.

외부 API에서 Requirement 상태를 직접 바꾸거나 확정 상태를 우회하는 기능은 노출하지 않는다.

## CRUD와 변경 이력 범위

Collaboration Plan 1.2의 외부 계약에는 Project·Document 생성/조회와 Requirement 조회만 있다. P1에서 Requirement 수동 생성·원문 수정·상태 PATCH·삭제를 금지하므로, 일반적인 의미의 PUT/PATCH/DELETE를 임의로 추가하지 않았다.

Requirement의 Core 변경 이력 책임은 다음 데이터 보존으로 구현했다.

- 현재 업무 버전 `contentVersion`과 별도 JPA 잠금 버전 `lockVersion`
- 승인된 Revision ID, 확정 본문, 확정 시각
- 최초 Analysis ID와 고객 원문 보존
- 예상 업무 버전이 맞을 때만 증가·상태 전이·확정하는 도메인 메서드

수정안·질문·분석 실행의 전체 이력은 기존 `requirement_revision`, `clarification`, `analysis` 테이블에 남는다. 이 영역의 Java Entity/Service와 외부 Workflow API는 신형섭 소유이므로 Core에 별도 History 테이블이나 중복 API를 만들지 않았다.

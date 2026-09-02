# 한형준 담당 범위 구현 확인

기준: `ReqBridge_Backend_Collaboration_Plan (1).md` 1.2와 API 명세 0.3.0.

| 담당 항목 | 상태 | 구현·판단 |
| --- | --- | --- |
| Project Entity/JPA | 완료 | ID, 이름, 설명, 생성 시각과 길이 계약 반영 |
| Document Entity/JPA | 완료 | Project ID, TEXT-only native enum, 제목, 원문 보존, 생성 시각 반영 |
| Requirement Entity/JPA | 완료 | 문서/최초 분석/순번/원문, 공용 5단계 상태, 업무·잠금 버전, 승인 Revision·확정 본문·시각 반영 |
| PostgreSQL 초기 스키마 | 완료 | `app` 스키마, JSONB/TIMESTAMPTZ/native enum, FK/CHECK/부분 UNIQUE 반영 |
| 로컬 JPA/Flyway | 완료 | 로컬 프로필은 Flyway V1 적용, Hibernate validate 사용 |
| Supabase JPA/JDBC 설정 | 설정 완료 | 수동 SQL 프로필과 환경 변수 구성. 실제 클라우드 인증은 DB 비밀번호 미제공으로 미검증 |
| Project API | 완료 | POST, 목록 GET, 상세 GET |
| Document API | 완료 | TEXT POST, 프로젝트별 목록 GET, 상세 GET |
| Requirement API | 완료 | 문서별 목록 GET, 기본 상세 GET |
| PUT/PATCH/DELETE | 적용 제외 | plan 7.2가 P1 수정·삭제 API와 승인 우회 상태 변경을 금지 |
| 데이터 저장·조회 | 완료 | Repository/Service, 명시적 정렬, 존재 검증, 트랜잭션 적용 |
| 잠금·버전 검증 | 완료 | Document/Requirement 비관적 잠금, JPA 낙관적 잠금, contentVersion 예상값 검증 |
| Requirement 변경 이력 Core 부분 | 완료 | 업무 버전, 최초 분석, 승인 Revision ID, 확정 본문·시각 보존 |
| Workflow 전체 이력 | 상대 담당 대기 | Analysis/Issue/Clarification/Revision Entity·Service는 신형섭 소유. DB 테이블만 준비 |
| 공통 응답 | 완료 | 단건 `{data}`, 목록 `{data:{items}}`, 오류 `{error}` |
| 입력 예외 | 완료 | bean validation, 미정의 JSON, enum, 타입, Unicode 공백·코드 포인트, ID 범위 |
| 업무·DB 예외 | Core 완료 | 404, 일반 409, DB constraint 409, 예상치 못한 오류 500 및 내부 정보 비노출 |
| 세분화 Workflow 409 코드 | Workflow 구현 대기 | 공용 기본 예외는 연결했으며 세부 코드는 Workflow 서비스에서 적용 |
| CoreRequirementPort adapter | 완료 | 공용 인터페이스 구현, Snapshot 매핑, 공용 상태·예외 연결 |
| Preview P2 | WorkflowPreviewPort 대기 | 계약 구현이 들어온 후 `preview` 프로필에서 연결 |

현재 한형준 담당 P0/P1의 독립 구현과 공용 Core 계약 연결은 완료됐다. 남은 항목은 Supabase DB 비밀번호를 이용한 실제 접속 확인과 Workflow 통합이다.

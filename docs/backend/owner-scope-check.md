# 한형준 담당 범위 구현 확인

기준: 공식 API 0.4.0, Collaboration Plan 1.3의 역할 분담, `feat/backend-core`의 `d4d47b4` + 2026-09-03 History/예외 처리 보강.

| 담당 항목 | 상태 | 구현·판단 |
| --- | --- | --- |
| Project Entity/JPA | 완료 | ID, 이름, 설명, 생성 시각과 길이 계약 반영 |
| Document Entity/JPA | 완료 | TEXT/FILE, 원문/추출 텍스트, PDF metadata 반영 |
| Requirement Entity/JPA | 완료 | 문서/최초 분석/순번/원문, 공용 5단계 상태, 업무·잠금 버전, 승인 Revision·확정 본문·시각 반영 |
| PostgreSQL 초기 스키마 | 완료 | `app` 스키마, JSONB/TIMESTAMPTZ/native enum, FK/CHECK/부분 UNIQUE 반영 |
| 로컬 JPA/Flyway | 검증 완료 | V1~V4, Hibernate validate, PostgreSQL 17.10 통합 테스트 |
| Supabase JPA/JDBC 설정 | 설정 완료·실환경 별도 | 수동 SQL 프로필 유지. 이번 작업에서 Supabase/Storage에 접속하지 않음 |
| Project API | 완료 | POST, 목록 GET, 상세 GET |
| Document API | 완료 | TEXT/PDF POST, 프로젝트별 목록 GET, 상세 GET |
| Requirement API | 완료 | 문서별 목록 GET, 기본 상세 GET |
| PUT/PATCH/DELETE | 적용 제외 | plan 7.2가 P1 수정·삭제 API와 승인 우회 상태 변경을 금지 |
| 데이터 저장·조회 | 완료 | Repository/Service, 명시적 정렬, 존재 검증, 트랜잭션 적용 |
| 잠금·버전 검증 | 보강 및 검증 완료 | 동시 Core 버전 증가는 하나만 성공, 오래된 버전/상한 검사, 승인/확정본 원자성·복합 FK 롤백 |
| Requirement 변경 이력 Core 부분 | 보강 및 검증 완료 | 최초 원문 연결 JPA 업데이트 제외, 확정 후 변경 차단, 승인 트랜잭션 필수 참여 |
| Workflow 연동 이력 | 해당 시나리오 통합 검증 완료 | 병합된 이력 API로 답변→추가 질문→거절→재생성→승인, 반복 조회/재전송 및 실패 재시도 보존 검증. 팀원 구현 재작성 없음 |
| 공통 응답 | 완료 | 단건 `{data}`, 목록 `{data:{items}}`, 오류 `{error}` |
| 입력 예외 | 보강 및 검증 완료 | 공통 advice 우선순위, fieldErrors, JSON/타입·필수 parameter/part, 기존 길이/공백/ID 범위 |
| 업무·DB 예외 | 보강 및 검증 완료 | DB constraint/잠금 충돌의 안전한 409, 서버 오류 내부 정보 비노출 500 |
| 세분화 Workflow 409 코드 | 보강 및 검증 완료 | 11개 업무 코드 보존, Core CONTENT_VERSION_CONFLICT·REQUIREMENT_CONFIRMED 생성 |
| CoreRequirementPort adapter | 완료 | 공용 인터페이스 구현, Snapshot 매핑, 공용 상태·예외 연결 |
| Preview P2 | 기존 병합 구현 | 이번 두 항목의 추가 구현 범위 밖. Preview 전체 QA 완료를 의미하지 않음 |

이번에 History 테이블·`/history` Endpoint·API 필드를 새로 추가하지 않았다. 현재 버전/확정본은 Requirement, 질문/답변/수정안은 workflow, 실행/실패/재시도는 analyses API로 확인한다. 기존 Workflow/contract/API 명세를 수정하지 않았다.

최종 로컬 검증: **179개 통과, 실패 0, 생략 0** (실제 PostgreSQL 테스트 10개 포함). 재실행 명령과 검증 한계는 `core-test-notes.md`, 코드/책임 설명은 `core-notes.md`를 참조한다.

새 DB DDL은 없으므로 이번 변경만을 위한 Supabase 추가 SQL은 없다. V1~V4 적용, 실제 클라우드 연결·Storage·배포 환경 준비는 별도로 확인한다. 커밋/push/PR은 이 기록 자체로 수행된 것이 아니다.

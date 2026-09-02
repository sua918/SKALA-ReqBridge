# ReqBridge 백엔드 병렬 개발 역할·계약 문서

- 작성일: 2026-09-02
- 대상: 한형준, 신형섭 및 각 담당자의 Codex·Claude 개발 세션
- 목적: 이 문서와 실제 저장소를 기준으로 두 사람이 담당 영역을 병렬 구현할 수 있도록 파일 소유권, 데이터 변경 권한, 연결 계약, 통합 순서를 정한다.
- 현재 산출물: 역할·설계 문서. 실제 코드·DB·API 명세를 생성하거나 수정한 결과가 아니다.

## 0. 적용 기준과 읽는 방법

이 문서는 ReqBridge 팀 주제 제안서, 2026-09-02 Day1 오전 회의록, 제공된 README, 이후 신형섭의 범위 지시를 바탕으로 작성했다. 기존·추가 첨부 제안서의 추출 본문은 동일했다.

실제 저장소는 제공되지 않았다. README에서 확인한 구성은 Java 21, Spring Boot 4.1.1, Gradle, Vue 3/Vite 8, Node.js 24, PostgreSQL 17, Spring Data JPA, 모노레포, PostgreSQL용 Docker Compose다. 버전 호환성과 구현 상태를 검증했다는 의미는 아니다. 개발 에이전트는 실제 빌드 파일과 기존 코드를 먼저 확인하며 임의로 버전을 바꾸거나 프로젝트를 재생성하지 않는다.

이 문서의 출처에 있는 역할·기능 범위와, 병렬 개발을 위해 이번에 정한 설계 기준을 구분한다.

| 구분 | 내용 |
| --- | --- |
| 사용자 확정 | 전체 API 명세는 신형섭이 작성·관리하고 팀 문서에 게시한다. |
| 사용자 확정 | 차별화 ① 답변 재판정·추가 질문, ② 불명확성 유형 분류는 MVP다. |
| 사용자 확정 | 차별화 ③ 고객 질문서·개발팀용 문서는 계획에 포함하되 MVP 연결 후 구현한다. 파일 다운로드는 추후 확장이다. |
| 회의록 기준 | Acceptance Criteria는 ②에 부속된 시간 여유 시 기능이며 후순위로 계획한다. |
| 이번 분담 기준 | 한형준은 기본 데이터·공통 기반·결과 Preview, 신형섭은 Workflow·Mock AI·전체 API 명세를 맡는다. |
| 이번 설계 기준 | 아래 상태값, 내부 Port, 입력 방식, 버전·중복 처리, 패키지·파일 경계는 병렬 구현을 위한 기본안이다. 회의에서 이미 확정된 사실로 인용하지 않는다. |

후속 명시적 팀 결정이 있으면 신형섭이 문서와 API 명세를 함께 갱신한다. 에이전트가 추측으로 공통 계약을 바꾸지 않는다. 기존 저장소 규칙과 충돌하면 충돌 파일과 대안을 보고하고, 영향을 받지 않는 담당 작업을 먼저 진행한다.

## 1. 서비스와 구현 범위

PM이 고객 문서를 등록하고, 불명확한 요구사항을 질문·답변으로 구체화한 뒤 수정안을 검토·승인한다. 고객 답변은 PM이 입력한다. AI는 초안을 제안하며 최종 승인은 사람이 수행한다.

### 1.1 구현 순서

| 단계 | 범위 | 완료 기준 |
| --- | --- | --- |
| P0 공통 기반 | 계약, 공통 DTO·오류, 엔티티 연결, 개발 DB, 샘플 입력 | 두 브랜치가 같은 계약과 데이터 규칙으로 작업 가능 |
| P1 MVP | 문서·요구사항 조회, Mock 분석, 불명확성 분류, 질문·답변, 재판정·추가 질문, 수정안·승인·거절 | 모호한 답변 뒤 추가 답변으로 요구사항 하나가 확정됨 |
| P2 계획 포함 | 고객 질문서·개발팀용 문서 Preview | 동일한 저장 데이터에서 결과를 조합해 화면에 전달 |
| P3 후순위 | Given–When–Then Acceptance Criteria, 직접 질문 작성 등 수동 보조 | P1·P2 안정화 후 수행 |
| 추후 확장 | PDF/Word/CSV 다운로드, 실제 LLM, 외부 메신저·이메일 발송, 고객 전용 계정 | 이번 기본 구현에 포함하지 않음 |

알림은 확장 시 단방향으로 제한한다. 이번 계획에서 외부 메시지를 자동 발송하거나 양방향 메신저 연동을 구현하지 않는다. 파일 다운로드 보류와 원본 입력은 별개다.

### 1.2 이번 구현의 입력·출력 기준

- 기본 입력은 TEXT와 준비된 샘플 원문이다. 실제 PDF 업로드·텍스트 추출은 기본 구현에 추가하지 않는다. `sourceType=FILE`은 모델 확장 가능성일 뿐 파일 처리 완료를 의미하지 않는다.
- 결과는 JSON 기반 Preview다. 별도 파일 저장소나 보고서 테이블을 먼저 만들지 않는다.
- 고객용 결과는 현재 미해결 질문 목록이다. 별도의 고객용 범위 확인서까지 자동으로 추가하지 않는다.
- 개발팀용 결과는 승인된 수정안과 그 근거, 별도로 표시한 미확정 요구사항이다.
- Mock의 성공은 실제 LLM 연동 완료가 아니다. 수동 질문·수정 기능을 구현하기 전에는 모든 AI 업무를 수동으로 대체할 수 있다고 표시하지 않는다.

## 2. 개발자별 소유권

각자 담당 영역의 Controller, Service, Entity, Repository, 구현용 DTO, 테스트를 함께 소유한다. 다른 사람의 소유 파일은 읽을 수 있지만 직접 수정하지 않는다.

| 영역 | 한형준 | 신형섭 |
| --- | --- | --- |
| Project·Document·Requirement | 엔티티·저장·조회·기본 API 전체 | 공개 Port를 통해 사용 |
| Analysis·Mock Adapter | 사용하지 않음 | 요청·작업 상태·실행·실패·결과 처리 |
| AmbiguityIssue | 직접 접근하지 않음 | 유형·근거·해결 상태 |
| Clarification | 직접 접근하지 않음 | 질문·답변·회차·재판정 |
| RequirementRevision | 확정본 복사와 ID 보관 | 초안·근거·승인·거절·버전 |
| AcceptanceCriteria | Preview 반영 | 생성·저장·조회, P3 |
| 결과 Preview | 고객 질문서·개발팀용 결과 조합과 API | 문서 생성용 Workflow 조회 Port 구현 |
| 요구사항 전체 상태 | 필드·저장 메서드의 코드 소유 | Workflow에서 전이 조건을 판단하고 Port로 변경 요청 |
| 공통 설정·응답 구현·예외 처리·DB 스키마 적용 | 단독 수정 책임 | 필요한 변경을 요청 문서로 전달 |
| 전체 OpenAPI·공용 계약 타입 | 검토·구현 | 단독 작성·변경·게시 책임 |
| 통합 조정 | 공통 기반·스키마·자신의 기능 수정 | API·Workflow·자신의 기능 수정 |

결과 Preview 소유권은 전체 API 명세 작성권과 다르다. 한형준이 결과 API를 구현하고 신형섭이 그 API 명세를 작성한다.

## 3. 저장소·파일 작업 경계

### 3.1 실제 루트 매핑

README 기준 루트는 `backend/`, `frontend/`, `docs/`, `docker-compose.yml`이다. Java 기본 패키지는 실제 `@SpringBootApplication` 위치에서 확인한다. 아래 `<base>`는 그 패키지의 경로를 뜻하며 문자 그대로 폴더를 만들지 않는다. 테스트는 `src/test/java`의 같은 하위 패키지를 사용한다.

기존 패키지가 있으면 P0에서 대응 경로를 이 문서에 기록하고 유지한다. 두 에이전트가 각각 패키지 구조를 새로 정하지 않는다.

| 파일·경로 | 수정 책임자 |
| --- | --- |
| `backend/src/main/java/<base>/project/**` | 한형준 |
| `.../document/**`, `.../requirement/**`, `.../report/**` | 한형준 |
| `.../analysis/**`, `.../ambiguity/**`, `.../clarification/**`, `.../revision/**`, `.../acceptance/**` | 신형섭 |
| `.../contract/**` | 신형섭. 양쪽이 의존하는 순수 인터페이스·record·enum·예외 타입 |
| `.../common/**`, `.../config/**`, Application 클래스 | 한형준 |
| Gradle 파일·wrapper, `application*.yml/properties`, Docker Compose, `.env.example`, 루트 README·CI | 한형준. 필요 변경만 수행 |
| DB 스키마 초기화·마이그레이션·공용 seed 파일 | 한형준 단독 적용 |
| `docs/api/**`, 이 역할 문서 | 신형섭 |
| `docs/backend/core-notes.md`, `docs/backend/core-test-notes.md` | 한형준 |
| `docs/backend/workflow-notes.md`, `docs/backend/workflow-schema-request.md` | 신형섭 |
| `frontend/**` | 백엔드 두 사람 모두 수정하지 않음 |

한형준은 공용 DTO 필드나 enum을 로컬 편의로 바꾸지 않는다. 신형섭은 스키마·공통 설정을 직접 바꾸지 않는다. 전체 프로젝트 자동 포맷이나 광범위한 import 정리도 하지 않는다.

### 3.2 Git 격리

- 각 개발자는 별도 clone 또는 worktree를 사용한다. 같은 작업 디렉터리에서 동시에 브랜치를 바꾸지 않는다.
- 권장 브랜치: `feat/backend-core`, `feat/backend-workflow`.
- P0 공통 계약·기반을 먼저 기본 브랜치에 반영하고 양쪽이 같은 커밋에서 분기한다. 병렬 개발 전에 필요한 짧은 선행 단계다.
- 통합 PR은 기본 데이터 → Workflow → Preview 순서로 합친다. Preview는 Workflow의 조회 구현이 들어온 뒤 활성화한다.
- 다른 브랜치의 변경을 복사해서 상대 담당 클래스를 대신 구현하지 않는다. 강제 push·타인 변경 덮어쓰기를 하지 않는다.

## 4. DB 모델과 무결성

### 4.1 회의록 엔티티를 유지하는 기본 관계

- Project 1:N Document
- Document 1:N Analysis
- 문서 최초 분석 Analysis 1:N Requirement
- Requirement 1:N AmbiguityIssue
- AmbiguityIssue 1:N Clarification
- Requirement 1:N RequirementRevision
- RequirementRevision 1:N AcceptanceCriteria (P3)

서로 다른 소유 영역은 ID 참조를 기본으로 한다. 상대 Entity에 대한 양방향 JPA 연관관계와 cascade는 만들지 않는다. 같은 소유 영역 내부 매핑은 담당자가 선택할 수 있다. DB 외래키는 한형준이 적용한다.

### 4.2 이번 설계에서 필요한 보완

| 모델 | 추가·명확화할 항목 | 목적 |
| --- | --- | --- |
| Requirement | `documentId`, `approvedRevisionId`, `confirmedText`, `contentVersion` | 문서 조회, 승인된 버전·본문, 오래된 분석 결과 검증 |
| Analysis | `kind`, 선택적 `requirementId`, 선택적 `clarificationId`, `inputContentVersion`, `errorCode` | 문서 분석과 답변 재판정·수정안 생성 작업을 같은 작업 모델로 추적 |
| RequirementRevision | `inputContentVersion`, `revisionNo`, `rejectionReason`, 근거 답변 연결 | 오래된 초안 승인 방지, 다중 답변 근거 |
| RevisionClarification | `revisionId`, `clarificationId` 연결 테이블, 신형섭 소유 | 수정안 하나가 여러 답변을 사용한 사실을 보존 |

회의록의 단일 `based_on_clarification_id`만으로 모든 답변 근거를 표현하지 않는다. P0에서 아직 코드가 없다면 다중 연결을 사용한다. 이미 컬럼이 있으면 즉시 삭제하지 말고 스키마 변경 요청을 작성해 한형준이 마이그레이션한다.

Clarification의 `requirementId`를 유지하면 연결된 AmbiguityIssue의 requirementId와 같은지 저장 시 검증한다. 연결 테이블의 답변들도 수정안과 동일한 요구사항에 속해야 한다.

`Requirement.contentVersion`은 최초 생성 시 1, 질문의 `roundNo`와 수정안의 `revisionNo`도 각각 소속 문제·요구사항 안에서 1부터 시작한다. `contentVersion`은 낙관적 잠금용 기술 필드와 별개다. 답변 등록 등 AI 판단 입력이 바뀔 때 증가한다. 작업 실행·완료 같은 진행 상태만 바뀔 때는 증가하지 않는다.

### 4.3 스키마 적용 책임

- 각 담당자가 자기 Entity의 필드를 설계한다. 실제 DB 스키마 변경 파일은 한형준만 수정·적용한다.
- 신형섭은 타입·null 허용·FK·unique·index·기존 데이터 영향을 `workflow-schema-request.md`에 적는다.
- 기존 저장소의 스키마 관리 방식을 유지한다. 초기화 SQL, 마이그레이션, Hibernate 자동 DDL을 서로 중복 활성화하지 않는다.
- 기존 방식이 없다면 P0에서 한형준이 로컬 PostgreSQL용 초기화 SQL 한 벌을 책임지고, JPA는 `ddl-auto=validate`를 기준으로 설정한다. 이후 변경도 같은 책임자가 관리한다. 파괴적 DB 초기화는 자동 실행하지 않는다.
- 초기화 SQL 변경은 기존 DB에 자동 반영되지 않으므로 변경분 적용 절차를 함께 남긴다. 테스트의 격리된 DB는 별도로 구성할 수 있다.
- 모든 Analysis는 documentId를 갖는다. DOCUMENT의 requirementId·clarificationId는 null, ANSWER는 둘 다 필수, REVISION은 requirementId만 필수다. Requirement.analysisId는 최초 DOCUMENT 작업만 가리킨다. Analysis의 선택적 역참조 FK는 테이블 생성 후 적용한다. Requirement.documentId와 연결 Analysis.documentId는 동일해야 한다.
- 제약: `(analysis_id, sequence_no)`, `(ambiguity_issue_id, round_no)`, `(requirement_id, revision_no)`, `(revision_id, clarification_id)` 중복 금지.
- P1에서 삭제 API와 cascade delete는 제공하지 않는다. 질문·답변·승인 근거가 사라지지 않도록 한다.

## 5. 상태와 처리 규칙

### 5.1 공용 enum

| 대상 | 값 | 의미 |
| --- | --- | --- |
| AnalysisKind | `DOCUMENT`, `ANSWER`, `REVISION` | 문서 추출·분석, 답변 재판정, 수정안 재생성 |
| AnalysisStatus | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | 작업 진행 상태 |
| RequirementStatus | `OPEN`, `IN_REVIEW`, `CONFIRMED` | 확인 중, 검토할 수정안 있음, 담당자 확정 |
| IssueStatus | `OPEN`, `RESOLVED` | 추가 정보 필요 여부 |
| ClarificationStatus | `WAITING`, `ANSWERED`, `RESOLVED` | 미답변, 답변 저장됨, 해당 답변으로 문제 해결 |
| RevisionStatus | `PROPOSED`, `APPROVED`, `REJECTED` | 수정안 검토 상태 |
| AmbiguityType | `QUANTITY_MISSING`, `PERFORMANCE_MISSING`, `CONDITION_MISSING`, `ACTOR_MISSING`, `SUCCESS_CRITERIA_MISSING`, `TERM_AMBIGUOUS`, `EXCEPTION_MISSING` | 회의록의 일곱 유형 |

판정상 명확함과 사람의 승인은 다르다. Analysis가 COMPLETED여도 Requirement가 CONFIRMED가 되는 것은 아니다.

### 5.2 정상 흐름과 분기

1. 문서 분석을 접수하면 Analysis(PENDING)를 DB에 먼저 저장하고 202를 반환한다. 실제 처리는 커밋 이후 실행한다.
2. Mock이 요구사항 목록·문제·질문을 반환한다. 신형섭이 결과를 검증하고 Core Port로 요구사항을 생성한 뒤 자기 소유의 문제·질문을 저장한다. 결과 전체 저장과 COMPLETED 전환은 한 트랜잭션으로 처리한다.
3. 문제가 있으면 Requirement는 OPEN, 문제는 OPEN, 첫 질문은 WAITING이다. 처음부터 문제가 없으면 원문을 기반으로 검토 가능한 수정안을 생성하고 IN_REVIEW로 보낸다. 자동 확정하지 않는다.
4. PM이 WAITING 질문에 답하면 answer를 저장하고 ANSWERED로 바꾸며 contentVersion을 증가시킨다. 동일 트랜잭션에서 ANSWER 작업을 등록한다.
5. 답변이 부족하면 기존 질문은 ANSWERED로 남기고, 같은 문제에 다음 round의 WAITING 질문을 만든다. 사용자 입력 없이 자동 반복하지 않는다.
6. 충분하면 해당 문제와 그 답변을 RESOLVED로 변경한다. 과거 불충분했던 답변은 ANSWERED로 유지한다. 다른 OPEN 문제가 남으면 Requirement는 OPEN이다.
7. 모든 문제가 해결되면 해당 시점의 원문·답변 전체를 근거로 수정안을 생성하고 IN_REVIEW로 바꾼다. 수정안에는 inputContentVersion과 근거 답변 ID 목록을 보존한다. 한 요구사항에 검토 가능한 PROPOSED는 최대 하나만 유지하며 새 초안 생성으로 기존 제안이 사라지게 하지 않는다.
8. 담당자가 제안된 수정안을 승인하면 Revision을 APPROVED로 바꾸고 Requirement의 approvedRevisionId·confirmedText·상태를 함께 반영한다.
9. 거절하면 Revision을 REJECTED, Requirement를 OPEN으로 바꾸고 필수 거절 사유를 저장한다. 동일 수정안을 같은 결정으로 다시 검토하면 기존 결과를 반환하며, 결정 번복은 409로 제한한다. 이미 해결된 문제를 자동으로 다시 열지는 않는다. 모든 문제가 해결된 상태에서는 별도의 REVISION 작업으로 새 수정안을 요청할 수 있다. Mock도 거절 사유·회차에 따른 다음 시나리오를 제공한다.
10. CONFIRMED 이후 내용 변경·재개방은 P1 범위 밖이다. 일반 수정 API나 재분석으로 확정본을 덮어쓰지 않는다.

### 5.3 실패·중복·동시성

- 한 요구사항에 활성 작업(PENDING/PROCESSING)이 있으면 새 답변·새 생성·승인 요청을 409로 막는다. 서로 다른 질문에 대한 답변도 P1에서는 요구사항 단위로 순차 처리한다.
- 같은 문서의 활성 DOCUMENT 작업도 하나로 제한한다. 성공한 최초 문서 분석의 재실행은 P1에서 409로 제한한다. 실패한 작업만 새 Analysis로 재시도한다.
- 이미 답한 질문에 같은 답변을 다시 제출해도 기존 Analysis를 반환하고 중복 작업을 만들지 않는다. 다른 답변으로 덮어쓰려 하면 409다. 답변 수정 기능은 별도 범위다.
- ANSWER 작업이 실패해도 저장된 답변과 과거 질문은 보존한다. 실패 작업 재시도는 동일 입력으로 새 Analysis를 생성하며 활성 재시도가 있으면 그 ID를 반환한다.
- 형식이 맞지 않는 Mock 응답은 FAILED로 처리한다. 부분적으로 생성된 요구사항·질문을 성공 결과처럼 노출하지 않는다. 결과 반영 트랜잭션이 롤백된 뒤 FAILED 기록은 별도 트랜잭션으로 남긴다. 승인·확정본 반영과 달리 실패 작업 기록은 독립 커밋이 필요하다.
- 작업 결과의 inputContentVersion과 현재 Requirement.contentVersion이 다르면 결과 반영을 거절한다. 오래된 PROPOSED 수정안도 승인하지 않는다.
- 문서 분석 접수는 문서 잠금, 요구사항 관련 변경은 Core Port의 요구사항 잠금을 먼저 확보한다. 잠금 뒤 관련 job과 revision을 확인하는 순서를 통일한다.
- 승인 시 요구사항 잠금 → revision 검증 → 승인·확정본 반영을 한 트랜잭션에서 수행한다. 이미 승인한 동일 revision 재승인은 같은 확정 결과를 반환하며 새 버전을 만들지 않는다.
- 비동기 작업을 요청 트랜잭션이 커밋되기 전에 실행하지 않는다. 앱 재시작 시 남은 PENDING/PROCESSING은 중단된 작업으로 FAILED 처리하고 명시적 재시도를 허용한다. 로컬 MVP에서 외부 큐는 추가하지 않는다.

## 6. 두 영역의 내부 연결 계약

### 6.1 의존 방향

- 신형섭 소유 Workflow Service → `CoreRequirementPort` → 한형준 소유 기본 데이터 Service
- 한형준 소유 Report Service → `WorkflowPreviewPort` → 신형섭 소유 조회 Service
- CoreRequirementPort 구현은 WorkflowPreviewPort나 Report Service에 의존하지 않는다.
- 인터페이스와 불변 DTO는 `contract`에 둔다. Entity·Repository·JPA annotation·Spring Bean 구현은 넣지 않는다.
- Port 호출은 동일 Spring 애플리케이션의 메서드 호출이다. 내부 HTTP와 별도 서버를 만들지 않는다.

### 6.2 P0에서 고정할 Port 서명

아래는 경계 계약이다. 신형섭이 실제 Java 인터페이스·record로 한 번 작성하고, 한형준이 구현 가능성을 확인한다. 메서드와 DTO를 각 브랜치에서 독립적으로 재작성하지 않는다.

```java
interface CoreRequirementPort {
    DocumentSnapshot getDocument(long documentId);
    DocumentSnapshot lockDocument(long documentId);
    List<RequirementSnapshot> createRequirements(
        long documentId, long analysisId, List<RequirementSeed> items);
    RequirementSnapshot getRequirement(long requirementId);
    RequirementSnapshot lockRequirement(long requirementId);
    long advanceContentVersion(long requirementId, long expectedContentVersion);
    void changeStatus(long requirementId, long expectedContentVersion,
                      RequirementStatus targetStatus);
    void confirmRequirement(long requirementId, long expectedContentVersion,
                            long revisionId, String approvedText);
    List<RequirementSnapshot> listRequirements(long documentId);
}

interface WorkflowPreviewPort {
    WorkflowPreviewSnapshot getPreview(long documentId);
}
```

| 공용 DTO | 필드 |
| --- | --- |
| DocumentSnapshot | `long id`, `long projectId`, `String title`, `String content`, `String sourceType` |
| RequirementSeed | `int sequenceNo`, `String originalText` |
| RequirementSnapshot | `long id`, `long documentId`, `long analysisId`, `int sequenceNo`, `String originalText`, `RequirementStatus status`, `long contentVersion`, `Long approvedRevisionId`, `String confirmedText` |
| WorkflowPreviewSnapshot | `long documentId`, `List<WorkflowRequirementSnapshot> requirements` |
| WorkflowRequirementSnapshot | `long requirementId`, `List<IssueSnapshot> issues`, `List<QuestionSnapshot> questions`, `ApprovedRevisionSnapshot approvedRevision` |
| IssueSnapshot | `long id`, `AmbiguityType type`, `String evidence`, `IssueStatus status` |
| QuestionSnapshot | `long id`, `long requirementId`, `long issueId`, `int roundNo`, `String questionText`, `String answerText`, `ClarificationStatus status` |
| ApprovedRevisionSnapshot | `long id`, `int revisionNo`, `String text`, `List<Long> basedOnClarificationIds`, `List<AcceptanceCriterionSnapshot> acceptanceCriteria` |
| AcceptanceCriterionSnapshot | `String given`, `String when`, `String then` |

nullable: approvedRevisionId, confirmedText, approvedRevision은 미확정 시 null, answerText는 미답변 시 null이다. 나머지 목록은 null 대신 빈 목록이다. Acceptance Criteria 미구현 시 빈 목록을 반환한다. `sequenceNo`는 1부터 시작하며 응답은 번호 오름차순이다.

- createRequirements의 반환 결과는 sequenceNo로 입력과 대응한다. 한형준은 이 메서드에서 분석이나 질문을 생성하지 않는다.
- lock 메서드는 호출자의 트랜잭션 안에서 동작하고 잠금을 반환 시 해제하지 않는다. REQUIRED 트랜잭션을 사용한다. REQUIRES_NEW로 확정본만 먼저 커밋하지 않는다.
- advanceContentVersion은 예상 버전 일치 여부를 확인하고 1 증가한 버전을 반환한다. 형섭의 답변 저장과 같은 트랜잭션에 참여한다.
- changeStatus는 OPEN↔IN_REVIEW만 허용한다. CONFIRMED는 confirmRequirement만 사용할 수 있다.
- confirmRequirement는 Core Entity를 변경하는 메서드다. revision의 유효성·문제 해결 여부 검증은 신형섭의 승인 Service가 먼저 수행한다. 외부 API에서 직접 노출하지 않는다.
- Port 예외는 공용 `ResourceNotFoundException`, `StateConflictException`으로 한정한다. 소유권은 신형섭, HTTP 매핑은 한형준이다. 구체적인 코드·메시지는 전체 API 명세에 기록한다.
- Report는 기본 데이터와 Workflow 조회를 하나의 읽기 전용 REPEATABLE_READ 트랜잭션으로 읽는다. approvedRevisionId 불일치가 발견되면 오류 처리하고 다른 버전의 본문을 혼합하지 않는다.

### 6.3 독립 개발용 대체 구현

- 상대 구현이 없으면 각자 테스트 소스에만 Port stub을 둔다. main 소스에 Fake 상대 Service나 가짜 Entity를 만들지 않는다.
- Core CRUD는 Workflow 없이 실행 가능해야 한다. Report Controller는 P2의 `preview` 프로필에서만 활성화하고 실제 WorkflowPreviewPort 구현이 있을 때 통합 실행한다. 기본 실행은 상대 Bean 부재로 실패하지 않는다.
- Workflow 단위·서비스 테스트는 Core Port stub으로 진행하고, 실제 Core가 반영된 뒤 통합 실행한다. Workflow 전체 애플리케이션 부팅을 위해 임시 공용 설정을 덮어쓰지 않는다.
- 임시 테스트 응답을 실제 DB 조회 성공이나 E2E 완료로 보고하지 않는다.

## 7. 전체 API 명세 작성과 라우트 소유권

전체 API 명세는 신형섭이 단독 관리한다. 한형준은 담당 API의 요청·응답·필드 제약·오류를 검토하고 구현한다. API 계약 변경은 신형섭이 먼저 명세에 반영하고 영향받는 담당자·프론트에 전달한다. 팀 문서 게시도 신형섭이 수행하며 에이전트가 임의 게시하지 않는다.

### 7.1 공통 기준

- Prefix `/api`, JSON camelCase, DB snake_case.
- ID는 양수 Long/BIGINT, 시간은 UTC ISO-8601. 열거형은 위 대문자 문자열을 사용한다.
- 성공 응답은 `{ "data": ... }`, 목록은 `{ "data": { "items": [...] } }`. P1은 페이지네이션을 추가하지 않는다.
- 오류는 `{ "error": { "code": "...", "message": "...", "fieldErrors": [] } }`. stack trace·raw AI 응답·비밀 값은 노출하지 않는다.
- 기본 HTTP 의미: 생성 201, 비동기 접수 202, 조회·일반 처리 200, 입력 검증 400, 대상 없음 404, 상태·버전 충돌 409, 서버 오류 500.
- 기존 `/api/health`의 계약은 실제 구현을 확인해 유지하며 일반 업무 응답에 맞춘다는 이유로 임의 변경하지 않는다.
- P1은 인증·회원가입을 새로 추가하지 않는 로컬 PM 데모다. 로그인 검증이 없는 상태에서 승인자 신원을 검증했다고 표시하지 않는다.

### 7.2 담당 라우트 기준안

아래는 분담용 라우트 기준이며 요청·응답 필드 전체를 갖춘 OpenAPI 완성본은 아니다. 신형섭이 이를 기준으로 명세를 작성·고정한 후 API 구현을 시작한다. 한 API에 Controller 두 개를 만들지 않는다.

| 담당 | Method · Path | 기능 |
| --- | --- | --- |
| 한형준 | POST /api/projects | 프로젝트 생성 |
| 한형준 | GET /api/projects | 프로젝트 목록 |
| 한형준 | GET /api/projects/{projectId} | 프로젝트 상세 |
| 한형준 | POST /api/projects/{projectId}/documents | 텍스트 문서 등록 |
| 한형준 | GET /api/projects/{projectId}/documents | 문서 목록 |
| 한형준 | GET /api/documents/{documentId} | 문서 원문 조회 |
| 한형준 | GET /api/documents/{documentId}/requirements | 요구사항 목록 |
| 한형준 | GET /api/requirements/{requirementId} | 요구사항 기본 상세·확정본 |
| 신형섭 | POST /api/documents/{documentId}/analyses | 최초 문서 분석 접수 |
| 신형섭 | GET /api/analyses/{analysisId} | 모든 종류의 작업 상태·결과·오류 |
| 신형섭 | POST /api/analyses/{analysisId}/retries | 실패 작업 재시도 |
| 신형섭 | GET /api/requirements/{requirementId}/workflow | 문제·질문·수정안 조회 |
| 신형섭 | POST /api/clarifications/{clarificationId}/answers | 답변 저장과 재판정 접수 |
| 신형섭 | POST /api/requirements/{requirementId}/revisions | 거절 이후 수정안 재생성 접수 |
| 신형섭 | POST /api/revisions/{revisionId}/review | 승인·거절, 승인 시 확정까지 수행 |
| 한형준 | GET /api/documents/{documentId}/previews/customer | 고객 질문서 Preview, P2 |
| 한형준 | GET /api/documents/{documentId}/previews/developer | 개발팀용 Preview, P2 |

P1에서 외부 수동 Requirement 생성·원문 수정·상태 PATCH·삭제 API는 기본 노출하지 않는다. 요구사항 일괄 생성과 상태 변경은 내부 Port다. 기존 CRUD 중 상태를 우회 변경할 수 있는 API가 이미 있다면 한형준이 제한하고 신형섭이 명세에 반영한다. P3 수동 편집을 추가할 때는 contentVersion 및 재판정 규칙을 함께 설계한다.

### 7.3 문서·계약 파일

- `docs/api/openapi.yaml`: 전체 외부 API의 기준 문서, 신형섭 소유.
- `docs/api/mock-scenarios.md`: 시나리오 입력·단계별 출력·실패 분기, 신형섭 소유.
- `docs/api/contract-changes.md`: 변경 필드·이유·영향 범위, 신형섭 소유.
- 공용 Java DTO와 외부 JSON DTO는 역할이 다르다. 내부 Port 변경 없이 외부 응답을 조립할 수 있지만 공개 필드 변경은 명세와 맞춘다.
- 형섭의 명세가 작성되기 전에는 외부 요청·응답 DTO를 각 에이전트가 추정 구현하지 않는다. 그동안 Entity·Repository·Port 구현·테스트를 진행한다.

## 8. Mock과 실제 AI의 경계

신형섭은 문서 분석, 답변 재판정, 수정안 생성 인터페이스를 구현하고 Mock Adapter를 연결한다. 실제 Spring AI·LLM SDK·API key·외부 호출은 이번 범위에 추가하지 않는다.

- AI 입력: 원문, 문제 유형·근거, 관련 질문·답변 이력, 현재 contentVersion, 필요한 경우 거절 사유.
- AI 출력: 요구사항 후보, 문제·질문 후보, 답변 충분 여부·근거·추가 질문, 수정안 초안.
- AI 출력에 DB 생성 ID·APPROVED·CONFIRMED 권한을 맡기지 않는다. ID는 DB, 최종 상태는 서버 업무 로직이 결정한다.
- 고정 JSON도 제출 답변·회차에 맞는 시나리오를 선택한다. 어떤 답변을 넣어도 같은 성공 결과만 반환하는 방식으로 재판정 완료를 주장하지 않는다.
- 같은 분석 ID 조회는 같은 저장된 결과를 반환한다. 조회할 때 재분석·새 질문 생성을 수행하지 않는다.
- 불명확한 답변, 충분한 답변, 여러 문제 중 일부만 해결, 분석 실패, 수정안 거절 후 재생성 시나리오를 준비한다.

## 9. 결과 Preview 상세 경계

### 고객 질문서

현재 OPEN 문제에 연결된 WAITING 질문을 요구사항별로 묶는다. 원문, 문제 유형·근거, 질문 ID·내용·회차를 포함한다. 이미 해결된 문제를 새 질문처럼 섞지 않는다. 질문할 것이 없으면 빈 목록과 해당 상태를 반환한다.

### 개발팀용 문서

CONFIRMED 요구사항에는 approvedRevisionId에 해당하는 승인 본문과 근거 답변을 표시한다. 미확정 요구사항은 별도 목록으로 제공하며 PROPOSED를 확정 본문으로 대체하지 않는다. AC는 승인된 수정안에 연결된 항목이 있을 때만 표시한다.

한형준은 저장 데이터를 양식에 맞춰 조합한다. 새 LLM 호출, 추가 요구조건 창작, 실제 파일 생성·다운로드를 넣지 않는다. 결과마다 생성 시각과 기반 수정안 ID를 제공한다. Preview를 고객이 승인한 문서라고 부르지 않는다.

## 10. 선행 작업·병렬 개발·통합

### P0: 한 번만 함께 맞추는 기준 커밋

1. 실제 저장소에서 기본 패키지, 기존 응답·오류·DB 방식·health 구현을 확인한다.
2. 신형섭이 `contract`의 인터페이스·DTO·enum·예외와 API 기준을 작성한다. 한형준은 실제 저장·조회 구현 가능성을 검토한다.
3. 한형준이 초기 스키마·공통 설정·응답 구현·예외 매핑을 반영한다. Workflow Entity용 테이블도 형섭의 필드 계약으로 먼저 준비한다.
4. 공용 변경을 순차 반영해 하나의 기준 커밋을 만든다. 양쪽이 동일 커밋에서 분기한다. 이후 담당 외 파일은 수정하지 않는다.

### P1: 병렬

- 한형준: Project·Document·Requirement, CoreRequirementPort, 기본 Controller·조회 테스트.
- 신형섭: Workflow Entity·Mock·작업 처리·문제·질문·답변·수정안·승인, 전체 API 명세, Core stub 기반 테스트.
- 상대 로직 부재는 테스트 stub으로 대체한다. 필요한 계약 수정은 요청 기록 후 신형섭이 공용 계약을 갱신한다.

### 통합과 P2

1. Core 실제 구현을 기본 브랜치에 반영한다.
2. Workflow 브랜치를 동기화하고 실제 Core 연결을 확인한다. 실패 시 자기 담당 파일만 수정한다.
3. 신형섭이 WorkflowPreviewPort를 완성한다. 한형준은 미리 작성한 Report의 stub 테스트를 실제 구현과 연결한다.
4. `preview` 프로필에서 Preview Controller를 활성화하고 전체 데모를 수행한다.
5. 최종 API 명세와 구현 응답을 대조한 후 신형섭이 팀 문서에 게시한다. 팀 E2E·발표는 채수아와 연계한다.

## 11. 담당별 검증과 완료 보고

테스트는 충돌·데이터 손실·상태 오류를 검증하는 데 집중한다. 상대 구현을 복제한 테스트를 만들지 않는다.

| 담당 | 반드시 확인할 내용 |
| --- | --- |
| 한형준 | 문서·요구사항 저장·조회, ID 매핑, Core Port 버전 검증·잠금, 승인 본문 보존, DB schema 일치 |
| 신형섭 | 부족한 답변 후 round 증가, 충분한 답변 처리, 복수 문제 부분 해결, 승인 없는 자동 확정 방지, 실패·중복 재시도, 오래된 수정안 거절 |
| 공동 통합 | 분석 결과 부분 저장 방지, 승인·확정 원자성, 기본 CRUD의 승인 우회 차단, 서로 다른 요구사항 ID 연결 거절 |
| Preview | 미확정 본문 분리, 승인된 동일 버전 참조, 해결된 질문 제외, 다운로드 미구현 유지 |

첫 E2E는 문서 한 개 → 문제 두 개 → 첫 답변 부족 → 추가 질문 → 한 문제 해결 → 남은 문제 해결 → 수정안 승인 → 고객 질문서·개발팀용 Preview다. 결과는 실제 DB에 남고 재조회되어야 한다.

완료 보고에는 변경 파일, 구현 API, 실행한 검증과 결과, stub으로만 확인한 부분, 남은 상대 의존성을 적는다. 테스트 실행이 불가능했다면 성공으로 표시하지 않는다.

## 12. Codex·Claude에 전달할 실행 지시

다음 지시에 이 문서 전체와 실제 저장소를 함께 제공한다. 프로젝트 전체 개발을 포괄적으로 지시하지 않는다.

### 한형준 세션 시작 문구

> 나는 한형준이며 ReqBridge 기본 데이터·공통 기반·결과 Preview 담당이다. 첨부된 역할·계약 문서를 기준으로 내 소유 경로만 구현하라. 먼저 실제 저장소를 읽어 문서의 경로를 매핑하고 P0 계약·기반 반영 여부를 확인하라. 전체 API 명세와 contract의 작성·변경권은 신형섭에게 있다. 상대 영역의 Entity·Repository·Service를 대신 작성하지 마라. 계약이 아직 없으면 추정 DTO로 구현하지 말고 필요한 계약과 영향을 보고하며 독립 가능한 내 작업을 진행하라. 별도 브랜치나 worktree에서 작업하고, 상대 영역 수정이 필요하면 변경 요청으로 남겨라. 문서 다운로드·실제 AI·프론트 수정은 수행하지 마라. 완료 시 실제 검증 결과와 미연결 항목을 구분해 보고하라.

### 신형섭 세션 시작 문구

> 나는 신형섭이며 ReqBridge AI Workflow·요구사항 품질·전체 API 명세 담당이다. 첨부된 역할·계약 문서를 기준으로 내 소유 경로만 구현하라. 먼저 실제 저장소를 읽고 P0에서 contract와 전체 API 명세를 단일 기준으로 작성하라. 한형준 소유 공통 설정·DB 스키마 파일·Core Entity·Repository는 직접 수정하지 마라. 분석 결과 저장과 상태·확정본 변경은 CoreRequirementPort로만 수행하라. 상대 구현이 없으면 테스트 소스의 stub을 사용하고 실제 통합 성공과 구분하라. 고객 질문서·개발팀용 Preview에 필요한 WorkflowPreviewPort를 제공하되 Report Controller는 만들지 마라. 차별화 ①·②를 MVP로 완성하고 ③의 연결을 지원하며, 다운로드는 추후 확장으로 유지하라. 실제 AI SDK·외부 메시지 발송은 추가하지 마라. 명세 변경은 contract-changes에 기록하고 팀 게시 자체는 사용자가 수행하도록 결과물을 제공하라.

---

이 문서는 충돌 가능성을 줄이는 작업 계약이다. 두 사람이 같은 기준 커밋과 최신 계약 버전을 사용하고, 소유권을 지키는 것이 전제다. 실제 저장소를 보지 않은 상태에서 무충돌·즉시 실행을 보장하는 문서는 아니다.

# AI-Ready 1차 구조 개선

## 범위

`fix/analysis`에서 Mock 분석을 교체 가능한 내부 계약으로 분리했다. 실제 LLM을 연결하거나 문서 업로드 직후 자동 분석하도록 변경한 작업은 아니다.

- 외부 REST Endpoint·HTTP Method·요청/응답 JSON·오류 코드 유지.
- 문서 등록 201 → 별도 분석 요청 202 → 작업 조회/폴링 유지.
- DB 컬럼·Enum·마이그레이션·Supabase·Storage·프론트 수정 없음.
- 승인·거절·contentVersion 증가·Preview 계약 유지. 실행 시에도 현재 버전과 확정 여부를 재확인한다.
- 기본 분석기는 계속 `MockWorkflowAnalyzer`. 시연 문장과 충분/불충분 판정 및 생성 문구 유지.

## 변경한 책임

| 구성요소 | 책임 |
| --- | --- |
| `contract.ai.WorkflowAnalyzer` | 문서 분석, 답변 판정, 수정안 생성, 구현체 종류·스키마 버전 제공 |
| `contract.ai.AnalyzerTypes` | DB Entity가 없는 공용 입력·출력 record |
| `MockWorkflowAnalyzer` | 기존 시연 분석·판정과 두 서비스에 있던 고정 수정안 문구 |
| `DocumentAnalysisService` | 문서 작업 접수, Analyzer 호출, 결과 검증, 요구사항·문제·질문 저장 |
| `AnswerWorkflowService` | 답변 접수·버전 처리, 판정 및 필요 시 수정안 생성 요청, 상태 변경·저장 |
| `RevisionWorkflowService` | 거절 후 생성 요청·저장, 기존 승인·거절 처리 |
| `AnalyzerOutputValidator` | 전체 후보 검증과 실행 Adapter/버전 일치 검사 |
| `AnalyzerInputs` | 업무 Entity를 값 DTO로 변환, 실행 시 기준 버전 확인 |

Controller는 변경하지 않았다. Workflow 서비스는 Mock 클래스나 그 내부 타입을 참조하지 않는다. `AnalysisConfiguration`의 기본 Bean 생성 지점만 Mock 구체 클래스를 알고 있다.

## 공용 입력 계약

메서드별 Java record가 실제 내부 계약이다. `analyzer-contract.schema.json`은 이 계약을 설명하는 JSON Schema이며, 향후 LLM Adapter가 JSON을 파싱할 때 사용할 수 있다. 이번 작업에 JSON Schema 실행 엔진이나 LLM 호출은 추가하지 않았다.

### `analyze(DocumentSnapshot)`

기존 Core DTO를 재사용한다: 문서 ID, 프로젝트 ID, 제목, 원문, TEXT/FILE 구분. 분석 원문과 sourceType은 작업의 저장된 `input_snapshot`에서 읽는다. PDF는 추출 텍스트를 전달하며 별도 AI 경로를 만들지 않는다.

### `assess(AnswerAssessmentInput)`

요구사항 ID·원문·기준 버전, 불명확성 유형·근거, 질문, 고객 답변, 해당 요구사항의 답변된 질문 이력을 전달한다. 이력은 issueId/roundNo 순서이고 불충분했던 답변도 포함한다. 이번 고객 답변은 저장된 작업 입력에서 읽는다.

### `generateRevision(RevisionGenerationInput)`

요구사항 ID·원문·기준 버전과 근거 질문/답변을 전달한다. 최초 생성에는 previousText/rejectionReason이 null이다. 거절 후 재생성에는 이전 거절 수정안과 작업에 저장된 거절 사유를 전달한다. 실제 의미 기반 재작성은 구현하지 않았으며 Mock은 여전히 같은 문장을 반환한다.

입력 ID는 출처를 식별하기 위한 값일 뿐 모델에 DB 접근 권한을 주지 않는다. 결과 DTO에는 생성 ID, 승인/확정 상태, contentVersion 변경 명령이 없다. 번호 할당·근거 연결·승인은 서비스에서 처리한다.

## 결과 검증

- DOCUMENT: 비어 있지 않은 요구사항 목록, 1부터 누락·중복 없는 sequenceNo, 유효한 원문 및 문제 목록. 문제에는 Enum 유형·근거·질문이 필요하다. 입력 배열 순서는 강제하지 않는다.
- ANSWER: 판정 근거 필수. 불충분이면 추가 질문 필수, 충분하면 추가 질문은 null.
- REVISION: 비어 있거나 공백-only인 본문 금지.
- 텍스트는 기존 공통 공백 규칙과 Unicode 코드포인트 기준을 사용한다. 내부 분석 출력의 텍스트 상한은 각각 100,000 코드포인트다.
- 모든 DOCUMENT 후보를 저장 전에 검증한다. 입력·출력 타입을 외부 API의 `AnalysisResult`로 그대로 직렬화하지 않고 기존 응답 계약으로 매핑한다.
- 검증 실패는 `AiOutputInvalidException` → 기존 Worker의 `AI_OUTPUT_INVALID` 처리. 예외에 원본 출력·문서·키를 포함하지 않는다.
- 마지막 Issue 해결 후 수정안 생성이 실패해도 실행 트랜잭션이 롤백된다. 제출된 고객 답변과 증가한 접수 버전은 보존되고, 실행에서 변경한 Issue/Clarification 해결 상태와 수정안 저장만 롤백된다.

## 분석 기록·재시도 호환성

신규 DOCUMENT·ANSWER·REVISION 작업은 주입된 Analyzer의 `adapterType()`과 `schemaVersion()`을 명시적으로 저장한다. DB의 기존 `adapter_type`, `schema_version`을 사용한다. 인자 없는 기존 factory는 기존 호출자·테스트의 Mock 호환용으로 유지하며 운영 서비스는 메타데이터 명시 overload를 사용한다.

기존 `input_snapshot` 필드는 추가·삭제·이름 변경하지 않았다.

| 작업 | 기존 저장 키 |
| --- | --- |
| DOCUMENT | documentId, sourceType, content |
| ANSWER | clarificationId, issueId, answerText, contentVersion |
| REVISION | requirementId, documentId, contentVersion, rejectionReason |

`Analysis.retry()`는 원래 입력·Adapter·스키마 버전을 그대로 복사한다. 실행 시작 전에 기록된 종류/버전이 현재 Analyzer와 같은지 검사한다. 다르면 모델을 호출하지 않고 기존 `ANALYSIS_EXECUTION_FAILED`로 종료한다. 현재 설정의 모델로 몰래 실행하면서 이전 모델의 기록을 남기지 않는다.

확장 입력의 원문·질문 이력·이전 수정안은 기존 업무 데이터에서 조립한다. ANSWER/REVISION은 요구사항 행을 잠그고 저장된 기준 버전을 확인한 뒤 조립한다. 이 방식은 현재의 원문·답변 불변 및 버전 규칙을 전제로 한다. 모든 맥락과 프롬프트를 작업별로 저장하는 완전한 재현 스냅샷이나 임의 SQL 변경에 대한 감사 기능은 아니다.

## Bean 교체 방법

이번 최소 범위에서는 설정 파일이나 환경변수를 추가할 필요가 없다. 기본 `WorkflowAnalyzer` Bean은 Mock이고, `@ConditionalOnMissingBean(WorkflowAnalyzer.class)`로 대체 Bean 등록 시 기본 Mock을 생성하지 않는다. 기본/대체 구성 모두 세 서비스에 같은 인터페이스 Bean이 주입되는 것을 테스트한다.

실제 Adapter 구현 시 공용 인터페이스를 구현하고 해당 Bean을 구성 단계에서 등록한다. 기존 서비스를 수정하지 않고 연결할 수 있지만, 업무 입력/출력 계약 자체가 변경되는 경우에는 별도 합의가 필요하다. 실제 공급자 선택은 그때 설정으로 명시적으로 추가한다. 미구현 `llm`을 설정한 뒤 Mock으로 조용히 대체하는 동작은 도입하지 않는다.

향후 복수 Adapter를 지원한다면 작업에 기록된 종류·버전으로 정확한 구현체를 선택해야 한다. 모델·프롬프트 버전, provider 모델 세부 식별자 보존 및 과거 버전 지원 정책도 별도로 정한다. schemaVersion은 프롬프트 버전과 같은 뜻이 아니다.

## 향후 LLM 프롬프트 계약

아직 실행되는 프롬프트는 없다. 실제 Adapter에 다음 원칙을 적용한다.

1. 문서·고객 답변·거절 사유는 신뢰하지 않는 분석 데이터로 전달한다. 문서 안의 시스템 지시·도구 호출 요청을 실행하지 않는다.
2. analyze/assess/generateRevision에 맞는 JSON 객체만 반환하게 하고, 타입·필수 필드·Enum·미정의 필드를 엄격하게 검사한다.
3. 충분한 근거 없이 새로운 수치·조건을 사실처럼 추가하지 않도록 지시하고 별도 평가 데이터로 검사한다. 현재의 구조 검증은 의미적 정확성을 보장하지 않는다.
4. 모델은 제안만 한다. 최종 승인·DB ID·상태 전이·버전 변경 권한은 서버와 사람에게 남긴다.
5. API key·프롬프트/모델 설정은 서버 환경에서 관리하고 입력·출력·키를 예외 메시지에 노출하지 않는다.

## 검증과 운영 한계

새 테스트는 인터페이스만 구현하는 대체 테스트 더블로 DOCUMENT/ANSWER/REVISION 처리, 입력 맥락 전달, 반환된 수정안 저장, 메타데이터·재시도 승계, 잘못된 출력 차단, Bean 교체를 검증한다. LLM이라고 표시되는 테스트 더블은 실제 네트워크 모델 호출이 아니다.

실제 PostgreSQL 테스트에서는 HTTP·비동기 Worker·JPA를 통해 잘못된 문서 후보의 부분 저장 방지, 수정안 생성 실패 시 Issue 해결 롤백, 동일 입력 재시도 성공, Adapter 버전 불일치의 실패 처리를 확인한다.

후속 범위:

- 실제 LLM·일반 문서 정확도·다양한 거절 사유 반영.
- 시간 제한·비용·rate limit·재시도 정책.
- 외부 LLM 호출 전후의 트랜잭션 분리와 결과 반영 시 버전 재검사. 현재 Mock 실행은 기존 실행 트랜잭션 안에 있고, ANSWER/REVISION은 행 잠금을 유지하므로 긴 외부 호출을 그대로 넣지 않는다.
- 전용 Async Executor·작업 소유권·중단 작업 복구·영속 큐. 현재 AFTER_COMMIT 이벤트 자체는 내구성 있는 큐가 아니다.
- 업로드 후 자동 분석, 인증·인가, 파일 다운로드, 새로운 업무 상태 전이는 이번 PR에 포함하지 않는다.

## 실행

기본 테스트:

```bash
SPRING_PROFILES_ACTIVE=default bash gradlew test --rerun-tasks
```

PostgreSQL 통합 검증은 공유·운영 DB가 아닌 **별도로 생성한 폐기 가능한 로컬 DB**를 지정한다. 테스트는 Flyway V1~V4를 적용하고 데이터를 추가한다.

```bash
SPRING_PROFILES_ACTIVE=default \
REQBRIDGE_TEST_POSTGRES_URL='jdbc:postgresql://127.0.0.1:5432/YOUR_DISPOSABLE_TEST_DB' \
REQBRIDGE_TEST_POSTGRES_USER='YOUR_TEST_DB_USER' \
REQBRIDGE_TEST_POSTGRES_PASSWORD='YOUR_TEST_DB_PASSWORD' \
bash gradlew test --rerun-tasks
```

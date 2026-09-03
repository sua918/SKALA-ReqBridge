# ReqBridge AI 프롬프트 및 입출력 계약 명세서 (Prompt Contract)

> **문서 버전:** 1.0.0  
> **기준 스키마 버전 (`schemaVersion`):** `1.0.0`  
> **대상 패키지:** `com.sua.reqbridge.contract.ai`

---

## 1. 개요 및 목적

ReqBridge는 요구사항 명세서의 모호성을 검출하고, 고객과의 질의응답을 거쳐 정량적이고 검증 가능한 요구사항 수정안을 도출하는 시스템입니다.  
본 문서는 실제 LLM(OpenAI, Anthropic Claude, Google Gemini 등) 또는 Mock 분석기가 Spring Boot 백엔드와 통신할 때 준수해야 하는 **프롬프트 계약(Prompt Contract)** 및 **입출력 데이터 규격**을 정의합니다.

---

## 2. 공통 원칙 (General Principles)

1. **순수 JSON 출력 (No Markdown / No Extra Text)**:
   - LLM은 응답 시 마크다운 백틱(```json ... ```)이나 설명 텍스트 없이 **순수 JSON 문자열만 출력**해야 합니다.
   - 백엔드는 JSON 파싱 실패 또는 스키마 미준수 시 작업을 즉시 `FAILED` 처리하고 실패 코드를 `AI_OUTPUT_INVALID`로 기록합니다.
2. **도메인 격리 (Pure AI Contract)**:
   - AI 엔진은 DB 생성 식별자(PK), 최종 승인 여부(`APPROVED`/`CONFIRMED`), `contentVersion` 증가 등을 결정하지 않습니다.
   - AI 엔진은 오직 **도메인 후보(요구사항 문장, 불명확성 분류, 질문 문구, 답변 충분성 판정, 수정안 텍스트)**만 제안합니다.
3. **불명확성 분류 (AmbiguityType) 7종 표준**:
   - `QUANTITY_MISSING`: 수치·수량·처리 건수 등 정량 기준 누락
   - `PERFORMANCE_MISSING`: 응답 시간, TPS, 처리 기한 등 성능 기준 누락
   - `CONDITION_MISSING`: 선행 조건, 특정 상황별 동작 조건 누락
   - `ACTOR_MISSING`: 행위 주체(사용자, 관리자, 시스템 등) 누락 또는 모호
   - `SUCCESS_CRITERIA_MISSING`: 성공/완료 판정 기준 및 검증 기준 누락
   - `TERM_AMBIGUOUS`: 모호하거나 다의적인 용어 사용
   - `EXCEPTION_MISSING`: 장애, 오류, 비정상 상황 시 예외 처리 및 복구 절차 누락

---

## 3. 작업별 입출력 계약

### 3.1 문서 분석 (`analyzeDocument`)

- **작업 목적**: 업로드된 문서 원문(`Document.content`)에서 개별 요구사항을 분리 추출하고, 각 요구사항별 모호성 이슈 및 고객 확인 질문을 생성합니다.
- **스키마 정의**: [`document-analysis.schema.json`](file:///Users/g087/Desktop/skala/project/mini1/reqBridge/SKALA-ReqBridge/docs/backend/ai/document-analysis.schema.json)

#### 입력 (`DocumentAnalysisInput`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `documentId` | `long` | 대상 문서 ID (참조용) |
| `content` | `String` | 요구사항 문서 원문 텍스트 (TEXT 직접 입력 또는 PDF 추출본) |

#### 출력 (`DocumentAnalysisResult`)
```json
{
  "requirements": [
    {
      "sequenceNo": 1,
      "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
      "issues": [
        {
          "type": "QUANTITY_MISSING",
          "evidence": "많은 사용자의 정량 기준이 없다.",
          "questionText": "부하 시험의 최대 동시 사용자는 몇 명인가요?"
        },
        {
          "type": "PERFORMANCE_MISSING",
          "evidence": "빠르게의 측정 가능한 응답 시간 기준이 없다.",
          "questionText": "부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?"
        }
      ]
    }
  ]
}
```

---

### 3.2 답변 판정 (`assessAnswer`)

- **작업 목적**: 고객이 제출한 답변 문구가 해당 모호성 이슈를 해소하기에 충분한지 판정합니다. 불충분할 경우 다음 라운드 후속 질문을 생성합니다.
- **스키마 정의**: [`answer-assessment.schema.json`](file:///Users/g087/Desktop/skala/project/mini1/reqBridge/SKALA-ReqBridge/docs/backend/ai/answer-assessment.schema.json)

#### 입력 (`AnswerAssessmentInput`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `requirementId` | `long` | 대상 요구사항 ID |
| `contentVersion` | `long` | 현재 요구사항 버전 |
| `requirementText` | `String` | 요구사항 원문 본문 (문맥 파악용) |
| `issueType` | `AmbiguityType` | 대상 불명확성 분류 |
| `evidence` | `String` | 불명확성 판단 근거 |
| `clarificationId` | `long` | 대상 질문 ID |
| `issueId` | `long` | 대상 이슈 ID |
| `roundNo` | `int` | 현재 질문 회차 (1부터 시작) |
| `questionText` | `String` | 이번 회차 질문 문구 |
| `answerText` | `String` | 고객이 제출한 답변 문구 |
| `history` | `List<ClarificationHistory>` | 동일 이슈에 대한 이전 라운드 질문/답변 이력 목록 |

#### 출력 (`AnswerAssessment`)
- **충분할 때 (`sufficient = true`)**:
  ```json
  {
    "sufficient": true,
    "reason": "정량 기준(최대 동시 사용자 3,000명)이 구체적 수치로 명시되었습니다.",
    "nextQuestionText": null
  }
  ```
- **불충분할 때 (`sufficient = false`)**:
  ```json
  {
    "sufficient": false,
    "reason": "최대 동시 사용자 수가 구체적인 숫자로 제시되지 않았습니다.",
    "nextQuestionText": "부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요."
  }
  ```

---

### 3.3 수정안 생성 (`generateRevision`)

- **작업 목적**: 해결된 모든 확인 질문-답변 내용과 (존재할 경우) 직전 수정안 거절 사유를 종합하여, 모호성이 해소된 요구사항 수정안 본문을 생성합니다.
- **스키마 정의**: [`revision-proposal.schema.json`](file:///Users/g087/Desktop/skala/project/mini1/reqBridge/SKALA-ReqBridge/docs/backend/ai/revision-proposal.schema.json)

#### 입력 (`RevisionGenerationInput`)
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `requirementId` | `long` | 대상 요구사항 ID |
| `originalText` | `String` | 요구사항 최초 원문 |
| `clarifications` | `List<ClarificationContext>` | 해결된 전체 질문 및 답변 이력 (`id`, `issueId`, `questionText`, `answerText`) |
| `rejectionReason` | `String?` | 직전 수정안 거절 사유 (최초 생성 시 null, 재생성 시 고객 입력 거절 사유) |

#### 출력 (`RevisionProposal`)
```json
{
  "proposedText": "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다."
}
```

---

## 4. 유효성 검증 및 예외 처리 정책 (`AnalyzerOutputValidator`)

백엔드는 Analyzer 응답 수신 즉시 [`AnalyzerOutputValidator`](file:///Users/g087/Desktop/skala/project/mini1/reqBridge/SKALA-ReqBridge/backend/src/main/java/com/sua/reqbridge/analysis/AnalyzerOutputValidator.java)를 통해 엄격한 유효성 검사를 수행합니다.

1. **검증 실패 시 처리**:
   - 검증 규칙 위반 시 즉시 `AiOutputInvalidException`이 발생하며, 트랜잭션이 롤백되어 DB에 요구사항이나 이슈가 **부분 저장되지 않습니다.**
   - 비동기 워커는 이를 포착하여 해당 `Analysis` 작업을 `FAILED` 상태 및 `error_code = "AI_OUTPUT_INVALID"`로 기록합니다.
2. **세부 검증 규칙**:
   - `sequenceNo`는 1 이상이어야 하며 중복될 수 없습니다.
   - `originalText`, `evidence`, `questionText`, `reason`, `proposedText`는 비어 있거나 공백일 수 없습니다.
   - `sufficient=false`인데 `nextQuestionText`가 없거나, `sufficient=true`인데 `nextQuestionText`가 존재하면 거절됩니다.
   - `proposedText`는 20,000자를 초과할 수 없습니다.

---

## 5. 메타데이터 및 재시도 정책

1. **메타데이터 기록**:
   - `Analysis.adapterType`: 실행에 사용된 어댑터 종류 (`MOCK`, `LLM`, `MANUAL`)
   - `Analysis.schemaVersion`: 적용된 프롬프트/입출력 스키마 버전 (예: `"1.0.0"`)
2. **재시도 (`retry`) 시 실행 이력 일치 정책**:
   - 실패 작업에 대한 재시도(`POST /api/analyses/{id}/retries`)는 **재시도 시점의 현재 활성 Analyzer(Bean)**로 실행됩니다.
   - 따라서 새 재시도 `Analysis` 엔티티에는 과거 실패 작업의 Adapter가 아닌, **실제 실행을 담당할 현재 Analyzer의 `adapterType`과 `schemaVersion`이 기록**됩니다.

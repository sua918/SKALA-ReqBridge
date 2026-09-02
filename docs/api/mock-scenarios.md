# ReqBridge Workflow Mock 시나리오

- 기준 API 계약: 0.3.0
- 적용 범위: P1 문서 분석, 답변 재판정, 수정안 재생성 Mock
- 구현 담당: 신형섭
- 목적: 실제 LLM 없이 상태·버전·저장·재시도 규칙을 반복 가능하게 검증한다.

이 문서는 Mock의 고정 입력과 기대 출력을 정의한다. HTTP JSON 구조는 `ReqBridge_API_Specification.md`를 따르며, Mock이 DB ID·최종 승인·확정 상태를 결정하지 않는다. 지원하지 않는 입력을 임의 성공 처리하지 않고 명시적인 실패로 반환한다.

## 1. 공통 Fixture

문서 101의 원문:

> 시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.

| 리소스 | 고정 ID | 설명 |
| --- | ---: | --- |
| Document | 101 | TEXT 원문 |
| DOCUMENT Analysis | 301 | 최초 분석 |
| Requirement | 401 | sequenceNo 1 |
| Issue | 501 | QUANTITY_MISSING |
| Issue | 502 | PERFORMANCE_MISSING |
| Clarification | 601 | Issue 501 round 1 |
| Clarification | 602 | Issue 502 round 1 |
| Clarification | 603 | Issue 501 round 2 |
| ANSWER Analysis | 302~304 | 답변별 재판정 |
| Revision | 701 | 최초 제안 |
| REVISION Analysis | 307 | 거절 후 재생성 |
| Revision | 702 | 재생성 제안 |

## 2. 최초 문서 분석

입력은 문서 101의 원문과 sourceType TEXT다. Mock은 다음 후보를 반환한다.

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

저장 기대값:

- Requirement 401: `EXTRACTED → AMBIGUOUS → CLARIFYING`, contentVersion 1
- Issue 501·502: OPEN
- Clarification 601·602: WAITING, roundNo 1
- Analysis 301: COMPLETED, requirementIds `[401]`, issueIds `[501, 502]`, clarificationIds `[601, 602]`, revisionIds `[]`, assessment `null`

요구사항·문제·질문·result 저장과 Analysis COMPLETED 전환은 한 트랜잭션이다.

## 3. 불충분한 첫 답변

입력:

```json
{
  "clarificationId": 601,
  "answerText": "많이 접속할 것 같습니다.",
  "expectedContentVersion": 1
}
```

Mock 판정:

```json
{
  "issueId": 501,
  "sufficient": false,
  "reason": "최대 동시 사용자 수가 숫자로 제시되지 않았습니다.",
  "nextQuestionText": "부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요."
}
```

저장 기대값:

- 답변은 정규화해 Clarification 601에 보존하고 상태는 ANSWERED
- Requirement 401: CLARIFYING, contentVersion 2
- 새 Clarification 603: Issue 501, roundNo 2, WAITING
- Analysis 302: COMPLETED, assessment의 nextClarificationId 603
- Issue 501은 OPEN, Revision은 생성하지 않음

## 4. 충분한 답변과 부분 해결

입력:

```json
{
  "clarificationId": 603,
  "answerText": "최대 동시 사용자 3,000명입니다.",
  "expectedContentVersion": 2
}
```

Mock은 sufficient `true`, reason `정량 기준이 확인되었습니다.`, nextQuestionText `null`을 반환한다.

저장 기대값:

- Clarification 603과 Issue 501: RESOLVED
- Requirement 401: CLARIFYING, contentVersion 3
- Issue 502가 OPEN이므로 Revision은 생성하지 않음
- Analysis 303: COMPLETED, issueIds `[501]`, clarificationIds `[]`, revisionIds `[]`

## 5. 모든 문제 해결과 최초 수정안

입력:

```json
{
  "clarificationId": 602,
  "answerText": "p95 응답 시간 2초 이하입니다.",
  "expectedContentVersion": 3
}
```

Mock은 sufficient `true`와 다음 수정안 본문을 반환한다.

> 시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.

저장 기대값:

- Clarification 602와 Issue 502: RESOLVED
- Requirement 401: IN_REVIEW, contentVersion 4
- Revision 701: PROPOSED, revisionNo 1, inputContentVersion 4
- basedOnClarificationIds: `[601, 603, 602]`
- acceptanceCriteria: `[]`
- Analysis 304: COMPLETED, revisionIds `[701]`

## 6. 거절 후 수정안 재생성

검토 요청 자체는 Mock 입력이 아니다. Revision 701을 다음 사유로 거절한 상태를 재생성 입력으로 사용한다.

> 동시 사용자 수를 최대치로 명확하게 표현해주세요.

거절 저장 기대값:

- Revision 701: REJECTED, inputContentVersion 4 유지
- Requirement 401: CLARIFYING, contentVersion 5
- 동일 거절 재전송: 추가 버전 증가 없음

Analysis 307의 Mock 입력에는 문서 원문, 모든 질문·답변, 거절 사유, contentVersion 5가 포함된다. 출력은 `최대 동시 사용자 수` 표현을 반영한 새 본문이다.

저장 기대값:

- Revision 702: PROPOSED, revisionNo 2, inputContentVersion 5
- Requirement 401: IN_REVIEW, contentVersion 5
- Revision 701과 근거 답변 이력은 변경하지 않음
- Analysis 307: COMPLETED, revisionIds `[702]`

Revision 702 APPROVE는 Mock 호출 없이 서버가 처리한다. APPROVED와 Requirement CONFIRMED, approvedRevisionId 702, confirmedText 저장은 한 트랜잭션이며 contentVersion은 5를 유지한다.

## 7. 실패와 재시도

테스트용 입력의 `scenario`가 `INVALID_OUTPUT`이면 Mock은 필수 필드가 누락된 결과를 반환한다. 서버는 부분 업무 데이터를 저장하지 않고 Analysis를 다음과 같이 종료한다.

- status: FAILED
- result: null
- error.code: `AI_OUTPUT_INVALID`
- error.message: 공개 가능한 고정 메시지
- inputSnapshot: 최초 실행 입력을 그대로 보존

재시도는 새 ID를 만들고 원래 AnalysisKind와 inputContentVersion을 유지한다. `retryOfAnalysisId`는 바로 직전 실패 작업을 가리킨다. 같은 실패 ID의 재시도가 이미 있으면 새 작업을 만들지 않고 기존 작업을 반환한다. 재시도도 실패하면 최신 실패 ID에 다음 작업을 연결한다.

## 8. 중복·충돌 기대값

| 상황 | 기대 결과 |
| --- | --- |
| 같은 질문에 정규화 후 동일한 답변 재전송 | 기존 Analysis 반환, contentVersion 증가 없음 |
| 같은 질문에 다른 답변 전송 | 409 ANSWER_ALREADY_SUBMITTED |
| 동일 revision의 같은 결정·같은 거절 사유 | 기존 결정과 현재 Requirement 반환, 부수 효과 없음 |
| 검토 결정 또는 거절 사유 변경 | 409 REVISION_ALREADY_REVIEWED |
| stale expectedContentVersion | 409 CONTENT_VERSION_CONFLICT |
| 같은 요구사항에 활성 ANSWER/REVISION 작업 | 409 ANALYSIS_IN_PROGRESS |
| FAILED가 아닌 작업의 신규 재시도 | 409 ANALYSIS_NOT_RETRYABLE |
| CONFIRMED 요구사항의 신규 변경 | 409 REQUIREMENT_CONFIRMED |

동일 요청 판정은 형식·소속 확인 이후, 새 처리의 버전·상태 검증보다 먼저 수행한다.

## 9. AmbiguityType 분류 Fixture

주 시나리오는 QUANTITY_MISSING과 PERFORMANCE_MISSING을 사용한다. 나머지 분류는 단위 테스트용 한 문장 Fixture로 확인한다.

| 입력 예시 | 기대 type |
| --- | --- |
| 사용자가 요청하면 처리한다. | ACTOR_MISSING |
| 특정 조건에서 알림을 보낸다. | CONDITION_MISSING |
| 처리가 성공해야 한다. | SUCCESS_CRITERIA_MISSING |
| 시스템은 적절한 기간 동안 보관한다. | TERM_AMBIGUOUS |
| 결제 실패 상황은 정의하지 않는다. | EXCEPTION_MISSING |

Mock은 위 표에 없는 임의 입력을 실제 AI처럼 해석했다고 표시하지 않는다.

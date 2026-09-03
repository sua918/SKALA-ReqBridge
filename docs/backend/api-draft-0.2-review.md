# API Endpoint + JSON 초안 0.2.0 검토

> 이 기록의 3단계 RequirementStatus 판단은 API 명세 0.3.0과 개정 DBML의 5단계 계약으로 대체됐다.

## 결론

P1/P2 경계, 18개 Endpoint, 응답 래퍼, TEXT-only 입력, 9개 enum, 버전·중복·재시도 흐름은 DB 요청 1.1 및 협업 계획과 대체로 일관된다. Core 담당 8개 P1 Endpoint는 현재 구현과 구조가 일치한다. 다만 아래 항목을 고친 뒤 공용 기준으로 확정하는 것이 안전하다.

## 수정 또는 합의가 필요한 항목

1. `Requirement.contentVersion` 설명을 수정해야 한다. Requirement의 값은 답변 등록과 최초 거절 때 증가하는 **현재 업무 버전**이다. “수정안 생성 당시 불변 버전”은 `Revision.inputContentVersion`과 `Analysis.inputContentVersion`에만 해당한다.
2. `GET /api/documents/{documentId}/analyses`는 타당한 추가 Endpoint지만 collaboration plan 7.2에는 없다. plan과 OpenAPI를 함께 갱신해야 DB 완료 확인표 14번을 만족한다.
3. 문서가 함께 제공됐다고 말하는 `ReqBridge_OpenAPI_Draft.yaml` 또는 `docs/api/openapi.yaml`이 현재 저장소와 Downloads에서 확인되지 않는다. 따라서 OpenAPI 문법·예시 85개 검증 주장은 이 작업 환경에서 재현할 수 없다.
4. 공통 규칙의 “202는 Analysis Location”을 각 비동기 Endpoint의 응답 헤더에도 명시하는 편이 좋다. 특히 AnswerReceipt는 Analysis가 중첩되어 있어 `/api/analyses/{analysis.id}`라는 점을 분명히 해야 한다.
5. 세분화된 409 코드 계약은 타당하지만 현재 공용 contract 예외가 없다. Core는 `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, 일반 `STATE_CONFLICT`까지만 연결되어 있으며 나머지는 Workflow 계약·구현과 함께 추가해야 한다.
6. JSON 안전 정수 상한은 요청에서 검증할 수 있지만 DB identity 자체는 BIGINT 상한까지 증가할 수 있다. MVP에서는 현실적 문제가 아니지만 장기 계약으로 유지하려면 identity 상한 또는 ID 문자열 전환 시점을 운영 규칙으로 남겨야 한다.
7. P2 Preview 구조는 조회 일관성 기준이 명확해 타당하다. 다만 `WorkflowPreviewPort` 구현 전에는 Controller를 활성화하면 안 되며, 현재는 계획대로 미구현 상태를 유지한다.

## 현재 Core 구현과의 일치 여부

| 항목 | 결과 |
| --- | --- |
| Project 3개 API | 일치 |
| Document 3개 API | 일치 |
| Requirement 조회 2개 API | 일치 |
| `{data}` / `{data:{items}}` / 오류 래퍼 | 일치 |
| 201 및 Location | 일치 |
| 프로젝트·문서 ID 내림차순, 요구사항 순번 오름차순 | 일치 |
| TEXT-only 및 미정의 요청 필드 400 | 일치 |
| Unicode 코드 포인트 길이와 지정 공백 정규화 | 일치 |
| 외부 ID JavaScript 안전 정수 상한 | Core path 입력에 반영 |
| Workflow 8개 API | 신형섭 담당, 현재 미구현 |
| Preview 2개 API | P2 및 WorkflowPreviewPort 대기 |
| OpenAPI와 실제 응답 자동 대조 | OpenAPI 파일 부재로 미검증 |

초안은 폐기할 수준의 문제가 아니라 **조건부 승인**이 적절하다. 1~5번을 공용 문서와 contract에 반영한 뒤 최종 API 기준으로 삼는다.

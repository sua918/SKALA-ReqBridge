결론부터 말하면, 초안의 전체 구조는 타당하며 현재 feat/backend-core 구현 방향과도 잘 맞습니다. 다만 그대로 확정하기 전에 반드시 수정할 항목이 있습니다. 이번 검토에서는 코드나 문서를 변경하지 않았습니다.
반드시 수정할 사항
1. 거절 시 contentVersion을 증가시켜야 합니다
초안 732행에는 거절 사유가 추가돼도 contentVersion=4를 유지한다고 되어 있습니다. 하지만 거절 사유는 다음 수정안 생성의 AI 입력이므로, 기존 계획의 “AI 판단 입력이 바뀌면 version 증가” 규칙과 충돌합니다.
권장 흐름:
수정안 검토 전: v4
REJECT + rejectionReason 저장: v5
새 수정안 요청: expectedContentVersion=5
새 Analysis.inputContentVersion=5
동일한 거절 요청을 재전송한 경우에는 기존 결과인 v5를 그대로 반환하면 됩니다. 승인은 새로운 AI 입력을 만들지 않으므로 버전을 유지해도 됩니다.
2. Analysis 저장 구조가 API 응답을 충족하지 못합니다
초안은 다음 정보를 영구 저장하고 조회하도록 요구합니다.
- retryOfAnalysisId
- result
- error.code
- error.message
현재 DB에는 error_code, raw_response만 있고 다음 컬럼이 없습니다.
retry_of_analysis_id
result
error_message
DB에는 최소한 다음 변경이 필요합니다.
ALTER TABLE analysis
    ADD COLUMN retry_of_analysis_id BIGINT
        REFERENCES analysis (id),
    ADD COLUMN result JSONB,
    ADD COLUMN error_message TEXT;

CREATE UNIQUE INDEX uq_analysis_retry_of
    ON analysis (retry_of_analysis_id)
    WHERE retry_of_analysis_id IS NOT NULL;
raw_response는 AI 원본 응답이고, result는 저장 완료 후 생성된 DB ID와 판정 결과이므로 분리하는 것이 안전합니다.
Analysis 종류에 따른 input_content_version 조건도 강화하는 편이 좋습니다.
DOCUMENT → null
ANSWER   → not null
REVISION → not null
3. OpenAPI YAML을 함께 받아야 합니다
문서에서는 ReqBridge_OpenAPI_Draft.yaml을 기준 스키마로 언급하지만, 현재 전달받은 파일은 Markdown 초안뿐입니다.
다음 항목은 YAML 없이는 확정 검증할 수 없습니다.
- oneOf를 사용한 APPROVE/REJECT 요청 구분
- nullable과 required 조합
- ID의 safe integer 최대값
- 미정의 필드 거절 설정
- enum과 $ref 연결
- 실제 /api servers/path 조합
Controller 구현 전 OpenAPI YAML도 저장소의 docs/api/openapi.yaml 후보로 받아 검토하는 것이 좋습니다.
4. 문서 경로가 실제 저장소와 다릅니다
초안 7행:
backend/docs/personal/
backend/docs/.gitignore
실제 저장소:
docs/personal/
docs/.gitignore
초안의 경로를 실제 저장소 구조에 맞게 수정해야 합니다.
초안은 유지하고 현재 코드를 수정할 사항
목록 정렬
초안은 Project와 Document 목록을 ID 내림차순으로 정의했습니다. 최근 항목이 먼저 노출되므로 타당합니다.
현재 코드는 오름차순이므로 계약 확정 후 다음처럼 바꿔야 합니다.
findAllByOrderByIdDesc()
findByProjectIdOrderByIdDesc(projectId)
Requirement는 초안과 현재 코드 모두 sequenceNo 오름차순이라 일치합니다.
문자열 길이 검증
초안의 제한은 합리적입니다.
Project.name       100
Project.description 2,000
Document.title      200
Document.content    100,000
Answer              20,000
Rejection reason     2,000
현재 Core Service는 공백 여부만 검사하므로, API 구현 때 길이 검증을 추가해야 합니다.
다만 초안은 “Unicode 코드 포인트 기준”이라고 명시했습니다. Jakarta Validation의 일반적인 @Size는 Java UTF-16 길이를 사용하므로 이 계약을 유지하려면 다음과 같은 별도 검증이 필요합니다.
value.codePointCount(0, value.length())
팀에서 이 정도 정밀도가 필요하지 않다면 초안의 “Unicode 코드 포인트 기준” 문구를 단순 문자 길이 제한으로 완화하는 것도 방법입니다.
미정의 JSON 필드 거절
초안은 요청에 정의되지 않은 필드가 들어오면 400을 반환하도록 요구합니다. 방향은 좋지만 Spring/Jackson 설정에서 이를 명시적으로 보장해야 합니다.
spring.jackson.deserialization.fail-on-unknown-properties=true
Spring Boot 4/Jackson 3에서 실제 적용되는 설정명은 구현 테스트로 확인해야 합니다.
sourceType
문서 등록 요청에서 sourceType은 TEXT만 허용됩니다. 현재 Service도 항상 TEXT로 저장하므로 잘 맞습니다.
Controller에서는 다음을 보장해야 합니다.
- 생략 → 400
- TEXT → 허용
- FILE → 400
- 알 수 없는 문자열 → 400
추가 합의가 필요한 사항
미확정 Developer Preview 범위
unconfirmedRequirements에 issues와 questions를 포함한다고 되어 있지만 어떤 상태를 포함하는지 명확하지 않습니다.
권장 규칙:
issues:
  해당 요구사항의 모든 Issue 이력

questions:
  해당 요구사항의 모든 Clarification 이력
  issueId 오름차순 → roundNo 오름차순
현재 진행 중인 질문만 보여주려는 목적이라면 OPEN Issue + WAITING Clarification으로 명시해야 합니다. 고객 Preview와 개발팀 Preview의 목적이 다르므로, 개발팀용에는 전체 이력을 제공하는 쪽이 더 유용합니다.
거절 사유 정규화
동일 거절 요청을 멱등 처리하려면 rejectionReason의 비교 규칙도 필요합니다. 답변과 동일하게 다음 정규화를 권장합니다.
앞뒤 공백 제거
CRLF → LF
정규화된 문자열로 동일성 비교
프로젝트 이름, 설명, 문서 제목의 공백 처리도 명시하면 좋습니다. 현재 Core 구현은 이름·설명·제목을 trim하고, 원문 content는 보존합니다.
검토 시간 기록
수정안 응답에는 createdAt, reviewedAt이 없습니다. 지금 DB도 승인에는 approved_at만 있고 거절 시각은 기록하지 않습니다.
MVP 화면에 필요하지 않다면 생략할 수 있지만 “승인·거절 이력”을 명확히 남길 계획이면 다음 구조가 더 낫습니다.
createdAt
reviewedAt
reviewedAt 하나로 승인과 거절 시각을 모두 표현할 수 있습니다.
무인증 범위
로그인 없는 구조는 로컬 또는 내부 데모라면 타당합니다. 하지만 Spring Boot API가 인터넷에 공개되면 누구나 문서를 등록하거나 수정안을 승인할 수 있습니다.
따라서 초안에 다음 경계를 명시하는 것을 권장합니다.
P1 무인증 구성은 로컬·내부 데모 전용이다.
외부 공개 배포 전에는 인증 또는 접근 제한이 필수다.
그대로 채택해도 좋은 부분
다음 설계는 잘 잡혀 있습니다.
- Vue → Spring Boot → Supabase PostgreSQL 단일 접근 경로
- 프론트의 Supabase 직접 접근 금지
- 문서 등록과 분석 실행 분리
- 202 + Location 기반 비동기 작업
- 분석 실패를 HTTP 오류가 아닌 저장된 작업 상태로 표현
- expectedContentVersion을 통한 오래된 화면 방지
- 중복 답변·재시도·검토의 멱등성 우선 판정
- Requirement 기본 조회와 Workflow 조회 분리
- 승인과 확정 본문의 동일 트랜잭션 처리
- Preview의 basis 및 REPEATABLE_READ 일관성
- 확정/미확정 요구사항 분리
- FILE 업로드·다운로드·실제 AI·인증의 MVP 제외
- 응답 래퍼와 오류 코드 구조
- 존재하지 않는 부모 목록은 404, 존재하지만 빈 목록은 200
종합적으로는 “조건부 승인”이 적절합니다. 우선 팀원에게 다음 네 가지를 요청하면 됩니다.
1. 거절 시 contentVersion 증가로 수정
2. retryOfAnalysisId/result/errorMessage DB 저장 요구 확정
3. 실제 경로를 docs/**로 수정
4. ReqBridge_OpenAPI_Draft.yaml 공유
이 네 항목이 확정되면 현재 브랜치에서 DB 마이그레이션을 보완하고 Project·Document·Requirement Controller 및 공통 응답/예외 처리를 구현할 수 있습니다.

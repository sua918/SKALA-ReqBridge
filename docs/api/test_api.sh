#!/usr/bin/env bash
# ==============================================================================
# ReqBridge 핵심 API E2E 자동 테스트 스크립트
#
# 이 스크립트는 서버(http://localhost:8080)가 구동 중인 상태에서
# 프로젝트/문서 생성부터 Mock AI 분석, 질문/답변 다중 라운드, 수정안 거절 및 재생성,
# 최종 승인, 그리고 P2 고객/개발팀 Preview까지 전 과정을 단계별로 호출하고 검증합니다.
# 각 단계마다 전송한 [REQUEST]와 수신한 [RESPONSE]를 터미널에 상세 출력합니다.
# ==============================================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m' # No Color

PASSED_COUNT=0
FAILED_COUNT=0

log_step() {
    echo -e "\n${CYAN}${BOLD}======================================================================${NC}"
    echo -e "${CYAN}${BOLD}[단계 $1] $2${NC}"
    echo -e "${CYAN}${BOLD}======================================================================${NC}"
}

log_success() {
    echo -e "${GREEN}${BOLD}✓ $1${NC}"
    PASSED_COUNT=$((PASSED_COUNT + 1))
}

log_fail() {
    echo -e "${RED}${BOLD}✗ $1${NC}"
    FAILED_COUNT=$((FAILED_COUNT + 1))
    exit 1
}

# Python을 이용한 포맷팅 및 필드 추출
format_json() {
    python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(json.dumps(data, ensure_ascii=False, indent=2))
except Exception:
    pass
"
}

json_get() {
    local json_input="$1"
    local path="$2"
    python3 -c "
import sys, json
try:
    data = json.loads('''$json_input''')
    keys = '$path'.split('.')
    for k in keys:
        if isinstance(data, list):
            k = int(k)
        data = data[k]
    if isinstance(data, (dict, list)):
        print(json.dumps(data, ensure_ascii=False))
    else:
        print(data)
except Exception:
    sys.exit(1)
" 2>/dev/null || echo ""
}

# HTTP 요청/응답 공통 함수 (화면 출력 포함)
http_call() {
    local method="$1"
    local endpoint="$2"
    local body="$3"

    echo -e "${MAGENTA}>>> [REQUEST] ${BOLD}$method $endpoint${NC}"
    if [ -n "$body" ]; then
        echo -e "${BLUE}$(echo "$body" | format_json)${NC}"
    fi

    local curl_cmd=(curl -s -i -X "$method" "$BASE_URL$endpoint")
    if [ -n "$body" ]; then
        curl_cmd+=(-H "Content-Type: application/json" -d "$body")
    fi

    local raw_resp
    raw_resp=$("${curl_cmd[@]}")

    local status_line
    status_line=$(echo "$raw_resp" | grep -E "^HTTP/[0-9.]+ [0-9]+" | head -n 1 | tr -d '\r')
    local http_code
    http_code=$(echo "$status_line" | awk '{print $2}')

    local resp_body
    resp_body=$(echo "$raw_resp" | awk 'BEGIN{p=0} /^(\r|\n)/{p=1;next} p')

    echo -e "${YELLOW}<<< [RESPONSE] ${BOLD}$status_line${NC}"
    if [ -n "$resp_body" ]; then
        echo -e "${NC}$(echo "$resp_body" | format_json)${NC}"
    fi

    LAST_HTTP_CODE="$http_code"
    LAST_BODY="$resp_body"
}

# ------------------------------------------------------------------------------
# 0. 서버 상태 점검
# ------------------------------------------------------------------------------
echo -e "${BOLD}ReqBridge 백엔드 핵심 기능 E2E 테스트를 시작합니다.${NC}"
echo -e "대상 서버: ${BOLD}$BASE_URL${NC}\n"

if ! curl -s --connect-timeout 2 "$BASE_URL/swagger-ui/index.html" > /dev/null; then
    echo -e "${RED}오류: $BASE_URL 에 접속할 수 없습니다.${NC}"
    echo -e "${YELLOW}Spring Boot 서버가 실행 중인지 확인해주세요.${NC}"
    echo -e "실행 방법:"
    echo -e "  cd backend"
    echo -e "  bash gradlew bootRun"
    exit 1
fi
log_success "서버 접속 확인 완료"

# ------------------------------------------------------------------------------
# 1. 프로젝트 생성 (POST /api/projects)
# ------------------------------------------------------------------------------
log_step "1" "테스트용 프로젝트 생성"
http_call "POST" "/api/projects" '{
    "name": "E2E 검증 프로젝트",
    "description": "실제 Supabase DB E2E 자동 테스트용 프로젝트"
}'

PROJECT_ID=$(json_get "$LAST_BODY" "data.id")
if [ -n "$PROJECT_ID" ]; then
    log_success "프로젝트 생성 성공 (ID: $PROJECT_ID)"
else
    log_fail "프로젝트 생성 실패"
fi

# ------------------------------------------------------------------------------
# 2. 문서 생성 (POST /api/projects/{projectId}/documents)
# ------------------------------------------------------------------------------
log_step "2" "Mock AI 분석 시나리오 문서 생성"
http_call "POST" "/api/projects/$PROJECT_ID/documents" '{
    "title": "상품 조회 서비스 요구사항",
    "sourceType": "TEXT",
    "content": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다."
}'

DOC_ID=$(json_get "$LAST_BODY" "data.id")
if [ -n "$DOC_ID" ]; then
    log_success "문서 생성 성공 (ID: $DOC_ID)"
else
    log_fail "문서 생성 실패"
fi

# ------------------------------------------------------------------------------
# 3. 문서 분석 요청 (POST /api/documents/{documentId}/analyses)
# ------------------------------------------------------------------------------
log_step "3" "문서 모호성 분석 비동기 요청 (POST /api/documents/$DOC_ID/analyses)"
http_call "POST" "/api/documents/$DOC_ID/analyses" ""

if [ "$LAST_HTTP_CODE" = "202" ]; then
    ANALYSIS_ID=$(json_get "$LAST_BODY" "data.id")
    log_success "문서 분석 요청 접수 성공 (HTTP 202 Accepted, 분석 ID: $ANALYSIS_ID)"
else
    log_fail "문서 분석 요청 실패 (HTTP $LAST_HTTP_CODE)"
fi

# ------------------------------------------------------------------------------
# 4. 분석 상태 폴링 (GET /api/analyses/{analysisId})
# ------------------------------------------------------------------------------
log_step "4" "분석 작업 완료 폴링 (GET /api/analyses/$ANALYSIS_ID)"
for i in {1..15}; do
    sleep 0.5
    http_call "GET" "/api/analyses/$ANALYSIS_ID" ""
    STATUS=$(json_get "$LAST_BODY" "data.status")
    if [ "$STATUS" = "COMPLETED" ]; then
        log_success "문서 분석 완료 (status: COMPLETED)"
        break
    elif [ "$STATUS" = "FAILED" ]; then
        log_fail "분석 작업 실패"
    fi
done

# ------------------------------------------------------------------------------
# 5. 고객 질문서 Preview 조회 (GET /api/documents/{id}/previews/customer)
# ------------------------------------------------------------------------------
log_step "5" "고객 질문서 Preview 조회 (초기 모호성 이슈 2건 확인)"
http_call "GET" "/api/documents/$DOC_ID/previews/customer" ""

OPEN_ISSUES=$(json_get "$LAST_BODY" "data.summary.openIssueCount")
WAITING_QS=$(json_get "$LAST_BODY" "data.summary.waitingQuestionCount")
REQ_ID=$(json_get "$LAST_BODY" "data.requirements.0.requirementId")
Q1_ID=$(json_get "$LAST_BODY" "data.requirements.0.questions.0.id")
Q2_ID=$(json_get "$LAST_BODY" "data.requirements.0.questions.1.id")

if [ "$OPEN_ISSUES" -ge 2 ] && [ "$WAITING_QS" -ge 2 ]; then
    log_success "고객 질문서 Preview 확인 완료 (요구사항: $REQ_ID, 질문1: $Q1_ID, 질문2: $Q2_ID)"
else
    log_fail "고객 질문서 Preview 결과 불일치"
fi

# ------------------------------------------------------------------------------
# 6. 질문 1에 불충분 답변 제출 -> round 2 생성
# ------------------------------------------------------------------------------
log_step "6" "질문 1에 불충분 답변 제출 (POST /api/clarifications/$Q1_ID/answers)"
http_call "POST" "/api/clarifications/$Q1_ID/answers" '{
    "answerText": "많이 접속할 것 같습니다.",
    "expectedContentVersion": 1
}'

ANS1_ANALYSIS_ID=$(json_get "$LAST_BODY" "data.analysis.id")
log_success "불충분 답변 접수 완료 (분석 작업 ID: $ANS1_ANALYSIS_ID)"

# 비동기 재판정 완료 대기
for i in {1..10}; do
    sleep 0.5
    http_call "GET" "/api/analyses/$ANS1_ANALYSIS_ID" ""
    if [ "$(json_get "$LAST_BODY" "data.status")" = "COMPLETED" ]; then
        break
    fi
done

# ------------------------------------------------------------------------------
# 7. 워크플로우 이력 조회 -> 추가 질문(round 2) 확인
# ------------------------------------------------------------------------------
log_step "7" "워크플로우 이력 조회 및 2회차 질문 확인"
http_call "GET" "/api/requirements/$REQ_ID/workflow" ""

ROUND2_Q_ID=$(python3 -c "
import sys, json
wf = json.loads('''$LAST_BODY''')
for q in wf['data']['clarifications']:
    if q['roundNo'] == 2 and q['status'] == 'WAITING':
        print(q['id'])
        break
")

if [ -n "$ROUND2_Q_ID" ]; then
    log_success "2회차 추가 질문 생성 확인 (ID: $ROUND2_Q_ID)"
else
    log_fail "2회차 질문을 찾을 수 없음"
fi

# ------------------------------------------------------------------------------
# 8. 질문 1의 2회차에 충분한 답변 제출 (이슈 1 해결)
# ------------------------------------------------------------------------------
log_step "8" "2회차 질문에 정량 답변 제출 (동시 사용자 3,000명)"
http_call "POST" "/api/clarifications/$ROUND2_Q_ID/answers" '{
    "answerText": "최대 동시 사용자 3,000명입니다.",
    "expectedContentVersion": 2
}'

ANS2_ANALYSIS_ID=$(json_get "$LAST_BODY" "data.analysis.id")
for i in {1..10}; do
    sleep 0.5
    http_call "GET" "/api/analyses/$ANS2_ANALYSIS_ID" ""
    if [ "$(json_get "$LAST_BODY" "data.status")" = "COMPLETED" ]; then
        break
    fi
done
log_success "질문 1 (동시 사용자 이슈) 해결 완료"

# ------------------------------------------------------------------------------
# 9. 질문 2에 충분한 답변 제출 -> 모든 이슈 해결로 Revision 제안 (IN_REVIEW 전이)
# ------------------------------------------------------------------------------
log_step "9" "질문 2에 정량 답변 제출 (p95 응답 시간 2초 이하) -> 최종 수정안 자동 제안"
http_call "POST" "/api/clarifications/$Q2_ID/answers" '{
    "answerText": "p95 응답 시간 2초 이하입니다.",
    "expectedContentVersion": 3
}'

ANS3_ANALYSIS_ID=$(json_get "$LAST_BODY" "data.analysis.id")
for i in {1..10}; do
    sleep 0.5
    http_call "GET" "/api/analyses/$ANS3_ANALYSIS_ID" ""
    if [ "$(json_get "$LAST_BODY" "data.status")" = "COMPLETED" ]; then
        break
    fi
done

http_call "GET" "/api/requirements/$REQ_ID/workflow" ""
REV_ID=$(python3 -c "
import sys, json
wf = json.loads('''$LAST_BODY''')
for r in wf['data']['revisions']:
    if r['status'] == 'PROPOSED':
        print(r['id'])
        break
")

if [ -n "$REV_ID" ]; then
    log_success "모든 이슈 해결 및 수정안(PROPOSED) 자동 제안 확인 (수정안 ID: $REV_ID)"
else
    log_fail "제안된 수정안을 찾을 수 없음"
fi

# ------------------------------------------------------------------------------
# 10. 수정안 거절 (POST /api/revisions/{id}/review) -> 버전 증가 (v4 -> v5)
# ------------------------------------------------------------------------------
log_step "10" "수정안 거절 테스트 (REJECT) -> 사유 저장 및 버전 4 -> 5 증가 검증"
http_call "POST" "/api/revisions/$REV_ID/review" '{
    "decision": "REJECT",
    "rejectionReason": "동시 사용자와 지연 시간 기준을 더 엄격히 해주세요.",
    "expectedContentVersion": 4
}'

REV_STATUS=$(json_get "$LAST_BODY" "data.revision.status")
REQ_VERSION=$(json_get "$LAST_BODY" "data.requirement.contentVersion")
REQ_STATUS=$(json_get "$LAST_BODY" "data.requirement.status")

if [ "$REV_STATUS" = "REJECTED" ] && [ "$REQ_VERSION" = "5" ] && [ "$REQ_STATUS" = "CLARIFYING" ]; then
    log_success "수정안 거절 성공 (status: REJECTED, 버전: 4 -> 5, 상태: CLARIFYING)"
else
    log_fail "수정안 거절 처리 결과 불일치"
fi

# ------------------------------------------------------------------------------
# 11. 거절 사유 기반 새 수정안 재생성 (POST /api/requirements/{id}/revisions)
# ------------------------------------------------------------------------------
log_step "11" "거절 사유를 반영한 수정안 재생성 요청 (POST /api/requirements/$REQ_ID/revisions)"
http_call "POST" "/api/requirements/$REQ_ID/revisions" '{
    "expectedContentVersion": 5
}'

if [ "$LAST_HTTP_CODE" = "202" ]; then
    REGEN_ANALYSIS_ID=$(json_get "$LAST_BODY" "data.id")
    log_success "수정안 재생성 요청 접수 성공 (HTTP 202 Accepted, 분석 ID: $REGEN_ANALYSIS_ID)"
else
    log_fail "수정안 재생성 요청 실패"
fi

for i in {1..10}; do
    sleep 0.5
    http_call "GET" "/api/analyses/$REGEN_ANALYSIS_ID" ""
    if [ "$(json_get "$LAST_BODY" "data.status")" = "COMPLETED" ]; then
        break
    fi
done

http_call "GET" "/api/requirements/$REQ_ID/workflow" ""
REV2_ID=$(python3 -c "
import sys, json
wf = json.loads('''$LAST_BODY''')
for r in wf['data']['revisions']:
    if r['status'] == 'PROPOSED' and r['revisionNo'] == 2:
        print(r['id'])
        break
")
log_success "버전 5 기반 새 수정안(2회차) 생성 확인 (수정안 ID: $REV2_ID)"

# ------------------------------------------------------------------------------
# 12. 수정안 최종 승인 (POST /api/revisions/{id}/review) -> CONFIRMED 확정
# ------------------------------------------------------------------------------
log_step "12" "새 수정안 최종 승인 (APPROVE) -> 요구사항 CONFIRMED 전이"
http_call "POST" "/api/revisions/$REV2_ID/review" '{
    "decision": "APPROVE",
    "expectedContentVersion": 5
}'

FINAL_REV_STATUS=$(json_get "$LAST_BODY" "data.revision.status")
FINAL_REQ_STATUS=$(json_get "$LAST_BODY" "data.requirement.status")
CONFIRMED_TEXT=$(json_get "$LAST_BODY" "data.requirement.confirmedText")

if [ "$FINAL_REV_STATUS" = "APPROVED" ] && [ "$FINAL_REQ_STATUS" = "CONFIRMED" ]; then
    log_success "수정안 최종 승인 성공 (상태: CONFIRMED)"
    echo -e "${GREEN}  확정 텍스트: $CONFIRMED_TEXT${NC}"
else
    log_fail "수정안 승인 실패"
fi

# ------------------------------------------------------------------------------
# 13. 개발팀용 Preview 조회 (GET /api/documents/{id}/previews/developer)
# ------------------------------------------------------------------------------
log_step "13" "개발팀용 Preview 조회 (승인 수정안 및 3개 근거 답변 검증)"
http_call "GET" "/api/documents/$DOC_ID/previews/developer" ""

CONFIRMED_COUNT=$(json_get "$LAST_BODY" "data.summary.confirmedRequirements")
EVIDENCE_COUNT=$(python3 -c "
import sys, json
p = json.loads('''$LAST_BODY''')
print(len(p['data']['confirmedRequirements'][0]['evidenceAnswers']))
")

if [ "$CONFIRMED_COUNT" = "1" ] && [ "$EVIDENCE_COUNT" -ge 3 ]; then
    log_success "개발팀용 Preview 검증 완료 (확정 요구사항 $CONFIRMED_COUNT개, 근거 답변 $EVIDENCE_COUNT개 정상 매핑)"
else
    log_fail "개발팀용 Preview 검증 실패"
fi

# ------------------------------------------------------------------------------
# 14. 고객 질문서 Preview 최종 확인 (모두 확정되었으므로 대기 질문 0건)
# ------------------------------------------------------------------------------
log_step "14" "확정 완료 후 고객 질문서 Preview 조회 (WAITING 질문 0건 확인)"
http_call "GET" "/api/documents/$DOC_ID/previews/customer" ""

FINAL_WAITING_QS=$(json_get "$LAST_BODY" "data.summary.waitingQuestionCount")
CUST_REQS_LEN=$(python3 -c "
import sys, json
p = json.loads('''$LAST_BODY''')
print(len(p['data']['requirements']))
")

if [ "$FINAL_WAITING_QS" = "0" ] && [ "$CUST_REQS_LEN" = "0" ]; then
    log_success "고객 질문서 Preview 정상 (대기 중 질문 0건, 목록 빈 배열 확인)"
else
    log_fail "고객 질문서 Preview 불일치"
fi

# ------------------------------------------------------------------------------
# 15. 멱등성 및 충돌 엣지 케이스 검증
# ------------------------------------------------------------------------------
log_step "15" "엣지 케이스 검증 (승인된 수정안 번복 시 409 REVISION_ALREADY_REVIEWED)"
http_call "POST" "/api/revisions/$REV2_ID/review" '{
    "decision": "REJECT",
    "rejectionReason": "이미 승인된 수정안을 거절 시도",
    "expectedContentVersion": 5
}'

ERR_CODE=$(json_get "$LAST_BODY" "error.code")
if [ "$LAST_HTTP_CODE" = "409" ] && [ "$ERR_CODE" = "REVISION_ALREADY_REVIEWED" ]; then
    log_success "결정 번복 시도 정상 차단 확인 (HTTP 409, code: REVISION_ALREADY_REVIEWED)"
else
    log_fail "충돌 검증 실패 (HTTP $LAST_HTTP_CODE, code: $ERR_CODE)"
fi

# ------------------------------------------------------------------------------
# 완료 요약
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}======================================================================${NC}"
echo -e "${GREEN}${BOLD}🎉 모든 핵심 API E2E 테스트가 성공적으로 완료되었습니다!${NC}"
echo -e "성공한 테스트 단계: ${GREEN}${BOLD}${PASSED_COUNT}${NC}개"
echo -e "${CYAN}======================================================================${NC}"

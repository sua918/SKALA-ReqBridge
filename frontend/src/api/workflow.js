/**
 * Workflow API (Spec 5.13~5.16 — 문제·질문·답변·수정안 검토).
 *
 * 파일 위치는 공용 api/. 구현·Mock 분기는 C (feat/fe-c).
 * 합의: Mock API 분기는 src/api/*.js 안에서만 한다. 화면은 useMock을 보지 않는다.
 *
 * contentVersion은 프론트에서 절대 +1 하지 않는다. 직전 응답의 값을 그대로
 * expectedContentVersion으로 되돌려 보낸다:
 *   - workflow 조회 응답의 data.contentVersion
 *   - 답변 응답의 data.contentVersion
 *   - 검토 응답의 data.requirement.contentVersion
 * 409 CONTENT_VERSION_CONFLICT면 workflow를 재조회하고 사용자 입력은 보존한다 (Spec 8절).
 */
import { api, unwrap, validationError } from '@/api/client'
import { useMock } from '@/api/config'
import { mockReject, notFound } from '@/api/mockError'
import { ApiErrorCode, ReviewDecision } from '@/types/api'
import {
  codePointLength,
  getWorkflowMock,
  normalizeText,
  recreateRevisionMock,
  reviewRevisionMock,
  submitAnswerMock,
} from '@/mocks/store.js'

/** Spec 9절 필드 사전 — answerText 20000자, rejectionReason 2000자. */
const ANSWER_MAX_LENGTH = 20000
const REJECTION_REASON_MAX_LENGTH = 2000

/**
 * Mock store가 돌려준 사유를 Spec 7절 HTTP 오류로 옮긴다.
 * 실BE 연동에서는 서버가 같은 code를 주므로 화면 분기는 그대로 쓰인다.
 */
const MOCK_ERRORS = {
  IN_PROGRESS: [
    409,
    ApiErrorCode.ANALYSIS_IN_PROGRESS,
    '이 요구사항에 진행 중인 작업이 있습니다.',
  ],
  ANSWER_ALREADY_SUBMITTED: [
    409,
    ApiErrorCode.ANSWER_ALREADY_SUBMITTED,
    '이미 다른 내용으로 답변한 질문입니다.',
  ],
  VERSION_CONFLICT: [
    409,
    ApiErrorCode.CONTENT_VERSION_CONFLICT,
    '다른 변경이 먼저 반영되었습니다. 최신 내용을 확인해주세요.',
  ],
  REQUIREMENT_CONFIRMED: [
    409,
    ApiErrorCode.REQUIREMENT_CONFIRMED,
    '이미 확정된 요구사항입니다.',
  ],
  OPEN_ISSUES_EXIST: [
    409,
    ApiErrorCode.OPEN_ISSUES_EXIST,
    '미해결 문제가 남아 있습니다.',
  ],
  REVISION_ALREADY_PROPOSED: [
    409,
    ApiErrorCode.REVISION_ALREADY_PROPOSED,
    '검토를 기다리는 수정안이 이미 있습니다.',
  ],
  REVISION_ALREADY_REVIEWED: [
    409,
    ApiErrorCode.REVISION_ALREADY_REVIEWED,
    '이미 검토를 마친 수정안입니다. 결정과 사유는 바꿀 수 없습니다.',
  ],
  STATE_CONFLICT: [
    409,
    ApiErrorCode.STATE_CONFLICT,
    '지금 상태에서는 처리할 수 없는 요청입니다.',
  ],
}

function rejectMockError(code, resource) {
  if (code === 'NOT_FOUND') {
    return notFound(resource)
  }
  const mapped = MOCK_ERRORS[code] ?? MOCK_ERRORS.STATE_CONFLICT
  return mockReject(...mapped)
}

/** GET /requirements/{requirementId}/workflow — 문제·질문 회차·수정안 전체. */
export async function getWorkflow(requirementId) {
  if (useMock) {
    const workflow = getWorkflowMock(requirementId)
    if (!workflow) {
      return notFound('요구사항')
    }
    return workflow
  }
  return unwrap(await api.get(`/requirements/${requirementId}/workflow`))
}

/**
 * POST /clarifications/{clarificationId}/answers — 답변 저장·재판정 접수.
 *
 * 정규화(CRLF->LF·앞뒤 공백 제거)를 보내기 전에 해 둔다. 서버가 같은 규칙으로
 * 저장하므로, 동일 답변 재전송이 「같은 답변」으로 판정되려면 값이 일치해야 한다.
 */
export async function submitAnswer(clarificationId, { answerText, expectedContentVersion }) {
  const normalized = normalizeText(answerText)

  if (normalized.length === 0) {
    return validationError([{ field: 'answerText', message: '답변을 입력해주세요.' }])
  }
  if (codePointLength(normalized) > ANSWER_MAX_LENGTH) {
    return validationError([
      { field: 'answerText', message: `답변은 ${ANSWER_MAX_LENGTH}자 이내로 입력해주세요.` },
    ])
  }

  const body = { answerText: normalized, expectedContentVersion }

  if (useMock) {
    const result = submitAnswerMock(clarificationId, body)
    if (result.error) {
      return rejectMockError(result.error, '질문')
    }
    return result.receipt
  }
  return unwrap(await api.post(`/clarifications/${clarificationId}/answers`, body))
}

/** POST /requirements/{requirementId}/revisions — 거절 이후 수정안 재생성 접수. */
export async function recreateRevision(requirementId, { expectedContentVersion }) {
  const body = { expectedContentVersion }

  if (useMock) {
    const result = recreateRevisionMock(requirementId, body)
    if (result.error) {
      return rejectMockError(result.error, '요구사항')
    }
    return result.analysis
  }
  return unwrap(await api.post(`/requirements/${requirementId}/revisions`, body))
}

/**
 * POST /revisions/{revisionId}/review — 승인·거절.
 *
 * APPROVE에는 rejectionReason을 아예 보내지 않는다 — Spec 2절이 미정의 필드를
 * 400으로 규정하므로, null이라도 실으면 거절당한다.
 */
export async function reviewRevision(
  revisionId,
  { decision, expectedContentVersion, rejectionReason },
) {
  if (decision !== ReviewDecision.APPROVE && decision !== ReviewDecision.REJECT) {
    return validationError([{ field: 'decision', message: '승인 또는 거절만 가능합니다.' }])
  }

  const body = { decision, expectedContentVersion }

  if (decision === ReviewDecision.REJECT) {
    const reason = normalizeText(rejectionReason)
    if (reason.length === 0) {
      return validationError([
        { field: 'rejectionReason', message: '거절 사유를 입력해주세요.' },
      ])
    }
    if (codePointLength(reason) > REJECTION_REASON_MAX_LENGTH) {
      return validationError([
        {
          field: 'rejectionReason',
          message: `거절 사유는 ${REJECTION_REASON_MAX_LENGTH}자 이내로 입력해주세요.`,
        },
      ])
    }
    body.rejectionReason = reason
  }

  if (useMock) {
    const result = reviewRevisionMock(revisionId, body)
    if (result.error) {
      return rejectMockError(result.error, '수정안')
    }
    return result.result
  }
  return unwrap(await api.post(`/revisions/${revisionId}/review`, body))
}

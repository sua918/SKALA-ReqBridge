/**
 * Workflow API stub (Spec §5.13~ 답변·수정안·검토).
 *
 * 파일 위치는 공용 api/. 구현은 C (`feat/fe-c`).
 * B는 함수명·시그니처만 맞춰 두고, 화면에서는 아직 호출하지 않는다.
 *
 * - getWorkflow: 문제·질문·수정안 전체
 * - submitAnswer: 답변 + expectedContentVersion → 재판정 접수
 * - recreateRevision: 거절 후 수정안 재생성
 * - reviewRevision: APPROVE / REJECT
 */

function notImplemented(name) {
  return Promise.reject(new Error(`Not implemented: ${name} (C feat/fe-c)`))
}

/** GET /requirements/{requirementId}/workflow */
export function getWorkflow(requirementId) {
  return notImplemented(`getWorkflow(${requirementId})`)
}

/** POST /clarifications/{clarificationId}/answers */
export function submitAnswer(clarificationId, body) {
  return notImplemented(`submitAnswer(${clarificationId})`)
}

/** POST /requirements/{requirementId}/revisions */
export function recreateRevision(requirementId, body) {
  return notImplemented(`recreateRevision(${requirementId})`)
}

/** POST /revisions/{revisionId}/review */
export function reviewRevision(revisionId, body) {
  return notImplemented(`reviewRevision(${revisionId})`)
}

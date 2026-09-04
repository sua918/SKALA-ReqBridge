/**
 * mock-scenarios.md 2~8절 · Spec 8절 E2E를 store 함수로 직접 검증한다.
 * 브라우저 없이 돌려 상태·버전·ID 규칙이 명세와 어긋나면 바로 잡는다.
 *
 *   node scripts/verify-workflow-mock.mjs
 *
 * '@/...' 별칭은 Vite 전용이라 Node에서는 통하지 않는다.
 * 실행 전에 store/fixtures/types를 상대 경로로 바꾼 사본을 만든다.
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const SRC = path.resolve('src')
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'reqbridge-mock-'))

for (const rel of ['types/api.js', 'mocks/fixtures.js', 'mocks/store.js']) {
  const out = path.join(work, path.basename(rel))
  const code = fs
    .readFileSync(path.join(SRC, rel), 'utf8')
    .replace(/'@\/types\/api'/g, "'./api.js'")
    .replace(/'@\/mocks\/fixtures\.js'/g, "'./fixtures.js'")
  fs.writeFileSync(out, code)
}

const store = await import(pathToFileURL(path.join(work, 'store.js')).href)

let pass = 0
const fails = []
function check(label, actual, expected) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) {
    pass += 1
    return
  }
  fails.push(label + '\n    기대: ' + e + '\n    실제: ' + a)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
/** Mock은 1.2초 뒤 종료한다. 조회를 해야 settle이 돈다. */
async function settle(analysisId) {
  await sleep(1400)
  return store.getAnalysisMock(analysisId)
}

// --- 2절: seed 상태 ------------------------------------------------------
let wf = store.getWorkflowMock(401)
check('2절 요구사항 401 상태', wf.status, 'CLARIFYING')
check('2절 contentVersion', wf.contentVersion, 1)
check('2절 문제 ID', wf.issues.map((i) => i.id), [501, 502])
check('2절 문제 상태', wf.issues.map((i) => i.status), ['OPEN', 'OPEN'])
check('2절 질문 ID', wf.clarifications.map((c) => c.id), [601, 602])
check('2절 질문 상태', wf.clarifications.map((c) => c.status), ['WAITING', 'WAITING'])
check('2절 수정안 없음', wf.revisions, [])
check('2절 활성 작업 없음', wf.activeAnalysis, null)

// --- 3절: 불충분한 첫 답변 -----------------------------------------------
let r = store.submitAnswerMock(601, { answerText: '많이 접속할 것 같습니다.', expectedContentVersion: 1 })
check('3절 접수 성공', r.error ?? null, null)
check('3절 버전 2', r.receipt.contentVersion, 2)
check('3절 작업 kind', r.receipt.analysis.kind, 'ANSWER')
check('3절 작업 PENDING', r.receipt.analysis.status, 'PENDING')
check('3절 inputContentVersion', r.receipt.analysis.inputContentVersion, 2)
const a302 = r.receipt.analysis.id

let done = await settle(a302)
check('3절 작업 COMPLETED', done.status, 'COMPLETED')
check('3절 판정 insufficient', done.result.assessment.sufficient, false)
check('3절 다음 질문 603', done.result.assessment.nextClarificationId, 603)
check('3절 result.clarificationIds', done.result.clarificationIds, [603])
check('3절 result.revisionIds', done.result.revisionIds, [])

wf = store.getWorkflowMock(401)
check('3절 601 ANSWERED', wf.clarifications.find((c) => c.id === 601).status, 'ANSWERED')
check('3절 501 OPEN 유지', wf.issues.find((i) => i.id === 501).status, 'OPEN')
check('3절 603 roundNo 2', wf.clarifications.find((c) => c.id === 603).roundNo, 2)
check('3절 603 WAITING', wf.clarifications.find((c) => c.id === 603).status, 'WAITING')
check('3절 질문 정렬', wf.clarifications.map((c) => c.id), [601, 603, 602])

// --- 8절: 동일 답변 재전송 ------------------------------------------------
r = store.submitAnswerMock(601, { answerText: '  많이 접속할 것 같습니다.  ', expectedContentVersion: 1 })
check('8절 동일 답변 재전송 성공', r.error ?? null, null)
check('8절 재사용 표시', r.reused, true)
check('8절 기존 작업 반환', r.receipt.analysis.id, a302)
check('8절 버전 유지', store.getWorkflowMock(401).contentVersion, 2)

r = store.submitAnswerMock(601, { answerText: '다른 답변입니다.', expectedContentVersion: 2 })
check('8절 다른 답변 거부', r.error, 'ANSWER_ALREADY_SUBMITTED')

r = store.submitAnswerMock(603, { answerText: '최대 동시 사용자 3,000명입니다.', expectedContentVersion: 1 })
check('8절 stale 버전 거부', r.error, 'VERSION_CONFLICT')

// --- 4절: 충분한 답변, 부분 해결 -------------------------------------------
r = store.submitAnswerMock(603, { answerText: '최대 동시 사용자 3,000명입니다.', expectedContentVersion: 2 })
check('4절 접수 성공', r.error ?? null, null)
check('4절 버전 3', r.receipt.contentVersion, 3)
done = await settle(r.receipt.analysis.id)
check('4절 판정 sufficient', done.result.assessment.sufficient, true)
check('4절 issueIds', done.result.issueIds, [501])
check('4절 clarificationIds 없음', done.result.clarificationIds, [])
check('4절 수정안 없음', done.result.revisionIds, [])

wf = store.getWorkflowMock(401)
check('4절 603 RESOLVED', wf.clarifications.find((c) => c.id === 603).status, 'RESOLVED')
check('4절 501 RESOLVED', wf.issues.find((i) => i.id === 501).status, 'RESOLVED')
check('4절 502 OPEN', wf.issues.find((i) => i.id === 502).status, 'OPEN')
check('4절 요구사항 CLARIFYING', wf.status, 'CLARIFYING')

// --- 5절: 모든 문제 해결 -> 최초 수정안 -------------------------------------
r = store.submitAnswerMock(602, { answerText: 'p95 응답 시간 2초 이하입니다.', expectedContentVersion: 3 })
check('5절 버전 4', r.receipt.contentVersion, 4)
done = await settle(r.receipt.analysis.id)
check('5절 수정안 701 생성', done.result.revisionIds, [701])

wf = store.getWorkflowMock(401)
check('5절 요구사항 IN_REVIEW', wf.status, 'IN_REVIEW')
check('5절 버전 4 유지', wf.contentVersion, 4)
const rev701 = wf.revisions.find((v) => v.id === 701)
check('5절 701 PROPOSED', rev701.status, 'PROPOSED')
check('5절 701 revisionNo 1', rev701.revisionNo, 1)
check('5절 701 inputContentVersion 4', rev701.inputContentVersion, 4)
check('5절 701 근거 질문', rev701.basedOnClarificationIds, [601, 603, 602])
check('5절 acceptanceCriteria 빈 배열', rev701.acceptanceCriteria, [])

// --- 6절: 거절 -> v5 -------------------------------------------------------
const REASON = '동시 사용자 수를 최대치로 명확하게 표현해주세요.'
let rev = store.reviewRevisionMock(701, { decision: 'REJECT', expectedContentVersion: 4, rejectionReason: REASON })
check('6절 거절 성공', rev.error ?? null, null)
check('6절 701 REJECTED', rev.result.revision.status, 'REJECTED')
check('6절 701 inputContentVersion 4 유지', rev.result.revision.inputContentVersion, 4)
check('6절 요구사항 CLARIFYING', rev.result.requirement.status, 'CLARIFYING')
check('6절 버전 5', rev.result.requirement.contentVersion, 5)
check('6절 confirmedText null', rev.result.requirement.confirmedText, null)

rev = store.reviewRevisionMock(701, { decision: 'REJECT', expectedContentVersion: 4, rejectionReason: REASON })
check('6절 동일 거절 재전송', rev.reused, true)
check('6절 버전 5 유지', rev.result.requirement.contentVersion, 5)

rev = store.reviewRevisionMock(701, { decision: 'APPROVE', expectedContentVersion: 5 })
check('6절 결정 변경 거부', rev.error, 'REVISION_ALREADY_REVIEWED')

// --- 6절: 재생성 -----------------------------------------------------------
r = store.recreateRevisionMock(401, { expectedContentVersion: 4 })
check('6절 stale 재생성 거부', r.error, 'VERSION_CONFLICT')

r = store.recreateRevisionMock(401, { expectedContentVersion: 5 })
check('6절 재생성 접수', r.error ?? null, null)
check('6절 작업 kind', r.analysis.kind, 'REVISION')
check('6절 작업 inputContentVersion 5', r.analysis.inputContentVersion, 5)

const dup = store.recreateRevisionMock(401, { expectedContentVersion: 5 })
check('6절 진행 중 재요청 거부', dup.error, 'IN_PROGRESS')

done = await settle(r.analysis.id)
check('6절 702 생성', done.result.revisionIds, [702])

wf = store.getWorkflowMock(401)
const rev702 = wf.revisions.find((v) => v.id === 702)
check('6절 702 revisionNo 2', rev702.revisionNo, 2)
check('6절 702 inputContentVersion 5', rev702.inputContentVersion, 5)
check('6절 702 PROPOSED', rev702.status, 'PROPOSED')
check('6절 701 REJECTED 유지', wf.revisions.find((v) => v.id === 701).status, 'REJECTED')
check('6절 수정안 정렬', wf.revisions.map((v) => v.revisionNo), [2, 1])
check('6절 요구사항 IN_REVIEW', wf.status, 'IN_REVIEW')
check('6절 버전 5 유지', wf.contentVersion, 5)

// --- 7절: 승인 -------------------------------------------------------------
rev = store.reviewRevisionMock(702, { decision: 'APPROVE', expectedContentVersion: 5 })
check('7절 승인 성공', rev.error ?? null, null)
check('7절 702 APPROVED', rev.result.revision.status, 'APPROVED')
check('7절 요구사항 CONFIRMED', rev.result.requirement.status, 'CONFIRMED')
check('7절 버전 5 유지', rev.result.requirement.contentVersion, 5)
check('7절 approvedRevisionId 702', rev.result.requirement.approvedRevisionId, 702)
check('7절 confirmedText 복사', rev.result.requirement.confirmedText, rev702.text)

rev = store.reviewRevisionMock(702, { decision: 'APPROVE', expectedContentVersion: 5 })
check('7절 동일 승인 재전송', rev.reused, true)
check('7절 버전 5 유지', rev.result.requirement.contentVersion, 5)

rev = store.reviewRevisionMock(701, { decision: 'REJECT', expectedContentVersion: 4, rejectionReason: REASON })
check('7절 과거 701 재전송 revision', rev.result.revision.status, 'REJECTED')
check('7절 과거 701 재전송 requirement', rev.result.requirement.status, 'CONFIRMED')

// --- 지원하지 않는 답변은 성공 처리하지 않는다 (mock-scenarios 서문) ---------
store.resetMockStore()
r = store.submitAnswerMock(601, { answerText: '대충 많이요', expectedContentVersion: 1 })
done = await settle(r.receipt.analysis.id)
check('서문 미지원 답변 insufficient', done.result.assessment.sufficient, false)
check('서문 미지원 답변 다음 회차 열림', done.result.clarificationIds.length, 1)
check('서문 문제 OPEN 유지', store.getWorkflowMock(401).issues[0].status, 'OPEN')

// --- 7절 실패·재시도 (mock-scenarios 7절) -----------------------------------
store.resetMockStore()
r = store.submitAnswerMock(602, { answerText: 'INVALID_OUTPUT', expectedContentVersion: 1 })
done = await settle(r.receipt.analysis.id)
check('7절 ANSWER 실패', done.status, 'FAILED')
check('7절 실패 코드', done.error.code, 'AI_OUTPUT_INVALID')
check('7절 실패 시 result 없음', done.result, null)

const retry = store.retryAnalysisMock(done.id)
check('7절 재시도 kind 유지', retry.analysis.kind, 'ANSWER')
check('7절 재시도 inputContentVersion 유지', retry.analysis.inputContentVersion, done.inputContentVersion)
check('7절 retryOfAnalysisId', retry.analysis.retryOfAnalysisId, done.id)
const again = store.retryAnalysisMock(done.id)
check('7절 재시도 중복 생성 안 함', again.analysis.id, retry.analysis.id)


// --- 5.17/5.18 Preview -----------------------------------------------------
store.resetMockStore()

// 분석 직후: 질문 2건이 열려 있는 상태
let cp = store.getCustomerPreviewMock(101)
check('5.17 documentId', cp.documentId, 101)
check('5.17 documentTitle', cp.documentTitle, '상품 조회 서비스 요구사항')
check('5.17 summary', cp.summary, {
  totalRequirements: 1,
  confirmedRequirements: 0,
  openIssueCount: 2,
  waitingQuestionCount: 2,
})
check('5.17 basis', cp.basis, [{ requirementId: 401, sequenceNo: 1, contentVersion: 1, approvedRevisionId: null }])
check('5.17 요구사항 1건', cp.requirements.length, 1)
check('5.17 질문 ID', cp.requirements[0].questions.map((q) => q.id), [601, 602])
check('5.17 질문에 type 포함', cp.requirements[0].questions[0].type, 'QUANTITY_MISSING')
check('5.17 질문에 evidence 포함', cp.requirements[0].questions[0].evidence, '많은 사용자의 정량 기준이 없다.')
check('5.17 질문 필드 집합', Object.keys(cp.requirements[0].questions[0]).sort(), ['evidence', 'id', 'issueId', 'questionText', 'roundNo', 'type'])

let dp = store.getDeveloperPreviewMock(101)
check('5.18 basis', dp.basis, [{ requirementId: 401, sequenceNo: 1, contentVersion: 1, approvedRevisionId: null }])
check('5.18 확정 없음', dp.confirmedRequirements, [])
check('5.18 미확정 1건', dp.unconfirmedRequirements.length, 1)
check('5.18 미확정 status', dp.unconfirmedRequirements[0].status, 'CLARIFYING')
check('5.18 미확정 문제 전부', dp.unconfirmedRequirements[0].issues.map((i) => i.id), [501, 502])
check('5.18 미확정 질문 전부', dp.unconfirmedRequirements[0].questions.map((q) => q.id), [601, 602])

// 한 문제만 해결한 중간 상태: 남은 질문만 고객용에 남는다
r = store.submitAnswerMock(601, { answerText: '많이 접속할 것 같습니다.', expectedContentVersion: 1 })
await settle(r.receipt.analysis.id)
r = store.submitAnswerMock(603, { answerText: '최대 동시 사용자 3,000명입니다.', expectedContentVersion: 2 })
await settle(r.receipt.analysis.id)

cp = store.getCustomerPreviewMock(101)
check('5.17 해결된 문제의 질문 제외', cp.requirements[0].questions.map((q) => q.id), [602])
check('5.17 openIssueCount 1', cp.summary.openIssueCount, 1)
check('5.17 waitingQuestionCount 1', cp.summary.waitingQuestionCount, 1)

// 전부 해결 -> 수정안 -> 승인
r = store.submitAnswerMock(602, { answerText: 'p95 응답 시간 2초 이하입니다.', expectedContentVersion: 3 })
await settle(r.receipt.analysis.id)
store.reviewRevisionMock(701, { decision: 'APPROVE', expectedContentVersion: 4 })

cp = store.getCustomerPreviewMock(101)
check('5.17 물을 게 없으면 요구사항 제외', cp.requirements, [])
check('5.17 확정 후 summary', cp.summary, {
  totalRequirements: 1,
  confirmedRequirements: 1,
  openIssueCount: 0,
  waitingQuestionCount: 0,
})
check('5.17 basis는 확정 후에도 전부', cp.basis, [{ requirementId: 401, sequenceNo: 1, contentVersion: 4, approvedRevisionId: 701 }])

dp = store.getDeveloperPreviewMock(101)
check('5.18 확정 1건', dp.confirmedRequirements.length, 1)
check('5.18 미확정 없음', dp.unconfirmedRequirements, [])
const cr = dp.confirmedRequirements[0]
check('5.18 contentVersion 4', cr.contentVersion, 4)
check('5.18 승인 수정안 701', cr.approvedRevision.id, 701)
check('5.18 승인 상태', cr.approvedRevision.status, 'APPROVED')
check('5.18 inputContentVersion 4', cr.approvedRevision.inputContentVersion, 4)
check('5.18 rejectionReason null', cr.approvedRevision.rejectionReason, null)
check('5.18 근거 답변 ID 대응', cr.evidenceAnswers.map((a) => a.id), [601, 603, 602])
check('5.18 근거 답변 본문', cr.evidenceAnswers.map((a) => a.answerText), [
  '많이 접속할 것 같습니다.',
  '최대 동시 사용자 3,000명입니다.',
  'p95 응답 시간 2초 이하입니다.',
])
check('5.18 basedOnClarificationIds와 일치', cr.evidenceAnswers.map((a) => a.id).sort(), [...cr.approvedRevision.basedOnClarificationIds].sort())

// basis는 고객 질문 포함 여부와 무관하게 모든 요구사항의 실제 순번을 유지한다.
const ordinalStore = store.getMockStore()
ordinalStore.requirements.push(
  { ...ordinalStore.requirements[0], id: 403, sequenceNo: 3, status: 'EXTRACTED', approvedRevisionId: null, confirmedText: null },
  { ...ordinalStore.requirements[0], id: 402, sequenceNo: 2, status: 'EXTRACTED', approvedRevisionId: null, confirmedText: null },
)
cp = store.getCustomerPreviewMock(101)
check('5.17 질문 없는 요구사항도 basis 순번 유지', cp.basis.map((b) => [b.requirementId, b.sequenceNo]), [
  [401, 1], [402, 2], [403, 3],
])
dp = store.getDeveloperPreviewMock(101)
check('5.18 basis 순번 유지', dp.basis.map((b) => [b.requirementId, b.sequenceNo]), [
  [401, 1], [402, 2], [403, 3],
])

// 6.4: 확정본과 승인 수정안이 어긋나면 409
ordinalStore.requirements.find((x) => x.id === 401).confirmedText = '손상된 확정본'
check('6.4 불일치 시 409', store.getDeveloperPreviewMock(101).error, 'PREVIEW_VERSION_CONFLICT')

check('5.17 없는 문서', store.getCustomerPreviewMock(999), null)
check('5.18 없는 문서', store.getDeveloperPreviewMock(999), null)

console.log('\n통과 ' + pass + ' / ' + (pass + fails.length))
if (fails.length) {
  console.log('\n실패:')
  fails.forEach((f) => console.log('  - ' + f))
  process.exit(1)
}
console.log('mock-scenarios.md 2~8절 · Spec 8절 전부 일치')

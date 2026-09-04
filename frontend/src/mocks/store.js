import {
  AmbiguityType,
  AnalysisFailureCode,
  AnalysisKind,
  AnalysisStatus,
  ClarificationStatus,
  DocumentSourceType,
  IssueStatus,
  RequirementStatus,
  ReviewDecision,
  RevisionStatus,
} from '@/types/api'
import {
  ANSWER_ASSESSMENTS,
  DEMO_CONTENT,
  REVISION_TEXTS,
  UNSUPPORTED_ASSESSMENT,
  seedAnalysisCompleted,
  seedClarifications,
  seedDocument,
  seedIssues,
  seedProject,
  seedRequirement,
} from '@/mocks/fixtures.js'

function clone(value) {
  return structuredClone(value)
}

function timestamp() {
  return new Date().toISOString().replace(/\.\d{3}Z$/, 'Z')
}

/**
 * 메모리 Mock DB. 페이지 새로고침 시 seed로 초기화.
 */
function createStore() {
  return {
    projects: [clone(seedProject)],
    documents: [clone(seedDocument)],
    analyses: [clone(seedAnalysisCompleted)],
    requirements: [clone(seedRequirement)],
    issues: clone(seedIssues),
    clarifications: clone(seedClarifications),
    revisions: [],
    nextProjectId: 2,
    nextDocumentId: 102,
    nextAnalysisId: 302,
    nextRequirementId: 402,
    nextIssueId: 503,
    //고정 ID는 mock-scenarios.md §1을 따른다 (질문 603부터, 수정안 701부터)
    nextClarificationId: 603,
    nextRevisionId: 701,
    //접수한 작업의 종료 예정 시각과 결과 (Spec 5.9 PENDING → 6.1 polling 재현)
    pendingCompletions: {},
  }
}

const ANALYSIS_DELAY_MS = 1200

/** mock-scenarios.md §7 — 입력에 이 표시가 있으면 작업을 실패로 끝낸다. */
const FAILURE_MARKER = 'INVALID_OUTPUT'

let store = createStore()

export function resetMockStore() {
  store = createStore()
}

export function getMockStore() {
  return store
}

export function listProjectsMock() {
  const items = [...store.projects].sort((a, b) => b.id - a.id)
  return { items: clone(items) }
}

/** 내부 조회는 원본을 쓰고, 화면으로 나가는 값은 clone한다 (Mock DB 오염 방지). */
function findProject(projectId) {
  return store.projects.find((p) => p.id === Number(projectId)) ?? null
}

function findDocument(documentId) {
  return store.documents.find((d) => d.id === Number(documentId)) ?? null
}

function findAnalysis(analysisId) {
  return store.analyses.find((a) => a.id === Number(analysisId)) ?? null
}

export function getProjectMock(projectId) {
  const project = findProject(projectId)
  return project ? clone(project) : null
}

export function createProjectMock({ name, description = null }) {
  const project = {
    id: store.nextProjectId++,
    name,
    description,
    createdAt: timestamp(),
  }
  store.projects.push(project)
  return clone(project)
}

export function listDocumentsMock(projectId) {
  const items = store.documents
    .filter((d) => d.projectId === Number(projectId))
    .sort((a, b) => b.id - a.id)
    .map(({ id, projectId: pid, title, sourceType, createdAt }) => ({
      id,
      projectId: pid,
      title,
      sourceType,
      createdAt,
    }))
  return { items }
}

export function getDocumentMock(documentId) {
  const document = findDocument(documentId)
  return document ? clone(document) : null
}

export function createTextDocumentMock(projectId, { title, sourceType, content }) {
  const document = {
    id: store.nextDocumentId++,
    projectId: Number(projectId),
    title,
    sourceType,
    content,
    createdAt: timestamp(),
  }
  store.documents.push(document)
  return clone(document)
}

/**
 * PDF 텍스트 추출은 서버 몫이라 Mock에서는 재현하지 않는다.
 * 파일명을 붙인 데모 원문을 content로 넣어 이후 분석 흐름만 이어지게 한다.
 */
export function createUploadedDocumentMock(projectId, { title, file }) {
  const document = {
    id: store.nextDocumentId++,
    projectId: Number(projectId),
    title,
    sourceType: DocumentSourceType.FILE,
    content: `${file.name}에서 추출한 텍스트 (Mock)\n${DEMO_CONTENT}`,
    createdAt: timestamp(),
  }
  store.documents.push(document)
  return clone(document)
}

export function listRequirementsMock(documentId) {
  const hasCompleted = store.analyses.some(
    (a) =>
      a.documentId === Number(documentId) &&
      a.kind === AnalysisKind.DOCUMENT &&
      a.status === AnalysisStatus.COMPLETED,
  )
  if (!hasCompleted) {
    return { items: [] }
  }
  const items = store.requirements
    .filter((r) => r.documentId === Number(documentId))
    .sort((a, b) => a.sequenceNo - b.sequenceNo)
  return { items: clone(items) }
}

export function getRequirementMock(requirementId) {
  const requirement =
    store.requirements.find((r) => r.id === Number(requirementId)) ?? null
  return requirement ? clone(requirement) : null
}

export function listIssuesByRequirementMock(requirementId) {
  const items = store.issues.filter((i) => i.requirementId === Number(requirementId))
  return { items: clone(items) }
}

export function listAnalysesMock(documentId, kind) {
  store.analyses.forEach(settlePendingAnalysis)
  let items = store.analyses.filter((a) => a.documentId === Number(documentId))
  if (kind) {
    items = items.filter((a) => a.kind === kind)
  }
  items = [...items].sort((a, b) => b.id - a.id)
  return { items: clone(items) }
}

export function getAnalysisMock(analysisId) {
  const analysis = findAnalysis(analysisId)
  if (!analysis) {
    return null
  }
  settlePendingAnalysis(analysis)
  return clone(analysis)
}

/** mock-scenarios.md §9 분류 기준의 축약 규칙. 표에 없는 표현은 문제로 보지 않는다. */
const AMBIGUITY_RULES = [
  { type: AmbiguityType.QUANTITY_MISSING, terms: ['많은', '다수', '대량', '여러'] },
  { type: AmbiguityType.PERFORMANCE_MISSING, terms: ['빠르게', '빠른', '신속', '즉시'] },
  { type: AmbiguityType.TERM_AMBIGUOUS, terms: ['적절', '적당', '충분', '유연'] },
  { type: AmbiguityType.CONDITION_MISSING, terms: ['특정 조건', '필요 시', '경우에 따라'] },
  { type: AmbiguityType.ACTOR_MISSING, terms: ['담당자가', '사용자가 요청하면'] },
  { type: AmbiguityType.SUCCESS_CRITERIA_MISSING, terms: ['성공해야', '정상적으로'] },
  { type: AmbiguityType.EXCEPTION_MISSING, terms: ['정의하지 않는다', '예외는'] },
]

function splitSentences(content) {
  return String(content ?? '')
    .split(/\r?\n+/)
    .flatMap((line) => line.split(/(?<=[.!?])\s+/))
    .map((sentence) => sentence.trim())
    .filter(Boolean)
}

function detectIssues(requirementId, text) {
  return AMBIGUITY_RULES.flatMap((rule) => {
    const term = rule.terms.find((candidate) => text.includes(candidate))
    if (!term) {
      return []
    }
    return [
      {
        id: store.nextIssueId++,
        requirementId,
        type: rule.type,
        evidence: `'${term}' 표현의 기준이 명시되지 않았다.`,
        status: IssueStatus.OPEN,
      },
    ]
  })
}

/** 문서 원문에서 요구사항·문제를 만들어 store에 저장한다. */
function extractRequirements(document, analysisId) {
  const requirements = []
  const issues = []

  splitSentences(document.content).forEach((text, index) => {
    const requirement = {
      id: store.nextRequirementId++,
      documentId: document.id,
      analysisId,
      sequenceNo: index + 1,
      originalText: text,
      status: RequirementStatus.EXTRACTED,
      contentVersion: 1,
      approvedRevisionId: null,
      confirmedText: null,
    }
    //질문(clarification)은 workflow 범위라 여기서 만들지 않는다 → CLARIFYING이 아니라 AMBIGUOUS
    const detected = detectIssues(requirement.id, text)
    if (detected.length > 0) {
      requirement.status = RequirementStatus.AMBIGUOUS
    }
    requirements.push(requirement)
    issues.push(...detected)
  })

  store.requirements.push(...requirements)
  store.issues.push(...issues)
  return { requirements, issues }
}

/**
 * 접수한 작업을 지연 후 종료한다. 요구사항 저장은 완료 시점에 수행한다.
 * 실패 작업은 부분 결과를 남기지 않는다 (mock-scenarios §7).
 */
function settlePendingAnalysis(analysis) {
  const pending = store.pendingCompletions[analysis.id]
  if (pending === undefined || Date.now() < pending.readyAt) {
    return
  }
  delete store.pendingCompletions[analysis.id]

  const now = timestamp()
  analysis.startedAt = analysis.startedAt ?? now
  analysis.completedAt = now

  if (pending.outcome === AnalysisStatus.FAILED) {
    analysis.status = AnalysisStatus.FAILED
    analysis.result = null
    analysis.error = {
      code: AnalysisFailureCode.AI_OUTPUT_INVALID,
      message: '분석 결과 형식이 올바르지 않습니다.',
    }
    return
  }

  //작업 종류마다 완료 결과가 다르다. DOCUMENT는 B, ANSWER·REVISION은 workflow(C).
  if (analysis.kind === AnalysisKind.ANSWER) {
    settleAnswerAnalysis(analysis)
    return
  }
  if (analysis.kind === AnalysisKind.REVISION) {
    settleRevisionAnalysis(analysis)
    return
  }

  const document = findDocument(analysis.documentId)
  const { requirements, issues } = extractRequirements(document, analysis.id)

  analysis.status = AnalysisStatus.COMPLETED
  analysis.error = null
  analysis.result = {
    requirementIds: requirements.map((r) => r.id),
    issueIds: issues.map((i) => i.id),
    clarificationIds: [],
    revisionIds: [],
    assessment: null,
  }
}

function schedule(analysisId, outcome) {
  store.pendingCompletions[analysisId] = {
    readyAt: Date.now() + ANALYSIS_DELAY_MS,
    outcome,
  }
}

export function startDocumentAnalysisMock(documentId) {
  const docId = Number(documentId)
  const document = findDocument(docId)
  if (!document) {
    return { error: 'NOT_FOUND' }
  }

  const active = store.analyses.find(
    (a) =>
      a.documentId === docId &&
      a.kind === AnalysisKind.DOCUMENT &&
      (a.status === AnalysisStatus.PENDING || a.status === AnalysisStatus.PROCESSING),
  )
  if (active) {
    return { error: 'IN_PROGRESS', analysis: clone(active) }
  }

  const completed = store.analyses.find(
    (a) =>
      a.documentId === docId &&
      a.kind === AnalysisKind.DOCUMENT &&
      a.status === AnalysisStatus.COMPLETED,
  )
  if (completed) {
    return { error: 'ALREADY_ANALYZED', analysis: clone(completed) }
  }

  const analysis = {
    id: store.nextAnalysisId++,
    kind: AnalysisKind.DOCUMENT,
    status: AnalysisStatus.PENDING,
    documentId: docId,
    requirementId: null,
    clarificationId: null,
    inputContentVersion: null,
    retryOfAnalysisId: null,
    createdAt: timestamp(),
    startedAt: null,
    completedAt: null,
    result: null,
    error: null,
  }
  store.analyses.push(analysis)
  schedule(
    analysis.id,
    document.content.includes(FAILURE_MARKER)
      ? AnalysisStatus.FAILED
      : AnalysisStatus.COMPLETED,
  )
  return { analysis: clone(analysis) }
}

export function retryAnalysisMock(analysisId) {
  const original = findAnalysis(analysisId)
  if (!original) {
    return { error: 'NOT_FOUND' }
  }
  settlePendingAnalysis(original)
  if (original.status !== AnalysisStatus.FAILED) {
    return { error: 'NOT_RETRYABLE' }
  }

  //같은 실패 작업의 재시도가 이미 있으면 새로 만들지 않는다 (Spec 5.12).
  const existing = store.analyses.find((a) => a.retryOfAnalysisId === original.id)
  if (existing) {
    settlePendingAnalysis(existing)
    return { analysis: clone(existing) }
  }

  //kind와 inputContentVersion은 원본을 유지한다 (mock-scenarios §7).
  const analysis = {
    ...clone(original),
    id: store.nextAnalysisId++,
    status: AnalysisStatus.PENDING,
    retryOfAnalysisId: original.id,
    createdAt: timestamp(),
    startedAt: null,
    completedAt: null,
    result: null,
    error: null,
  }
  store.analyses.push(analysis)
  //Mock은 실패를 한 번 재현한 뒤 재시도 성공 경로를 보여준다.
  schedule(analysis.id, AnalysisStatus.COMPLETED)
  return { analysis: clone(analysis) }
}


/* ---------------------------------------------------------------------------
   Workflow Mock (Spec 5.13~5.16 · mock-scenarios.md 3~6절) — C 담당.
   B의 문서 분석 경로는 그대로 두고, 여기서 ANSWER·REVISION만 다룬다.
   --------------------------------------------------------------------------- */

/**
 * Spec §2.1이 고정한 앞뒤 공백 집합. `\s`로 대신하지 않는다 —
 * JS의 `\s`는 U+FEFF를 포함하고 U+0085를 빼는 등 명세와 집합이 다르다.
 * 서버와 같은 집합을 써야 「같은 답변」 판정이 양쪽에서 일치한다.
 */
const TRIM_CLASS =
  '[\u0009-\u000D\u0020\u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]'
const TRIM_RE = new RegExp(`^${TRIM_CLASS}+|${TRIM_CLASS}+$`, 'g')

/** 답변·거절 사유: CRLF→LF 후 앞뒤 공백 제거. 저장과 동일성 비교에 같은 값을 쓴다. */
export function normalizeText(value) {
  return String(value ?? '')
    .replace(/\r\n/g, '\n')
    .replace(TRIM_RE, '')
}

/** 길이는 Unicode 코드 포인트 기준 (Spec §2.1). `.length`는 이모지를 2로 센다. */
export function codePointLength(value) {
  return [...String(value ?? '')].length
}

function findClarification(clarificationId) {
  return store.clarifications.find((c) => c.id === Number(clarificationId)) ?? null
}

function findRevision(revisionId) {
  return store.revisions.find((r) => r.id === Number(revisionId)) ?? null
}

function findRequirement(requirementId) {
  return store.requirements.find((r) => r.id === Number(requirementId)) ?? null
}

function issuesOf(requirementId) {
  return store.issues
    .filter((i) => i.requirementId === Number(requirementId))
    .sort((a, b) => a.id - b.id)
}

/** issueId -> roundNo 오름차순 (Spec 5.13 정렬 계약). */
function clarificationsOf(requirementId) {
  return store.clarifications
    .filter((c) => c.requirementId === Number(requirementId))
    .sort((a, b) => a.issueId - b.issueId || a.roundNo - b.roundNo)
}

/** revisionNo 내림차순 (Spec 5.13). */
function revisionsOf(requirementId) {
  return store.revisions
    .filter((r) => r.requirementId === Number(requirementId))
    .sort((a, b) => b.revisionNo - a.revisionNo)
}

/**
 * 같은 요구사항의 활성 ANSWER/REVISION 작업.
 * DOCUMENT 작업은 문서 단위라 여기 포함하지 않는다 (Spec 6.3).
 */
function activeWorkflowAnalysis(requirementId) {
  return (
    store.analyses.find(
      (a) =>
        a.requirementId === Number(requirementId) &&
        (a.kind === AnalysisKind.ANSWER || a.kind === AnalysisKind.REVISION) &&
        (a.status === AnalysisStatus.PENDING ||
          a.status === AnalysisStatus.PROCESSING),
    ) ?? null
  )
}

/** 답변이 들어온 질문들. 수정안의 근거 집합이 된다 (Spec 5.16 basedOnClarificationIds). */
function basisClarificationIds(requirementId) {
  return clarificationsOf(requirementId)
    .filter((c) => c.answerText !== null)
    .map((c) => c.id)
}

function createWorkflowAnalysis(requirement, kind, clarificationId) {
  const analysis = {
    id: store.nextAnalysisId++,
    kind,
    status: AnalysisStatus.PENDING,
    documentId: requirement.documentId,
    requirementId: requirement.id,
    clarificationId: clarificationId ?? null,
    inputContentVersion: requirement.contentVersion,
    retryOfAnalysisId: null,
    createdAt: timestamp(),
    startedAt: null,
    completedAt: null,
    result: null,
    error: null,
  }
  store.analyses.push(analysis)
  return analysis
}

const EMPTY_RESULT = {
  requirementIds: [],
  issueIds: [],
  clarificationIds: [],
  revisionIds: [],
  assessment: null,
}

/** 모든 문제가 해결됐을 때 붙일 수정안을 만든다 (mock-scenarios 5·6절). */
function proposeRevision(requirement, text) {
  const revisionNo =
    revisionsOf(requirement.id).reduce((max, r) => Math.max(max, r.revisionNo), 0) + 1
  const revision = {
    id: store.nextRevisionId++,
    requirementId: requirement.id,
    revisionNo,
    text,
    status: RevisionStatus.PROPOSED,
    inputContentVersion: requirement.contentVersion,
    basedOnClarificationIds: basisClarificationIds(requirement.id),
    rejectionReason: null,
    //Acceptance Criteria는 P3라 항상 빈 배열 (Spec 1절).
    acceptanceCriteria: [],
  }
  store.revisions.push(revision)
  requirement.status = RequirementStatus.IN_REVIEW
  return revision
}

/** 답변 재판정 완료 (mock-scenarios 3~5절). */
function settleAnswerAnalysis(analysis) {
  const requirement = findRequirement(analysis.requirementId)
  const clarification = findClarification(analysis.clarificationId)
  const issue = store.issues.find((i) => i.id === clarification?.issueId) ?? null

  analysis.status = AnalysisStatus.COMPLETED
  analysis.error = null

  if (!requirement || !clarification || !issue) {
    analysis.result = { ...EMPTY_RESULT }
    return
  }

  const matched = ANSWER_ASSESSMENTS.find((row) => row.answer === clarification.answerText)
  const verdict = matched ?? UNSUPPORTED_ASSESSMENT

  if (!verdict.sufficient) {
    //문제는 OPEN으로 남고 같은 문제의 다음 회차 질문이 열린다.
    const roundNo =
      store.clarifications
        .filter((c) => c.issueId === issue.id)
        .reduce((max, c) => Math.max(max, c.roundNo), 0) + 1
    const next = {
      id: store.nextClarificationId++,
      requirementId: requirement.id,
      issueId: issue.id,
      roundNo,
      questionText: verdict.nextQuestionText,
      answerText: null,
      status: ClarificationStatus.WAITING,
    }
    store.clarifications.push(next)

    analysis.result = {
      ...EMPTY_RESULT,
      requirementIds: [requirement.id],
      issueIds: [issue.id],
      clarificationIds: [next.id],
      assessment: {
        issueId: issue.id,
        sufficient: false,
        reason: verdict.reason,
        nextClarificationId: next.id,
      },
    }
    return
  }

  clarification.status = ClarificationStatus.RESOLVED
  issue.status = IssueStatus.RESOLVED

  const allResolved = issuesOf(requirement.id).every(
    (i) => i.status === IssueStatus.RESOLVED,
  )
  const assessment = {
    issueId: issue.id,
    sufficient: true,
    reason: verdict.reason,
    nextClarificationId: null,
  }

  if (!allResolved) {
    //문제 하나만 풀렸으면 수정안을 만들지 않는다 (Spec 8절 분기 검증 a).
    analysis.result = {
      ...EMPTY_RESULT,
      requirementIds: [requirement.id],
      issueIds: [issue.id],
      assessment,
    }
    return
  }

  const revision = proposeRevision(requirement, REVISION_TEXTS.FIRST)
  analysis.result = {
    ...EMPTY_RESULT,
    requirementIds: [requirement.id],
    issueIds: [issue.id],
    revisionIds: [revision.id],
    assessment,
  }
}

/** 거절 이후 수정안 재생성 완료 (mock-scenarios 6절). */
function settleRevisionAnalysis(analysis) {
  const requirement = findRequirement(analysis.requirementId)
  analysis.status = AnalysisStatus.COMPLETED
  analysis.error = null

  if (!requirement) {
    analysis.result = { ...EMPTY_RESULT }
    return
  }

  //거절된 수정안과 근거 답변 이력은 손대지 않는다 (mock-scenarios 6절).
  const revision = proposeRevision(requirement, REVISION_TEXTS.REGENERATED)
  analysis.result = {
    ...EMPTY_RESULT,
    requirementIds: [requirement.id],
    revisionIds: [revision.id],
  }
}

/** GET /requirements/{id}/workflow — 조회는 새 질문·수정안을 만들지 않는다 (Spec 2절 GET). */
export function getWorkflowMock(requirementId) {
  const requirement = findRequirement(requirementId)
  if (!requirement) {
    return null
  }
  store.analyses.forEach(settlePendingAnalysis)

  return clone({
    requirementId: requirement.id,
    status: requirement.status,
    contentVersion: requirement.contentVersion,
    activeAnalysis: activeWorkflowAnalysis(requirement.id),
    issues: issuesOf(requirement.id),
    clarifications: clarificationsOf(requirement.id),
    revisions: revisionsOf(requirement.id),
  })
}

/**
 * POST /clarifications/{id}/answers.
 *
 * 검증 순서는 Spec 6.2 그대로다. 특히 동일 답변 재제출 판정이
 * 버전·상태 검증보다 먼저다 — 버전이 이미 진행됐다는 이유만으로 정상
 * 재전송을 실패시키지 않기 위함이다 (mock-scenarios 8절).
 */
export function submitAnswerMock(clarificationId, { answerText, expectedContentVersion }) {
  const clarification = findClarification(clarificationId)
  if (!clarification) {
    return { error: 'NOT_FOUND' }
  }
  const requirement = findRequirement(clarification.requirementId)
  if (!requirement) {
    return { error: 'NOT_FOUND' }
  }
  store.analyses.forEach(settlePendingAnalysis)

  const normalized = normalizeText(answerText)

  //(3) 동일 요청 판정 — 저장된 작업을 그대로 돌려주고 버전을 올리지 않는다.
  if (clarification.answerText !== null) {
    if (clarification.answerText !== normalized) {
      return { error: 'ANSWER_ALREADY_SUBMITTED' }
    }
    const original =
      store.analyses.find(
        (a) => a.kind === AnalysisKind.ANSWER && a.clarificationId === clarification.id,
      ) ?? null
    return {
      receipt: {
        clarificationId: clarification.id,
        requirementId: requirement.id,
        contentVersion: requirement.contentVersion,
        analysis: original ? clone(original) : null,
      },
      reused: true,
    }
  }

  //(4) 새 처리의 전제 검증
  if (requirement.status === RequirementStatus.CONFIRMED) {
    return { error: 'REQUIREMENT_CONFIRMED' }
  }
  if (activeWorkflowAnalysis(requirement.id)) {
    return { error: 'IN_PROGRESS' }
  }
  if (Number(expectedContentVersion) !== requirement.contentVersion) {
    return { error: 'VERSION_CONFLICT' }
  }
  if (clarification.status !== ClarificationStatus.WAITING) {
    return { error: 'STATE_CONFLICT' }
  }

  //(5) 답변 저장·버전 증가·작업 접수는 한 트랜잭션 (Spec 5.14)
  clarification.answerText = normalized
  clarification.status = ClarificationStatus.ANSWERED
  requirement.contentVersion += 1

  const analysis = createWorkflowAnalysis(requirement, AnalysisKind.ANSWER, clarification.id)
  schedule(
    analysis.id,
    normalized.includes(FAILURE_MARKER) ? AnalysisStatus.FAILED : AnalysisStatus.COMPLETED,
  )

  return {
    receipt: {
      clarificationId: clarification.id,
      requirementId: requirement.id,
      contentVersion: requirement.contentVersion,
      analysis: clone(analysis),
    },
  }
}

/** POST /requirements/{id}/revisions — 거절 이후 재생성 (Spec 5.15). */
export function recreateRevisionMock(requirementId, { expectedContentVersion }) {
  const requirement = findRequirement(requirementId)
  if (!requirement) {
    return { error: 'NOT_FOUND' }
  }
  store.analyses.forEach(settlePendingAnalysis)

  if (requirement.status === RequirementStatus.CONFIRMED) {
    return { error: 'REQUIREMENT_CONFIRMED' }
  }
  if (activeWorkflowAnalysis(requirement.id)) {
    return { error: 'IN_PROGRESS' }
  }

  const revisions = revisionsOf(requirement.id)
  if (revisions.some((r) => r.status === RevisionStatus.PROPOSED)) {
    return { error: 'REVISION_ALREADY_PROPOSED' }
  }
  if (issuesOf(requirement.id).some((i) => i.status === IssueStatus.OPEN)) {
    return { error: 'OPEN_ISSUES_EXIST' }
  }
  //거절 이력이 있을 때만 재생성한다. 최초 수정안은 답변 재판정이 만든다 (Spec 5.15).
  if (!revisions.some((r) => r.status === RevisionStatus.REJECTED)) {
    return { error: 'STATE_CONFLICT' }
  }
  if (Number(expectedContentVersion) !== requirement.contentVersion) {
    return { error: 'VERSION_CONFLICT' }
  }

  const analysis = createWorkflowAnalysis(requirement, AnalysisKind.REVISION, null)
  schedule(analysis.id, AnalysisStatus.COMPLETED)
  return { analysis: clone(analysis) }
}

/** POST /revisions/{id}/review — 승인·거절 (Spec 5.16). */
export function reviewRevisionMock(
  revisionId,
  { decision, expectedContentVersion, rejectionReason },
) {
  const revision = findRevision(revisionId)
  if (!revision) {
    return { error: 'NOT_FOUND' }
  }
  const requirement = findRequirement(revision.requirementId)
  if (!requirement) {
    return { error: 'NOT_FOUND' }
  }
  store.analyses.forEach(settlePendingAnalysis)

  const reason = decision === ReviewDecision.REJECT ? normalizeText(rejectionReason) : null

  //(3) 이미 검토된 수정안 — 같은 결정·같은 사유면 부수 효과 없이 그대로 돌려준다.
  if (revision.status !== RevisionStatus.PROPOSED) {
    const sameDecision =
      (decision === ReviewDecision.APPROVE && revision.status === RevisionStatus.APPROVED) ||
      (decision === ReviewDecision.REJECT &&
        revision.status === RevisionStatus.REJECTED &&
        revision.rejectionReason === reason)
    if (!sameDecision) {
      return { error: 'REVISION_ALREADY_REVIEWED' }
    }
    //과거 revision과 현재 requirement의 조합이다 (Spec 8절).
    return {
      result: { revision: clone(revision), requirement: clone(requirement) },
      reused: true,
    }
  }

  //(4) 새 처리의 전제 검증
  if (activeWorkflowAnalysis(requirement.id)) {
    return { error: 'IN_PROGRESS' }
  }
  if (requirement.status === RequirementStatus.CONFIRMED) {
    return { error: 'REQUIREMENT_CONFIRMED' }
  }
  if (issuesOf(requirement.id).some((i) => i.status === IssueStatus.OPEN)) {
    return { error: 'OPEN_ISSUES_EXIST' }
  }
  if (Number(expectedContentVersion) !== requirement.contentVersion) {
    return { error: 'VERSION_CONFLICT' }
  }

  if (decision === ReviewDecision.APPROVE) {
    //승인은 버전을 올리지 않는다 (Spec 6.3).
    revision.status = RevisionStatus.APPROVED
    revision.rejectionReason = null
    requirement.status = RequirementStatus.CONFIRMED
    requirement.approvedRevisionId = revision.id
    requirement.confirmedText = revision.text
  } else {
    //거절 사유가 새 입력이라 버전이 1 오른다. 문제를 자동 재개방하지 않는다 (Spec 6.3).
    revision.status = RevisionStatus.REJECTED
    revision.rejectionReason = reason
    requirement.status = RequirementStatus.CLARIFYING
    requirement.contentVersion += 1
    requirement.approvedRevisionId = null
    requirement.confirmedText = null
  }

  return { result: { revision: clone(revision), requirement: clone(requirement) } }
}


/* ---------------------------------------------------------------------------
   Preview Mock (Spec 5.17~5.18 · 6.4) — C P2 담당.

   Preview는 만들어 내는 화면이 아니라 「지금 저장된 것을 그대로 보여주는」
   읽기 전용 조합이다. 여기서 질문·수정안을 새로 만들지 않는다 (Spec 2절 GET).
   --------------------------------------------------------------------------- */

function requirementsOfDocument(documentId) {
  return store.requirements
    .filter((r) => r.documentId === Number(documentId))
    .sort((a, b) => a.sequenceNo - b.sequenceNo)
}

/** 문서 전체 기준 요약. 요구사항별이 아니라 문서 단위다 (Spec 5.17). */
function previewSummary(requirements) {
  const ids = new Set(requirements.map((r) => r.id))
  return {
    totalRequirements: requirements.length,
    confirmedRequirements: requirements.filter(
      (r) => r.status === RequirementStatus.CONFIRMED,
    ).length,
    openIssueCount: store.issues.filter(
      (i) => ids.has(i.requirementId) && i.status === IssueStatus.OPEN,
    ).length,
    waitingQuestionCount: store.clarifications.filter(
      (c) => ids.has(c.requirementId) && c.status === ClarificationStatus.WAITING,
    ).length,
  }
}

/** basis는 조회 당시 모든 요구사항의 버전이다 (Spec 6.4). 일부만 담지 않는다. */
function previewBasis(requirements) {
  return requirements.map((r) => ({
    requirementId: r.id,
    sequenceNo: r.sequenceNo,
    contentVersion: r.contentVersion,
    approvedRevisionId: r.approvedRevisionId,
  }))
}

/**
 * GET /documents/{documentId}/previews/customer.
 *
 * 고객에게 나가는 문서라 「지금 답이 필요한 것」만 남긴다:
 * OPEN 문제의 WAITING 질문. 물을 게 없는 요구사항은 목록에서 아예 뺀다.
 */
export function getCustomerPreviewMock(documentId) {
  const document = findDocument(documentId)
  if (!document) {
    return null
  }
  store.analyses.forEach(settlePendingAnalysis)

  const requirements = requirementsOfDocument(document.id)
  const openIssueById = new Map(
    store.issues
      .filter((i) => i.status === IssueStatus.OPEN)
      .map((i) => [i.id, i]),
  )

  const items = requirements
    .map((requirement) => {
      const questions = clarificationsOf(requirement.id)
        .filter(
          (c) => c.status === ClarificationStatus.WAITING && openIssueById.has(c.issueId),
        )
        .map((c) => {
          const issue = openIssueById.get(c.issueId)
          return {
            id: c.id,
            issueId: c.issueId,
            type: issue.type,
            evidence: issue.evidence,
            roundNo: c.roundNo,
            questionText: c.questionText,
          }
        })
      return {
        requirementId: requirement.id,
        sequenceNo: requirement.sequenceNo,
        originalText: requirement.originalText,
        contentVersion: requirement.contentVersion,
        questions,
      }
    })
    //질문 없는 요구사항은 제외한다 (Spec 5.17).
    .filter((item) => item.questions.length > 0)

  return clone({
    documentId: document.id,
    documentTitle: document.title,
    generatedAt: timestamp(),
    summary: previewSummary(requirements),
    basis: previewBasis(requirements),
    requirements: items,
  })
}

/**
 * GET /documents/{documentId}/previews/developer.
 *
 * 확정된 것과 아직 아닌 것을 갈라 놓는다. 확정 쪽에는 승인된 수정안과
 * 그 근거 답변만, 미확정 쪽에는 문제·질문 이력을 상태 필터 없이 전부 담는다.
 */
export function getDeveloperPreviewMock(documentId) {
  const document = findDocument(documentId)
  if (!document) {
    return null
  }
  store.analyses.forEach(settlePendingAnalysis)

  const requirements = requirementsOfDocument(document.id)
  const confirmed = []
  const unconfirmed = []

  for (const requirement of requirements) {
    if (requirement.status !== RequirementStatus.CONFIRMED) {
      unconfirmed.push({
        requirementId: requirement.id,
        sequenceNo: requirement.sequenceNo,
        originalText: requirement.originalText,
        status: requirement.status,
        contentVersion: requirement.contentVersion,
        //상태별로 거르지 않는다 — 개발팀은 어떤 논의를 거쳤는지를 본다 (Spec 5.18).
        issues: issuesOf(requirement.id),
        questions: clarificationsOf(requirement.id),
      })
      continue
    }

    const approved = findRevision(requirement.approvedRevisionId)
    //Spec 6.4: 확정본과 승인 수정안이 어긋나면 서로 다른 버전을 섞지 않고 409를 낸다.
    if (
      !approved ||
      approved.status !== RevisionStatus.APPROVED ||
      approved.requirementId !== requirement.id ||
      approved.text !== requirement.confirmedText
    ) {
      return { error: 'PREVIEW_VERSION_CONFLICT' }
    }

    //근거 답변은 배열 위치가 아니라 ID로 대응시킨다 (Spec 6.4).
    const basisIds = new Set(approved.basedOnClarificationIds)
    confirmed.push({
      requirementId: requirement.id,
      sequenceNo: requirement.sequenceNo,
      originalText: requirement.originalText,
      contentVersion: requirement.contentVersion,
      approvedRevision: approved,
      evidenceAnswers: clarificationsOf(requirement.id).filter((c) => basisIds.has(c.id)),
    })
  }

  return clone({
    documentId: document.id,
    documentTitle: document.title,
    generatedAt: timestamp(),
    summary: previewSummary(requirements),
    basis: previewBasis(requirements),
    confirmedRequirements: confirmed,
    unconfirmedRequirements: unconfirmed,
  })
}

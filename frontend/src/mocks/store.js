import {
  AmbiguityType,
  AnalysisFailureCode,
  AnalysisKind,
  AnalysisStatus,
  DocumentSourceType,
  IssueStatus,
  RequirementStatus,
} from '@/types/api'
import {
  DEMO_CONTENT,
  seedAnalysisCompleted,
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
    nextProjectId: 2,
    nextDocumentId: 102,
    nextAnalysisId: 302,
    nextRequirementId: 402,
    nextIssueId: 503,
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

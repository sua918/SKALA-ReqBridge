import {
  AmbiguityType,
  AnalysisKind,
  AnalysisStatus,
  IssueStatus,
  RequirementStatus,
} from '@/types/api'
import {
  seedAnalysisCompleted,
  seedDocument,
  seedIssues,
  seedProject,
  seedRequirement,
} from '@/mocks/fixtures.js'

function clone(value) {
  return structuredClone(value)
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
    //분석 접수 후 완료까지의 지연 (Spec 5.9 PENDING → 6.1 polling 재현)
    pendingCompletions: {},
  }
}

const ANALYSIS_DELAY_MS = 1200

let store = createStore()

export function resetMockStore() {
  store = createStore()
}

export function getMockStore() {
  return store
}

export function listProjectsMock() {
  const items = [...store.projects].sort((a, b) => b.id - a.id)
  return { items }
}

export function getProjectMock(projectId) {
  return store.projects.find((p) => p.id === Number(projectId)) ?? null
}

export function createProjectMock({ name, description = null }) {
  const project = {
    id: store.nextProjectId++,
    name,
    description,
    createdAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
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
  return store.documents.find((d) => d.id === Number(documentId)) ?? null
}

export function createTextDocumentMock(projectId, { title, sourceType, content }) {
  const document = {
    id: store.nextDocumentId++,
    projectId: Number(projectId),
    title,
    sourceType,
    content,
    createdAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
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
  return store.requirements.find((r) => r.id === Number(requirementId)) ?? null
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
  const analysis = store.analyses.find((a) => a.id === Number(analysisId)) ?? null
  if (analysis) {
    settlePendingAnalysis(analysis)
  }
  return analysis
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

/** 접수한 작업을 지연 후 COMPLETED로 종료한다. 요구사항 저장은 완료 시점에 수행한다. */
function settlePendingAnalysis(analysis) {
  const readyAt = store.pendingCompletions[analysis.id]
  if (readyAt === undefined || Date.now() < readyAt) {
    return
  }
  delete store.pendingCompletions[analysis.id]

  const document = getDocumentMock(analysis.documentId)
  const { requirements, issues } = extractRequirements(document, analysis.id)
  const now = new Date().toISOString().replace(/\.\d{3}Z$/, 'Z')

  analysis.status = AnalysisStatus.COMPLETED
  analysis.startedAt = analysis.startedAt ?? now
  analysis.completedAt = now
  analysis.result = {
    requirementIds: requirements.map((r) => r.id),
    issueIds: issues.map((i) => i.id),
    clarificationIds: [],
    revisionIds: [],
    assessment: null,
  }
}

export function startDocumentAnalysisMock(documentId) {
  const docId = Number(documentId)
  const document = getDocumentMock(docId)
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
    createdAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    startedAt: null,
    completedAt: null,
    result: null,
    error: null,
  }
  store.analyses.push(analysis)
  store.pendingCompletions[analysis.id] = Date.now() + ANALYSIS_DELAY_MS
  return { analysis: clone(analysis) }
}

export function retryAnalysisMock(analysisId) {
  const original = getAnalysisMock(analysisId)
  if (!original) {
    return { error: 'NOT_FOUND' }
  }
  if (original.status !== AnalysisStatus.FAILED) {
    return { error: 'NOT_RETRYABLE' }
  }

  const existing = store.analyses.find((a) => a.retryOfAnalysisId === original.id)
  if (existing) {
    return { analysis: clone(existing) }
  }

  const analysis = {
    ...clone(original),
    id: store.nextAnalysisId++,
    status: AnalysisStatus.COMPLETED,
    retryOfAnalysisId: original.id,
    createdAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    startedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    completedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    error: null,
  }
  store.analyses.push(analysis)
  return { analysis }
}

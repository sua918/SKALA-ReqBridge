import { AnalysisKind, AnalysisStatus } from '@/types/api'
import {
  seedAnalysisCompleted,
  seedDocument,
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
    nextProjectId: 2,
    nextDocumentId: 102,
    nextAnalysisId: 302,
  }
}

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

export function listAnalysesMock(documentId, kind) {
  let items = store.analyses.filter((a) => a.documentId === Number(documentId))
  if (kind) {
    items = items.filter((a) => a.kind === kind)
  }
  items = [...items].sort((a, b) => b.id - a.id)
  return { items: clone(items) }
}

export function getAnalysisMock(analysisId) {
  return store.analyses.find((a) => a.id === Number(analysisId)) ?? null
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

  //접수 직후 COMPLETED로 두어 polling 데모·목록 조회가 바로 됨
  const analysis = {
    id: store.nextAnalysisId++,
    kind: AnalysisKind.DOCUMENT,
    status: AnalysisStatus.COMPLETED,
    documentId: docId,
    requirementId: null,
    clarificationId: null,
    inputContentVersion: null,
    retryOfAnalysisId: null,
    createdAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    startedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    completedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    result: {
      requirementIds: docId === 101 ? [401] : [],
      issueIds: docId === 101 ? [501, 502] : [],
      clarificationIds: docId === 101 ? [601, 602] : [],
      revisionIds: [],
      assessment: null,
    },
    error: null,
  }
  store.analyses.push(analysis)
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

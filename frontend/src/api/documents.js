/**
 * 문서 API (Spec §5.4~5.6).
 *
 * 주 사용: B (TEXT 등록·목록·원문 조회).
 * PDF upload는 이후 추가. C는 문서 제목/breadcrumb용 getDocument 정도.
 */
import { api, unwrap } from '@/api/client'
import { useMock } from '@/api/config'
import { mockReject, notFound } from '@/api/mockError'
import { ApiErrorCode, DocumentSourceType } from '@/types/api'
import {
  createTextDocumentMock,
  getDocumentMock,
  getProjectMock,
  listDocumentsMock,
} from '@/mocks/store.js'

/** GET /projects/{projectId}/documents — 목록 (content 제외). */
export async function listDocuments(projectId) {
  if (useMock) {
    if (!getProjectMock(projectId)) {
      return notFound('프로젝트')
    }
    return listDocumentsMock(projectId)
  }
  return unwrap(await api.get(`/projects/${projectId}/documents`))
}

/** GET /documents/{documentId} — 원문 포함 상세. */
export async function getDocument(documentId) {
  if (useMock) {
    const document = getDocumentMock(documentId)
    if (!document) {
      return notFound('문서')
    }
    return document
  }
  return unwrap(await api.get(`/documents/${documentId}`))
}

/**
 * POST /projects/{projectId}/documents — TEXT 문서 등록.
 * sourceType은 TEXT만 허용 (Spec §5.4).
 */
export async function createTextDocument(projectId, { title, content }) {
  const body = {
    title,
    sourceType: DocumentSourceType.TEXT,
    content,
  }

  if (useMock) {
    if (!getProjectMock(projectId)) {
      return notFound('프로젝트')
    }
    if (!title?.trim() || !content?.trim()) {
      return mockReject(400, ApiErrorCode.VALIDATION_ERROR, '요청 값을 확인해주세요.', [
        { field: !title?.trim() ? 'title' : 'content', message: '필수 값을 입력해주세요.' },
      ])
    }
    return createTextDocumentMock(projectId, body)
  }

  return unwrap(await api.post(`/projects/${projectId}/documents`, body))
}

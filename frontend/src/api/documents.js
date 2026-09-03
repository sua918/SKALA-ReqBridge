/**
 * 문서 API (Spec §5.4~5.6).
 *
 * 주 사용: B (TEXT 등록·PDF 업로드·목록·원문 조회).
 * C는 문서 제목/breadcrumb용 getDocument 정도.
 */
import { api, unwrap, validationError } from '@/api/client'
import { useMock } from '@/api/config'
import { mockReject, notFound } from '@/api/mockError'
import { ApiErrorCode, DocumentSourceType } from '@/types/api'
import {
  createTextDocumentMock,
  createUploadedDocumentMock,
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

/** Spec §5.4.1 — 비어 있지 않은 PDF 한 개, 최대 10MB. */
const PDF_MAX_BYTES = 10 * 1024 * 1024
const TITLE_MAX_LENGTH = 200

function validateUpload(title, file) {
  if (!title?.trim() || [...title.trim()].length > TITLE_MAX_LENGTH) {
    return { field: 'title', message: '제목을 200자 이내로 입력해주세요.' }
  }
  if (!file || file.size === 0) {
    return { field: 'file', message: 'PDF 파일을 선택해주세요.' }
  }
  const isPdf =
    file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) {
    return { field: 'file', message: 'PDF 파일만 등록할 수 있습니다.' }
  }
  if (file.size > PDF_MAX_BYTES) {
    return { field: 'file', message: '파일 크기는 10MB를 넘을 수 없습니다.' }
  }
  return null
}

/**
 * POST /projects/{projectId}/documents/upload — PDF 문서 등록 (Spec §5.4.1).
 * multipart/form-data. sourceType은 보내지 않고 서버가 FILE로 정한다.
 */
export async function uploadPdfDocument(projectId, { title, file }) {
  const invalid = validateUpload(title, file)
  if (invalid) {
    return validationError([invalid])
  }

  if (useMock) {
    if (!getProjectMock(projectId)) {
      return notFound('프로젝트')
    }
    return createUploadedDocumentMock(projectId, { title: title.trim(), file })
  }

  const form = new FormData()
  form.append('title', title.trim())
  form.append('file', file)
  const response = await api.post(`/projects/${projectId}/documents/upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return unwrap(response)
}

/**
 * 분석(Analysis) API (Spec §5.9~5.12).
 *
 * B: 문서 분석 시작·polling·이력·실패 재시도.
 * C: 답변/재생성 후 ANSWER·REVISION 작업 polling·재시도에 동일 함수 사용.
 */
import { api, unwrap } from '@/api/client'
import { useMock } from '@/api/config'
import { mockReject, notFound } from '@/api/mockError'
import { ApiErrorCode } from '@/types/api'
import {
  getAnalysisMock,
  getDocumentMock,
  listAnalysesMock,
  retryAnalysisMock,
  startDocumentAnalysisMock,
} from '@/mocks/store.js'

/** POST /documents/{documentId}/analyses — 최초 문서 분석 접수. */
export async function startDocumentAnalysis(documentId) {
  if (useMock) {
    const result = startDocumentAnalysisMock(documentId)
    if (result.error === 'NOT_FOUND') {
      return notFound('문서')
    }
    if (result.error === 'IN_PROGRESS') {
      return mockReject(
        409,
        ApiErrorCode.ANALYSIS_IN_PROGRESS,
        '이미 진행 중인 분석이 있습니다.',
      )
    }
    if (result.error === 'ALREADY_ANALYZED') {
      return mockReject(
        409,
        ApiErrorCode.DOCUMENT_ALREADY_ANALYZED,
        '이미 분석이 완료된 문서입니다.',
      )
    }
    return result.analysis
  }
  return unwrap(await api.post(`/documents/${documentId}/analyses`))
}

/**
 * GET /documents/{documentId}/analyses — 이력 (F5 복구·polling용).
 * @param {string} [kind] DOCUMENT | ANSWER | REVISION
 */
export async function listDocumentAnalyses(documentId, kind) {
  if (useMock) {
    if (!getDocumentMock(documentId)) {
      return notFound('문서')
    }
    return listAnalysesMock(documentId, kind)
  }
  const response = await api.get(`/documents/${documentId}/analyses`, {
    params: kind ? { kind } : undefined,
  })
  return unwrap(response)
}

/** GET /analyses/{analysisId} — 상태·결과·오류 (HTTP 200 + status 필드). */
export async function getAnalysis(analysisId) {
  if (useMock) {
    const analysis = getAnalysisMock(analysisId)
    if (!analysis) {
      return notFound('분석')
    }
    return analysis
  }
  return unwrap(await api.get(`/analyses/${analysisId}`))
}

/** POST /analyses/{analysisId}/retries — FAILED 작업 재시도. */
export async function retryAnalysis(analysisId) {
  if (useMock) {
    const result = retryAnalysisMock(analysisId)
    if (result.error === 'NOT_FOUND') {
      return notFound('분석')
    }
    if (result.error === 'NOT_RETRYABLE') {
      return mockReject(
        409,
        ApiErrorCode.ANALYSIS_NOT_RETRYABLE,
        '재시도할 수 없는 분석입니다.',
      )
    }
    return result.analysis
  }
  return unwrap(await api.post(`/analyses/${analysisId}/retries`))
}

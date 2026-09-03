/**
 * 요구사항 API (Spec §5.7~5.8).
 *
 * B: 문서 화면 요구사항 목록.
 * C: workflow 진입·breadcrumb용 getRequirement (documentId).
 * 이슈(ambiguity) 본문은 Spec상 workflow 쪽 — 목록 API에 없음.
 */
import { api, unwrap } from '@/api/client'
import { useMock } from '@/api/config'
import { notFound } from '@/api/mockError'
import {
  getDocumentMock,
  getRequirementMock,
  listIssuesByRequirementMock,
  listRequirementsMock,
} from '@/mocks/store.js'

/** GET /documents/{documentId}/requirements — 목록 (분석 전이면 items=[]). */
export async function listRequirements(documentId) {
  if (useMock) {
    if (!getDocumentMock(documentId)) {
      return notFound('문서')
    }
    return listRequirementsMock(documentId)
  }
  return unwrap(await api.get(`/documents/${documentId}/requirements`))
}

/** GET /requirements/{requirementId} — 기본 상세·확정본 필드. */
export async function getRequirement(requirementId) {
  if (useMock) {
    const requirement = getRequirementMock(requirementId)
    if (!requirement) {
      return notFound('요구사항')
    }
    return requirement
  }
  return unwrap(await api.get(`/requirements/${requirementId}`))
}

/**
 * Spec 목록 API에는 issues가 없다.
 * B Ambiguity 표시용 Mock fixture만 제공. (실BE·C는 workflow 사용)
 */
export async function listMockIssuesByRequirement(requirementId) {
  if (!useMock) {
    return { items: [] }
  }
  return listIssuesByRequirementMock(requirementId)
}

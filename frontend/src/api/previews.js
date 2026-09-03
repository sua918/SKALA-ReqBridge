/**
 * Preview API (Spec 5.17~5.18) — P2, C 담당.
 *
 * 인계서 2절대로 workflow와 별도 파일로 둔다. 두 Preview는 워크플로우를
 * 진행시키지 않는 읽기 전용 조회라 성격이 다르다.
 *
 * 다운로드는 제공하지 않는다 (Spec 8절). 화면에서 보여주기만 한다.
 */
import { api, unwrap } from '@/api/client'
import { useMock } from '@/api/config'
import { mockReject, notFound } from '@/api/mockError'
import { ApiErrorCode } from '@/types/api'
import { getCustomerPreviewMock, getDeveloperPreviewMock } from '@/mocks/store.js'

/**
 * Spec 6.4 — 확정본과 승인 수정안이 어긋나면 서로 다른 버전을 섞지 않고
 * 409로 끊는다. 화면은 이 코드를 「지금 다시 조회해야 한다」로 읽는다.
 */
function previewVersionConflict() {
  return mockReject(
    409,
    ApiErrorCode.PREVIEW_VERSION_CONFLICT,
    '확정본과 승인된 수정안이 일치하지 않습니다. 잠시 후 다시 조회해주세요.',
  )
}

/** GET /documents/{documentId}/previews/customer — 지금 답이 필요한 질문만. */
export async function getCustomerPreview(documentId) {
  if (useMock) {
    const preview = getCustomerPreviewMock(documentId)
    if (!preview) {
      return notFound('문서')
    }
    if (preview.error === 'PREVIEW_VERSION_CONFLICT') {
      return previewVersionConflict()
    }
    return preview
  }
  return unwrap(await api.get(`/documents/${documentId}/previews/customer`))
}

/** GET /documents/{documentId}/previews/developer — 확정본과 근거, 미확정 이력. */
export async function getDeveloperPreview(documentId) {
  if (useMock) {
    const preview = getDeveloperPreviewMock(documentId)
    if (!preview) {
      return notFound('문서')
    }
    if (preview.error === 'PREVIEW_VERSION_CONFLICT') {
      return previewVersionConflict()
    }
    return preview
  }
  return unwrap(await api.get(`/documents/${documentId}/previews/developer`))
}

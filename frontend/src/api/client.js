import axios from 'axios'
import { ApiErrorCode } from '@/types/api'

/**
 * Spec 0.4.0 §2 공통 HTTP 클라이언트. (B·C 공용)
 *
 * 성공: `{ data: ... }` — `unwrap()`으로 내부 payload만 꺼낸다.
 * 오류: `{ error: { code, message, fieldErrors } }` — `error.apiError`에 넣는다.
 *
 * contentVersion: 프론트에서 +1 하지 않는다. 마지막 API 응답의
 * contentVersion만 다음 요청의 expectedContentVersion으로 보낸다.
 * 409 CONTENT_VERSION_CONFLICT면 workflow/requirement를 다시 조회하고
 * 사용자 입력은 유지한다. (화면 연동은 C / workflow)
 *
 * src/api/ 전체: 화면은 여기 export 함수만 호출. useMock이면 mocks, 아니면 실BE.
 * 수정 시 상대방 PR review (합의).
 */

/** axios 인스턴스. baseURL `/api` (Vite proxy → BE). */
export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const payload = error.response?.data?.error
    if (payload) {
      error.apiError = {
        code: payload.code,
        message: payload.message,
        fieldErrors: payload.fieldErrors ?? [],
        status: error.response.status,
      }
    }
    return Promise.reject(error)
  },
)

/** Spec 성공 래퍼 `{ data }`의 안쪽 payload만 반환. */
export function unwrap(response) {
  return response.data?.data
}

/**
 * 서버에 보내기 전 걸러낸 입력 오류를 Spec §7과 같은 형태로 만든다.
 * 화면의 오류 처리 경로를 HTTP 400과 동일하게 유지하기 위함.
 */
export function validationError(fieldErrors, message = '요청 값을 확인해주세요.') {
  const error = new Error(message)
  error.apiError = {
    code: ApiErrorCode.VALIDATION_ERROR,
    message,
    fieldErrors,
    status: 400,
  }
  return Promise.reject(error)
}

/** 409 CONTENT_VERSION_CONFLICT 여부 (C workflow에서 사용). */
export function isContentVersionConflict(error) {
  return error.apiError?.code === ApiErrorCode.CONTENT_VERSION_CONFLICT
}

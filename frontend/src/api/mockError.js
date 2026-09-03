/**
 * Mock 전용 오류 생성.
 * 실BE와 같이 error.apiError / Spec `{ error }` 형태를 맞춰,
 * 화면의 에러 처리 코드를 Mock·실연동에서 동일하게 쓰게 한다.
 */
import { ApiErrorCode } from '@/types/api'

/** Spec §7 형식의 실패 Promise. */
export function mockReject(status, code, message, fieldErrors = []) {
  const error = new Error(message)
  error.response = {
    status,
    data: {
      error: { code, message, fieldErrors },
    },
  }
  error.apiError = {
    code,
    message,
    fieldErrors,
    status,
  }
  return Promise.reject(error)
}

/** 404 RESOURCE_NOT_FOUND. */
export function notFound(resource = '리소스') {
  return mockReject(
    404,
    ApiErrorCode.RESOURCE_NOT_FOUND,
    `${resource}를 찾을 수 없습니다.`,
  )
}

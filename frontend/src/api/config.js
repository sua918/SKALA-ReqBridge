/**
 * Mock 사용 여부 스위치.
 *
 * - false(기본): 실BE HTTP 호출 (dev 서버는 /api를 8080으로 프록시)
 * - true: mocks/store 예비 데이터로 API 함수가 응답 (`npm run dev:mock`)
 *
 * 값은 vite.config.js에서 mode 또는 VITE_USE_MOCK으로 결정한다.
 */
export const useMock = import.meta.env.VITE_USE_MOCK === 'true'

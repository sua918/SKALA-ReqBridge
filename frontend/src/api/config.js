/**
 * Mock 사용 여부 스위치.
 *
 * - true(기본): mocks/store 예비 데이터로 API 함수가 응답
 * - false: 실BE HTTP 호출 (VITE_USE_MOCK=false)
 *
 * BE P1이 아직 불완전할 때 FE 화면·함수를 먼저 붙이기 위함.
 */
export const useMock = import.meta.env.VITE_USE_MOCK !== 'false'

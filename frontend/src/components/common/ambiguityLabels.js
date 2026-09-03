/**
 * AmbiguityType → 화면 라벨.
 * 문제 상세는 workflow 응답에만 있으므로 요구사항 화면(C)에서 사용한다.
 */
export const AMBIGUITY_TYPE_LABELS = Object.freeze({
  QUANTITY_MISSING: '수량 누락',
  PERFORMANCE_MISSING: '성능 기준 누락',
  CONDITION_MISSING: '조건 누락',
  ACTOR_MISSING: '행위자 누락',
  SUCCESS_CRITERIA_MISSING: '성공 기준 누락',
  TERM_AMBIGUOUS: '용어 모호',
  EXCEPTION_MISSING: '예외 누락',
})

export function resolveAmbiguityTypeLabel(type) {
  return AMBIGUITY_TYPE_LABELS[type] ?? type
}

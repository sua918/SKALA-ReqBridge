/**
 * AmbiguityType → 화면 라벨 (B 목록 표시용).
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

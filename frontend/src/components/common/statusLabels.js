/**
 * StatusBadge용 표시 라벨 (Spec enum → 한글).
 * value 문자열은 types/api.js와 1:1.
 */
export const STATUS_LABELS = Object.freeze({
  requirement: {
    EXTRACTED: '추출됨',
    AMBIGUOUS: '불명확',
    CLARIFYING: '확인 중',
    IN_REVIEW: '검토 중',
    CONFIRMED: '확정',
  },
  analysis: {
    PENDING: '대기',
    PROCESSING: '분석 중',
    COMPLETED: '완료',
    FAILED: '실패',
  },
  clarification: {
    WAITING: '답변 대기',
    ANSWERED: '답변됨',
    RESOLVED: '해결',
  },
  issue: {
    OPEN: '미해결',
    RESOLVED: '해결',
  },
  revision: {
    PROPOSED: '제안',
    APPROVED: '승인',
    REJECTED: '거절',
  },
})

/** kind별 톤 (CSS modifier). */
export const STATUS_TONES = Object.freeze({
  requirement: {
    EXTRACTED: 'neutral',
    AMBIGUOUS: 'warn',
    CLARIFYING: 'info',
    IN_REVIEW: 'info',
    CONFIRMED: 'success',
  },
  analysis: {
    PENDING: 'neutral',
    PROCESSING: 'info',
    COMPLETED: 'success',
    FAILED: 'danger',
  },
  clarification: {
    WAITING: 'warn',
    ANSWERED: 'info',
    RESOLVED: 'success',
  },
  issue: {
    OPEN: 'warn',
    RESOLVED: 'success',
  },
  revision: {
    PROPOSED: 'info',
    APPROVED: 'success',
    REJECTED: 'danger',
  },
})

export function resolveStatusLabel(kind, value) {
  return STATUS_LABELS[kind]?.[value] ?? value
}

export function resolveStatusTone(kind, value) {
  return STATUS_TONES[kind]?.[value] ?? 'neutral'
}

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
    //확인 중(고객 답변 대기)과 검토 중(수정안 검토)은 명확도 여정에서 다른 단계다.
    //둘 다 info면 목록에서 눈으로 가릴 수 없어, 검토 단계를 보라로 뗀다.
    IN_REVIEW: 'purple',
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
    //수정안 PROPOSED와 요구사항 IN_REVIEW는 같은 순간이다 (Spec 6.3: 새 수정안
    //제안 -> IN_REVIEW). 한 화면에 나란히 뜨는데 색이 갈리면 파랑/보라 구분이
    //도로 흐려진다. 색은 「누가 다음 행동을 하는가」로 읽힌다 —
    //노랑=사람의 입력, 파랑=기계, 보라=사람의 판단, 초록=완료, 빨강=실패.
    PROPOSED: 'purple',
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

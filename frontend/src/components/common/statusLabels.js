/**
 * StatusBadge용 표시 라벨 (Spec enum → 한글).
 * value 문자열은 types/api.js와 1:1.
 */
export const STATUS_LABELS = Object.freeze({
  requirement: {
    //「불명확」이 무엇을 뜻하는지, 「검토 중」에 누가 무엇을 해야 하는지는 상태 이름만
    //봐서는 알 수 없었다. 상태가 곧 「다음에 할 일」을 말하도록 바꾼다
    //(프론트엔드-추가-요청사항 4.2).
    EXTRACTED: '추출 완료',
    AMBIGUOUS: '불명확성 발견',
    CLARIFYING: '보완 답변 필요',
    IN_REVIEW: '수정안 승인 대기',
    CONFIRMED: '확정 완료',
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

/**
 * kind별 톤 (CSS modifier).
 *
 * 색은 「누가 다음 행동을 하는가」로 읽힌다.
 *
 *   호박 = 사람이 손을 대야 한다   불명확성 발견 · 보완 답변 필요 · 미해결 · 답변 대기
 *   보라 = 사람이 판단해야 한다     수정안 승인 대기 · 제안
 *   무채 = 기다리면 된다            추출 완료 · 대기 · 분석 중 · 답변됨
 *   초록 = 끝났다                   확정 완료 · 해결 · 승인 · 분석 완료
 *   빨강 = 잘못됐다                 실패 · 거절
 *
 * 한때 이 색들을 전부 걷어내고 초록·빨강만 남긴 적이 있다. 그때는 배지가 알약이라
 * 한 줄에 둘만 놓여도 화면이 물들었기 때문이다. 알약을 벗기고 점 하나와 글자만
 * 남긴 지금은, 색이 판을 덮지 않고 글자에만 실린다. 그래서 색을 되살려도 조용하다.
 */
export const STATUS_TONES = Object.freeze({
  requirement: {
    EXTRACTED: 'neutral',
    AMBIGUOUS: 'warn',
    CLARIFYING: 'warn',
    //확인 중(고객 답변 대기)과 승인 대기(수정안 판단)는 할 일이 다르다.
    //둘 다 호박이면 목록에서 무엇을 먼저 열어야 할지 가릴 수 없다.
    IN_REVIEW: 'purple',
    CONFIRMED: 'success',
  },
  analysis: {
    PENDING: 'neutral',
    PROCESSING: 'neutral',
    COMPLETED: 'success',
    FAILED: 'danger',
  },
  clarification: {
    WAITING: 'warn',
    ANSWERED: 'neutral',
    RESOLVED: 'success',
  },
  issue: {
    OPEN: 'warn',
    RESOLVED: 'success',
  },
  revision: {
    //수정안 제안과 요구사항 승인 대기는 같은 순간이다 (Spec 6.3). 색이 갈리면 안 된다.
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

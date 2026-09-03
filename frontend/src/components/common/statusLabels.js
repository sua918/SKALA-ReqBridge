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
 * **색은 끝났거나 잘못된 것에만 쓴다.**
 *
 * 예전에는 상태마다 색이 있었다. 그런데 「불명확성 발견」·「보완 답변 필요」·「미해결」·
 * 「답변 대기」는 문서를 검토하는 동안 거의 항상 켜져 있는 상태다. 늘 켜져 있는 것에
 * 색을 주면 화면이 노랗게 물들고, 그 순간 색은 「봐야 할 것」을 가리키지 못한다.
 * 전부 강조하면 아무것도 강조되지 않는다.
 *
 * 그래서 진행 중인 일상 상태는 전부 무채색으로 두고, 색은 두 가지에만 남긴다.
 *
 *   초록 = 끝났다      확정 완료 · 해결 · 승인 · 분석 완료
 *   빨강 = 잘못됐다    실패 · 거절
 *
 * 이러면 목록을 훑을 때 색이 보이는 자리가 곧 「결론이 난 자리」다.
 * 지금 할 일은 색이 아니라 행의 동작 문구(「불명확성 확인」)와 입력칸이 말한다.
 */
export const STATUS_TONES = Object.freeze({
  requirement: {
    EXTRACTED: 'neutral',
    AMBIGUOUS: 'neutral',
    CLARIFYING: 'neutral',
    //수정안 승인 대기도 무채색이다. 사람의 판단을 기다리는 자리라 색을 주고 싶지만,
    //그 판단은 화면에 승인·거절 단추로 이미 나와 있다. 배지까지 색을 쓰면 규칙이
    //「끝났거나 잘못된 것」에서 「중요해 보이는 것」으로 흐려진다.
    IN_REVIEW: 'neutral',
    CONFIRMED: 'success',
  },
  analysis: {
    PENDING: 'neutral',
    PROCESSING: 'neutral',
    COMPLETED: 'success',
    FAILED: 'danger',
  },
  clarification: {
    WAITING: 'neutral',
    ANSWERED: 'neutral',
    RESOLVED: 'success',
  },
  issue: {
    OPEN: 'neutral',
    RESOLVED: 'success',
  },
  revision: {
    PROPOSED: 'neutral',
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

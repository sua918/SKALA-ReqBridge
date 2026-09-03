<script setup>
/**
 * 상태 뱃지 (B·C 공용).
 * kind로 enum 종류를 구분하고, value는 Spec 문자열 그대로.
 */
import { computed } from 'vue'
import { resolveStatusLabel, resolveStatusTone } from '@/components/common/statusLabels.js'

const props = defineProps({
  /** requirement | analysis | clarification | issue | revision */
  kind: {
    type: String,
    required: true,
    validator: (v) =>
      ['requirement', 'analysis', 'clarification', 'issue', 'revision'].includes(v),
  },
  /** Spec enum 값 (예: CLARIFYING, FAILED) */
  value: {
    type: String,
    required: true,
  },
})

const label = computed(() => resolveStatusLabel(props.kind, props.value))
const tone = computed(() => resolveStatusTone(props.kind, props.value))
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`" :title="value">
    {{ label }}
  </span>
</template>

<style scoped>
/* 색·크기를 전부 디자인 토큰에서 가져온다. props와 tone 이름
   (neutral/info/warn/success/danger/purple)은 그대로다.

   알약을 벗겼다. 배지에 바탕과 테두리를 주면 한 줄에 둘만 놓여도 「누를 수 있는 것」이
   여럿인 화면처럼 보인다. 상태는 누르는 것이 아니다.
   상태는 글자색으로 말하고, 색을 눈이 먼저 잡도록 앞에 작은 점 하나만 둔다.
   회색 「추출 완료」도 점이 있으면 흰 카드에서 묻히지 않는다. */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  font-family: var(--font-body);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: -.01em;
  line-height: 1.4;
  white-space: nowrap;
}

/* 점은 글자색을 그대로 쓴다 — 색이 두 벌로 갈리지 않는다. */
.status-badge::before {
  content: '';
  flex: 0 0 auto;
  width: 7px; height: 7px;
  border-radius: 50%;
  background: currentColor;
}

/* 진행 중인 일상 상태 — 색은 쓰지 않지만 흐리지도 않다.
   회색 #44484F는 옆의 설명글과 무게가 같아 「미해결」이 눈에 안 걸렸다.
   본문보다 진한 먹으로 올려, 색 없이도 또렷한 글자로 선다.
   점은 글자보다 한 톤 옅게 두어 글자를 가리지 않는다. */
.status-badge--neutral { color: var(--fg-700); }
.status-badge--neutral::before { background: var(--fg-400); }

/* 분석 중 · 답변됨 — 기계가 일하는 중이거나 사람의 입력이 들어온 뒤 */
.status-badge--info { color: var(--blue-tx); }

/* 수정안 승인 대기 — 수정안이 나와 사람의 판단을 기다리는 단계.
   같은 「진행 중」이지만 보완 답변 필요(고객 답변 대기)와는 할 일이 다르다. */
.status-badge--purple { color: var(--purple-tx); }

/* 불명확성 발견 · 답변 대기 · 미해결 — 사람의 손이 필요한 상태 */
.status-badge--warn { color: var(--amber-so); }

/* 확정 완료 · 완료 · 해결 · 승인 — 끝난 상태 */
.status-badge--success { color: var(--green-tx); }

/* 실패 · 거절 */
.status-badge--danger { color: var(--red-tx); }
</style>

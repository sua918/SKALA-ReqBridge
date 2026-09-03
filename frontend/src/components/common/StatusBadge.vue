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
/* 색·크기·모서리를 전부 디자인 토큰에서 가져온다.
   예전에는 Tailwind 계열 색(#fef3c7 등)을 직접 박아 두어, 같은 화면에서
   배지의 빨강(#fee2e2)과 거절 사유 판의 빨강(--red-bg #FFE6E4)이 미묘하게
   달랐다. 한 화면에 색 체계가 두 벌 있으면 어느 쪽이 기준인지 알 수 없다.

   props와 tone 이름(neutral/info/warn/success/danger)은 그대로다. */
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px 4px;
  /* 검은 윤곽 1px. 색만으로는 회색 「추출됨」이 흰 카드에 묻히고, 배지 하나하나가
     「물건」으로 안 잡힌다. 굵기를 올리면(1.5·2px) 배지가 버튼처럼 보여 못 누르는
     것을 누를 것처럼 만든다. 1px이면 윤곽은 서고 무게는 안 는다.
     이 화면은 이미 기둥 구분선과 제목이 검정이라 윤곽선이 이물질이 아니다. */
  border: 1px solid var(--fg-950);
  border-radius: 3px;
  font-family: var(--font-body);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: -.01em;
  line-height: 1.4;
  white-space: nowrap;
}

/* 추출됨 · 대기 — 아직 아무 일도 일어나지 않은 상태 */
.status-badge--neutral {
  color: var(--gray-tx);
  background: var(--gray-bg);
}

/* 확인 중 · 검토 중 · 처리 중 · 답변됨 · 제안 — 진행 중 */
.status-badge--info {
  color: var(--blue-tx);
  background: var(--blue-bg);
}

/* 검토 중 — 수정안이 나와 사람의 판단을 기다리는 단계.
   같은 「진행 중」이지만 확인 중(고객 답변 대기)과는 할 일이 다르다. */
.status-badge--purple {
  color: var(--purple-tx);
  background: var(--purple-bg);
}

/* 불명확 · 답변 대기 · 미해결 — 사람의 손이 필요한 상태 */
.status-badge--warn {
  color: var(--amber-tx);
  background: var(--amber-bg);
}

/* 확정 · 완료 · 해결 · 승인 — 끝난 상태 */
.status-badge--success {
  color: var(--green-tx);
  background: var(--green-bg);
}

/* 실패 · 거절 */
.status-badge--danger {
  color: var(--red-tx);
  background: var(--red-bg);
}
</style>

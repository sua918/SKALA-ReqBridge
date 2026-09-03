<script setup>
import { computed } from 'vue'
import { RequirementStatus } from '@/types/api'
import { resolveStatusLabel } from '@/components/common/statusLabels.js'

/**
 * 처리 단계의 순서. Spec 4.1의 상태 전이를 그대로 따른다.
 * 라벨은 공용 statusLabels.js에서 가져와 배지와 같은 말을 쓰게 한다.
 */
const JOURNEY = [
  RequirementStatus.EXTRACTED,
  RequirementStatus.AMBIGUOUS,
  RequirementStatus.CLARIFYING,
  RequirementStatus.IN_REVIEW,
  RequirementStatus.CONFIRMED,
].map((key) => [key, resolveStatusLabel('requirement', key)])

/**
 * 처리 단계 — 이 도구의 대표 그래픽. 목록·상세·Preview 어디서나 같은 의미다.
 *
 * **가로 막대에서 세로 스텝퍼로 바꿨다.** 가로로 눕히면 다섯 이름을 한 줄에 늘어놓아야
 * 하는데, 상태 이름이 「검토 중」에서 「수정안 승인 대기」로 길어진 뒤로는 272px 칸에서
 * 이름끼리 겹쳐 어느 것도 못 읽었다. 현재 단계만 적는 식으로 버텨 봤지만, 그러면
 * 「다음에 무엇이 오는가」를 알 수 없다 — 이 화면에서 가장 알고 싶은 것이 그것이다.
 *
 * 세로로 세우면 이름마다 한 줄을 온전히 쓰므로 길이 제약이 사라진다. 참조 기둥은
 * 어차피 위아래로 긴 칸이라 세로가 자리에도 맞는다.
 *
 * 지나온 단계는 번호 대신 체크로 바꾼다 — 번호는 「해야 할 순서」를, 체크는 「끝났음」을
 * 말한다. 한 눈에 어디까지 왔는지가 두 기호의 경계로 드러난다.
 */
const props = defineProps({
  status: { type: String, required: true },
  /** 가로였던 시절의 상한 폭. 세로에서는 칸을 그대로 쓰므로 상한으로만 남긴다. */
  width: { type: Number, default: 380 },
  labels: { type: Boolean, default: true },
})

const idx = computed(() => Math.max(0, JOURNEY.findIndex(([k]) => k === props.status)))

const nodes = computed(() =>
  JOURNEY.map(([key, label], i) => ({
    key,
    label,
    number: i + 1,
    done: i < idx.value,
    current: i === idx.value,
  })),
)
</script>

<template>
  <div class="cj" :style="{ maxWidth: width + 'px' }">
    <div
      v-for="n in nodes" :key="n.key"
      class="step" :class="{ done: n.done, current: n.current }"
    >
      <span class="marker">{{ n.done ? '✓' : n.number }}</span>
      <span v-if="labels" class="copy"><strong>{{ n.label }}</strong></span>
      <span v-if="n.current" class="now">현재</span>
    </div>
  </div>
</template>

<style scoped>
.cj { width: 100%; display: flex; flex-direction: column; }

.step {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 42px;
  color: var(--fg-400);
}

/* 단계를 잇는 세로선. 원 아래에서 시작해 다음 원 앞에서 끝난다 —
   원을 관통하면 번호가 선에 꿰인 것처럼 보인다. */
.step:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 13px;
  top: 34px;
  bottom: -8px;
  width: 2px;
  background: var(--bg-300);
}

.marker {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  box-sizing: border-box;
  border: 1px solid var(--bg-300);
  border-radius: 50%;
  background: var(--bg-0);
  color: var(--fg-400);
  font-size: 12px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.copy { min-width: 0; }
.copy strong {
  font-size: var(--fs-sm);
  font-weight: 680;
  line-height: 1.4;
  word-break: keep-all;
}

/* 지나온 단계 — 옅은 파랑으로 「끝났음」만 말하고 시선은 뺏지 않는다. */
.step.done .marker { border-color: var(--primary-300); background: var(--primary-50); color: var(--primary-700); }
.step.done .copy strong { color: var(--fg-800); }

/* 현재 단계 — 이 칸에서 유일하게 채운 원이다. */
.step.current { color: var(--primary-800); }
.step.current .marker { border-color: var(--primary-600); background: var(--primary-600); color: var(--bg-0); }
.step.current .copy strong { font-weight: 800; }

.now {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--primary-100);
  color: var(--primary-800);
  font-size: var(--fs-nano);
  font-weight: 750;
  white-space: nowrap;
}
</style>

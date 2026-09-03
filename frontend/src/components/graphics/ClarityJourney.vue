<script setup>
import { computed } from 'vue'
import { RequirementStatus } from '@/types/api'
import { resolveStatusLabel } from '@/components/common/statusLabels.js'

/**
 * 명확도 여정의 순서. Spec 4.1의 상태 전이를 그대로 따른다.
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
 * 명확도 여정 — 이 도구의 대표 그래픽. 목록·상세·Preview 어디서나 같은 의미다.
 *
 * SVG로 그리던 것을 HTML/CSS로 옮겼다. 이유는 폭이다.
 * SVG는 `width`를 픽셀로 못 박아야 하는데, 호출부가 넘기던 380·396px이 실제로 놓이는
 * 칸(요구사항 워크플로우 왼쪽 기둥 ≈272px)보다 넓어서 그래픽이 칸 밖으로 삐져나갔다.
 * 첫 라벨(「추출됨」)은 왼쪽에서 잘리고 마지막 라벨은 옆 칸의 괘선까지 넘어갔다.
 *
 * HTML로 그리면 점 위치를 `calc()`로 폭에 맞춰 잡을 수 있고, 라벨이 진짜 텍스트라
 * 본문과 같은 서체·같은 크기(13px)로 렌더된다. SVG처럼 통째로 축소돼 글씨만 작아지는 일이 없다.
 *
 * `--pad`는 양 끝 점이 칸 가장자리에 붙지 않게 띄우는 값이다. 가장 긴 라벨(「검토 중」)의
 * 절반보다 커야 첫·끝 라벨이 잘리지 않는다.
 */
const props = defineProps({
  status: { type: String, required: true },
  /** 넘겨받은 값은 상한으로만 쓴다 — 칸이 좁으면 칸을 따른다. */
  width: { type: Number, default: 380 },
  labels: { type: Boolean, default: true },
})

const idx = computed(() => Math.max(0, JOURNEY.findIndex(([k]) => k === props.status)))
const last = JOURNEY.length - 1

/** 점 위치. 좌우 `--pad`를 뺀 나머지를 4등분한다. */
const at = (i) => `calc(var(--pad) + (100% - var(--pad) * 2) * ${(i / last).toFixed(6)})`

const nodes = computed(() =>
  JOURNEY.map(([key, label], i) => ({
    key, label, left: at(i), done: i < idx.value, current: i === idx.value,
  })),
)

/** 채움은 전체 그라디언트를 잘라서 보여준다 — 진행률에 따라 색이 변하면 안 된다. */
const clip = computed(() => `inset(0 ${(100 - (idx.value / last) * 100).toFixed(4)}% 0 0)`)
</script>

<template>
  <div class="cj" :style="{ maxWidth: width + 'px' }">
    <div class="track">
      <div class="rail" />
      <div class="railwrap"><div class="fill" :style="{ clipPath: clip }" /></div>
      <span
        v-for="n in nodes" :key="n.key"
        class="dot" :class="{ done: n.done, cur: n.current }" :style="{ left: n.left }"
      />
    </div>
    <div v-if="labels" class="labels">
      <span
        v-for="n in nodes" :key="n.key"
        class="lb" :class="{ done: n.done, cur: n.current }" :style="{ left: n.left }"
      >{{ n.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.cj { --pad: 30px; width: 100%; }

.track { position: relative; height: 26px; }
.rail, .railwrap {
  position: absolute; left: var(--pad); right: var(--pad); top: 12px; height: 2px; border-radius: 1px;
}
.rail { background: var(--bg-300); }
.railwrap { overflow: hidden; }
.fill {
  width: 100%; height: 100%;
  background: linear-gradient(90deg, var(--logo-blue), var(--logo-lav), var(--logo-coral));
  transition: clip-path .6s var(--ease);
}

.dot {
  position: absolute; top: 13px; transform: translate(-50%, -50%);
  width: 9px; height: 9px; border-radius: 50%;
  background: var(--bg-50); border: 1.4px solid var(--bg-300);
  box-sizing: border-box;
}
.dot.done {
  width: 10px; height: 10px; border: 0;
  background: linear-gradient(90deg, var(--logo-blue), var(--logo-lav));
}
.dot.cur {
  width: 13px; height: 13px; border: 0;
  background: linear-gradient(90deg, var(--logo-blue), var(--logo-lav));
  /* SVG에서 r=11 옅은 링이던 것. 링을 따로 그리지 않고 그림자로 두면 폭 계산이 단순해진다. */
  box-shadow: 0 0 0 1.5px var(--bg-50), 0 0 0 3px color-mix(in srgb, var(--logo-lav) 45%, transparent);
}

.labels { position: relative; height: 19px; margin-top: 8px; }
.lb {
  position: absolute; top: 0; transform: translateX(-50%);
  white-space: nowrap;
  font-size: var(--fs-xs); line-height: 19px; letter-spacing: var(--tr-body);
  color: var(--fg-400);
}
.lb.done { color: var(--fg-600); }
.lb.cur { color: var(--fg-950); font-weight: 600; }

@media (prefers-reduced-motion: reduce) { .fill { transition: none; } }
</style>

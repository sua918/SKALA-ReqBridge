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
 * `--pad`는 양 끝 점이 칸 가장자리에 붙지 않게 띄우는 값이다.
 *
 * 라벨은 **현재 단계 하나만** 글자로 적는다. 상태 이름이 「검토 중」에서
 * 「수정안 승인 대기」로 길어지면서(프론트엔드-추가-요청사항 4.2) 다섯 개를 한 줄에
 * 늘어놓을 수 없게 됐다. 272px 칸에서 칸당 몫은 54px인데 가장 긴 이름은 90px을 넘어,
 * 이름들이 서로 겹쳐 어느 것도 못 읽는 상태가 된다.
 *
 * 이름을 줄여 다섯 개를 다 넣는 선택지도 있었지만, 그러면 배지와 여정이 같은 상태를
 * 다른 말로 부르게 된다 — 사용자가 화면에서 찾아야 할 단어가 두 벌이 된다.
 * 지금 어디인지는 글자로, 남은 단계는 점으로 말한다. 나머지 이름은 점의 `title`에 남겨
 * 필요할 때 짚어볼 수 있게 한다.
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

const currentLabel = computed(() => JOURNEY[idx.value]?.[1] ?? '')

/**
 * 라벨은 현재 점과 같은 가로 위치에 놓되, 어느 변을 그 위치에 맞출지는 단계마다 다르다.
 * 첫 단계에서 글자를 점 중심에 두면 왼쪽 절반이 칸 밖으로 나가고, 마지막 단계에서는
 * 오른쪽이 나간다. 양 끝에서는 글자의 변을 점에 맞춰 안쪽으로만 자라게 한다.
 */
const labelShift = computed(() => {
  if (idx.value === 0) return 'translateX(0)'
  if (idx.value === last) return 'translateX(-100%)'
  return 'translateX(-50%)'
})
</script>

<template>
  <div class="cj" :style="{ maxWidth: width + 'px' }">
    <div class="track">
      <div class="rail" />
      <div class="railwrap"><div class="fill" :style="{ clipPath: clip }" /></div>
      <span
        v-for="n in nodes" :key="n.key"
        class="dot" :class="{ done: n.done, cur: n.current }" :style="{ left: n.left }"
        :title="n.label"
      />
    </div>
    <div v-if="labels" class="labels">
      <span class="lb" :style="{ left: nodes[idx].left, transform: labelShift }">
        {{ currentLabel }}
      </span>
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
  position: absolute; top: 0;
  white-space: nowrap;
  font-size: var(--fs-xs); line-height: 19px; letter-spacing: var(--tr-body);
  color: var(--fg-950); font-weight: 600;
}

@media (prefers-reduced-motion: reduce) { .fill { transition: none; } }
</style>

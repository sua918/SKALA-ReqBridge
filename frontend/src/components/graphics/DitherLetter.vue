<script setup>
import { ref, onMounted } from 'vue'
import DitherWave from './DitherWave.vue'

/**
 * 디더 그래픽을 **글자 모양으로만** 보여준다.
 *
 * 앞서 SVG로 두 번 시도했다가 형태가 무너졌던 것과 방식이 다르다.
 * 여기서는 WebGL 캔버스(레퍼런스 셰이더 그대로)를 그린 뒤, CSS `clip-path`로 글자 모양만
 * 남긴다. 잘라내는 주체가 브라우저의 벡터 클리핑이라 **자소 외곽이 항상 또렷하다** —
 * 점으로 그리던 방식처럼 획이 끊길 여지가 없다.
 *
 * clipPath는 인라인 SVG로 DOM에 두어야 웹폰트(SUITE)가 적용된다.
 * data URI 마스크로는 외부 폰트가 로드되지 않아 폴백 서체로 잘린다.
 */
const props = defineProps({
  letter: { type: String, required: true },
  size: { type: Number, default: 340 },
})

const uid = Math.random().toString(36).slice(2, 8)
/** 폰트가 로드되기 전에 클립이 계산되면 폴백 서체 모양으로 잘린다. 로드 후 한 번 다시 그린다. */
const ready = ref(false)
onMounted(() => {
  document.fonts?.ready.then(() => { ready.value = true }) ?? (ready.value = true)
})
</script>

<template>
  <div class="dl" :style="{ width: size + 'px', height: size + 'px' }">
    <!-- 글자 틀. 크기가 0이라 자리를 차지하지 않는다. -->
    <svg class="defs" aria-hidden="true" focusable="false">
      <defs>
        <clipPath :id="`dl-${uid}`" clipPathUnits="userSpaceOnUse">
          <text
            :key="String(ready)"
            :x="size / 2" :y="size / 2" text-anchor="middle" dominant-baseline="central"
            :font-size="size * 0.98" font-weight="900" class="glyph"
          >{{ letter }}</text>
        </clipPath>
      </defs>
    </svg>

    <div class="clip" :style="{ clipPath: `url(#dl-${uid})` }">
      <!-- 파형이 옅은 자리에서도 글자가 사라지지 않게 받쳐 주는 바탕 -->
      <div class="floor" />
      <DitherWave :pixel-size="2" />
    </div>
  </div>
</template>

<style scoped>
.dl { position: relative; pointer-events: none; user-select: none; }
.defs { position: absolute; width: 0; height: 0; }
.glyph { font-family: var(--font-head); letter-spacing: -.05em; }

.clip { position: absolute; inset: 0; }
.floor {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, #5DCBF5 0%, var(--logo-blue) 52%, var(--logo-lav) 100%);
  opacity: .16;
}
.clip :deep(canvas) { position: absolute; inset: 0; }
</style>

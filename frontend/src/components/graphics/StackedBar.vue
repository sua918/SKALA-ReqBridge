<script setup>
import { computed } from 'vue'

/** 요구사항 상태 구성비. segs = [{ value, label, tone }] */
const props = defineProps({
  segs: { type: Array, required: true },
  width: { type: Number, default: 520 },
  height: { type: Number, default: 22 },
})
const total = computed(() => props.segs.reduce((s, x) => s + x.value, 0) || 1)
const viewBox = computed(() => '0 0 ' + props.width + ' ' + props.height)
const parts = computed(() => {
  let x = 0
  return props.segs
    .filter((s) => s.value > 0)
    .map((s) => {
      const w = (props.width * s.value) / total.value
      const part = { ...s, x, w: Math.max(w - 1.5, 1) }
      x += w
      return part
    })
})
const swatch = (tone) => ({
  width: '8px', height: '8px', borderRadius: '2px',
  background: 'var(--' + tone + '-so)', display: 'inline-block',
})
</script>

<template>
  <div>
    <svg :height="height" :viewBox="viewBox" preserveAspectRatio="none" fill="none"
         style="display: block; width: 100%"><!-- width는 컨테이너에 맞춘다. 고정 px면 좁은 열에서 넘친다 -->
      <rect
        v-for="p in parts" :key="p.label" :x="p.x" y="0" :width="p.w" :height="height" rx="3"
        :fill="'var(--' + p.tone + '-so)'"
      />
    </svg>
    <div class="legend">
      <div v-for="s in segs" :key="s.label" class="legend-item">
        <span :style="swatch(s.tone)" />
        <span class="mi legend-label">{{ s.label }}</span>
        <span class="fig legend-value">{{ s.value }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.legend { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 12px; }
.legend-item { display: flex; align-items: center; gap: 7px; }
.legend-label { font-size: var(--fs-micro); color: var(--fg-700); }
.legend-value { font-size: 14px; color: var(--fg-950); }
</style>

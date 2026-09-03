<script setup>
import { computed, useId } from 'vue'
import BrandGradient from './BrandGradient.vue'

const props = defineProps({
  pct: { type: Number, default: 0 },
  size: { type: Number, default: 104 },
  stroke: { type: Number, default: 9 },
  cap: { type: String, default: '' },
  sub: { type: String, default: '' },
})
const gid = 'r-' + useId()
const radius = computed(() => (props.size - props.stroke) / 2)
const circ = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => circ.value * (1 - Math.min(100, Math.max(0, props.pct)) / 100))
const viewBox = computed(() => '0 0 ' + props.size + ' ' + props.size)
const boxStyle = computed(() => ({ position: 'relative', width: props.size + 'px', height: props.size + 'px' }))
const capStyle = computed(() => ({ fontSize: Math.round(props.size * 0.26) + 'px', fontWeight: 700 }))
</script>

<template>
  <div :style="boxStyle">
    <svg :width="size" :height="size" :viewBox="viewBox" fill="none" style="transform: rotate(-90deg)">
      <defs><BrandGradient :id="gid" /></defs>
      <circle :cx="size / 2" :cy="size / 2" :r="radius" stroke="var(--bg-200)" :stroke-width="stroke" />
      <circle
        :cx="size / 2" :cy="size / 2" :r="radius" :stroke="'url(#' + gid + ')'" :stroke-width="stroke"
        stroke-linecap="round" :stroke-dasharray="circ" :stroke-dashoffset="offset"
        style="transition: stroke-dashoffset .7s var(--ease)"
      />
    </svg>
    <div class="ring-cap">
      <span class="hd" :style="capStyle">{{ cap }}</span>
      <span class="ring-sub">{{ sub }}</span>
    </div>
  </div>
</template>

<style scoped>
.ring-cap {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1px;
}
.ring-sub {
  font-family: var(--font-body); font-size: var(--fs-micro); font-weight: 500; letter-spacing: 0;
  text-transform: uppercase; color: var(--fg-600);
}
</style>

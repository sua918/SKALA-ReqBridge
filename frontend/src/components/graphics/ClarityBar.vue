<script setup>
import { computed, useId } from 'vue'
import BrandGradient from './BrandGradient.vue'

/** 여정의 행 축소판 — 목록에서 진행을 한 눈에 읽게 한다. */
const props = defineProps({
  pct: { type: Number, default: 0 },
  width: { type: Number, default: 120 },
  height: { type: Number, default: 5 },
})
const gid = 'b-' + useId()
const cid = 'bc-' + useId()
const done = computed(() => (props.width * Math.min(100, Math.max(0, props.pct))) / 100)
const viewBox = computed(() => '0 0 ' + props.width + ' ' + props.height)
const radius = computed(() => props.height / 2)
</script>

<template>
  <svg :width="width" :height="height" :viewBox="viewBox" fill="none" style="display: block">
    <defs>
      <BrandGradient :id="gid" />
      <clipPath :id="cid"><rect x="0" y="0" :width="done" :height="height" :rx="radius" /></clipPath>
    </defs>
    <rect x="0" y="0" :width="width" :height="height" :rx="radius" fill="var(--bg-200)" />
    <rect x="0" y="0" :width="width" :height="height" :rx="radius" :fill="'url(#' + gid + ')'" :clip-path="'url(#' + cid + ')'" />
  </svg>
</template>

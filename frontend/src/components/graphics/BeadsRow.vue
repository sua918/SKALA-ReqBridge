<script setup>
import { computed } from 'vue'

/** 로고 안의 점 3개 — 문제별 회차 표시로 재사용한다. */
const props = defineProps({
  total: { type: Number, default: 2 },
  done: { type: Number, default: 0 },
  size: { type: Number, default: 7 },
  gap: { type: Number, default: 5 },
})
const width = computed(() => props.total * props.size + (props.total - 1) * props.gap)
const viewBox = computed(() => '0 0 ' + width.value + ' ' + props.size)
const radius = computed(() => props.size / 2 - 0.5)
const dots = computed(() =>
  Array.from({ length: props.total }, (_, i) => ({
    cx: props.size / 2 + i * (props.size + props.gap),
    filled: i < props.done,
  })),
)
</script>

<template>
  <svg :width="width" :height="size" :viewBox="viewBox" fill="none" style="display: block">
    <circle
      v-for="(d, i) in dots" :key="i" :cx="d.cx" :cy="size / 2" :r="radius"
      :fill="d.filled ? 'var(--logo-blue)' : 'none'"
      :stroke="d.filled ? 'var(--logo-blue)' : 'var(--bg-300)'" stroke-width="1.2"
    />
  </svg>
</template>

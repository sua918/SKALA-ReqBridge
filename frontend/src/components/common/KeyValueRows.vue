<script setup>
import SectionLabel from './SectionLabel.vue'

/** LUMÈRE Hero의 label/value 표 — 요구사항 메타데이터와 Preview basis에 그대로 맞는다. */
defineProps({
  rows: { type: Array, required: true },
  labelWidth: { type: String, default: '120px' },
  /** 판의 머리말. 비우면 표만 담긴 판이 된다. */
  label: { type: String, default: '' },
})
</script>

<template>
  <!-- 바탕(--bg-100) 위에 줄만 놓이면 「표가 어디서 시작해 어디서 끝나는지」가 안 보인다.
       다른 정보 덩어리와 마찬가지로 판에 담는다. -->
  <div class="card list">
    <SectionLabel v-if="label" :text="label" />
    <div
      v-for="r in rows" :key="r.label" class="kv row"
      :style="{ gridTemplateColumns: labelWidth + ' minmax(0, 1fr)' }"
    >
      <span class="eb k">{{ r.label }}</span>
      <span class="v"><slot :name="r.label" :row="r">{{ r.value }}</slot></span>
    </div>
  </div>
</template>

<style scoped>
.kv { display: grid; gap: 16px; padding: 11px 0 12px; }
/* 「요구사항」 「활성 작업」 — 표의 항목명이라 값과 같이 읽힌다. 값보다 한 단만 낮춘다. */
.k { font-size: var(--fs-micro); color: var(--fg-500); letter-spacing: 0; }
.v { font-size: var(--fs-sm); color: var(--fg-950); line-height: 1.6; }
</style>

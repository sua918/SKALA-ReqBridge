<script setup>
import { computed } from 'vue'
import { AMBIGUITY_TYPE_LABELS, resolveAmbiguityTypeLabel } from '@/components/common/ambiguityLabels.js'

const props = defineProps({ type: { type: String, required: true } })

/** 라벨 사전은 공용 ambiguityLabels.js 하나만 쓴다 — types/api.js에 사본을 만들지 않는다. */
const label = computed(() => {
  if (!AMBIGUITY_TYPE_LABELS[props.type]) {
    console.error('[AmbiguityTypeBadge] 사전에 없는 값', props.type)
    return null
  }
  return resolveAmbiguityTypeLabel(props.type)
})
</script>

<template>
  <span v-if="label" class="amb mi">{{ label }}</span>
</template>

<style scoped>
.amb {
  display: inline-flex; align-items: center; padding: 3px 9px 4px; border-radius: 999px;
  background: var(--bg-50); border: 1px solid var(--gray-bd); color: var(--gray-tx);
  font-size: var(--fs-micro); letter-spacing: .02em; white-space: nowrap;
}
</style>

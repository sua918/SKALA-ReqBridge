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
/* 알약과 테두리를 벗겼다.
   이건 상태가 아니라 「어떤 종류의 불명확성인가」라는 분류다. 한 줄에 상태 배지와
   나란히 놓이는데 둘 다 알약이면 어느 쪽이 지금 할 일을 말하는지 알 수 없고,
   둘 다 눌러야 하는 것처럼 보인다. 배지는 상태 하나만 쓰고, 분류는 글자로 둔다. */
.amb {
  display: inline-flex; align-items: center;
  color: var(--fg-500);
  font-size: var(--fs-micro); letter-spacing: .01em; white-space: nowrap;
}
</style>

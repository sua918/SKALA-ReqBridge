<script setup>
/**
 * 상태 뱃지 (B·C 공용).
 * kind로 enum 종류를 구분하고, value는 Spec 문자열 그대로.
 */
import { computed } from 'vue'
import { resolveStatusLabel, resolveStatusTone } from '@/components/common/statusLabels.js'

const props = defineProps({
  /** requirement | analysis | clarification | issue | revision */
  kind: {
    type: String,
    required: true,
    validator: (v) =>
      ['requirement', 'analysis', 'clarification', 'issue', 'revision'].includes(v),
  },
  /** Spec enum 값 (예: CLARIFYING, FAILED) */
  value: {
    type: String,
    required: true,
  },
})

const label = computed(() => resolveStatusLabel(props.kind, props.value))
const tone = computed(() => resolveStatusTone(props.kind, props.value))
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`" :title="value">
    {{ label }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}

.status-badge--neutral {
  color: #475569;
  background: #f1f5f9;
}

.status-badge--info {
  color: #1d4ed8;
  background: #dbeafe;
}

.status-badge--warn {
  color: #b45309;
  background: #fef3c7;
}

.status-badge--success {
  color: #15803d;
  background: #dcfce7;
}

.status-badge--danger {
  color: #b91c1c;
  background: #fee2e2;
}
</style>

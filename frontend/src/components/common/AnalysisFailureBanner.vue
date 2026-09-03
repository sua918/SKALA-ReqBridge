<script setup>
/**
 * Analysis status=FAILED 전용 배너 (HTTP는 보통 200).
 * 재시도 버튼은 슬롯/이벤트로 화면에서 연결 (배너는 표시만).
 */
defineProps({
  /** Analysis.error.code */
  code: {
    type: String,
    default: '',
  },
  /** Analysis.error.message */
  message: {
    type: String,
    default: '분석에 실패했습니다. 다시 시도해 주세요.',
  },
})

defineEmits(['retry'])
</script>

<template>
  <div class="analysis-failure" role="alert">
    <div class="analysis-failure__body">
      <p class="analysis-failure__title">분석 실패</p>
      <p class="analysis-failure__message">{{ message }}</p>
      <p v-if="code" class="analysis-failure__code">code: {{ code }}</p>
    </div>
    <div class="analysis-failure__actions">
      <slot name="actions">
        <button type="button" class="analysis-failure__retry" @click="$emit('retry')">
          다시 시도
        </button>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.analysis-failure {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #fdba74;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
}

.analysis-failure__title {
  margin: 0 0 4px;
  font-size: 0.9rem;
  font-weight: 700;
}

.analysis-failure__message {
  margin: 0;
  font-size: 0.875rem;
}

.analysis-failure__code {
  margin: 6px 0 0;
  color: #c2410c;
  font-size: 0.75rem;
}

.analysis-failure__retry {
  padding: 6px 12px;
  border: 1px solid #ea580c;
  border-radius: 6px;
  background: #ffffff;
  color: #c2410c;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.analysis-failure__retry:hover {
  background: #ffedd5;
}
</style>

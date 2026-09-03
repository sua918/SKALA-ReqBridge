<script setup>
/**
 * HTTP API 오류 표시 (400/404/409 등).
 * Analysis FAILED(HTTP 200)는 AnalysisFailureBanner 사용.
 */
defineProps({
  /** 사용자에게 보여줄 메시지 */
  message: {
    type: String,
    default: '',
  },
  /** Spec fieldErrors: [{ field, message }] */
  fieldErrors: {
    type: Array,
    default: () => [],
  },
})
</script>

<template>
  <div v-if="message || fieldErrors.length" class="error-message" role="alert">
    <p v-if="message" class="error-message__text">{{ message }}</p>
    <ul v-if="fieldErrors.length" class="error-message__fields">
      <li v-for="(item, index) in fieldErrors" :key="`${item.field}-${index}`">
        <span v-if="item.field" class="error-message__field">{{ item.field }}:</span>
        {{ item.message }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.error-message {
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #991b1b;
}

.error-message__text {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
}

.error-message__fields {
  margin: 8px 0 0;
  padding-left: 1.1rem;
  font-size: 0.85rem;
}

.error-message__field {
  font-weight: 600;
}
</style>

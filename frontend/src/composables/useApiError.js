import { computed, ref } from 'vue'

/**
 * axios / apiError → 화면용 메시지·fieldErrors 변환만 담당.
 * 재시도·navigation·polling은 여기서 하지 않는다 (합의: 책임 한정).
 */
export function useApiError() {
  const message = ref('')
  const fieldErrors = ref([])
  const code = ref('')
  const status = ref(null)

  const hasError = computed(
    () => Boolean(message.value) || fieldErrors.value.length > 0,
  )

  function clearError() {
    message.value = ''
    fieldErrors.value = []
    code.value = ''
    status.value = null
  }

  function captureError(error) {
    const apiError = error?.apiError
    if (apiError) {
      message.value = apiError.message || '요청을 처리하지 못했습니다.'
      fieldErrors.value = Array.isArray(apiError.fieldErrors)
        ? apiError.fieldErrors
        : []
      code.value = apiError.code || ''
      status.value = apiError.status ?? null
      return
    }

    message.value = error?.message || '알 수 없는 오류가 발생했습니다.'
    fieldErrors.value = []
    code.value = ''
    status.value = null
  }

  return {
    message,
    fieldErrors,
    code,
    status,
    hasError,
    clearError,
    captureError,
  }
}

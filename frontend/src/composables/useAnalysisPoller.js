import { onUnmounted, ref, shallowRef } from 'vue'
import { getAnalysis } from '@/api/analyses'
import { AnalysisStatus } from '@/types/api'

const TERMINAL = new Set([AnalysisStatus.COMPLETED, AnalysisStatus.FAILED])

/**
 * 분석 상태 1초 polling (B·C 공용 로직, 상태는 호출 화면마다 독립).
 *
 * @param {object} [options]
 * @param {number} [options.intervalMs=1000]
 * @param {(analysis: object) => void|Promise<void>} [options.onComplete]
 * @param {(analysis: object) => void|Promise<void>} [options.onFailed]
 * @param {(error: unknown) => void} [options.onError]
 */
export function useAnalysisPoller(options = {}) {
  const intervalMs = options.intervalMs ?? 1000
  const handlers = {
    onComplete: options.onComplete,
    onFailed: options.onFailed,
    onError: options.onError,
  }

  const analysis = shallowRef(null)
  const isPolling = ref(false)
  const lastError = ref(null)

  let timerId = null
  let activeAnalysisId = null

  function clearTimer() {
    if (timerId != null) {
      clearInterval(timerId)
      timerId = null
    }
  }

  function stop() {
    clearTimer()
    isPolling.value = false
    activeAnalysisId = null
  }

  async function tick() {
    if (activeAnalysisId == null) {
      return
    }

    try {
      const next = await getAnalysis(activeAnalysisId)
      analysis.value = next
      lastError.value = null

      if (TERMINAL.has(next.status)) {
        stop()
        //콜백이 async면 그 실패도 onError로 이어져야 한다.
        if (next.status === AnalysisStatus.COMPLETED) {
          await handlers.onComplete?.(next)
        } else {
          await handlers.onFailed?.(next)
        }
      }
    } catch (error) {
      lastError.value = error
      stop()
      handlers.onError?.(error)
    }
  }

  /**
   * @param {number|string} analysisId
   * @param {object} [runtimeOptions] onComplete / onFailed / onError 덮어쓰기
   */
  function start(analysisId, runtimeOptions = {}) {
    if (runtimeOptions.onComplete) handlers.onComplete = runtimeOptions.onComplete
    if (runtimeOptions.onFailed) handlers.onFailed = runtimeOptions.onFailed
    if (runtimeOptions.onError) handlers.onError = runtimeOptions.onError

    stop()
    activeAnalysisId = Number(analysisId)
    isPolling.value = true
    analysis.value = null
    lastError.value = null

    void tick()
    timerId = setInterval(() => {
      void tick()
    }, intervalMs)
  }

  onUnmounted(() => {
    stop()
  })

  return {
    analysis,
    isPolling,
    lastError,
    start,
    stop,
  }
}

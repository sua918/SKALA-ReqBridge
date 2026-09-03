import { onUnmounted, ref, shallowRef } from 'vue'
import { getAnalysis } from '@/api/analyses'
import { AnalysisStatus } from '@/types/api'

const TERMINAL = new Set([AnalysisStatus.COMPLETED, AnalysisStatus.FAILED])

/**
 * 분석 상태 1초 polling (B·C 공용 로직, 상태는 호출 화면마다 독립).
 *
 * @param {object} [options]
 * @param {number} [options.intervalMs=1000]
 * @param {(analysis: object) => void} [options.onComplete]
 * @param {(analysis: object) => void} [options.onFailed]
 * @param {(error: unknown) => void} [options.onError]
 */
export function useAnalysisPoller(options = {}) {
  const intervalMs = options.intervalMs ?? 1000
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
        if (next.status === AnalysisStatus.COMPLETED) {
          options.onComplete?.(next)
        } else {
          options.onFailed?.(next)
        }
      }
    } catch (error) {
      lastError.value = error
      stop()
      options.onError?.(error)
    }
  }

  /**
   * @param {number|string} analysisId
   */
  function start(analysisId) {
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

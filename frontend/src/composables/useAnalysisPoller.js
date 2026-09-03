import { onUnmounted, ref, shallowRef } from 'vue'
import { getAnalysis } from '@/api/analyses'
import { AnalysisStatus } from '@/types/api'

const TERMINAL = new Set([AnalysisStatus.COMPLETED, AnalysisStatus.FAILED])

/**
 * 분석 상태 1초 polling (B·C 공용 로직, 상태는 호출 화면마다 독립).
 *
 * 이전 요청이 끝나기 전에는 다음 tick을 건너뛴다 (`inFlight`).
 * `stop()` / 재`start()` 이후 늦게 도착한 응답은 세대 ID로 무시한다.
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
  let inFlight = false
  let requestGeneration = 0

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
    //진행 중 요청의 응답이 상태를 덮어쓰지 못하게 세대를 올리고, 다음 start의 tick이 막히지 않게 inFlight를 푼다.
    requestGeneration += 1
    inFlight = false
  }

  async function tick() {
    if (activeAnalysisId == null || inFlight) {
      return
    }

    const generation = requestGeneration
    const analysisId = activeAnalysisId
    inFlight = true

    let next
    try {
      next = await getAnalysis(analysisId)
    } catch (error) {
      if (generation !== requestGeneration) {
        return
      }
      lastError.value = error
      stop()
      handlers.onError?.(error)
      return
    }

    if (generation !== requestGeneration) {
      return
    }

    analysis.value = next
    lastError.value = null

    if (!TERMINAL.has(next.status)) {
      inFlight = false
      return
    }

    //종료 상태면 먼저 polling을 끊고, 콜백 오류는 세대와 무관하게 onError로 보낸다.
    stop()
    try {
      if (next.status === AnalysisStatus.COMPLETED) {
        await handlers.onComplete?.(next)
      } else {
        await handlers.onFailed?.(next)
      }
    } catch (error) {
      lastError.value = error
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

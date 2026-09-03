import { computed, ref } from 'vue'
import { AnalysisStatus } from '@/types/api'

/**
 * 진행 중 분석이 있으면 입력/재분석 버튼을 잠그는 패턴 (합의 activeAnalysis).
 * 화면별로 인스턴스를 따로 만든다.
 */
export function useActiveAnalysisLock() {
  const activeAnalysis = ref(null)

  const isLocked = computed(() => {
    const status = activeAnalysis.value?.status
    return (
      status === AnalysisStatus.PENDING || status === AnalysisStatus.PROCESSING
    )
  })

  function setActiveAnalysis(value) {
    activeAnalysis.value = value
  }

  function clearActiveAnalysis() {
    activeAnalysis.value = null
  }

  return {
    activeAnalysis,
    isLocked,
    setActiveAnalysis,
    clearActiveAnalysis,
  }
}

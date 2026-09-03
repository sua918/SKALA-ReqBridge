<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  listDocumentAnalyses,
  retryAnalysis,
  startDocumentAnalysis,
} from '@/api/analyses'
import { getDocument } from '@/api/documents'
import { listRequirements } from '@/api/requirements'
import AnalysisFailureBanner from '@/components/common/AnalysisFailureBanner.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useActiveAnalysisLock } from '@/composables/useActiveAnalysisLock'
import { useAnalysisPoller } from '@/composables/useAnalysisPoller'
import { useApiError } from '@/composables/useApiError'
import { AnalysisKind, AnalysisStatus, ApiErrorCode, RequirementStatus } from '@/types/api'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => Number(route.params.documentId))

const loading = ref(true)
const document = ref(null)
const requirements = ref([])

const { message, fieldErrors, hasError, captureError, clearError } = useApiError()
const { activeAnalysis, isLocked, setActiveAnalysis, clearActiveAnalysis } =
  useActiveAnalysisLock()

const failedAnalysis = computed(() => {
  const current = activeAnalysis.value
  if (current?.status === AnalysisStatus.FAILED) {
    return current
  }
  return null
})

const latestAnalysisStatus = computed(() => activeAnalysis.value?.status ?? null)

const isAnalyzed = computed(
  () => activeAnalysis.value?.status === AnalysisStatus.COMPLETED,
)

/** 이력은 재시도까지 포함하므로 항상 가장 최근 작업이 현재 상태다. */
function pickLatestAnalysis(items) {
  return items.reduce(
    (latest, item) => (latest === null || item.id > latest.id ? item : latest),
    null,
  )
}

const { isPolling, start: startPolling } = useAnalysisPoller()

/** 성공 이력은 409, 실패 이후에는 retries만 허용한다 (Spec 5.9·6.3). */
const isAnalyzeDisabled = computed(
  () =>
    isLocked.value ||
    isPolling.value ||
    isAnalyzed.value ||
    failedAnalysis.value !== null,
)

const analyzeButtonLabel = computed(() => {
  if (isPolling.value || isLocked.value) {
    return '분석 중…'
  }
  if (isAnalyzed.value) {
    return '분석 완료'
  }
  return failedAnalysis.value ? '재시도 필요' : '분석 요청'
})

/**
 * 문제 상세(type·evidence)는 Spec 5.7 목록 응답에 없다.
 * 이 화면은 분석 결과의 건수까지만 보여주고, 상세는 요구사항 화면의 workflow가 담당한다.
 */
const detectedIssueCount = computed(
  () => activeAnalysis.value?.result?.issueIds?.length ?? 0,
)

const displayedRequirements = computed(() => {
  const reviewStatuses = new Set([
    RequirementStatus.AMBIGUOUS,
    RequirementStatus.CLARIFYING,
  ])

  return [...requirements.value].sort((a, b) => {
    const aPriority = reviewStatuses.has(a.status) ? 0 : 1
    const bPriority = reviewStatuses.has(b.status) ? 0 : 1

    if (aPriority !== bPriority) {
      return aPriority - bPriority
    }

    return a.sequenceNo - b.sequenceNo
  })
})

async function loadRequirements() {
  const data = await listRequirements(documentId.value)
  requirements.value = data.items ?? []
}

async function restoreActiveAnalysis() {
  const data = await listDocumentAnalyses(documentId.value, AnalysisKind.DOCUMENT)
  const items = data.items ?? []
  const active = items.find(
    (item) =>
      item.status === AnalysisStatus.PENDING ||
      item.status === AnalysisStatus.PROCESSING,
  )
  if (active) {
    setActiveAnalysis(active)
    startPolling(active.id, {
      onComplete: async (analysis) => {
        setActiveAnalysis(analysis)
        clearError()
        await loadRequirements()
      },
      onFailed: (analysis) => {
        setActiveAnalysis(analysis)
      },
      onError: (error) => {
        captureError(error)
        clearActiveAnalysis()
      },
    })
    return
  }

  const latest = pickLatestAnalysis(items)
  if (latest) {
    setActiveAnalysis(latest)
  }
}

async function bootstrap() {
  loading.value = true
  clearError()
  try {
    document.value = await getDocument(documentId.value)
    await restoreActiveAnalysis()
    await loadRequirements()
  } catch (error) {
    captureError(error)
  } finally {
    loading.value = false
  }
}

async function onAnalyze() {
  clearError()
  try {
    const analysis = await startDocumentAnalysis(documentId.value)
    setActiveAnalysis(analysis)

    if (analysis.status === AnalysisStatus.COMPLETED) {
      await loadRequirements()
      return
    }
    if (analysis.status === AnalysisStatus.FAILED) {
      return
    }

    startPolling(analysis.id, {
      onComplete: async (done) => {
        setActiveAnalysis(done)
        clearError()
        await loadRequirements()
      },
      onFailed: (failed) => {
        setActiveAnalysis(failed)
      },
      onError: (error) => {
        captureError(error)
        clearActiveAnalysis()
      },
    })
  } catch (error) {
    captureError(error)
    //409는 이미 진행 중이거나 끝난 작업이 있다는 뜻이라 이력에서 복구한다 (Spec 6.3)
    const code = error.apiError?.code
    if (
      code === ApiErrorCode.DOCUMENT_ALREADY_ANALYZED ||
      code === ApiErrorCode.ANALYSIS_IN_PROGRESS
    ) {
      await restoreActiveAnalysis()
      await loadRequirements()
    }
  }
}

async function onRetry() {
  const failed = failedAnalysis.value
  if (!failed) {
    return
  }
  clearError()
  try {
    const analysis = await retryAnalysis(failed.id)
    setActiveAnalysis(analysis)
    if (analysis.status === AnalysisStatus.COMPLETED) {
      await loadRequirements()
      return
    }
    if (analysis.status === AnalysisStatus.FAILED) {
      return
    }
    startPolling(analysis.id, {
      onComplete: async (done) => {
        setActiveAnalysis(done)
        await loadRequirements()
      },
      onFailed: (next) => {
        setActiveAnalysis(next)
      },
      onError: (error) => {
        captureError(error)
      },
    })
  } catch (error) {
    captureError(error)
  }
}

function openRequirement(requirementId) {
  router.push({
    name: 'requirement-workflow',
    params: { requirementId: String(requirementId) },
  })
}

function requirementActionLabel(status) {
  if (
    status === RequirementStatus.AMBIGUOUS ||
    status === RequirementStatus.CLARIFYING
  ) {
    return '불명확성 확인'
  }

  return '요구사항 보기'
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <section class="doc-page">
    <header class="doc-header">
      <div>
        <h1>{{ document?.title ?? '문서' }}</h1>
        <p v-if="latestAnalysisStatus" class="doc-meta">
          <StatusBadge kind="analysis" :value="latestAnalysisStatus" />
        </p>
      </div>
      <!-- Preview 진입은 C(P2) 몫으로 비어 있던 자리다 (인계서 4.7). -->
      <RouterLink class="preview-link" :to="{ name: 'document-preview', params: { documentId: String(documentId) } }">
        미리보기
      </RouterLink>
    </header>

    <ErrorMessage
      v-if="hasError"
      :message="message"
      :field-errors="fieldErrors"
    />

    <p v-if="loading">불러오는 중…</p>

    <template v-else-if="document">
      <section class="panel">
        <h2>원문</h2>
        <pre class="content">{{ document.content }}</pre>
        <div class="actions">
          <button
            type="button"
            class="btn-primary"
            :disabled="isAnalyzeDisabled"
            @click="onAnalyze"
          >
            {{ analyzeButtonLabel }}
          </button>
        </div>
      </section>

      <AnalysisFailureBanner
        v-if="failedAnalysis"
        :code="failedAnalysis.error?.code ?? ''"
        :message="failedAnalysis.error?.message ?? '분석에 실패했습니다.'"
        @retry="onRetry"
      />

      <section class="panel">
        <h2>요구사항 목록</h2>
        <p v-if="isAnalyzed" class="issue-summary">
          이번 분석에서 확인된 불명확성 {{ detectedIssueCount }}건. 유형과 근거는 요구사항
          화면에서 확인합니다.
        </p>
        <p v-if="requirements.length === 0" class="empty">
          아직 추출된 요구사항이 없습니다. 분석을 실행해 주세요.
        </p>
        <ul v-else class="req-list">
          <li
            v-for="requirement in displayedRequirements"
            :key="requirement.id"
            class="req-item"
          >
            <button
              type="button"
              class="req-button"
              @click="openRequirement(requirement.id)"
            >
              <div class="req-top">
                <span class="req-code">요구사항 {{ requirement.sequenceNo }}</span>
                <StatusBadge kind="requirement" :value="requirement.status" />
              </div>
              <p class="req-text">{{ requirement.originalText }}</p>
              <span class="req-link">{{ requirementActionLabel(requirement.status) }} →</span>
            </button>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.doc-page {
  max-width: 880px;
}

.doc-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.preview-link {
  padding: 8px 16px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 0.875rem;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.preview-link:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.doc-header h1 {
  margin: 0 0 6px;
  font-size: 1.5rem;
}

.doc-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 0 20px;
  color: #64748b;
  font-size: 0.9rem;
}

.panel {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 1rem;
}

.content {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  white-space: pre-wrap;
  word-break: break-word;
}

.actions {
  margin-top: 12px;
}

.btn-primary {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty {
  margin: 0;
  color: #94a3b8;
  font-size: 0.9rem;
}

.req-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.req-item + .req-item {
  margin-top: 10px;
}

.req-button {
  display: block;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}

.req-button:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.req-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.req-code {
  font-size: 0.8rem;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.01em;
}

.req-text {
  margin: 0 0 10px;
  color: #334155;
  font-size: 0.95rem;
  line-height: 1.55;
}

.issue-summary {
  margin: 0 0 12px;
  color: #b45309;
  font-size: 0.85rem;
}

.req-link {
  color: #2563eb;
  font-size: 0.8rem;
  font-weight: 600;
}
</style>

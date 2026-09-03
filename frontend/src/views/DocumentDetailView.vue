<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  listDocumentAnalyses,
  retryAnalysis,
  startDocumentAnalysis,
} from '@/api/analyses'
import { getDocument } from '@/api/documents'
import {
  listMockIssuesByRequirement,
  listRequirements,
} from '@/api/requirements'
import AnalysisFailureBanner from '@/components/common/AnalysisFailureBanner.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { resolveAmbiguityTypeLabel } from '@/components/common/ambiguityLabels.js'
import { useActiveAnalysisLock } from '@/composables/useActiveAnalysisLock'
import { useAnalysisPoller } from '@/composables/useAnalysisPoller'
import { useApiError } from '@/composables/useApiError'
import { AnalysisKind, AnalysisStatus, ApiErrorCode } from '@/types/api'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => Number(route.params.documentId))

const loading = ref(true)
const document = ref(null)
const requirements = ref([])
const issuesByRequirementId = ref({})

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

const { isPolling, start: startPolling } = useAnalysisPoller()

async function loadIssuesForRequirements(items) {
  const map = {}
  await Promise.all(
    items.map(async (requirement) => {
      const { items: issues } = await listMockIssuesByRequirement(requirement.id)
      map[requirement.id] = issues
    }),
  )
  issuesByRequirementId.value = map
}

async function loadRequirements() {
  const data = await listRequirements(documentId.value)
  requirements.value = data.items ?? []
  await loadIssuesForRequirements(requirements.value)
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
        clearActiveAnalysis()
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

  const failed = items.find((item) => item.status === AnalysisStatus.FAILED)
  if (failed) {
    setActiveAnalysis(failed)
    return
  }

  const completed = items.find((item) => item.status === AnalysisStatus.COMPLETED)
  if (completed) {
    setActiveAnalysis(completed)
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
    if (error.apiError?.code === ApiErrorCode.DOCUMENT_ALREADY_ANALYZED) {
      captureError(error)
      await loadRequirements()
      const data = await listDocumentAnalyses(documentId.value, AnalysisKind.DOCUMENT)
      const completed = data.items?.find((item) => item.status === AnalysisStatus.COMPLETED)
      if (completed) {
        setActiveAnalysis(completed)
      }
      return
    }
    captureError(error)
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

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <section class="doc-page">
    <header class="doc-header">
      <h1>{{ document?.title ?? '문서' }}</h1>
      <p class="doc-meta">
        문서 #{{ documentId }}
        <template v-if="latestAnalysisStatus">
          ·
          <StatusBadge kind="analysis" :value="latestAnalysisStatus" />
        </template>
      </p>
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
            :disabled="isLocked || isPolling"
            @click="onAnalyze"
          >
            {{ isPolling || isLocked ? '분석 중…' : '분석 요청' }}
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
        <p v-if="requirements.length === 0" class="empty">
          아직 추출된 요구사항이 없습니다. 분석을 실행해 주세요.
        </p>
        <ul v-else class="req-list">
          <li
            v-for="requirement in requirements"
            :key="requirement.id"
            class="req-item"
          >
            <button
              type="button"
              class="req-button"
              @click="openRequirement(requirement.id)"
            >
              <div class="req-top">
                <span class="req-id">#{{ requirement.sequenceNo }}</span>
                <StatusBadge kind="requirement" :value="requirement.status" />
              </div>
              <p class="req-text">{{ requirement.originalText }}</p>
              <ul
                v-if="(issuesByRequirementId[requirement.id] ?? []).length"
                class="issue-list"
              >
                <li
                  v-for="issue in issuesByRequirementId[requirement.id]"
                  :key="issue.id"
                  class="issue-item"
                >
                  <StatusBadge kind="issue" :value="issue.status" />
                  <span class="issue-type">{{
                    resolveAmbiguityTypeLabel(issue.type)
                  }}</span>
                  <span class="issue-evidence">{{ issue.evidence }}</span>
                </li>
              </ul>
              <span class="req-link">확인 · 답변으로 이동 →</span>
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
  gap: 8px;
  margin-bottom: 8px;
}

.req-id {
  font-weight: 700;
  color: #0f172a;
}

.req-text {
  margin: 0 0 10px;
  color: #334155;
  font-size: 0.9rem;
  line-height: 1.5;
}

.issue-list {
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
}

.issue-item {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #ffffff;
  font-size: 0.8rem;
}

.issue-type {
  font-weight: 600;
  color: #b45309;
}

.issue-evidence {
  color: #64748b;
}

.req-link {
  color: #2563eb;
  font-size: 0.8rem;
  font-weight: 600;
}
</style>

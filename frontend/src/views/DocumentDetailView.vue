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
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHero from '@/components/common/PageHero.vue'
import PillButton from '@/components/common/PillButton.vue'
import SectionLabel from '@/components/common/SectionLabel.vue'
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

/** 히어로에 얹는 요약. 표시용이라 조회 로직과 무관하다. */
/**
 * 이 요구사항이 사람의 답변을 기다리는가. 목록에서 「먼저 볼 것」을 가르는 기준이다.
 * 확정·추출 완료는 읽기만 하면 되고, 불명확성이 걸린 것은 들어가서 답해야 한다.
 */
function needsAttention(status) {
  return status === RequirementStatus.AMBIGUOUS || status === RequirementStatus.CLARIFYING
}

/**
 * 답변이 필요한 요구사항을 목록 위로 올린다. 정상과 불명확이 같은 모양으로 섞여 있으면
 * 무엇을 먼저 봐야 할지 알려면 전체를 하나씩 열어봐야 한다
 * (프론트엔드-추가-요청사항 4.1).
 *
 * **순번은 다시 매기지 않는다.** 5번이 1번보다 위에 와도 그 항목은 여전히 문서의
 * 5번째 요구사항이다. 화면 사정으로 번호를 바꾸면 원문과 대조할 수 없게 된다.
 */
const displayedRequirements = computed(() => {
  return [...requirements.value].sort((a, b) => {
    const priority = Number(needsAttention(b.status)) - Number(needsAttention(a.status))
    return priority !== 0 ? priority : a.sequenceNo - b.sequenceNo
  })
})

const chips = computed(() => [
  { value: String(requirements.value.length), label: '요구사항' },
  {
    value: String(requirements.value.filter((r) => r.status === 'CONFIRMED').length),
    label: '확정',
  },
  { value: String(detectedIssueCount.value), label: '불명확성' },
])

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
  <div class="page">
    <PageHero
      num="03" eyebrow="문서 상세" watermark="D" :chips="chips"
    >
      <template #title>{{ document?.title ?? '문서' }}</template>
      <template #actions>
        <RouterLink :to="{ name: 'document-preview', params: { documentId: String(documentId) } }">
          <PillButton variant="quiet">미리보기</PillButton>
        </RouterLink>
      </template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <LoadingSpinner v-if="loading" />

    <div v-else-if="document" class="grid12 split at-4">
      <!-- 참조 열: 원문과 분석 -->
      <div class="left stickycol" data-reveal>
        <!-- 원문은 「읽는 곳」이라 한 톤 내린다. 아래 분석 카드(버튼이 있는 「하는 곳」)와
             같은 흰 판이면 눈이 어디부터 봐야 할지 알 수 없다. -->
        <div class="card pad quiet">
          <div class="cardhead">
            <span class="eb">원문</span>
            <span class="mi len">{{ [...(document.content ?? '')].length.toLocaleString() }}자</span>
          </div>
          <p class="source">{{ document.content }}</p>
        </div>

        <div class="card pad">
          <div class="cardhead">
            <span class="eb">분석</span>
            <StatusBadge v-if="latestAnalysisStatus" kind="analysis" :value="latestAnalysisStatus" />
          </div>

          <div v-if="isPolling || isLocked" class="sysnote" style="--tone: var(--on-blue)">
            <span class="scan" aria-hidden="true" />
            <div class="body">
              <div class="head"><span class="k">분석 처리 중</span></div>
              <p class="msg">끝나면 요구사항이 오른쪽에 나타납니다.</p>
            </div>
          </div>

          <p v-else class="mi hint">
            {{ isAnalyzed ? '확인된 불명확성 ' + detectedIssueCount + '건' : '아직 분석하지 않았습니다.' }}
          </p>

          <div class="btnrow">
            <PillButton
              variant="primary" :loading="isPolling"
              :disabled="isAnalyzeDisabled" @click="onAnalyze"
            >{{ analyzeButtonLabel }}</PillButton>
          </div>
        </div>

        <AnalysisFailureBanner
          v-if="failedAnalysis"
          :code="failedAnalysis.error?.code ?? ''"
          :message="failedAnalysis.error?.message ?? '분석에 실패했습니다.'"
          @retry="onRetry"
        />
      </div>

      <!-- 작업 열: 요구사항 목록 -->
      <div class="right" data-reveal>
        <div class="card list" data-reveal-stagger>
          <SectionLabel :text="'요구사항 ' + requirements.length + '건'" />

          <button
            v-for="requirement in displayedRequirements"
            :key="requirement.id"
            type="button"
            class="rrow row stagger-child"
            @click="openRequirement(requirement.id)"
          >
            <div class="rhead">
              <span class="fig seq">#{{ requirement.sequenceNo }}</span>
              <div class="rbody">
                <div class="rtext">{{ requirement.originalText }}</div>
                <div v-if="requirement.confirmedText" class="mi confirmed">
                  확정본 — {{ requirement.confirmedText }}
                </div>
              </div>
            </div>
            <div class="rmeta">
              <StatusBadge kind="requirement" :value="requirement.status" />
              <span class="fig ver">v{{ requirement.contentVersion }}</span>
              <!-- 모든 행의 동작이 화살표 하나로 같으면 무엇을 먼저 봐야 할지 알 수 없다.
                   답변이 필요한 것과 그냥 읽는 것을 문구로 가른다
                   (프론트엔드-추가-요청사항 4.1). -->
              <span class="mi act" :class="{ needs: needsAttention(requirement.status) }">
                {{ needsAttention(requirement.status) ? '불명확성 확인' : '요구사항 보기' }}
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
                  <path d="M5 12h14M13 6l6 6-6 6" />
                </svg>
              </span>
            </div>
          </button>

          <EmptyState
            v-if="requirements.length === 0"
            title="아직 요구사항이 없습니다"
            description="왼쪽에서 분석을 시작하면 요구사항이 추출됩니다."
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 참조 4 : 주 작업 8. 전 화면이 같은 비율을 쓴다. */
/* 요구사항이 수십 건이면 목록을 훑는 내내 원문이 필요하다. 참조 기둥은 따라오게 둔다. */
.left { grid-column: span 4; display: flex; flex-direction: column; gap: 18px; }
.right { grid-column: span 8; }

@media (max-width: 900px) {
  .left, .right { grid-column: span 12; }
}

.len { font-size: var(--fs-micro); color: var(--fg-600); }
.source { margin: 0; font-size: var(--fs-sm); line-height: 1.9; color: var(--fg-800); white-space: pre-wrap; word-break: keep-all; }
/* 진행 중 알림은 먹판(.sysnote)이라 여기서는 여백만 잡는다. */
.sysnote { margin-bottom: 14px; }
.hint { font-size: var(--fs-sm); color: var(--fg-600); line-height: 1.7; }
.btnrow { display: flex; gap: 8px; margin: 14px 0 2px; }

/* 한 줄에 여러 칼럼을 밀어넣으면 본문이 눌려 글자가 세로로 흐른다.
   본문은 위 한 줄을 통째로 쓰고, 계측값(상태·버전)은 아래 줄로 내린다. */
.rrow {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  padding: 16px 0 17px;
  border: 0;
  border-bottom: 1px solid var(--rule);
  background: none;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: background-color .25s var(--ease);
}

.rrow:hover { background: var(--bg-100); }

.rhead { display: grid; grid-template-columns: 38px minmax(0, 1fr) 20px; gap: 12px; align-items: start; }
.rbody { min-width: 0; }
.rmeta { display: flex; align-items: center; gap: 12px; padding-left: 50px; flex-wrap: wrap; }
/* 행의 동작 문구는 줄 끝으로 민다 — 배지·버전과 붙어 있으면 상태의 일부로 읽힌다. */
.act {
  display: inline-flex; align-items: center; gap: 6px;
  margin-left: auto;
  white-space: nowrap;
  /* 행이 하는 일은 「누를 수 있는 것」으로 보여야 한다. 회색 400은 설명글과 같은
     무게라 눌러도 되는지 알 수 없었다. 브랜드 남색에 굵기를 준다. */
  font-size: var(--fs-sm); font-weight: 700; color: var(--accent-700);
  transition: color .25s var(--ease);
}
/* 답변이 필요한 행만 동작 문구에 색을 준다. 왼쪽 색띠 대신 「할 일」 자체를 짚는다. */
.act.needs { color: var(--amber-tx); font-weight: 600; }
.rrow:hover .act { color: var(--primary-600); }
.rrow:hover .arrow { transform: translateX(3px); }
.arrow { transition: transform .3s var(--ease); }
.seq { font-size: 17px; color: var(--accent-700); line-height: 1.5; }
.rtext { font-size: var(--fs-sm); line-height: 1.7; color: var(--fg-950); word-break: keep-all; }
.confirmed { margin-top: 7px; font-size: var(--fs-micro); color: var(--green-tx); line-height: 1.6; word-break: keep-all; }
.ver { font-size: 14px; color: var(--fg-600); }
</style>

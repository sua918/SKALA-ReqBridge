<script setup>
/**
 * 요구사항 상세·검토 화면 (Spec 5.13~5.16 · 8절) — C 담당.
 *
 * 한 화면에서 세 가지 일을 한다.
 *   1) 문제마다 열린 질문에 고객 답변을 대신 입력한다 (PM 대리 입력)
 *   2) 재판정 작업을 1초 간격으로 조회한다
 *   3) 모든 문제가 풀려 수정안이 나오면 승인하거나 사유를 달아 거절한다
 *
 * 버전 규칙: 프론트는 contentVersion을 절대 +1 하지 않는다.
 * 화면이 들고 있는 값은 언제나 「직전 응답이 준 값」이고, 그대로 되돌려 보낸다.
 * 409면 workflow를 다시 읽고 입력창 내용은 지우지 않는다 (Spec 8절).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listDocumentAnalyses, retryAnalysis } from '@/api/analyses'
import { getRequirement } from '@/api/requirements'
import {
  getWorkflow,
  recreateRevision,
  reviewRevision,
  submitAnswer,
} from '@/api/workflow'
import { isContentVersionConflict } from '@/api/client'
import AnalysisFailureBanner from '@/components/common/AnalysisFailureBanner.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { resolveAmbiguityTypeLabel } from '@/components/common/ambiguityLabels.js'
import { useAnalysisPoller } from '@/composables/useAnalysisPoller'
import { useApiError } from '@/composables/useApiError'
import {
  AnalysisKind,
  AnalysisStatus,
  ClarificationStatus,
  IssueStatus,
  RequirementStatus,
  ReviewDecision,
  RevisionStatus,
} from '@/types/api'

const route = useRoute()
const requirementId = computed(() => Number(route.params.requirementId))

const loading = ref(true)
const requirement = ref(null)
const workflow = ref(null)
/** 답변 초안. 409·실패로 요청이 튕겨도 지우지 않는다. */
const answerDrafts = reactive({})
const rejectionReason = ref('')
const submittingClarificationId = ref(null)
/**
 * 버전 충돌만 따로 알린다. 다른 409(진행 중·이미 답변함)는 「지금은 안 된다」지만,
 * 버전 충돌은 「보고 있던 화면이 낡았다」라서 사용자가 할 일이 다르다 —
 * 새로 온 내용을 읽고 다시 판단해야 한다.
 */
const staleVersionNotice = ref(false)
const reviewing = ref(false)
const regenerating = ref(false)
/**
 * 이 요구사항의 **가장 최근 작업**. 분석 이력에서 읽어 온다.
 *
 * polling 메모리만 보면 새로고침 순간 실패 정보가 사라진다. 그러면 재시도 버튼이
 * 안 뜨는데, 질문은 이미 ANSWERED라 답변 재제출도 막혀서 워크플로우가 멈춘다.
 * workflow 응답의 activeAnalysis도 답이 못 된다 — FAILED는 「활성」이 아니라 항상 null이다.
 * 그래서 진입할 때마다 이력에서 직접 찾는다 (Spec 6.1 · 8절).
 */
const latestAnalysis = ref(null)

const { message, fieldErrors, hasError, captureError, clearError } = useApiError()
const { analysis: polledAnalysis, isPolling, start: startPolling } = useAnalysisPoller()

/**
 * 작업이 진행 중이면 이 요구사항의 답변·검토를 통째로 잠근다 (Spec 8절).
 * 다른 질문의 작업이 돌고 있어도 마찬가지다 — 판정 결과가 상태를 바꾸기 때문이다.
 */
const activeAnalysis = computed(() => {
  const polled = polledAnalysis.value
  if (polled && polled.status !== AnalysisStatus.COMPLETED && polled.status !== AnalysisStatus.FAILED) {
    return polled
  }
  return workflow.value?.activeAnalysis ?? null
})
const isLocked = computed(() => isPolling.value || activeAnalysis.value !== null)

/**
 * FAILED 작업은 재시도 버튼을 띄운다 (Spec 6.1).
 * 이번 화면에서 관측한 실패(polling)와 새로고침 뒤 이력에서 복구한 실패를 모두 본다.
 */
const failedAnalysis = computed(() => {
  if (polledAnalysis.value?.status === AnalysisStatus.FAILED) {
    return polledAnalysis.value
  }
  const restored = latestAnalysis.value
  return restored?.status === AnalysisStatus.FAILED ? restored : null
})

const contentVersion = computed(() => workflow.value?.contentVersion ?? null)
const status = computed(() => workflow.value?.status ?? requirement.value?.status ?? null)
const isConfirmed = computed(() => status.value === RequirementStatus.CONFIRMED)

/** 문제 하나에 회차 질문들을 묶어 보여준다. 질문만 나열하면 무엇을 묻는 중인지 흐려진다. */
const issueGroups = computed(() => {
  const wf = workflow.value
  if (!wf) {
    return []
  }
  return wf.issues.map((issue) => ({
    issue,
    rounds: wf.clarifications.filter((c) => c.issueId === issue.id),
  }))
})

const openIssueCount = computed(
  () => workflow.value?.issues.filter((i) => i.status === IssueStatus.OPEN).length ?? 0,
)
const waitingCount = computed(
  () =>
    workflow.value?.clarifications.filter((c) => c.status === ClarificationStatus.WAITING)
      .length ?? 0,
)

/** 검토 대상은 PROPOSED 하나뿐이다 (Spec 6.3 「PROPOSED 최대 1개」). */
const proposedRevision = computed(
  () => workflow.value?.revisions.find((r) => r.status === RevisionStatus.PROPOSED) ?? null,
)
/** 이력에서는 검토가 끝난 것만 따로 보여준다. */
const reviewedRevisions = computed(
  () => workflow.value?.revisions.filter((r) => r.status !== RevisionStatus.PROPOSED) ?? [],
)
const hasRejected = computed(() =>
  (workflow.value?.revisions ?? []).some((r) => r.status === RevisionStatus.REJECTED),
)

/**
 * 재생성 조건 (Spec 5.15): CLARIFYING · 모든 문제 RESOLVED ·
 * 활성 작업 없음 · PROPOSED 없음 · 거절 이력 있음.
 * 조건을 화면에서도 막아 불필요한 409 왕복을 줄인다.
 */
const canRegenerate = computed(
  () =>
    !isLocked.value &&
    status.value === RequirementStatus.CLARIFYING &&
    openIssueCount.value === 0 &&
    proposedRevision.value === null &&
    hasRejected.value,
)

function clarificationLabel(clarification) {
  return clarification.roundNo + '회차'
}

/** workflow와 기본 상세를 함께 다시 읽는다. 작업이 끝날 때마다 호출한다 (Spec 8절). */
async function reloadWorkflow() {
  const [next, detail] = await Promise.all([
    getWorkflow(requirementId.value),
    getRequirement(requirementId.value),
  ])
  workflow.value = next
  requirement.value = detail
  //작업 상태는 workflow가 다 알려주지 않는다. FAILED는 activeAnalysis에 안 잡히므로
  //이력에서 따로 읽는다. 실패한 문서 조회가 화면 전체를 막지는 않게 둔다.
  await refreshLatestAnalysis(detail.documentId)
}

/**
 * 이 요구사항의 최신 ANSWER/REVISION 작업을 분석 이력에서 찾는다.
 *
 * 이력 API는 문서 단위라 요구사항으로 한 번 더 거른다. 재시도까지 포함해
 * 항상 ID가 가장 큰 것이 현재 상태다 (B의 문서 상세와 같은 판정 방식).
 */
async function refreshLatestAnalysis(documentId) {
  if (documentId == null) {
    return
  }
  try {
    const data = await listDocumentAnalyses(documentId)
    const mine = (data.items ?? []).filter(
      (a) =>
        a.requirementId === requirementId.value &&
        (a.kind === AnalysisKind.ANSWER || a.kind === AnalysisKind.REVISION),
    )
    latestAnalysis.value = mine.reduce(
      (latest, item) => (latest === null || item.id > latest.id ? item : latest),
      null,
    )
  } catch {
    //이력을 못 읽어도 workflow 화면 자체는 열려 있어야 한다.
    latestAnalysis.value = null
  }
}

/** 진행 중 작업을 이어서 조회한다. 새로고침으로 들어와도 polling이 살아난다 (Spec 8절). */
function watchAnalysis(analysis) {
  if (!analysis) {
    return
  }
  if (analysis.status === AnalysisStatus.COMPLETED || analysis.status === AnalysisStatus.FAILED) {
    return
  }
  startPolling(analysis.id, {
    //작업이 끝났다고 오류 표시를 지우지 않는다. 방금 튕긴 409는 그 작업과 무관하고,
    //사용자는 「왜 내 요청이 안 들어갔는지」를 계속 보고 있어야 한다.
    //오류는 다음 사용자 동작이 시작될 때 clearError()로 지운다.
    onComplete: async () => {
      await reloadWorkflow()
    },
    //실패는 화면에 배너로 남긴다. 상태를 되돌리지 않고 재시도를 기다린다.
    onFailed: async () => {
      await reloadWorkflow()
    },
    onError: (error) => captureError(error),
  })
}

async function bootstrap() {
  loading.value = true
  clearError()
  try {
    await reloadWorkflow()
    //진행 중 작업은 workflow가, 실패는 이력이 알려준다. 둘 다 이어받는다.
    watchAnalysis(workflow.value?.activeAnalysis ?? latestAnalysis.value)
  } catch (error) {
    captureError(error)
  } finally {
    loading.value = false
  }
}

/**
 * 409면 최신 상태를 다시 읽는다. 입력값은 그대로 둔다 —
 * 사용자가 쓴 답변을 화면이 지워버리면 다시 타이핑해야 한다 (Spec 8절).
 */
async function recoverFromConflict(error) {
  const status = error?.apiError?.status
  if (status !== 409) {
    return
  }
  staleVersionNotice.value = isContentVersionConflict(error)
  try {
    await reloadWorkflow()
    watchAnalysis(workflow.value?.activeAnalysis)
  } catch (reloadError) {
    captureError(reloadError)
  }
}

async function onSubmitAnswer(clarification) {
  submittingClarificationId.value = clarification.id
  clearError()
  staleVersionNotice.value = false
  try {
    const receipt = await submitAnswer(clarification.id, {
      answerText: answerDrafts[clarification.id] ?? '',
      //직전 응답이 준 값을 그대로 돌려보낸다. 화면에서 +1 하지 않는다.
      expectedContentVersion: contentVersion.value,
    })
    //접수에 성공했으니 초안은 지운다. 저장된 답변은 아래 이력에 남는다.
    answerDrafts[clarification.id] = ''
    await reloadWorkflow()
    watchAnalysis(receipt.analysis)
  } catch (error) {
    captureError(error)
    await recoverFromConflict(error)
  } finally {
    submittingClarificationId.value = null
  }
}

async function onReview(decision) {
  const revision = proposedRevision.value
  if (!revision) {
    return
  }
  reviewing.value = true
  clearError()
  staleVersionNotice.value = false
  try {
    await reviewRevision(revision.id, {
      decision,
      expectedContentVersion: contentVersion.value,
      rejectionReason: rejectionReason.value,
    })
    rejectionReason.value = ''
    await reloadWorkflow()
  } catch (error) {
    captureError(error)
    await recoverFromConflict(error)
  } finally {
    reviewing.value = false
  }
}

async function onRegenerate() {
  regenerating.value = true
  clearError()
  staleVersionNotice.value = false
  try {
    const analysis = await recreateRevision(requirementId.value, {
      expectedContentVersion: contentVersion.value,
    })
    await reloadWorkflow()
    watchAnalysis(analysis)
  } catch (error) {
    captureError(error)
    await recoverFromConflict(error)
  } finally {
    regenerating.value = false
  }
}

/** 실패한 작업만 재시도한다. 새 POST로 형제 작업을 만들지 않는다 (Spec 6.3). */
async function onRetry() {
  const failed = failedAnalysis.value
  if (!failed) {
    return
  }
  clearError()
  try {
    const analysis = await retryAnalysis(failed.id)
    await reloadWorkflow()
    watchAnalysis(analysis)
  } catch (error) {
    captureError(error)
  }
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <section class="wf-page">
    <header class="wf-header">
      <h1>요구사항 #{{ requirement?.sequenceNo ?? requirementId }}</h1>
      <p class="wf-meta">
        요구사항 #{{ requirementId }}
        <template v-if="status">
          ·
          <StatusBadge kind="requirement" :value="status" />
        </template>
        <template v-if="contentVersion !== null"> · 버전 v{{ contentVersion }}</template>
      </p>
    </header>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <p v-if="staleVersionNotice" class="notice notice--stale">
      다른 변경이 먼저 반영되어 최신 내용을 다시 불러왔습니다. 입력하신 내용은 그대로
      두었으니, 바뀐 내용을 확인하고 다시 보내주세요.
    </p>

    <p v-if="loading">불러오는 중…</p>

    <template v-else-if="workflow">
      <section class="panel">
        <h2>원문</h2>
        <pre class="content">{{ requirement?.originalText }}</pre>
      </section>

      <section v-if="requirement?.confirmedText" class="panel panel--confirmed">
        <h2>확정본</h2>
        <pre class="content">{{ requirement.confirmedText }}</pre>
        <p class="hint">승인한 수정안 #{{ requirement.approvedRevisionId }}의 본문입니다.</p>
      </section>

      <p v-if="isLocked" class="notice notice--busy">
        분석 작업 {{ activeAnalysis?.id }}이(가) 진행 중입니다. 끝날 때까지 답변 입력과 검토를
        잠급니다.
      </p>

      <AnalysisFailureBanner
        v-if="failedAnalysis"
        :code="failedAnalysis.error?.code ?? ''"
        :message="failedAnalysis.error?.message ?? '작업에 실패했습니다.'"
        @retry="onRetry"
      />

      <section class="panel">
        <h2>문제와 질문</h2>
        <p class="summary">
          미해결 문제 {{ openIssueCount }}건 · 답변 대기 질문 {{ waitingCount }}건
        </p>

        <p v-if="issueGroups.length === 0" class="empty">
          이 요구사항에는 확인할 문제가 없습니다.
        </p>

        <article v-for="group in issueGroups" :key="group.issue.id" class="issue">
          <div class="issue-head">
            <span class="issue-type">{{ resolveAmbiguityTypeLabel(group.issue.type) }}</span>
            <StatusBadge kind="issue" :value="group.issue.status" />
            <span class="issue-id">문제 #{{ group.issue.id }}</span>
          </div>
          <p class="issue-evidence">근거 — {{ group.issue.evidence }}</p>

          <p v-if="group.rounds.length === 0" class="empty">
            아직 이 문제로 만들어진 질문이 없습니다.
          </p>

          <div v-for="round in group.rounds" :key="round.id" class="round">
            <div class="round-head">
              <span class="round-no">{{ clarificationLabel(round) }}</span>
              <StatusBadge kind="clarification" :value="round.status" />
            </div>
            <p class="question">{{ round.questionText }}</p>

            <p v-if="round.answerText" class="answer">{{ round.answerText }}</p>

            <!-- 답변 입력창이 열리는 유일한 상태는 WAITING (Spec 8절) -->
            <div v-if="round.status === ClarificationStatus.WAITING" class="answer-form">
              <label class="sr-only" :for="`answer-${round.id}`">답변</label>
              <textarea
                :id="`answer-${round.id}`"
                v-model="answerDrafts[round.id]"
                class="answer-input"
                rows="3"
                :disabled="isLocked || isConfirmed"
                placeholder="고객이 준 답변을 그대로 입력하세요."
              />
              <div class="answer-actions">
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="
                    isLocked ||
                    isConfirmed ||
                    submittingClarificationId === round.id ||
                    !(answerDrafts[round.id] ?? '').trim()
                  "
                  @click="onSubmitAnswer(round)"
                >
                  {{ submittingClarificationId === round.id ? '보내는 중…' : '답변 제출' }}
                </button>
              </div>
            </div>
          </div>
        </article>
      </section>

      <section class="panel">
        <h2>수정안</h2>

        <p v-if="!proposedRevision && reviewedRevisions.length === 0" class="empty">
          모든 문제가 해결되면 수정안이 만들어집니다.
        </p>

        <article v-if="proposedRevision" class="revision revision--proposed">
          <div class="revision-head">
            <span class="revision-no">{{ proposedRevision.revisionNo }}차 수정안</span>
            <StatusBadge kind="revision" :value="proposedRevision.status" />
            <span class="revision-version">
              입력 버전 v{{ proposedRevision.inputContentVersion }}
            </span>
          </div>
          <pre class="content">{{ proposedRevision.text }}</pre>
          <p v-if="proposedRevision.basedOnClarificationIds.length" class="hint">
            근거 답변 — 질문 {{ proposedRevision.basedOnClarificationIds.join(', ') }}
          </p>

          <div class="review">
            <label class="review-label" for="rejection-reason">
              거절 사유 · 거절할 때만 필요합니다
            </label>
            <textarea
              id="rejection-reason"
              v-model="rejectionReason"
              class="answer-input"
              rows="2"
              :disabled="isLocked || reviewing"
              placeholder="어떤 점을 고쳐야 하는지 적어주세요. 최대 2000자."
            />
            <div class="review-actions">
              <button
                type="button"
                class="btn-primary"
                :disabled="isLocked || reviewing"
                @click="onReview(ReviewDecision.APPROVE)"
              >
                승인
              </button>
              <button
                type="button"
                class="btn-secondary"
                :disabled="isLocked || reviewing || !rejectionReason.trim()"
                @click="onReview(ReviewDecision.REJECT)"
              >
                거절
              </button>
            </div>
          </div>
        </article>

        <div v-if="canRegenerate" class="regenerate">
          <p class="hint">
            거절한 수정안이 있고 모든 문제가 해결됐습니다. 거절 사유를 반영해 다시 만들 수 있습니다.
          </p>
          <button
            type="button"
            class="btn-primary"
            :disabled="regenerating"
            @click="onRegenerate"
          >
            {{ regenerating ? '요청 중…' : '수정안 다시 만들기' }}
          </button>
        </div>

        <article
          v-for="revision in reviewedRevisions"
          :key="revision.id"
          class="revision revision--past"
        >
          <div class="revision-head">
            <span class="revision-no">{{ revision.revisionNo }}차 수정안</span>
            <StatusBadge kind="revision" :value="revision.status" />
            <span class="revision-version">입력 버전 v{{ revision.inputContentVersion }}</span>
          </div>
          <pre class="content">{{ revision.text }}</pre>
          <p v-if="revision.rejectionReason" class="rejection">
            거절 사유 — {{ revision.rejectionReason }}
          </p>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped>
.wf-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.wf-header h1 {
  margin: 0 0 6px;
  font-size: 1.5rem;
}

.wf-meta {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  color: #64748b;
  font-size: 0.875rem;
}

.panel {
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 1rem;
}

.panel--confirmed {
  border-color: #86efac;
  background: #f0fdf4;
}

.content {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f8fafc;
  font-family: inherit;
  font-size: 0.9375rem;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: keep-all;
}

.summary {
  margin: 0 0 14px;
  color: #475569;
  font-size: 0.875rem;
}

.empty {
  margin: 0;
  color: #94a3b8;
  font-size: 0.875rem;
}

.hint {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 0.8125rem;
}

.notice {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 0.875rem;
}

.notice--busy {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.notice--stale {
  border: 1px solid #fcd34d;
  background: #fffbeb;
  color: #92400e;
  line-height: 1.6;
  word-break: keep-all;
}

.issue {
  padding: 14px 0 4px;
  border-top: 1px solid #f1f5f9;
}

.issue:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.issue-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.issue-type {
  font-weight: 600;
  font-size: 0.9375rem;
}

.issue-id {
  color: #94a3b8;
  font-size: 0.8125rem;
}

.issue-evidence {
  margin: 6px 0 12px;
  color: #475569;
  font-size: 0.875rem;
}

.round {
  margin-left: 12px;
  padding: 10px 0 12px 12px;
  border-left: 2px solid #e2e8f0;
}

.round-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.round-no {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #475569;
}

.question {
  margin: 6px 0 0;
  font-size: 0.9375rem;
  line-height: 1.6;
  word-break: keep-all;
}

.answer {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 0.875rem;
  line-height: 1.6;
  word-break: keep-all;
}

.answer-form {
  margin-top: 10px;
}

.answer-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font: inherit;
  font-size: 0.875rem;
  line-height: 1.6;
  resize: vertical;
}

.answer-input:disabled {
  background: #f8fafc;
  color: #94a3b8;
}

.answer-actions,
.review-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.revision {
  padding: 14px 0;
  border-top: 1px solid #f1f5f9;
}

.revision:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.revision--past .content {
  color: #64748b;
}

.revision-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.revision-no {
  font-weight: 600;
  font-size: 0.9375rem;
}

.revision-version {
  color: #94a3b8;
  font-size: 0.8125rem;
}

.rejection {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fef2f2;
  color: #991b1b;
  font-size: 0.875rem;
  line-height: 1.6;
  word-break: keep-all;
}

.review {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
}

.review-label {
  display: block;
  margin-bottom: 6px;
  color: #475569;
  font-size: 0.8125rem;
}

.regenerate {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.regenerate .hint {
  margin: 0 0 10px;
}

.btn-primary,
.btn-secondary {
  padding: 8px 16px;
  border-radius: 8px;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.btn-secondary {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #b91c1c;
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>

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
import EmptyState from '@/components/common/EmptyState.vue'
import KeyValueRows from '@/components/common/KeyValueRows.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHero from '@/components/common/PageHero.vue'
import PillButton from '@/components/common/PillButton.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AmbiguityTypeBadge from '@/components/common/AmbiguityTypeBadge.vue'
import ClarityJourney from '@/components/graphics/ClarityJourney.vue'
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

/** 히어로에 얹는 요약. 표시용이라 조회 로직과 무관하다. */
const chips = computed(() => [
  { value: String(openIssueCount.value), label: '미해결 문제' },
  { value: String(waitingCount.value), label: '답변 대기' },
  { value: String(workflow.value?.revisions.length ?? 0), label: '수정안' },
])

/** 참조 열의 메타 표. 값은 전부 이미 받아 둔 응답에서 꺼낸다. */
const metaRows = computed(() => [
  { label: '요구사항', value: `${requirementId.value} · 순번 #${requirement.value?.sequenceNo ?? '-'}` },
  { label: '문서', value: requirement.value?.documentId ?? '-' },
  { label: '버전', value: contentVersion.value === null ? '-' : `v${contentVersion.value}` },
  { label: '활성 작업', value: activeAnalysis.value ? `#${activeAnalysis.value.id}` : '없음' },
])

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
  <div class="page">
    <PageHero
      num="04" eyebrow="요구사항 워크플로우" watermark="R" :chips="chips"
    >
      <template #title>Requirement</template>
      <!-- 상태 배지는 #actions(버튼 자리)가 아니라 제목 옆에 둔다.
           그 자리는 「누르는 것」 자리이고, 배지는 못 누른다. 그래픽 위에 얹히면
           바탕이 계속 변해 읽히는 정도도 일정하지 않다. -->
      <template #subject>
        <span class="subjline">
          #{{ requirement?.sequenceNo ?? requirementId }}
          <StatusBadge v-if="status" kind="requirement" :value="status" />
        </span>
      </template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <p v-if="staleVersionNotice" class="notice notice--stale">
      다른 변경이 먼저 반영되어 최신 내용을 다시 불러왔습니다. 입력하신 내용은 그대로
      두었으니, 바뀐 내용을 확인하고 다시 보내주세요.
    </p>

    <LoadingSpinner v-if="loading" />

    <div v-else-if="workflow" class="grid12 split at-4">
      <!-- 참조 열 -->
      <div class="left stickycol" data-reveal>
        <!-- 참조 기둥의 원문·여정·메타는 전부 「읽는 곳」이다. -->
        <div class="card pad quiet">
          <div class="eb mb12">원문</div>
          <p class="source">{{ requirement?.originalText }}</p>
        </div>

        <div v-if="requirement?.confirmedText" class="card pad confirmed-card">
          <div class="cardhead">
            <span class="eb">확정본</span>
            <StatusBadge kind="revision" value="APPROVED" />
          </div>
          <p class="source">{{ requirement.confirmedText }}</p>
          <p class="mi hint">승인한 수정안 #{{ requirement.approvedRevisionId }}의 본문입니다.</p>
        </div>

        <div class="card pad quiet journey">
          <div class="cardhead">
            <span class="eb">명확도 여정</span>
            <span v-if="contentVersion !== null" class="fig ver">v{{ contentVersion }}</span>
          </div>
          <ClarityJourney v-if="status" :status="status" />
        </div>

        <KeyValueRows label="이 요구사항" :rows="metaRows" label-width="106px" class="quiet" />
      </div>

      <!-- 작업 열 -->
      <div class="right" data-reveal>
        <div v-if="isLocked" class="sysnote" style="--tone: var(--on-blue)">
          <span class="scan" aria-hidden="true" />
          <div class="body">
            <div class="head"><span class="k">분석 처리 중</span>
              <span class="code">작업 {{ activeAnalysis?.id }}</span>
            </div>
            <p class="msg">끝날 때까지 답변 입력과 검토를 잠급니다.</p>
          </div>
        </div>

        <AnalysisFailureBanner
          v-if="failedAnalysis"
          :code="failedAnalysis.error?.code ?? ''"
          :message="failedAnalysis.error?.message ?? '작업에 실패했습니다.'"
          @retry="onRetry"
        />

        <!-- 문제 · 질문 -->
        <div v-for="group in issueGroups" :key="group.issue.id" class="card pad issue">
          <div class="issuehead">
            <span class="fig inum">{{ group.issue.id }}</span>
            <AmbiguityTypeBadge :type="group.issue.type" />
            <StatusBadge kind="issue" :value="group.issue.status" />
          </div>
          <p class="evidence">근거 — {{ group.issue.evidence }}</p>

          <div v-for="round in group.rounds" :key="round.id" class="round">
            <div class="roundhead">
              <span class="eb sm">{{ clarificationLabel(round) }}</span>
              <StatusBadge kind="clarification" :value="round.status" />
            </div>
            <p class="question">{{ round.questionText }}</p>

            <div v-if="round.answerText" class="answered">{{ round.answerText }}</div>

            <!-- 답변 입력창이 열리는 유일한 상태는 WAITING -->
            <div v-if="round.status === ClarificationStatus.WAITING" class="answerbox">
              <textarea
                v-model="answerDrafts[round.id]" class="field ta" rows="3"
                :disabled="isLocked || isConfirmed"
                placeholder="고객이 준 답변을 그대로 입력하세요."
              />
              <div class="answerfoot">
                <PillButton
                  variant="primary"
                  :loading="submittingClarificationId === round.id"
                  :disabled="isLocked || isConfirmed || !(answerDrafts[round.id] ?? '').trim()"
                  @click="onSubmitAnswer(round)"
                >답변 제출</PillButton>
              </div>
            </div>
          </div>
        </div>

        <EmptyState
          v-if="issueGroups.length === 0"
          title="확인할 문제가 없습니다"
          description="이 요구사항에는 불명확한 표현이 발견되지 않았습니다."
        />

        <!-- 검토 대기 수정안 -->
        <div v-if="proposedRevision" class="card pad revision live">
          <div class="cardhead">
            <span class="eb">수정안 {{ proposedRevision.id }} · {{ proposedRevision.revisionNo }}차</span>
            <StatusBadge kind="revision" :value="proposedRevision.status" />
          </div>
          <p class="revtext">{{ proposedRevision.text }}</p>
          <p v-if="proposedRevision.basedOnClarificationIds.length" class="mi hint">
            근거 답변 — 질문 {{ proposedRevision.basedOnClarificationIds.join(', ') }}
            · 입력 버전 v{{ proposedRevision.inputContentVersion }}
          </p>

          <div class="review">
            <span class="eb sm">거절 사유 · 거절할 때만 필요합니다</span>
            <textarea
              v-model="rejectionReason" class="field ta" rows="2" :disabled="isLocked || reviewing"
              placeholder="어떤 점을 고쳐야 하는지 적어주세요. 최대 2000자."
            />
            <div class="reviewfoot">
              <PillButton
                variant="quiet" :disabled="isLocked || reviewing || !rejectionReason.trim()"
                @click="onReview(ReviewDecision.REJECT)"
              >거절</PillButton>
              <PillButton
                variant="primary" :loading="reviewing" :disabled="isLocked"
                @click="onReview(ReviewDecision.APPROVE)"
              >승인</PillButton>
            </div>
          </div>
        </div>

        <div v-if="canRegenerate" class="card pad quiet">
          <p class="mi hint mb12">
            거절한 수정안이 있고 모든 문제가 해결됐습니다. 거절 사유를 반영해 다시 만들 수 있습니다.
          </p>
          <PillButton variant="primary" :loading="regenerating" @click="onRegenerate">
            수정안 다시 만들기
          </PillButton>
        </div>

        <!-- 검토를 마친 수정안 이력 -->
        <div v-for="revision in reviewedRevisions" :key="revision.id" class="card pad revision past">
          <div class="cardhead">
            <span class="eb">수정안 {{ revision.id }} · {{ revision.revisionNo }}차</span>
            <StatusBadge kind="revision" :value="revision.status" />
          </div>
          <p class="revtext">{{ revision.text }}</p>
          <p v-if="revision.rejectionReason" class="rejection">
            거절 사유 — {{ revision.rejectionReason }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 참조 4 : 주 작업 8. 전 화면이 같은 비율을 쓴다. */
.left { grid-column: span 4; display: flex; flex-direction: column; gap: 18px; }
.right { grid-column: span 8; display: flex; flex-direction: column; gap: 18px; }

@media (max-width: 900px) {
  .left, .right { grid-column: span 12; position: static; }
}

/* 제목 옆 상태. 부제 서체가 크므로 배지가 글자에 눌리지 않게 가운데를 맞춘다. */
.subjline { display: inline-flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.mb12 { margin-bottom: 12px; }
.source { margin: 0; font-size: var(--fs-sm); line-height: 1.9; color: var(--fg-800); word-break: keep-all; }
.hint { font-size: var(--fs-sm); color: var(--fg-600); line-height: 1.7; margin: 10px 0 0; }
.hint.mb12 { margin: 0 0 12px; }
.ver { font-size: var(--fs-sm); color: var(--fg-600); }

/* 확정본은 「끝난 것」이라 초록 계열로 한 번만 표시한다. */
.confirmed-card { border-color: var(--green-bd); background: color-mix(in srgb, var(--green-bg) 45%, var(--bg-0)); }
.journey { padding-bottom: 22px; }

.notice {
  margin: 0 0 18px;
  padding: 13px 16px;
  border-radius: var(--radius-card);
  font-size: var(--fs-sm);
  line-height: 1.7;
  word-break: keep-all;
}
.notice--stale { border: 1px solid var(--amber-bd); background: var(--amber-bg); color: var(--amber-tx); }

.issuehead { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.inum { font-size: 15px; color: var(--fg-400); }
.evidence { margin: 0 0 4px; font-size: var(--fs-sm); color: var(--fg-600); line-height: 1.7; word-break: keep-all; }

/* 회차는 문제 아래에 딸린 대화다. 들여쓰기로 소속을 보인다. */
.round { margin-top: 16px; padding-top: 15px; border-top: 1px solid var(--rule); }
.roundhead { display: flex; align-items: center; gap: 9px; margin-bottom: 8px; }
.eb.sm { font-size: var(--fs-micro); }
.question { margin: 0; font-size: var(--fs-sm); line-height: 1.75; color: var(--fg-950); word-break: keep-all; }

.answered {
  margin-top: 10px;
  padding: 11px 14px;
  border-radius: var(--radius-card);
  background: var(--bg-100);
  border: 1px solid var(--bg-200);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--fg-700);
  word-break: keep-all;
}

.answerbox { margin-top: 12px; }
.ta { resize: vertical; line-height: 1.7; }
.answerfoot, .reviewfoot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }

.revtext { margin: 0; font-size: var(--fs-lead); line-height: 1.8; color: var(--fg-950); word-break: keep-all; }
.revision.past .revtext { font-size: var(--fs-sm); color: var(--fg-600); }

.review { margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--rule); }
.review .eb.sm { display: block; margin-bottom: 8px; }

.rejection {
  margin: 12px 0 0;
  padding: 11px 14px;
  border-radius: var(--radius-card);
  background: var(--red-bg);
  border: 1px solid var(--red-bd);
  color: var(--red-tx);
  font-size: var(--fs-sm);
  line-height: 1.7;
  word-break: keep-all;
}
</style>

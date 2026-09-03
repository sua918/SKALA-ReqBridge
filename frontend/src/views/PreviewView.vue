<script setup>
/**
 * 결과 Preview 화면 (Spec 5.17~5.18 · 8절) — C P2 담당.
 *
 * 두 벌의 같은 내용을 서로 다른 독자에게 보여준다.
 *   고객용   지금 답이 필요한 질문만. 물을 게 없는 요구사항은 빠진다.
 *   개발팀용 확정본과 그 근거, 그리고 아직 확정 안 된 것들의 논의 이력.
 *
 * 만들어 내는 화면이 아니라 저장된 것을 조합해 보여주는 읽기 전용이다.
 * 다운로드 버튼은 제공하지 않는다 (Spec 8절).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getCustomerPreview, getDeveloperPreview } from '@/api/previews'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { resolveAmbiguityTypeLabel } from '@/components/common/ambiguityLabels.js'
import { useApiError } from '@/composables/useApiError'

const route = useRoute()
const documentId = computed(() => Number(route.params.documentId))

const TABS = [
  { key: 'customer', label: '고객 질문서' },
  { key: 'developer', label: '개발팀용' },
]

const activeTab = ref('customer')
const loading = ref(true)
const preview = ref(null)

const { message, fieldErrors, hasError, captureError, clearError } = useApiError()

const summary = computed(() => preview.value?.summary ?? null)
const isCustomer = computed(() => activeTab.value === 'customer')

/**
 * 고객용 requirements가 비어 있다고 「다 끝났다」로 읽으면 안 된다.
 * 분석 전·처리 중·실패·검토 대기일 수도 있다 (Spec 9절 CustomerPreview).
 */
const customerEmptyNote = computed(() => {
  const s = summary.value
  if (!s) {
    return ''
  }
  if (s.totalRequirements === 0) {
    return '아직 추출된 요구사항이 없습니다. 문서 분석을 먼저 실행해주세요.'
  }
  if (s.confirmedRequirements === s.totalRequirements) {
    return '모든 요구사항이 확정됐습니다. 고객에게 더 물을 것이 없습니다.'
  }
  return '지금 고객에게 물을 질문이 없습니다. 분석이 진행 중이거나 수정안 검토를 기다리는 중일 수 있습니다.'
})

/** basis는 조회 시점의 버전 스냅샷이다. 어느 버전을 보고 있는지 화면에 남긴다 (Spec 6.4). */
const basis = computed(() => preview.value?.basis ?? [])

/**
 * 늦게 도착한 응답을 버리기 위한 세대 번호.
 *
 * 두 탭은 서로 다른 endpoint라 응답 모양도 다르다. 탭을 빠르게 바꿨을 때
 * 먼저 보낸 고객용 응답이 나중에 도착하면, 개발팀 탭에 고객용 데이터가 들어앉는다.
 * 그 상태에서 템플릿이 `confirmedRequirements.length`를 읽으면 화면이 깨진다.
 * 요청할 때 세대를 올려 두고, 돌아왔을 때 세대가 바뀌었으면 결과를 버린다.
 */
let loadGeneration = 0

async function load() {
  const generation = ++loadGeneration
  const wantsCustomer = isCustomer.value

  loading.value = true
  clearError()
  //다른 탭의 데이터를 잠시라도 현재 탭 모양으로 그리지 않는다.
  preview.value = null

  try {
    const next = wantsCustomer
      ? await getCustomerPreview(documentId.value)
      : await getDeveloperPreview(documentId.value)
    if (generation !== loadGeneration) {
      return
    }
    preview.value = next
  } catch (error) {
    if (generation !== loadGeneration) {
      return
    }
    preview.value = null
    captureError(error)
  } finally {
    //뒤늦은 응답이 로딩 표시를 끄면, 아직 오지 않은 최신 요청이 끝난 것처럼 보인다.
    if (generation === loadGeneration) {
      loading.value = false
    }
  }
}

//탭을 바꾸면 다른 endpoint라 다시 조회한다. 두 응답을 섞어 들고 있지 않는다.
watch(activeTab, () => {
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="pv-page">
    <header class="pv-header">
      <h1>{{ preview?.documentTitle ?? 'Preview' }}</h1>
      <p class="pv-meta">
        문서 #{{ documentId }}
        <template v-if="preview"> · 조회 시점 {{ preview.generatedAt }}</template>
      </p>
    </header>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        role="tab"
        class="tab"
        :class="{ 'tab--on': activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <p v-if="loading">불러오는 중…</p>

    <template v-else-if="preview">
      <section v-if="summary" class="panel">
        <h2>요약</h2>
        <dl class="summary">
          <div><dt>요구사항</dt><dd>{{ summary.totalRequirements }}</dd></div>
          <div><dt>확정</dt><dd>{{ summary.confirmedRequirements }}</dd></div>
          <div><dt>미해결 문제</dt><dd>{{ summary.openIssueCount }}</dd></div>
          <div><dt>답변 대기</dt><dd>{{ summary.waitingQuestionCount }}</dd></div>
        </dl>
      </section>

      <!-- 고객 질문서 -->
      <section v-if="isCustomer" class="panel">
        <h2>지금 답이 필요한 질문</h2>
        <p v-if="preview.requirements.length === 0" class="empty">
          {{ customerEmptyNote }}
        </p>

        <article
          v-for="item in preview.requirements"
          :key="item.requirementId"
          class="req"
        >
          <div class="req-head">
            <span class="req-no">#{{ item.sequenceNo }}</span>
            <span class="req-version">v{{ item.contentVersion }}</span>
          </div>
          <p class="req-text">{{ item.originalText }}</p>

          <div v-for="question in item.questions" :key="question.id" class="question">
            <div class="question-head">
              <span class="question-type">{{ resolveAmbiguityTypeLabel(question.type) }}</span>
              <span class="question-round">{{ question.roundNo }}회차</span>
            </div>
            <p class="question-text">{{ question.questionText }}</p>
            <p class="question-evidence">근거 — {{ question.evidence }}</p>
          </div>
        </article>
      </section>

      <!-- 개발팀용 -->
      <template v-else>
        <section class="panel">
          <h2>확정 요구사항</h2>
          <p v-if="preview.confirmedRequirements.length === 0" class="empty">
            아직 확정된 요구사항이 없습니다.
          </p>

          <article
            v-for="item in preview.confirmedRequirements"
            :key="item.requirementId"
            class="req"
          >
            <div class="req-head">
              <span class="req-no">#{{ item.sequenceNo }}</span>
              <StatusBadge kind="revision" :value="item.approvedRevision.status" />
              <span class="req-version">v{{ item.contentVersion }}</span>
            </div>

            <p class="label">확정본</p>
            <pre class="content content--confirmed">{{ item.approvedRevision.text }}</pre>

            <p class="label">원문</p>
            <pre class="content">{{ item.originalText }}</pre>

            <p class="label">
              근거 답변 · {{ item.approvedRevision.revisionNo }}차 수정안의
              basedOnClarificationIds
            </p>
            <div v-for="answer in item.evidenceAnswers" :key="answer.id" class="evidence">
              <div class="evidence-head">
                <span class="question-round">{{ answer.roundNo }}회차</span>
                <StatusBadge kind="clarification" :value="answer.status" />
                <span class="evidence-id">질문 #{{ answer.id }}</span>
              </div>
              <p class="question-text">{{ answer.questionText }}</p>
              <p class="answer-text">{{ answer.answerText }}</p>
            </div>
          </article>
        </section>

        <section class="panel">
          <h2>미확정 요구사항</h2>
          <p v-if="preview.unconfirmedRequirements.length === 0" class="empty">
            미확정 요구사항이 없습니다.
          </p>

          <article
            v-for="item in preview.unconfirmedRequirements"
            :key="item.requirementId"
            class="req"
          >
            <div class="req-head">
              <span class="req-no">#{{ item.sequenceNo }}</span>
              <StatusBadge kind="requirement" :value="item.status" />
              <span class="req-version">v{{ item.contentVersion }}</span>
            </div>
            <p class="req-text">{{ item.originalText }}</p>

            <p class="label">문제 이력</p>
            <div v-for="issue in item.issues" :key="issue.id" class="issue-row">
              <span class="question-type">{{ resolveAmbiguityTypeLabel(issue.type) }}</span>
              <StatusBadge kind="issue" :value="issue.status" />
              <span class="evidence-id">#{{ issue.id }}</span>
              <span class="issue-evidence">{{ issue.evidence }}</span>
            </div>

            <p class="label">질문·답변 이력</p>
            <div v-for="question in item.questions" :key="question.id" class="evidence">
              <div class="evidence-head">
                <span class="question-round">{{ question.roundNo }}회차</span>
                <StatusBadge kind="clarification" :value="question.status" />
                <span class="evidence-id">질문 #{{ question.id }}</span>
              </div>
              <p class="question-text">{{ question.questionText }}</p>
              <p v-if="question.answerText" class="answer-text">{{ question.answerText }}</p>
              <p v-else class="empty">아직 답변이 없습니다.</p>
            </div>
          </article>
        </section>
      </template>

      <section class="panel">
        <h2>조회 기준 버전</h2>
        <p class="hint">
          이 Preview는 아래 버전으로 조합했습니다. 이후 답변·검토가 있었다면 다시 조회해야
          합니다.
        </p>
        <ul class="basis">
          <li v-for="row in basis" :key="row.requirementId">
            요구사항 #{{ row.requirementId }} · v{{ row.contentVersion }}
            <template v-if="row.approvedRevisionId">
              · 승인 수정안 #{{ row.approvedRevisionId }}
            </template>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.pv-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pv-header h1 {
  margin: 0 0 6px;
  font-size: 1.5rem;
  word-break: keep-all;
}

.pv-meta {
  margin: 0;
  color: #64748b;
  font-size: 0.875rem;
}

.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: #f1f5f9;
  align-self: flex-start;
}

.tab {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.tab--on {
  background: #ffffff;
  color: #1d4ed8;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.08);
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

.summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin: 0;
}

.summary div {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.summary dt {
  color: #64748b;
  font-size: 0.75rem;
}

.summary dd {
  margin: 4px 0 0;
  font-size: 1.25rem;
  font-weight: 700;
}

.req {
  padding: 14px 0;
  border-top: 1px solid #f1f5f9;
}

.req:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.req-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.req-no {
  font-weight: 700;
  color: #1d4ed8;
}

.req-version {
  color: #94a3b8;
  font-size: 0.8125rem;
}

.req-text {
  margin: 0;
  font-size: 0.9375rem;
  line-height: 1.7;
  word-break: keep-all;
}

.label {
  margin: 14px 0 6px;
  color: #64748b;
  font-size: 0.75rem;
  font-weight: 600;
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

.content--confirmed {
  border: 1px solid #86efac;
  background: #f0fdf4;
}

.question,
.evidence {
  margin-top: 10px;
  padding: 10px 0 0 12px;
  border-left: 2px solid #e2e8f0;
}

.question-head,
.evidence-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.question-type {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #b45309;
}

.question-round {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #475569;
}

.question-text {
  margin: 6px 0 0;
  font-size: 0.9375rem;
  line-height: 1.6;
  word-break: keep-all;
}

.question-evidence {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 0.8125rem;
  line-height: 1.6;
  word-break: keep-all;
}

.answer-text {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 0.875rem;
  line-height: 1.6;
  word-break: keep-all;
}

.evidence-id,
.issue-evidence {
  color: #94a3b8;
  font-size: 0.8125rem;
}

.issue-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0;
  border-bottom: 1px solid #f8fafc;
}

.issue-evidence {
  flex: 1 1 100%;
  color: #64748b;
  line-height: 1.6;
  word-break: keep-all;
}

.basis {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 0.875rem;
  line-height: 1.8;
}

.hint {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 0.8125rem;
  line-height: 1.6;
  word-break: keep-all;
}

.empty {
  margin: 0;
  color: #94a3b8;
  font-size: 0.875rem;
  line-height: 1.6;
  word-break: keep-all;
}
</style>

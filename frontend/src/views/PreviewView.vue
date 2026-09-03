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
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHero from '@/components/common/PageHero.vue'
import SectionLabel from '@/components/common/SectionLabel.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AmbiguityTypeBadge from '@/components/common/AmbiguityTypeBadge.vue'
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
 * basis는 요구사항을 저장번호로만 가리킨다. 화면 위쪽 카드들은 전부 문서 내 순번(`#3`)으로
 * 부르는데 여기만 `요구사항 10`이라 적으면, 같은 것을 두 이름으로 부르게 된다.
 * 이미 받아 둔 목록에서 순번을 찾아 같은 이름으로 맞춘다.
 */
const sequenceById = computed(() => {
  const p = preview.value
  if (!p) return new Map()
  const lists = [p.requirements, p.confirmedRequirements, p.unconfirmedRequirements]
  const map = new Map()
  for (const list of lists) {
    for (const item of list ?? []) map.set(item.requirementId, item.sequenceNo)
  }
  return map
})

/** 순번을 못 찾으면 저장번호를 대신 쓰지 않는다 — 뜻이 다른 수를 같은 자리에 넣는 셈이다. */
function sequenceOf(requirementId) {
  return sequenceById.value.get(requirementId) ?? '-'
}

/**
 * 늦게 도착한 응답을 버리기 위한 세대 번호.
 *
 * 두 탭은 서로 다른 endpoint라 응답 모양도 다르다. 탭을 빠르게 바꿨을 때
 * 먼저 보낸 고객용 응답이 나중에 도착하면, 개발팀 탭에 고객용 데이터가 들어앉는다.
 * 그 상태에서 템플릿이 `confirmedRequirements.length`를 읽으면 화면이 깨진다.
 * 요청할 때 세대를 올려 두고, 돌아왔을 때 세대가 바뀌었으면 결과를 버린다.
 */
/** 히어로에 얹는 요약. summary 응답을 그대로 옮긴 것이라 계산이 없다. */
const chips = computed(() => {
  const s = summary.value
  if (!s) {
    return []
  }
  return [
    { value: String(s.totalRequirements), label: '요구사항' },
    { value: String(s.confirmedRequirements), label: '확정' },
    { value: String(s.openIssueCount), label: '미해결 문제' },
    { value: String(s.waitingQuestionCount), label: '답변 대기' },
  ]
})

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
  <div class="page">
    <PageHero
      num="05"
      :eyebrow="isCustomer ? '고객 질문서' : '개발팀용'"
      watermark="P"
      :chips="chips"
    >
      <template #title>문서 미리보기</template>
      <template #subject>{{ preview?.documentTitle ?? '문서 #' + documentId }}</template>
      <template #actions>
        <div class="tabs" role="tablist">
          <button
            v-for="tab in TABS" :key="tab.key" type="button" role="tab"
            class="tab" :class="{ 'tab--on': activeTab === tab.key }"
            :aria-selected="activeTab === tab.key"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>
      </template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <LoadingSpinner v-if="loading" />

    <div v-else-if="preview" class="grid12">
      <div class="main" data-reveal>
        <!-- 고객 질문서 -->
        <template v-if="isCustomer">
          <SectionLabel :text="'지금 답이 필요한 질문 · ' + preview.requirements.length + '건'" />

          <EmptyState
            v-if="preview.requirements.length === 0"
            title="지금 물을 질문이 없습니다"
            :description="customerEmptyNote"
          />

          <div v-for="item in preview.requirements" :key="item.requirementId" class="card pad item">
            <div class="cardhead">
              <span class="eb">#{{ item.sequenceNo }} 요구사항</span>
              <span class="fig ver">v{{ item.contentVersion }}</span>
            </div>
            <p class="orig">{{ item.originalText }}</p>

            <div v-for="q in item.questions" :key="q.id" class="qrow">
              <div class="qhead">
                <AmbiguityTypeBadge :type="q.type" />
                <span class="eb sm">{{ q.roundNo }}회차</span>
                <!-- 601 같은 저장번호를 「질문 601」로 적으면 601번째 질문으로 읽힌다.
                     회차는 왼쪽에 이미 있으니 여기서는 무엇인지만 말한다. -->
                <span class="mi qid">확인 질문</span>
              </div>
              <p class="question">{{ q.questionText }}</p>
              <p class="mi evidence">근거 — {{ q.evidence }}</p>
            </div>
          </div>
        </template>

        <!-- 개발팀용 -->
        <template v-else>
          <SectionLabel :text="'확정 요구사항 · ' + preview.confirmedRequirements.length + '건'" />

          <EmptyState
            v-if="preview.confirmedRequirements.length === 0"
            title="아직 확정된 요구사항이 없습니다"
            description="모든 문제가 해결되고 수정안이 승인되면 여기에 쌓입니다."
          />

          <div v-for="item in preview.confirmedRequirements" :key="item.requirementId" class="card pad item">
            <div class="cardhead">
              <span class="eb">#{{ item.sequenceNo }} 요구사항</span>
              <StatusBadge kind="revision" :value="item.approvedRevision.status" />
            </div>

            <p class="final">{{ item.approvedRevision.text }}</p>

            <div class="eb sm mt">원문</div>
            <p class="orig">{{ item.originalText }}</p>

            <div class="eb sm mt">
              근거 답변 · {{ item.approvedRevision.revisionNo }}차 수정안
            </div>
            <div v-for="a in item.evidenceAnswers" :key="a.id" class="qrow">
              <div class="qhead">
                <span class="eb sm">{{ a.roundNo }}회차</span>
                <StatusBadge kind="clarification" :value="a.status" />
                <span class="mi qid">확인 질문</span>
              </div>
              <p class="question">{{ a.questionText }}</p>
              <p class="answer">{{ a.answerText }}</p>
            </div>
          </div>

          <SectionLabel
            class="mt-lg"
            :text="'미확정 요구사항 · ' + preview.unconfirmedRequirements.length + '건'"
          />

          <p v-if="preview.unconfirmedRequirements.length === 0" class="mi none">
            미확정 요구사항이 없습니다.
          </p>

          <div v-for="item in preview.unconfirmedRequirements" :key="item.requirementId" class="card pad item">
            <!-- 상태와 버전은 한 묶음으로 오른쪽에 세운다. 셋을 그냥 나열하면
                 space-between이 가운데 것(상태)을 카드 한복판에 떨어뜨려 놓아,
                 제목에도 버전에도 붙지 않은 채 홀로 뜬다
                 (프론트엔드-추가-요청사항 5.1). -->
            <div class="cardhead">
              <span class="eb">#{{ item.sequenceNo }} 요구사항</span>
              <span class="headmeta">
                <StatusBadge kind="requirement" :value="item.status" />
                <span class="fig ver">v{{ item.contentVersion }}</span>
              </span>
            </div>
            <p class="orig">{{ item.originalText }}</p>

            <div class="eb sm mt">문제 이력</div>
            <div v-for="(issue, i) in item.issues" :key="issue.id" class="irow">
              <AmbiguityTypeBadge :type="issue.type" />
              <StatusBadge kind="issue" :value="issue.status" />
              <span class="mi qid">#{{ i + 1 }}</span>
              <span class="mi evidence full">{{ issue.evidence }}</span>
            </div>

            <div class="eb sm mt">질문·답변 이력</div>
            <div v-for="q in item.questions" :key="q.id" class="qrow">
              <div class="qhead">
                <span class="eb sm">{{ q.roundNo }}회차</span>
                <StatusBadge kind="clarification" :value="q.status" />
                <span class="mi qid">확인 질문</span>
              </div>
              <p class="question">{{ q.questionText }}</p>
              <p v-if="q.answerText" class="answer">{{ q.answerText }}</p>
              <p v-else class="mi none">아직 답변이 없습니다.</p>
            </div>
          </div>
        </template>
      </div>

      <aside class="aside stickycol" data-reveal>
        <!-- 왼쪽 기둥이 SectionLabel 아래에서 카드를 시작하므로 오른쪽도 같은 자리에서
             시작해야 두 기둥의 윗변이 맞는다. 라벨을 카드 머리말로 넣으면 33px 어긋난다. -->
        <SectionLabel text="조회 기준 버전" />
        <div class="card pad quiet">
          <p class="mi note">
            이 미리보기는 아래 버전으로 조합했습니다. 이후 답변·검토가 있었다면 다시 조회해야 합니다.
          </p>
          <div v-for="row in basis" :key="row.requirementId" class="brow">
            <span class="mi blabel">요구사항 #{{ sequenceOf(row.requirementId) }}</span>
            <span class="fig bver">v{{ row.contentVersion }}</span>
            <span v-if="row.approvedRevisionId" class="mi bapp">승인된 수정안</span>
          </div>
          <p v-if="preview.generatedAt" class="mi gen">조회 시점 {{ preview.generatedAt }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* 카드 머리말의 오른쪽 정보 묶음. 상태와 버전은 같은 것을 말하므로 붙여 둔다. */
.headmeta { display: inline-flex; align-items: center; gap: 10px; }
/* 본문 8 : 근거 4. 오른쪽이 이미 카드라 세로 괘선은 쓰지 않는다. */
.main { grid-column: span 8; }
.aside { grid-column: span 4; }

@media (max-width: 900px) {
  .main, .aside { grid-column: span 12; position: static; }
}

/* 히어로 액션 자리에 놓는 탭. 두 벌의 같은 내용을 다른 독자에게 보여주는 전환이다. */
.tabs {
  display: inline-flex;
  gap: 3px;
  padding: 4px;
  border-radius: 999px;
  background: var(--bg-200);
}

.tab {
  padding: 7px 17px 8px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--fg-500);
  font: inherit;
  font-size: var(--fs-micro);
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color .25s var(--ease), color .25s var(--ease);
}

.tab--on { background: var(--bg-0); color: var(--primary-700); }

.item { margin-bottom: 18px; }
.mt { margin-top: 18px; display: block; }
.mt-lg { margin-top: 34px; }
.ver { font-size: var(--fs-sm); color: var(--fg-600); }
.eb.sm { font-size: var(--fs-micro); }

.orig { margin: 0; font-size: var(--fs-sm); line-height: 1.85; color: var(--fg-700); word-break: keep-all; }

/* 확정본은 이 화면의 결론이라 본문보다 한 단 크고 진하게 둔다. */
.final {
  margin: 0;
  padding: 15px 17px;
  border-radius: var(--radius-card);
  border: 1px solid var(--green-bd);
  background: color-mix(in srgb, var(--green-bg) 45%, var(--bg-0));
  font-size: var(--fs-lead);
  line-height: 1.8;
  color: var(--fg-950);
  word-break: keep-all;
}

.qrow { margin-top: 14px; padding-top: 13px; border-top: 1px solid var(--rule); }
.qhead { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; margin-bottom: 8px; }
.qid { font-size: var(--fs-nano); color: var(--fg-400); }
.question { margin: 0; font-size: var(--fs-sm); line-height: 1.75; color: var(--fg-950); word-break: keep-all; }
.evidence { margin: 7px 0 0; font-size: var(--fs-micro); color: var(--fg-500); line-height: 1.7; word-break: keep-all; }
.evidence.full { flex: 1 1 100%; margin: 0; }

.answer {
  margin: 9px 0 0;
  padding: 11px 14px;
  border-radius: var(--radius-card);
  background: var(--bg-100);
  border: 1px solid var(--bg-200);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--fg-700);
  word-break: keep-all;
}

.irow {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
  padding: 10px 0;
  border-bottom: 1px solid var(--rule);
}

.none { font-size: var(--fs-sm); color: var(--fg-400); margin: 8px 0 0; }

.note { font-size: var(--fs-micro); color: var(--fg-500); line-height: 1.7; margin: 0 0 14px; word-break: keep-all; }
.brow { display: flex; align-items: baseline; gap: 9px; flex-wrap: wrap; padding: 9px 0; border-bottom: 1px solid var(--rule); }
.blabel { font-size: var(--fs-micro); color: var(--fg-700); }
.bver { font-size: var(--fs-sm); color: var(--fg-950); }
.bapp { font-size: var(--fs-nano); color: var(--accent-700); }
.gen { margin: 12px 0 0; font-size: var(--fs-nano); color: var(--fg-400); }
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createProject, listProjects } from '@/api/projects'
import { useMock } from '@/api/config'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHero from '@/components/common/PageHero.vue'
import PillButton from '@/components/common/PillButton.vue'
import SectionLabel from '@/components/common/SectionLabel.vue'
import { useApiError } from '@/composables/useApiError'

const router = useRouter()
const loading = ref(true)
const projects = ref([])
const name = ref('')
const description = ref('')
const saving = ref(false)

const { message, fieldErrors, hasError, captureError, clearError } = useApiError()

async function loadProjects() {
  const data = await listProjects()
  projects.value = data.items ?? []
}

async function bootstrap() {
  loading.value = true
  clearError()
  try {
    await loadProjects()
  } catch (error) {
    captureError(error)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  saving.value = true
  clearError()
  try {
    const trimmed = description.value.trim()
    const project = await createProject({
      name: name.value.trim(),
      description: trimmed === '' ? null : trimmed,
    })
    name.value = ''
    description.value = ''
    await loadProjects()
    openProject(project.id)
  } catch (error) {
    captureError(error)
  } finally {
    saving.value = false
  }
}

/** 히어로에 얹는 요약. 표시용이라 조회 로직과 무관하다. */
const chips = computed(() => [
  { value: String(projects.value.length), label: '프로젝트' },
  { value: useMock ? 'MOCK' : 'API', label: '데이터 원본' },
])

function openProject(projectId) {
  router.push({
    name: 'document-list',
    params: { projectId: String(projectId) },
  })
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <div class="page">
    <!-- 페이지 정체성. 전 화면이 같은 히어로를 쓴다 — 화면을 옮겨도 기준선이 그대로다. -->
    <PageHero
      num="01" eyebrow="프로젝트" watermark="P"
      :chips="chips"
    >
      <template #title>Projects</template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <LoadingSpinner v-if="loading" />

    <div v-else class="grid12">
      <div class="col-list">
        <div class="card list" data-reveal-stagger>
          <SectionLabel :text="'프로젝트 ' + projects.length + '건'" />

          <button
            v-for="project in projects"
            :key="project.id"
            type="button"
            class="prow row stagger-child"
            @click="openProject(project.id)"
          >
            <span class="fig pid">{{ project.id }}</span>
            <div class="pbody">
              <div class="hd pname">{{ project.name }}</div>
              <div class="mi pdesc" :class="{ none: !project.description }">
                {{ project.description || '설명 없음' }}
              </div>
            </div>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--fg-400)"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </button>

          <EmptyState
            v-if="projects.length === 0"
            title="아직 프로젝트가 없습니다"
            description="오른쪽에서 첫 프로젝트를 만들고 요구사항 문서를 등록하세요."
          />
        </div>
      </div>

      <form class="card pad form col-form stickycol" @submit.prevent="onCreate">
        <div class="cardhead"><span class="eb">새 프로젝트</span></div>

        <label class="lbl">
          <span class="eb sm">이름</span>
          <input v-model="name" type="text" class="field" maxlength="100"
                 placeholder="예: 상품 조회 서비스" />
        </label>

        <label class="lbl">
          <span class="eb sm">설명 · 선택</span>
          <textarea v-model="description" class="field ta" rows="2" maxlength="2000"
                    placeholder="한 줄 설명" />
        </label>

        <div class="formfoot">
          <PillButton variant="primary" :loading="saving" :disabled="name.trim() === ''">
            프로젝트 생성
          </PillButton>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
/* 목록 8 : 등록 폼 4. 전 화면이 같은 비율을 쓴다. */
.col-list { grid-column: span 8; }
/* 폼은 목록과 나란히 서는 도구다. 목록이 길어져도 따라오게 붙인다. */
.col-form { grid-column: span 4; }

/* 좁아지면 나란히 두기엔 이름이 잘린다. 폼을 아래로 내려 목록이 폭을 다 쓰게 한다. */
@media (max-width: 1240px) {
  .col-list { grid-column: span 12; }
  .col-form { grid-column: span 12; position: static; margin-top: 28px; }
}

/* 좌우 여백과 마지막 줄 밑줄은 .card.list가 맡는다. */
/* 식별자를 앞에 둔다. 문서 목록이 「102 · 제목」 순서인데 프로젝트만 이름이
   먼저고 ID가 화면 끝에 있으면, 같은 서비스 안에서 읽는 규칙이 둘이 된다.
   시선도 행 끝까지 갔다 돌아와야 한다. */
.prow {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 22px;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 15px 0 16px;
  border-left: 0;
  border-right: 0;
  border-top: 0;
  background: none;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: background-color .25s var(--ease);
}

.prow:hover { background: var(--bg-100); }
.prow:hover .pname { color: var(--primary-700); }
.prow:hover .arrow { transform: translateX(3px); stroke: var(--primary-600); }

.pbody { min-width: 0; }

/* 목록의 일은 「고르는 것」이지 「읽는 것」이 아니다.
   이름 100자·설명 2000자를 다 펼치면 행 하나가 925px이 돼 한 화면에 한 건도 안 들어온다.
   이름은 한 줄, 설명은 두 줄로 자르고 넘치면 말줄임한다. 전문은 들어가서 본다. */
.pname {
  font-size: 17px;
  font-weight: 600;
  transition: color .25s var(--ease);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pdesc {
  margin-top: 5px;
  font-size: var(--fs-sm);
  color: var(--fg-500);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pdesc.none { color: var(--fg-300); }
.pid { font-size: 16px; color: var(--accent-700); align-self: start; padding-top: 1px; }
.arrow { justify-self: end; transition: transform .3s var(--ease); }

.form { display: flex; flex-direction: column; }
.lbl { display: block; margin-bottom: 15px; }
/* 입력 폼의 라벨이다 — 무엇을 쓰는 칸인지 알려주는 글이라 더 줄이지 않는다. */
.eb.sm { font-size: var(--fs-micro); display: block; margin-bottom: 7px; }
.ta { resize: vertical; line-height: 1.7; }
.formfoot { display: flex; justify-content: flex-end; margin-top: 2px; }
</style>

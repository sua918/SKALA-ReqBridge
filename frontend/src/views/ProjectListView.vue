<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createProject, listProjects } from '@/api/projects'
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

/**
 * 히어로에 얹는 요약. 표시용이라 조회 로직과 무관하다.
 *
 * 예전에는 여기에 `MOCK`/`API` 칩이 있었다. 개발 중에 「지금 어느 데이터를 보고 있나」를
 * 확인하려던 것인데, 쓰는 사람에게는 뜻이 없는 글자다. 그 확인은 개발자 도구의
 * 네트워크 탭에서 하면 된다 (프론트엔드-추가-요청사항 2.3).
 */
const chips = computed(() => [
  { value: String(projects.value.length), label: '프로젝트' },
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
      <template #title>프로젝트</template>
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
            <!-- 앞자리에 있던 DB 저장번호를 뺐다. 목록 순번처럼 읽히지만 실제로는
                 저장 순서라 5번 다음에 12번이 오고, 사용자가 셀 수 있는 수가 아니다.
                 프로젝트는 이름으로 구분한다 (프론트엔드-추가-요청사항 2.3). -->
            <div class="pbody">
              <div class="hd pname" :title="project.name">{{ project.name }}</div>
              <div
                class="mi pdesc" :class="{ none: !project.description }"
                :title="project.description || undefined"
              >{{ project.description || '설명 없음' }}</div>
            </div>
            <!-- 화살표만 있으면 눌러서 어디로 가는지 알 수 없다. 행이 하는 일을 적는다. -->
            <span class="mi act">
              프로젝트 열기
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </span>
          </button>

          <EmptyState
            v-if="projects.length === 0"
            title="아직 프로젝트가 없습니다"
            description="오른쪽에서 첫 프로젝트를 만들고 요구사항 문서를 등록하세요."
          />
        </div>
      </div>

      <form class="card pad form col-form" @submit.prevent="onCreate">
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
/* 접히는 기준을 1240 -> 900px로 내렸다. 1240은 노트북 폭(1280~1440)에서도 자주
   걸려, 나란히 놓을 자리가 충분한데도 폼이 목록 아래로 떨어졌다.
   4칸이 손가락만 해지는 900px 아래에서만 쌓는다. */
@media (max-width: 900px) {
  .col-list { grid-column: span 12; }
  .col-form { grid-column: span 12; position: static; margin-top: 20px; }
}

/* 좌우 여백과 마지막 줄 밑줄은 .card.list가 맡는다. */
/* 이름이 첫 칸이다. 앞자리를 채우던 저장번호를 뺐으니 시선이 바로 이름에서 시작한다.
   행이 하는 일(「프로젝트 열기」)은 오른쪽 끝에 고정폭으로 세워, 행마다 같은 자리에서
   같은 글자를 읽게 한다. */
.prow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 18px 0 19px;
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
.prow:hover .act { color: var(--primary-600); }
.prow:hover .arrow { transform: translateX(3px); }

.pbody { min-width: 0; }

/* 목록의 일은 「고르는 것」이지 「읽는 것」이 아니다.
   이름 100자·설명 2000자를 다 펼치면 행 하나가 925px이 돼 한 화면에 한 건도 안 들어온다.
   이름은 한 줄, 설명은 두 줄로 자르고 넘치면 말줄임한다. 전문은 들어가서 본다. */
.pname {
  font-size: 18px;
  font-weight: 650;
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
/* 행의 동작 문구. 이름이 아무리 길어도 이 칸은 줄지 않아야 눌러야 할 곳이 흔들리지 않는다. */
.act {
  display: inline-flex; align-items: center; gap: 6px;
  white-space: nowrap;
  /* 행이 하는 일은 「누를 수 있는 것」으로 보여야 한다. 회색 400은 설명글과 같은
     무게라 눌러도 되는지 알 수 없었다. 브랜드 남색에 굵기를 준다. */
  font-size: var(--fs-sm); font-weight: 700; color: var(--accent-700);
  transition: color .25s var(--ease);
}
.arrow { transition: transform .3s var(--ease); }

.form { display: flex; flex-direction: column; }
.lbl { display: block; margin-bottom: 15px; }
/* 입력 폼의 라벨이다 — 무엇을 쓰는 칸인지 알려주는 글이라 더 줄이지 않는다. */
.eb.sm { font-size: var(--fs-micro); display: block; margin-bottom: 7px; }
.ta { resize: vertical; line-height: 1.7; }
.formfoot { display: flex; justify-content: flex-end; margin-top: 2px; }
</style>

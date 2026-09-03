<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createTextDocument,
  listDocuments,
  uploadPdfDocument,
} from '@/api/documents'
import { DEMO_CONTENT } from '@/mocks/fixtures.js'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHero from '@/components/common/PageHero.vue'
import PillButton from '@/components/common/PillButton.vue'
import SectionLabel from '@/components/common/SectionLabel.vue'
import { useApiError } from '@/composables/useApiError'
import { DocumentSourceType } from '@/types/api'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))

const loading = ref(true)
const documents = ref([])
const title = ref('새 요구사항 문서')
const content = ref(DEMO_CONTENT)
const saving = ref(false)

const uploadTitle = ref('')
const uploadFile = ref(null)
const uploading = ref(false)

const { message, fieldErrors, hasError, captureError, clearError } = useApiError()

async function loadDocuments() {
  const data = await listDocuments(projectId.value)
  documents.value = data.items ?? []
}

async function bootstrap() {
  loading.value = true
  clearError()
  try {
    await loadDocuments()
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
    const document = await createTextDocument(projectId.value, {
      title: title.value,
      content: content.value,
    })
    await loadDocuments()
    router.push({
      name: 'document-detail',
      params: { documentId: String(document.id) },
    })
  } catch (error) {
    captureError(error)
  } finally {
    saving.value = false
  }
}

function onFileChange(event) {
  uploadFile.value = event.target.files?.[0] ?? null
}

async function onUpload() {
  uploading.value = true
  clearError()
  try {
    const document = await uploadPdfDocument(projectId.value, {
      title: uploadTitle.value,
      file: uploadFile.value,
    })
    uploadTitle.value = ''
    uploadFile.value = null
    await loadDocuments()
    openDocument(document.id)
  } catch (error) {
    captureError(error)
  } finally {
    uploading.value = false
  }
}

/** 히어로에 얹는 요약. 표시용이라 조회 로직과 무관하다. */
const chips = computed(() => [
  { value: String(documents.value.length), label: '문서' },
  {
    value: String(documents.value.filter((d) => d.sourceType === DocumentSourceType.FILE).length),
    label: 'PDF',
  },
])

function openDocument(documentId) {
  router.push({
    name: 'document-detail',
    params: { documentId: String(documentId) },
  })
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <div class="page">
    <PageHero
      num="02" eyebrow="문서" watermark="D" :chips="chips"
    >
      <template #title>Documents</template>
      <template #subject>프로젝트 #{{ projectId }}</template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <LoadingSpinner v-if="loading" />

    <div v-else class="grid12">
      <div class="col-list">
        <div class="card list">
          <SectionLabel :text="'문서 ' + documents.length + '건'" />

          <button
            v-for="doc in documents"
            :key="doc.id"
            type="button"
            class="drow row"
            @click="openDocument(doc.id)"
          >
            <span class="fig did">{{ doc.id }}</span>
            <span class="hd dtitle">{{ doc.title }}</span>
            <span class="mi dsrc" :class="{ file: doc.sourceType === DocumentSourceType.FILE }">
              {{ doc.sourceType === DocumentSourceType.FILE ? 'PDF 업로드' : '직접 입력' }}
            </span>
            <span class="mi ddate">{{ doc.createdAt.slice(0, 10) }}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--fg-400)"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </button>

          <EmptyState
            v-if="documents.length === 0"
            title="등록된 문서가 없습니다"
            description="오른쪽에서 요구사항 원문을 등록하세요."
          />
        </div>
      </div>

      <div class="col-form stickycol">
        <form class="card pad" @submit.prevent="onCreate">
          <div class="cardhead">
            <span class="eb">직접 입력</span>
            <span class="mi srchint">{{ DocumentSourceType.TEXT }}</span>
          </div>

          <label class="lbl">
            <span class="eb sm">제목</span>
            <input v-model="title" type="text" class="field" />
          </label>

          <label class="lbl">
            <span class="eb sm">원문</span>
            <textarea v-model="content" class="field ta" rows="8" />
          </label>

          <div class="formfoot">
            <PillButton variant="primary" :loading="saving">문서 등록</PillButton>
          </div>
        </form>

        <form class="card pad mt" @submit.prevent="onUpload">
          <div class="cardhead">
            <span class="eb">PDF 업로드</span>
            <span class="mi srchint">{{ DocumentSourceType.FILE }}</span>
          </div>

          <p class="mi hint">
            PDF 한 개, 최대 10MB. 원문은 서버가 추출합니다.
          </p>

          <label class="lbl">
            <span class="eb sm">제목</span>
            <input v-model="uploadTitle" type="text" class="field" maxlength="200" />
          </label>

          <label class="lbl">
            <span class="eb sm">파일</span>
            <input type="file" class="filefield" accept="application/pdf,.pdf" @change="onFileChange" />
          </label>

          <div class="formfoot">
            <PillButton
              variant="quiet" :loading="uploading"
              :disabled="uploadFile === null || uploadTitle.trim() === ''"
            >PDF 등록</PillButton>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.col-list { grid-column: span 8; }
/* 등록 폼은 목록과 나란히 서는 도구다. 목록이 길어져도 따라오게 붙인다. */
.col-form { grid-column: span 4; }

/* 좁아지면 제목이 잘려 무슨 문서인지 안 읽힌다. 폼을 아래로 내려 목록이 폭을 다 쓰게 한다. */
@media (max-width: 1240px) {
  .col-list { grid-column: span 12; }
  .col-form { grid-column: span 12; position: static; margin-top: 28px; }
}

/* 좌우 여백과 마지막 줄 밑줄은 .card.list가 맡는다. */
.drow {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 88px 96px 22px;
  gap: 14px;
  align-items: center;
  width: 100%;
  min-height: 54px;
  padding: 12px 0 13px;
  border: 0;
  border-bottom: 1px solid var(--rule);
  background: none;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: background-color .25s var(--ease);
}

.drow:hover { background: var(--bg-100); }
.drow:hover .dtitle { color: var(--primary-700); }
.drow:hover .arrow { transform: translateX(3px); stroke: var(--primary-600); }

.did { font-size: 16px; color: var(--accent-700); }

/* 제목이 두 줄이 되면 그 행만 키가 커져 목록 리듬이 깨진다.
   한 줄로 자르고 넘치면 말줄임 — 전체 제목은 상세 화면에서 본다. */
.dtitle {
  font-size: var(--fs-h3);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color .25s var(--ease);
}

.dsrc { font-size: var(--fs-micro); color: var(--fg-500); }
.dsrc.file { color: var(--accent-700); }
.ddate { font-size: var(--fs-micro); color: var(--fg-400); }
.arrow { justify-self: end; transition: transform .3s var(--ease); }

.mt { margin-top: 18px; }
.srchint { font-size: var(--fs-nano); color: var(--fg-400); }
.lbl { display: block; margin-bottom: 15px; }
/* 입력 폼의 라벨이다 — 무엇을 쓰는 칸인지 알려주는 글이라 더 줄이지 않는다. */
.eb.sm { font-size: var(--fs-micro); display: block; margin-bottom: 7px; }
.ta { resize: vertical; line-height: 1.7; }
.hint { font-size: var(--fs-sm); color: var(--fg-600); line-height: 1.7; margin: 0 0 14px; }
.filefield { width: 100%; font-size: var(--fs-sm); color: var(--fg-700); }
.formfoot { display: flex; justify-content: flex-end; margin-top: 2px; }
</style>

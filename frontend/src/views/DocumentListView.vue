<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createTextDocument,
  listDocuments,
  uploadPdfDocument,
} from '@/api/documents'
import { startDocumentAnalysis } from '@/api/analyses'
import { getProject } from '@/api/projects'
import FileUpload from 'primevue/fileupload'
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
const project = ref(null)
/**
 * 빈 값으로 시작한다. 예전에는 제목에 「새 요구사항 문서」, 원문에 데모 본문이 미리
 * 채워져 있었다. 시연에는 편했지만, 예시인지 내가 쓴 것인지 구분이 안 돼 그대로
 * 저장될 수 있었다. 쓰는 방법은 placeholder로만 안내한다
 * (프론트엔드-추가-요청사항 3.2).
 */
const title = ref('')
const content = ref('')
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
    // 프로젝트를 함께 읽는다. 하위 화면에서도 「어느 프로젝트에 있는지」가 제목으로
    // 보여야 한다 (프론트엔드-추가-요청사항 3.1). 이름 조회가 실패해도 문서 목록은
    // 보여야 하므로 둘을 따로 처리한다.
    const [, loadedProject] = await Promise.all([
      loadDocuments(),
      getProject(projectId.value).catch(() => null),
    ])
    project.value = loadedProject
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

/** 고른 파일이 맞는지 확인할 수 있게 크기를 사람이 읽는 단위로 적는다. */
function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const uploader = ref(null)

function onFileSelect(event) {
  const file = event.files?.[0] ?? null
  uploadFile.value = file
  // 제목을 따로 지어야 등록 버튼이 열리던 탓에, 파일을 고르고도 한 칸 더 채워야 했다.
  // 대개 파일명이 곧 문서 이름이므로 비어 있을 때만 채운다.
  // 사용자가 이미 쓴 제목은 건드리지 않는다 (프론트엔드-추가-요청사항 3.3).
  if (file && uploadTitle.value.trim() === '') {
    uploadTitle.value = file.name
  }
}

/** 업로드가 끝나면 위젯이 들고 있는 파일까지 비운다 — 화면만 지우면 다음 선택이 막힌다. */
function clearSelectedFile() {
  uploadFile.value = null
  uploader.value?.clear?.()
}

async function onUpload() {
  uploading.value = true
  clearError()
  try {
    const document = await uploadPdfDocument(projectId.value, {
      title: uploadTitle.value,
      file: uploadFile.value,
    })
    // 올린 뒤 분석을 따로 눌러야 하면 흐름이 한 번 끊긴다. 여기서 이어서 건다.
    // 실패해도 삼킨다 — 문서는 이미 저장됐고, 상세 화면의 restoreActiveAnalysis가
    // 진행 중·완료 이력을 복구한다 (프론트엔드-추가-요청사항 3.3).
    try {
      await startDocumentAnalysis(document.id)
    } catch {
      /* 상세에서 복구한다 */
    }
    uploadTitle.value = ''
    clearSelectedFile()
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
      <template #title>{{ project?.name ?? '문서' }}</template>
      <template v-if="project?.description" #subject>{{ project.description }}</template>
    </PageHero>

    <ErrorMessage v-if="hasError" :message="message" :field-errors="fieldErrors" />

    <LoadingSpinner v-if="loading" />

    <div v-else class="grid12">
      <div class="col-list">
        <div class="card list" data-reveal-stagger>
          <SectionLabel text="문서" :count="documents.length" />

          <button
            v-for="doc in documents"
            :key="doc.id"
            type="button"
            class="drow row stagger-child"
            @click="openDocument(doc.id)"
          >
            <!-- 저장번호를 뺐다. 문서는 제목으로 구분한다 (프론트엔드-추가-요청사항 2.3). -->
            <span class="hd dtitle" :title="doc.title">{{ doc.title }}</span>
            <span class="mi dsrc" :class="{ file: doc.sourceType === DocumentSourceType.FILE }">
              {{ doc.sourceType === DocumentSourceType.FILE ? 'PDF' : '직접 입력' }}
            </span>
            <span class="mi ddate">{{ doc.createdAt.slice(0, 10) }}</span>
            <span class="mi act">
              문서 열기
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </span>
          </button>

          <EmptyState
            v-if="documents.length === 0"
            title="등록된 문서가 없습니다"
            description="오른쪽에서 요구사항 원문을 등록하세요."
          />
        </div>
      </div>

      <!-- 등록 폼은 따라다니지 않는다. 폼이 화면에 붙어 스크롤을 따라오면 목록을
           훑는 내내 시야 절반을 차지하고, 폼이 길어지면 안에서 또 스크롤이 생긴다.
           원문·조회 기준처럼 「보면서 작업하는 판」만 따라오게 둔다. -->
      <div class="col-form">
        <!-- 시연의 시작점은 PDF 업로드다. 직접 입력이 먼저 보이면 준비한 문서를 두고
             본문을 붙여넣는 길로 눈이 먼저 간다 (프론트엔드-추가-요청사항 3.3). -->
        <form class="card pad" @submit.prevent="onUpload">
          <div class="cardhead">
            <span class="eb">PDF 업로드 및 분석</span>
          </div>

          <p class="mi hint">PDF 한 개 · 최대 10MB</p>

          <label class="lbl">
            <span class="eb sm">제목</span>
            <input
              v-model="uploadTitle" type="text" class="field" maxlength="200"
              :disabled="uploading"
              placeholder="비워 두면 PDF 파일명을 씁니다"
            />
          </label>

          <div class="lbl">
            <span class="eb sm">파일</span>
            <!-- 브라우저 기본 파일 입력은 OS마다 다른 모양으로 그려져 이 화면의
                 다른 단추들과 아무 관계 없어 보인다. 고르는 일만 위젯에 맡기고
                 업로드는 아래 단추가 계속 맡는다(auto=false). -->
<span class="picker">
              <FileUpload
                ref="uploader" mode="basic" name="file"
                accept="application/pdf,.pdf" :max-file-size="10485760"
                :auto="false" :custom-upload="true" :disabled="uploading"
                choose-label="PDF 선택"
                @select="onFileSelect" @clear="uploadFile = null"
              />
            </span>
          </div>

          <!-- 무엇이 올라갈지 눌러서 확인할 수 없으니 고른 것을 적어 둔다. -->
          <p v-if="uploadFile" class="mi picked">
            <span class="pname">{{ uploadFile.name }}</span>
            <span class="psize">{{ formatFileSize(uploadFile.size) }}</span>
          </p>

          <div class="formfoot">
            <PillButton
              variant="primary" :loading="uploading"
              :disabled="uploadFile === null || uploadTitle.trim() === ''"
            >{{ uploading ? '업로드 및 분석 중…' : 'PDF 업로드 및 분석' }}</PillButton>
          </div>
        </form>

        <form class="card pad mt" @submit.prevent="onCreate">
          <div class="cardhead">
            <span class="eb">직접 입력</span>
          </div>

          <label class="lbl">
            <span class="eb sm">제목</span>
            <input
              v-model="title" type="text" class="field"
              placeholder="문서 제목을 입력하세요"
            />
          </label>

          <label class="lbl">
            <span class="eb sm">원문</span>
            <textarea
              v-model="content" class="field ta" rows="8"
              placeholder="요구사항 원문을 입력하세요"
            />
          </label>

          <div class="formfoot">
            <PillButton variant="quiet" :loading="saving">문서 등록</PillButton>
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
/* 접히는 기준을 1240 -> 900px로 내렸다. 1240은 노트북 폭(1280~1440)에서도 자주
   걸려, 나란히 놓을 자리가 충분한데도 폼이 목록 아래로 떨어졌다.
   4칸이 손가락만 해지는 900px 아래에서만 쌓는다. */
@media (max-width: 900px) {
  .col-list { grid-column: span 12; }
  .col-form { grid-column: span 12; position: static; margin-top: 20px; }
}

/* PrimeVue FileUpload는 자기 테마 색(에메랄드)을 들고 온다. 이 화면에서 초록은
   「해결·완료」를 뜻하는 색이라, 아직 아무 일도 안 한 단추가 그 색이면 뜻이 어긋난다.
   조용한 단추와 같은 결로 낮춘다. */
.picker { display: inline-flex; align-items: center; }
.picker :deep(.p-fileupload-choose-button) {
  padding: 8px 15px;
  border-radius: 999px;
  border: 1px solid var(--rule);
  background: var(--bg-50);
  color: var(--fg-800);
  font-family: var(--font-body);
  font-size: var(--fs-sm);
  font-weight: 600;
  gap: 7px;
}
.picker :deep(.p-fileupload-choose-button:hover) { background: var(--bg-100); border-color: var(--bg-300); }
.picker :deep(.p-fileupload-choose-button:disabled) { opacity: .55; }
/* 위젯이 파일명 자리에 「No file chosen」을 영어로 박아 둔다. 고른 파일은 바로 아래
   줄에서 이름과 크기까지 우리가 적으므로 이 자리는 비운다. */
.picker :deep(.p-fileupload-choose-button + span) { display: none; }

/* 고른 파일 표시. 파일명이 길어도 줄바꿈으로 흘러 크기 값을 밀어내지 않는다. */
.picked {
  display: flex; align-items: baseline; gap: 8px;
  margin: -6px 0 15px; color: var(--fg-600);
}
.picked .pname { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picked .psize { flex-shrink: 0; color: var(--fg-400); }

/* 좌우 여백과 마지막 줄 밑줄은 .card.list가 맡는다. */
.drow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px 96px auto;
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
.drow:hover .act { color: var(--primary-600); }
.drow:hover .arrow { transform: translateX(3px); }

/* 행의 동작 문구. 제목이 길어져도 이 칸은 줄지 않는다. */
.act {
  display: inline-flex; align-items: center; gap: 6px;
  white-space: nowrap;
  /* 행이 하는 일은 「누를 수 있는 것」으로 보여야 한다. 회색 400은 설명글과 같은
     무게라 눌러도 되는지 알 수 없었다. 브랜드 남색에 굵기를 준다. */
  font-size: var(--fs-sm); font-weight: 700; color: var(--accent-700);
  transition: color .25s var(--ease);
}

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
.arrow { transition: transform .3s var(--ease); }

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

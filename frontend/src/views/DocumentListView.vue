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
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import { useApiError } from '@/composables/useApiError'
import { DocumentSourceType } from '@/types/api'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))

const loading = ref(true)
const project = ref(null)
const documents = ref([])
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
    const [loadedProject] = await Promise.all([
      getProject(projectId.value),
      loadDocuments(),
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

const fileInput = ref(null)

function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function onFileChange(event) {
  const file = event.target.files?.[0] ?? null
  uploadFile.value = file
  if (file && uploadTitle.value.trim() === '') {
    uploadTitle.value = file.name
  }
}

function clearSelectedFile() {
  uploadFile.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function onUpload() {
  uploading.value = true
  clearError()
  try {
    const document = await uploadPdfDocument(projectId.value, {
      title: uploadTitle.value,
      file: uploadFile.value,
    })
    try {
      await startDocumentAnalysis(document.id)
    } catch {
      // 상세 화면의 restoreActiveAnalysis가 진행 중/완료 이력을 복구한다.
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

function openDocument(documentId) {
  router.push({
    name: 'document-detail',
    params: { documentId: String(documentId) },
  })
}

function sourceTypeLabel(sourceType) {
  if (sourceType === DocumentSourceType.FILE) {
    return 'PDF'
  }
  if (sourceType === DocumentSourceType.TEXT) {
    return '직접 입력'
  }
  return '문서'
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <section class="doc-list-page">
    <header class="page-header">
      <h1>{{ project?.name ?? '프로젝트' }}</h1>
      <p v-if="project?.description" class="subtitle">{{ project.description }}</p>
    </header>

    <ErrorMessage
      v-if="hasError"
      :message="message"
      :field-errors="fieldErrors"
    />

    <p v-if="loading">불러오는 중…</p>

    <template v-else>
      <section class="panel">
        <h2>문서 목록</h2>
        <p v-if="documents.length === 0" class="empty">등록된 문서가 없습니다.</p>
        <ul v-else class="list">
          <li v-for="doc in documents" :key="doc.id">
            <button type="button" class="list-button" @click="openDocument(doc.id)">
              <span class="list-title">{{ doc.title }}</span>
              <span class="list-meta">{{ sourceTypeLabel(doc.sourceType) }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h2>PDF 업로드 및 분석</h2>
        <p class="hint">PDF 한 개, 최대 10MB. 원문은 서버가 추출합니다.</p>
        <label class="field">
          <span>제목</span>
          <input
            v-model="uploadTitle"
            type="text"
            maxlength="200"
            :disabled="uploading"
          />
        </label>
        <label class="field">
          <span>파일</span>
          <input
            ref="fileInput"
            type="file"
            accept="application/pdf,.pdf"
            :disabled="uploading"
            @change="onFileChange"
          />
        </label>
        <div v-if="uploadFile" class="selected-file">
          <span class="selected-file-label">선택한 파일</span>
          <strong class="selected-file-name">{{ uploadFile.name }}</strong>
          <span class="selected-file-size">{{ formatFileSize(uploadFile.size) }}</span>
        </div>
        <button
          type="button"
          class="btn-primary"
          :disabled="uploading || uploadFile === null || uploadTitle.trim() === ''"
          @click="onUpload"
        >
          {{ uploading ? '업로드 및 분석 중…' : 'PDF 업로드 및 분석' }}
        </button>
      </section>

      <section class="panel">
        <h2>직접 입력</h2>
        <p class="hint">제목과 원문을 입력해 문서를 등록합니다.</p>
        <label class="field">
          <span>제목</span>
          <input
            v-model="title"
            type="text"
            placeholder="문서 제목을 입력하세요"
          />
        </label>
        <label class="field">
          <span>원문</span>
          <textarea
            v-model="content"
            rows="8"
            placeholder="요구사항 원문을 입력하세요"
          />
        </label>
        <button
          type="button"
          class="btn-primary"
          :disabled="saving"
          @click="onCreate"
        >
          {{ saving ? '등록 중…' : '문서 등록' }}
        </button>
      </section>
    </template>
  </section>
</template>

<style scoped>
.doc-list-page {
  max-width: 880px;
}

.page-header {
  margin: 0 0 20px;
}

h1 {
  margin: 0;
  font-size: 1.5rem;
}

.subtitle {
  margin: 4px 0 0;
  color: #64748b;
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

.empty {
  margin: 0;
  color: #94a3b8;
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.list li + li {
  margin-top: 8px;
}

.list-button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  cursor: pointer;
  text-align: left;
}

.list-button:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.list-title {
  font-weight: 600;
  color: #0f172a;
}

.list-meta {
  color: #64748b;
  font-size: 0.8rem;
}

.hint {
  margin: 0 0 12px;
  color: #94a3b8;
  font-size: 0.8rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 0.85rem;
  font-weight: 600;
  color: #475569;
}

.field input,
.field textarea {
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font: inherit;
  font-weight: 400;
  color: #0f172a;
}

.selected-file {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.selected-file-label {
  width: 100%;
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
}

.selected-file-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #0f172a;
}

.selected-file-size {
  font-size: 0.8rem;
  color: #64748b;
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
</style>

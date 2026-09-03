<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createTextDocument, listDocuments } from '@/api/documents'
import { DEMO_CONTENT } from '@/mocks/fixtures.js'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
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
  <section class="doc-list-page">
    <h1>문서 목록</h1>
    <p class="subtitle">프로젝트 #{{ projectId }}</p>

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
              <span class="list-title">#{{ doc.id }} {{ doc.title }}</span>
              <span class="list-meta">{{ doc.sourceType }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h2>TEXT 문서 등록</h2>
        <p class="hint">sourceType: {{ DocumentSourceType.TEXT }}</p>
        <label class="field">
          <span>제목</span>
          <input v-model="title" type="text" />
        </label>
        <label class="field">
          <span>원문</span>
          <textarea v-model="content" rows="8" />
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

h1 {
  margin: 0 0 4px;
  font-size: 1.5rem;
}

.subtitle {
  margin: 0 0 20px;
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

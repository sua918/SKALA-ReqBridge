<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createProject, listProjects } from '@/api/projects'
import { useMock } from '@/api/config'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
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
  <section class="project-page">
    <h1>프로젝트 목록</h1>
    <p class="meta">Mock: {{ useMock ? 'ON' : 'OFF' }}</p>

    <ErrorMessage
      v-if="hasError"
      :message="message"
      :field-errors="fieldErrors"
    />

    <p v-if="loading">불러오는 중…</p>

    <template v-else>
      <section class="panel">
        <h2>프로젝트</h2>
        <p v-if="projects.length === 0" class="empty">
          등록된 프로젝트가 없습니다. 아래에서 먼저 만들어 주세요.
        </p>
        <ul v-else class="list">
          <li v-for="project in projects" :key="project.id">
            <button
              type="button"
              class="list-button"
              @click="openProject(project.id)"
            >
              <span class="list-title">#{{ project.id }} {{ project.name }}</span>
              <span class="list-meta">{{ project.description ?? '설명 없음' }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h2>프로젝트 생성</h2>
        <label class="field">
          <span>이름</span>
          <input v-model="name" type="text" maxlength="100" />
        </label>
        <label class="field">
          <span>설명 (선택)</span>
          <textarea v-model="description" rows="3" maxlength="2000" />
        </label>
        <button
          type="button"
          class="btn-primary"
          :disabled="saving || name.trim() === ''"
          @click="onCreate"
        >
          {{ saving ? '생성 중…' : '프로젝트 생성' }}
        </button>
      </section>
    </template>
  </section>
</template>

<style scoped>
.project-page {
  max-width: 880px;
}

h1 {
  margin: 0 0 4px;
  font-size: 1.5rem;
}

.meta {
  margin: 0 0 20px;
  font-size: 0.85rem;
  color: #94a3b8;
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

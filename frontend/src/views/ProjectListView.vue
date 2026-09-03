<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listProjects } from '@/api/projects'
import { useMock } from '@/api/config'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import { useApiError } from '@/composables/useApiError'

const router = useRouter()
const loading = ref(true)
const projects = ref([])
const { message, fieldErrors, hasError, captureError, clearError } = useApiError()

onMounted(async () => {
  clearError()
  try {
    const data = await listProjects()
    projects.value = data.items ?? []
  } catch (error) {
    captureError(error)
  } finally {
    loading.value = false
  }
})

function openProject(projectId) {
  router.push({
    name: 'document-list',
    params: { projectId: String(projectId) },
  })
}
</script>

<template>
  <section class="placeholder-page">
    <h1>프로젝트 목록</h1>
    <p class="meta">Mock: {{ useMock ? 'ON' : 'OFF' }}</p>

    <ErrorMessage
      v-if="hasError"
      :message="message"
      :field-errors="fieldErrors"
    />

    <p v-if="loading">불러오는 중…</p>
    <ul v-else-if="!hasError" class="project-list">
      <li v-for="project in projects" :key="project.id">
        <button type="button" class="project-button" @click="openProject(project.id)">
          #{{ project.id }} {{ project.name }}
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.meta {
  margin: 8px 0 16px;
  font-size: 0.85rem;
  color: #94a3b8;
}

.project-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.project-list li + li {
  margin-top: 8px;
}

.project-button {
  display: block;
  width: 100%;
  max-width: 520px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  font-size: 0.95rem;
  font-weight: 600;
  color: #0f172a;
  cursor: pointer;
}

.project-button:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}
</style>

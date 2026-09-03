<script setup>
import { onMounted, ref } from 'vue'
import { listProjects } from '@/api/projects'
import { useMock } from '@/api/config'

const loading = ref(true)
const errorMessage = ref('')
const projects = ref([])

onMounted(async () => {
  try {
    const data = await listProjects()
    projects.value = data.items ?? []
  } catch (error) {
    errorMessage.value = error.apiError?.message ?? error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="placeholder-page">
    <h1>프로젝트 목록</h1>
    <p>준비 중 (B) — Step 4 API 연동 확인</p>
    <p class="meta">Mock: {{ useMock ? 'ON' : 'OFF' }}</p>

    <p v-if="loading">불러오는 중…</p>
    <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>
    <ul v-else>
      <li v-for="project in projects" :key="project.id">
        #{{ project.id }} {{ project.name }}
      </li>
    </ul>
  </section>
</template>

<style scoped>
.meta {
  margin-top: 8px;
  font-size: 0.85rem;
  color: #94a3b8;
}
.error {
  color: #b91c1c;
}
ul {
  margin: 16px 0 0;
  padding-left: 1.2rem;
}
</style>

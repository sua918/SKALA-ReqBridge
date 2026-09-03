<script setup>
import { onMounted, ref } from 'vue'
import { listProjects } from '@/api/projects'
import { useMock } from '@/api/config'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ErrorMessage from '@/components/common/ErrorMessage.vue'
import AnalysisFailureBanner from '@/components/common/AnalysisFailureBanner.vue'
import { useApiError } from '@/composables/useApiError'
import {
  AnalysisStatus,
  RequirementStatus,
} from '@/types/api'

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
</script>

<template>
  <section class="placeholder-page">
    <h1>프로젝트 목록</h1>
    <p>준비 중 (B) — API·공통 부품 확인</p>
    <p class="meta">Mock: {{ useMock ? 'ON' : 'OFF' }}</p>

    <ErrorMessage
      v-if="hasError"
      :message="message"
      :field-errors="fieldErrors"
    />

    <p v-if="loading">불러오는 중…</p>
    <ul v-else-if="!hasError">
      <li v-for="project in projects" :key="project.id">
        #{{ project.id }} {{ project.name }}
      </li>
    </ul>

    <!-- 공통 부품 스모크 (문서 화면에 배치 예정) -->
    <div class="smoke">
      <h2>공통 부품</h2>
      <div class="smoke-row">
        <StatusBadge :kind="'requirement'" :value="RequirementStatus.CLARIFYING" />
        <StatusBadge :kind="'requirement'" :value="RequirementStatus.CONFIRMED" />
        <StatusBadge :kind="'analysis'" :value="AnalysisStatus.PROCESSING" />
        <StatusBadge :kind="'analysis'" :value="AnalysisStatus.FAILED" />
      </div>
      <AnalysisFailureBanner
        code="AI_OUTPUT_INVALID"
        message="Mock 분석 결과가 올바르지 않습니다."
      />
    </div>
  </section>
</template>

<style scoped>
.meta {
  margin: 8px 0 0;
  font-size: 0.85rem;
  color: #94a3b8;
}

ul {
  margin: 16px 0 0;
  padding-left: 1.2rem;
}

.smoke {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.smoke h2 {
  margin: 0 0 12px;
  font-size: 1rem;
}

.smoke-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
</style>

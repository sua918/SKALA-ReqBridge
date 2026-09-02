<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'

const connectionStatus = ref('확인 중')
const isConnected = ref(false)

onMounted(async () => {
  try {
    const response = await axios.get('/api/health')
    isConnected.value = response.data.status === 'OK'
    connectionStatus.value = isConnected.value ? '연결됨' : '응답 확인 필요'
  } catch {
    connectionStatus.value = '연결되지 않음'
  }
})
</script>

<template>
  <main class="setup-card">
    <p class="eyebrow">ReqBridge</p>
    <h1>프로젝트 초기 설정 완료</h1>
    <p class="description">Vue와 Spring Boot의 개발 환경이 준비되었습니다.</p>
    <p class="status" :class="{ connected: isConnected }">
      <span class="status-dot" aria-hidden="true"></span>
      Backend: {{ connectionStatus }}
    </p>
  </main>
</template>

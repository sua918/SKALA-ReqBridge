<script setup>
// 로고 아치의 블루→라벤더→코랄. 정지점 세 개가 그대로 「불명확 → 확인 중 → 명확」이다.
defineProps({ id: { type: String, required: true } })
</script>

<template>
  <linearGradient :id="id" x1="0%" y1="0%" x2="100%" y2="0%">
    <stop offset="0%" stop-color="var(--logo-blue)" />
    <stop offset="50%" stop-color="var(--logo-lav)" />
    <stop offset="100%" stop-color="var(--logo-coral)" />
  </linearGradient>
</template>

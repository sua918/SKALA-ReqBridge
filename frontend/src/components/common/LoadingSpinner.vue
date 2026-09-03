<script setup>
defineProps({ label: { type: String, default: '불러오는 중' } })
</script>

<template>
  <div class="wrap">
    <svg class="spin" width="20" height="20" viewBox="0 0 24 24" fill="none"
         stroke="var(--primary-600)" stroke-width="2" stroke-linecap="round">
      <path d="M12 3a9 9 0 1 0 9 9" />
    </svg>
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.wrap { display: flex; align-items: center; gap: 10px; padding: 28px 0; color: var(--fg-700); font-size: var(--fs-sm); }
.spin { animation: rb-spin 1s linear infinite; }
</style>

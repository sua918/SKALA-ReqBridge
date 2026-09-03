<script setup>
defineProps({ num: { type: String, default: '' }, text: { type: String, required: true } })
</script>

<template>
  <div class="eb label">
    <span v-if="num" class="fig num">{{ num }}</span>{{ text }}
  </div>
</template>

<style scoped>
.label { margin-bottom: 14px; }
.num {
  font-size: var(--fs-sm); letter-spacing: 0; text-transform: none;
  color: var(--accent-700); margin-right: 8px;
}
</style>

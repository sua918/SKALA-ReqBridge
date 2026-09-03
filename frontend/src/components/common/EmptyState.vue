<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<template>
  <div class="empty">
    <img src="/reqbridge-mark.png" alt="" class="mark" />
    <div class="hd title">{{ title }}</div>
    <div v-if="description" class="desc">{{ description }}</div>
    <div v-if="$slots.default" class="action"><slot /></div>
  </div>
</template>

<style scoped>
.empty {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  gap: 11px; padding: 44px 0 46px; background: var(--bg-100);
  border: 1px solid var(--bg-200); border-radius: 10px;
}
.mark { width: 62px; opacity: .28; }
.title { font-size: 16px; font-weight: 600; }
.desc { font-size: var(--fs-xs); color: var(--fg-600); }
.action { margin-top: 4px; }
</style>

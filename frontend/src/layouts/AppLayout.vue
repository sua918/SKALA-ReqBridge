<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const crumbs = computed(() => {
  const items = route.meta.breadcrumb
  if (!Array.isArray(items)) {
    return []
  }

  return items.map((item) => {
    if (item.skeleton) {
      return { type: 'skeleton', key: 'skeleton' }
    }

    if (item.dynamic) {
      const id = route.params[item.dynamic]
      const label = id != null ? `${item.prefix ?? ''}${id}` : null
      if (label == null) {
        return { type: 'skeleton', key: item.dynamic }
      }

      const to =
        typeof item.to === 'function' ? item.to(route) : item.to ?? undefined

      return {
        type: 'text',
        key: item.dynamic,
        label,
        to,
      }
    }

    return {
      type: 'text',
      key: item.label,
      label: item.label,
      to: item.to,
    }
  })
})
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <p class="app-brand">ReqBridge</p>
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <ol class="breadcrumb-list">
          <li
            v-for="(crumb, index) in crumbs"
            :key="`${crumb.key}-${index}`"
            class="breadcrumb-item"
          >
            <span
              v-if="crumb.type === 'skeleton'"
              class="breadcrumb-skeleton"
              aria-hidden="true"
            />
            <RouterLink
              v-else-if="crumb.to && index < crumbs.length - 1"
              :to="crumb.to"
              class="breadcrumb-link"
            >
              {{ crumb.label }}
            </RouterLink>
            <span
              v-else
              class="breadcrumb-current"
              :aria-current="index === crumbs.length - 1 ? 'page' : undefined"
            >
              {{ crumb.label }}
            </span>
            <span
              v-if="index < crumbs.length - 1"
              class="breadcrumb-sep"
              aria-hidden="true"
            >
              /
            </span>
          </li>
        </ol>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

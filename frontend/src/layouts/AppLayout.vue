<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBreadcrumbLabels } from '@/composables/useBreadcrumbLabels'

const route = useRoute()
const {
  projectName,
  documentTitle,
  documentProjectId,
  documentProjectName,
  documentIdOfRequirement,
  resolving,
} = useBreadcrumbLabels()

/** 조회한 이름을 우선 쓰고, 없으면 일반명으로 대체한다. DB ID는 표시하지 않는다. */
function resolvedName(kind) {
  if (kind === 'project') {
    return projectName.value
  }
  if (kind === 'documentProject') {
    return documentProjectName.value
  }
  if (kind === 'document' || kind === 'requirementDocument') {
    return documentTitle.value
  }
  return null
}

/** 문서가 속한 프로젝트. route에 projectId가 없어 문서 응답으로 채운다. */
function documentProjectCrumb(name) {
  if (name == null && resolving.value) {
    return { type: 'skeleton', key: 'documentProject' }
  }
  const projectId = documentProjectId.value
  if (projectId == null) {
    return null
  }
  return {
    type: 'text',
    key: 'documentProject',
    label: name ?? '프로젝트',
    to: { name: 'document-list', params: { projectId: String(projectId) } },
  }
}

const crumbs = computed(() => {
  const items = route.meta.breadcrumb
  if (!Array.isArray(items)) {
    return []
  }

  //조회에 실패해 표시할 수 없는 항목은 빼고 나머지 경로는 그대로 보여준다.
  return items.map(toCrumb).filter((crumb) => crumb !== null)
})

function toCrumb(item) {
  const name = item.resolve ? resolvedName(item.resolve) : null

  if (item.resolve === 'documentProject') {
    return documentProjectCrumb(name)
  }

  if (item.resolve === 'requirementDocument') {
    if (name == null && resolving.value) {
      return { type: 'skeleton', key: 'requirementDocument' }
    }
    const documentId = documentIdOfRequirement.value
    return {
      type: 'text',
      key: 'requirementDocument',
      label: name ?? '문서',
      to: documentId
        ? {
            name: 'document-detail',
            params: { documentId: String(documentId) },
          }
        : undefined,
    }
  }

  if (item.dynamic) {
    const id = route.params[item.dynamic]
    if (id == null) {
      return { type: 'skeleton', key: item.dynamic }
    }
    if (item.resolve && name == null && resolving.value) {
      return { type: 'skeleton', key: item.dynamic }
    }

    const to = typeof item.to === 'function' ? item.to(route) : item.to ?? undefined

    return {
      type: 'text',
      key: item.dynamic,
      label: name ?? item.fallback ?? '항목',
      to,
    }
  }

  return {
    type: 'text',
    key: item.label,
    label: item.label,
    to: item.to,
  }
}
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

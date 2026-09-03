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

/** 조회한 이름을 우선 쓰고, 아직 없으면 ID 표기로 대체한다. */
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
    label: name ?? `프로젝트 #${projectId}`,
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
      label: name ?? (documentId ? `문서 #${documentId}` : '문서'),
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
      label: name ?? `${item.prefix ?? ''}${id}`,
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
      <div class="app-bar">
        <RouterLink class="app-brand" :to="{ name: 'project-list' }">
          <img class="app-mark" src="/reqbridge-mark.png" alt="" aria-hidden="true" />
          <span class="app-word">ReqBridge</span>
        </RouterLink>

        <span class="app-div" aria-hidden="true" />

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
              <!-- 슬래시 대신 갈매기표. 경로는 「구분」이 아니라 「어디서 어디로」다. -->
              <svg
                v-if="index < crumbs.length - 1"
                class="breadcrumb-sep" width="12" height="12" viewBox="0 0 24 24"
                fill="none" stroke="currentColor" stroke-width="2.4"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
              >
                <path d="M9 6l6 6-6 6" />
              </svg>
            </li>
          </ol>
        </nav>

      </div>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
/* 원본은 스타일을 전역 style.css에 두었는데, 그 파일을 디자인 시스템으로 바꾸면서
   .app-header·.breadcrumb-* 규칙이 사라졌다. 셸 전용 스타일이라 여기로 가져온다.
   색·서체·간격은 토큰만 쓴다. */
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 바(chrome)는 지반보다 밝은 판이다 — 스크롤되는 내용이 그 아래로 지나간다. */
.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(252, 253, 255, .9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--bg-300);
}

/* 로고와 경로를 한 줄에 둔다. 두 줄로 쌓으면 업무 화면에서 상단 90px를 쓰는데,
   그중 오른쪽 절반이 통째로 비어 있었다. */
/* 안쪽 여백을 바가 직접 갖는다. 헤더에 padding을 주고 바에 max-width를 걸면
   바가 「남은 폭」의 가운데로 가서 본문 기둥보다 40px 왼쪽에서 시작한다.
   상단 로고와 본문 제목의 왼쪽 변이 어긋나면 화면 전체가 흔들려 보인다. */
.app-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  max-width: var(--maxw);
  margin-inline: auto;
  padding: 14px var(--gutter) 15px;
  min-height: 68px;
  box-sizing: border-box;
}

.app-brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  flex: 0 0 auto;
  text-decoration: none;
}

.app-mark {
  width: 25px;
  height: auto;
  display: block;
  transition: transform .5s var(--ease);
}

.app-brand:hover .app-mark { transform: rotate(-8deg) scale(1.06); }

/* 워드마크는 로고타입이라 디스플레이 서체를 그대로 쓴다. */
.app-word {
  font-family: var(--font-head);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -.025em;
  color: var(--fg-950);
}

/* 로고(정체성)와 경로(위치)는 다른 종류의 정보다. 얇은 선으로 갈라 둔다. */
.app-div {
  width: 1px;
  height: 18px;
  background: var(--bg-300);
  flex: 0 0 auto;
}

.breadcrumb { flex: 1 1 auto; min-width: 0; }

.breadcrumb-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.breadcrumb-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--fg-500);
}

.breadcrumb-link {
  color: var(--fg-500);
  text-decoration: none;
  white-space: nowrap;
  transition: color .2s var(--ease);
}

.breadcrumb-link:hover { color: var(--primary-700); }

/* 현재 위치는 링크가 아니라 「지금 여기」다 — 색이 아니라 무게로 구분한다.
   길면 줄이 두 줄이 되는 대신 말줄임한다. 상단 바가 흔들리면 안 된다. */
.breadcrumb-current {
  color: var(--fg-800);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb-sep { color: var(--fg-300); flex: 0 0 auto; }

/* 이름을 조회하는 동안 자리를 잡아 둔다. 글자가 늦게 들어와도 줄이 밀리지 않는다. */
.breadcrumb-skeleton {
  display: inline-block;
  width: 84px;
  height: 12px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--bg-200) 0%, var(--bg-300) 50%, var(--bg-200) 100%);
  background-size: 200% 100%;
  animation: rb-shimmer 1.4s linear infinite;
}

@keyframes rb-shimmer {
  to { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .breadcrumb-skeleton { animation: none; }
  .app-mark { transition: none; }
}

@media (max-width: 720px) {
  .app-div { display: none; }
  .app-bar { flex-wrap: wrap; gap: 9px 12px; padding-block: 12px 13px; }
  .breadcrumb { flex-basis: 100%; }
}

.app-main {
  flex: 1 1 auto;
  width: 100%;
}
</style>

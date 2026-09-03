// frontend/src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/projects',
    },
    {
      path: '/projects',
      name: 'project-list',
      component: () => import('@/views/ProjectListView.vue'),
      meta: { breadcrumb: [{ label: '프로젝트' }] },
    },
    // Step 3에서 route 추가 — 지금은 placeholder 1~2개만
  ],
})

export default router
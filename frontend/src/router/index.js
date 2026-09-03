import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      // route 추가
      children: [
        {
          path: '',
          redirect: '/projects',
        },
        {
          path: 'projects',
          name: 'project-list',
          component: () => import('@/views/ProjectListView.vue'),
          meta: {
            breadcrumb: [{ label: '프로젝트' }],
          },
        },
        {
          path: 'projects/:projectId(\\d+)/documents',
          name: 'document-list',
          component: () => import('@/views/DocumentListView.vue'),
          meta: {
            breadcrumb: [
              { label: '프로젝트', to: { name: 'project-list' } },
              { dynamic: 'projectId', prefix: '프로젝트 #' },
              { label: '문서' },
            ],
          },
        },
        {
          path: 'documents/:documentId(\\d+)',
          name: 'document-detail',
          component: () => import('@/views/DocumentDetailView.vue'),
          meta: {
            breadcrumb: [
              { label: '프로젝트', to: { name: 'project-list' } },
              // 제목 API 연동 전: URL id 표시 (이후 GET /documents/{id})
              { dynamic: 'documentId', prefix: '문서 #' },
            ],
          },
        },
        {
          path: 'requirements/:requirementId(\\d+)',
          name: 'requirement-workflow',
          component: () => import('@/views/RequirementWorkflowView.vue'),
          meta: {
            breadcrumb: [
              { label: '프로젝트', to: { name: 'project-list' } },
              // documentId는 GET /requirements/{id}로 채움
              { label: '문서', skeleton: true },
              { dynamic: 'requirementId', prefix: '요구사항 #' },
            ],
          },
        },
        {
          path: 'documents/:documentId(\\d+)/preview',
          name: 'document-preview',
          component: () => import('@/views/PreviewView.vue'),
          meta: {
            breadcrumb: [
              { label: '프로젝트', to: { name: 'project-list' } },
              {
                dynamic: 'documentId',
                prefix: '문서 #',
                to: (route) => ({
                  name: 'document-detail',
                  params: { documentId: route.params.documentId },
                }),
              },
              { label: 'Preview' },
            ],
          },
        },
      ],
    },
  ],
})

export default router

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
              { dynamic: 'projectId', prefix: '프로젝트 #', resolve: 'project' },
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
              // 문서 목록으로 돌아갈 projectId는 문서 응답에서 얻는다
              { resolve: 'documentProject' },
              { dynamic: 'documentId', prefix: '문서 #', resolve: 'document' },
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
              { resolve: 'documentProject' },
              // 문서명은 GET /requirements/{id}의 documentId로 조회한다
              { resolve: 'requirementDocument' },
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
              { resolve: 'documentProject' },
              {
                dynamic: 'documentId',
                prefix: '문서 #',
                resolve: 'document',
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

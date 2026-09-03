import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument } from '@/api/documents'
import { getProject } from '@/api/projects'
import { getRequirement } from '@/api/requirements'

/**
 * breadcrumb에 표시할 이름을 route 파라미터로부터 조회한다 (합의 1).
 *
 * - 프로젝트명: GET /projects/{id}
 * - 문서명: GET /documents/{id}. 응답의 projectId로 상위 프로젝트도 함께 조회한다
 * - requirement 화면: GET /requirements/{id}의 documentId로 위 경로를 이어서 조회
 * - 조회 실패는 화면을 막지 않고 ID 표기로 되돌린다.
 */
export function useBreadcrumbLabels() {
  const route = useRoute()

  const projectName = ref(null)
  const documentTitle = ref(null)
  const documentProjectId = ref(null)
  const documentProjectName = ref(null)
  const documentIdOfRequirement = ref(null)
  const resolving = ref(false)

  //라우트를 빠르게 옮기면 늦게 도착한 응답이 새 화면 이름을 덮어쓸 수 있다.
  let currentRequest = 0

  async function fetchOrNull(promise) {
    try {
      return await promise
    } catch {
      return null
    }
  }

  async function resolveProject(request, projectId) {
    if (projectId == null) {
      return
    }
    const project = await fetchOrNull(getProject(projectId))
    if (request === currentRequest) {
      projectName.value = project?.name ?? null
    }
  }

  async function resolveDocument(request, documentId) {
    if (documentId == null) {
      return
    }
    const document = await fetchOrNull(getDocument(documentId))
    if (request !== currentRequest) {
      return
    }
    documentTitle.value = document?.title ?? null
    documentProjectId.value = document?.projectId ?? null

    if (documentProjectId.value == null) {
      return
    }
    const project = await fetchOrNull(getProject(documentProjectId.value))
    if (request === currentRequest) {
      documentProjectName.value = project?.name ?? null
    }
  }

  async function resolveFromRequirement(request, requirementId) {
    if (requirementId == null) {
      return
    }
    const requirement = await fetchOrNull(getRequirement(requirementId))
    if (request !== currentRequest) {
      return
    }
    documentIdOfRequirement.value = requirement?.documentId ?? null
    await resolveDocument(request, documentIdOfRequirement.value)
  }

  watch(
    () => [
      route.params.projectId,
      route.params.documentId,
      route.params.requirementId,
    ],
    async ([projectId, documentId, requirementId]) => {
      const request = ++currentRequest
      resolving.value = true
      projectName.value = null
      documentTitle.value = null
      documentProjectId.value = null
      documentProjectName.value = null
      documentIdOfRequirement.value = null

      await Promise.all([
        resolveProject(request, projectId),
        requirementId != null
          ? resolveFromRequirement(request, requirementId)
          : resolveDocument(request, documentId),
      ])

      if (request === currentRequest) {
        resolving.value = false
      }
    },
    { immediate: true },
  )

  return {
    projectName,
    documentTitle,
    documentProjectId,
    documentProjectName,
    documentIdOfRequirement,
    resolving,
  }
}

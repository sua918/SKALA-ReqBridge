/**
 * 프로젝트 API (Spec §5.1~5.3).
 *
 * 주 사용: B (목록·생성·상세).
 * C는 거의 사용하지 않음.
 */
import { api, unwrap } from '@/api/client'
import { useMock } from '@/api/config'
import { notFound } from '@/api/mockError'
import {
  createProjectMock,
  getProjectMock,
  listProjectsMock,
} from '@/mocks/store.js'

/** GET /projects — 목록 (id 내림차순). */
export async function listProjects() {
  if (useMock) {
    return listProjectsMock()
  }
  return unwrap(await api.get('/projects'))
}

/** GET /projects/{projectId} — 상세. */
export async function getProject(projectId) {
  if (useMock) {
    const project = getProjectMock(projectId)
    if (!project) {
      return notFound('프로젝트')
    }
    return project
  }
  return unwrap(await api.get(`/projects/${projectId}`))
}

/** POST /projects — 생성. body: { name, description? }. */
export async function createProject(body) {
  if (useMock) {
    return createProjectMock(body)
  }
  return unwrap(await api.post('/projects', body))
}

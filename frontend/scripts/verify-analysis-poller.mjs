/**
 * useAnalysisPoller: inFlight · 세대 ID 검증.
 * 실행: cd frontend && npx vite-node scripts/verify-analysis-poller.mjs
 */
import { createServer } from 'vite'
import { fileURLToPath, URL } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const srcRoot = fileURLToPath(new URL('../src', import.meta.url))
const pollerPath = fileURLToPath(
  new URL('../src/composables/useAnalysisPoller.js', import.meta.url),
)

let getAnalysisHandler = async () => {
  throw new Error('getAnalysis handler not set')
}

const server = await createServer({
  configFile: false,
  root,
  plugins: [
    {
      name: 'poller-verify-mocks',
      enforce: 'pre',
      resolveId(id) {
        if (
          id === '@/api/analyses' ||
          id.endsWith('/api/analyses') ||
          id.endsWith('/api/analyses.js')
        ) {
          return '\0mock-analyses'
        }
      },
      load(id) {
        if (id === '\0mock-analyses') {
          return `
            export async function getAnalysis(analysisId) {
              return globalThis.__pollerGetAnalysis(analysisId)
            }
          `
        }
      },
      transform(code, id) {
        if (id !== pollerPath && !id.endsWith('useAnalysisPoller.js')) {
          return null
        }
        //검증 스크립트는 컴포넌트 setup 밖에서도 호출한다.
        return code
          .replace(
            /import \{ onUnmounted, ref, shallowRef \} from 'vue'/,
            "import { ref, shallowRef } from 'vue'",
          )
          .replace(
            /onUnmounted\(\(\) => \{\s*stop\(\)\s*\}\)/,
            '/* onUnmounted skipped in verify */',
          )
      },
    },
  ],
  resolve: {
    alias: {
      '@': srcRoot,
    },
  },
})

await server.pluginContainer.buildStart({})
globalThis.__pollerGetAnalysis = (id) => getAnalysisHandler(id)

const { useAnalysisPoller } = await server.ssrLoadModule(pollerPath)
const { AnalysisStatus } = await server.ssrLoadModule(
  fileURLToPath(new URL('../src/types/api.js', import.meta.url)),
)

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

const results = []

// --- 1) inFlight: 느린 요청 중 interval tick이 겹쳐도 동시 호출은 1개 ---
{
  const poller = useAnalysisPoller({ intervalMs: 40 })
  let concurrent = 0
  let maxConcurrent = 0
  let callCount = 0
  const releases = []

  getAnalysisHandler = () =>
    new Promise((resolve) => {
      callCount += 1
      concurrent += 1
      maxConcurrent = Math.max(maxConcurrent, concurrent)
      releases.push(() => {
        concurrent -= 1
        resolve({
          id: 1,
          status: AnalysisStatus.PENDING,
          result: null,
          error: null,
        })
      })
    })

  poller.start(1)
  await sleep(130)
  assert(callCount === 1, `inFlight: 겹친 요청 금지 (calls=${callCount})`)
  assert(maxConcurrent === 1, `inFlight: 동시성 1 (max=${maxConcurrent})`)
  releases.forEach((release) => release())
  await sleep(20)
  poller.stop()
  results.push({ name: 'inFlight blocks overlap', ok: true, callCount, maxConcurrent })
}

// --- 2) stop 이후 늦게 도착한 COMPLETED는 무시 ---
{
  const poller = useAnalysisPoller({ intervalMs: 40 })
  let completeCount = 0
  let resolveFirst
  const firstGate = new Promise((resolve) => {
    resolveFirst = resolve
  })
  let calls = 0

  getAnalysisHandler = async (id) => {
    calls += 1
    if (calls === 1) {
      await firstGate
      return {
        id,
        status: AnalysisStatus.COMPLETED,
        result: {
          requirementIds: [1],
          issueIds: [],
          clarificationIds: [],
          revisionIds: [],
          assessment: null,
        },
        error: null,
      }
    }
    return { id, status: AnalysisStatus.PENDING, result: null, error: null }
  }

  poller.start(10, {
    onComplete: () => {
      completeCount += 1
    },
  })
  await sleep(15)
  poller.stop()
  resolveFirst()
  await sleep(50)

  assert(completeCount === 0, '세대: stop 후 늦은 COMPLETED → onComplete 금지')
  assert(poller.analysis.value === null, '세대: stop 후 analysis 유지(null)')
  assert(poller.isPolling.value === false, 'stop 후 isPolling=false')
  poller.stop()
  results.push({
    name: 'stale response ignored after stop',
    ok: true,
    calls,
    completeCount,
  })
}

// --- 3) 재start: 이전 analysisId 응답은 무시, 새 ID만 완료 ---
{
  const poller = useAnalysisPoller({ intervalMs: 40 })
  const completeFor = []
  const gates = []

  getAnalysisHandler = (id) =>
    new Promise((resolve) => {
      gates.push({
        id,
        release: (status) =>
          resolve({
            id,
            status,
            result:
              status === AnalysisStatus.COMPLETED
                ? {
                    requirementIds: [],
                    issueIds: [],
                    clarificationIds: [],
                    revisionIds: [],
                    assessment: null,
                  }
                : null,
            error: null,
          }),
      })
    })

  poller.start(10, {
    onComplete: (a) => {
      completeFor.push(a.id)
    },
  })
  await sleep(20)
  poller.start(20, {
    onComplete: (a) => {
      completeFor.push(a.id)
    },
  })
  await sleep(20)

  const gate10 = gates.filter((g) => g.id === 10)
  const gate20 = gates.filter((g) => g.id === 20)
  assert(gate10.length >= 1, 'id=10 요청 존재')
  assert(gate20.length >= 1, 'id=20 요청 존재')

  gate10.forEach((g) => g.release(AnalysisStatus.COMPLETED))
  await sleep(40)
  assert(
    completeFor.length === 0,
    `재start 후 옛 id=10 COMPLETED 무시 (got ${completeFor})`,
  )

  gate20.forEach((g) => g.release(AnalysisStatus.COMPLETED))
  await sleep(40)
  assert(
    completeFor.length === 1 && completeFor[0] === 20,
    `완료는 id=20만 (got ${completeFor})`,
  )
  assert(poller.analysis.value?.id === 20, 'analysis.id === 20')
  poller.stop()
  results.push({
    name: 'restart ignores previous generation',
    ok: true,
    completeFor,
  })
}

// --- 4) stop 이후 늦은 오류는 onError 무시 ---
{
  const poller = useAnalysisPoller({ intervalMs: 40 })
  let errors = 0

  getAnalysisHandler = async () => {
    throw new Error('network down')
  }
  poller.start(1, {
    onError: () => {
      errors += 1
    },
  })
  await sleep(30)
  assert(errors === 1, `조회 실패 onError 1회 (got ${errors})`)
  assert(poller.isPolling.value === false, '오류 후 polling 중단')

  let rejectLate
  getAnalysisHandler = () =>
    new Promise((_, reject) => {
      rejectLate = () => reject(new Error('late fail'))
    })
  poller.start(2, {
    onError: () => {
      errors += 1
    },
  })
  await sleep(15)
  poller.stop()
  rejectLate()
  await sleep(40)
  assert(errors === 1, `stop 후 늦은 오류 무시 (errors=${errors})`)
  results.push({ name: 'stale error ignored after stop', ok: true, errors })
}

await server.close()

for (const row of results) {
  console.log(`PASS: ${row.name}`)
}
console.log(`ALL_PASS ${results.length}/4`)

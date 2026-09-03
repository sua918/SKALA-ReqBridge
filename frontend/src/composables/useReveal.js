import { onMounted, onUnmounted } from 'vue'

/**
 * 스크롤 리빌 활성기 (nexus-reference의 useReveal 이식).
 *
 * `[data-reveal]` 이 뷰포트에 들어오면 `.is-visible` 을 붙인다.
 * `[data-reveal-stagger]` 는 자식 `.stagger-child` 에 인덱스(`--si`)를 심어 순차 등장시킨다.
 * 실제 애니메이션은 전부 style.css가 담당한다 — 여기서는 클래스만 붙인다.
 *
 * 이 화면들은 목록·상세를 **비동기로 불러온 뒤** 그린다. 그래서 원본처럼
 * 마운트 시점에 한 번만 훑으면 나중에 생긴 노드를 놓치고, 그 노드는 `opacity:0` 인 채로
 * 영영 남아 **콘텐츠가 통째로 안 보인다.** MutationObserver로 DOM 변화를 따라가며 다시 훑는다.
 *
 * 앱 루트에서 한 번만 호출한다.
 */
export function useReveal() {
  let io = null
  let mo = null
  let queued = false

  const indexStagger = (root) => {
    root.querySelectorAll('.stagger-child').forEach((kid, i) => {
      if (!kid.style.getPropertyValue('--si')) kid.style.setProperty('--si', String(i))
    })
  }

  const scan = () => {
    queued = false
    if (!io) return
    document
      .querySelectorAll('[data-reveal]:not(.is-visible), [data-reveal-stagger]:not(.is-visible)')
      .forEach((el) => {
        if (el.hasAttribute('data-reveal-stagger')) indexStagger(el)
        io.observe(el) // 같은 요소를 다시 넘겨도 중복 등록되지 않는다
      })
  }

  /** DOM 변화가 몰아칠 때 프레임당 한 번만 훑는다. */
  const queueScan = () => {
    if (queued) return
    queued = true
    requestAnimationFrame(scan)
  }

  /** 관찰자를 못 쓰는 환경에서는 숨기지 않는다 — 안 보이는 것보다 낫다. */
  const revealAll = () => {
    document
      .querySelectorAll('[data-reveal], [data-reveal-stagger]')
      .forEach((el) => el.classList.add('is-visible'))
  }

  onMounted(() => {
    if (typeof IntersectionObserver === 'undefined') {
      revealAll()
      return
    }

    io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (!e.isIntersecting) return
          e.target.classList.add('is-visible')
          io.unobserve(e.target) // 1회성 — 다시 스크롤해도 재생하지 않는다
        })
      },
      { threshold: 0.12, rootMargin: '0px 0px -48px 0px' },
    )

    scan()

    // 라우트 전환·비동기 로딩으로 새로 그려지는 노드를 모두 따라잡는다.
    if (typeof MutationObserver !== 'undefined') {
      mo = new MutationObserver(queueScan)
      mo.observe(document.body, { childList: true, subtree: true })
    }
  })

  onUnmounted(() => {
    mo?.disconnect()
    io?.disconnect()
  })
}

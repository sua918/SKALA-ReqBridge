<script setup>
import { useReveal } from '@/composables/useReveal'

/**
 * 스크롤 리빌을 앱 루트에서 한 번만 건다. 각 화면은 `[data-reveal]` 속성만 붙이면 된다.
 *
 * 이 호출이 없으면 `[data-reveal]` 요소가 `opacity: 0`인 채로 남아 화면이 비어 보인다.
 * 레이아웃은 라우터가 AppLayout을 부모 route로 잡아 붙이므로 여기서 감싸지 않는다.
 */
useReveal()
</script>

<template>
  <RouterView />
</template>

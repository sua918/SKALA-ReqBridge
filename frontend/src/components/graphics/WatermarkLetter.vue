<script setup>
/**
 * 페이지 정체성 글자(P·D·R·C·X) — 배경 그래픽을 **도려내는** 역할이다.
 *
 * 글자를 종이색으로 불투명하게 찍으면, 뒤의 디더 파도가 글자 모양만큼 사라진다.
 * 글자는 잉크가 아니라 「빈 자리」로 읽히고, 획 안쪽이 종이색이라 형태가 무너질 일이 없다.
 *
 * SVG 마스크 대신 이 방법을 쓰는 이유: 마스크로 HTML 요소를 파내는 건 브라우저 지원이
 * 고르지 않은데, 종이색으로 덮는 건 어디서나 똑같이 동작한다.
 */
defineProps({
  letter: { type: String, required: true },
  size: { type: Number, default: 360 },
})
</script>

<template>
  <span class="wm-letter" :style="{ fontSize: size + 'px' }">{{ letter }}</span>
</template>

<style scoped>
.wm-letter {
  display: block; line-height: .76;
  font-family: var(--font-head); font-weight: 900; letter-spacing: -.05em;
  color: var(--bg-50);
  user-select: none; pointer-events: none;
}
</style>

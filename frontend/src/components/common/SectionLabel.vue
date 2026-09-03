<script setup>
/**
 * 목록 위에 서는 제목. 「프로젝트」 「지금 답이 필요한 질문」처럼 무엇의 목록인지 말한다.
 *
 * `count`를 넘기면 건수를 제목과 다른 무게로 붙인다. 예전에는 호출부가
 * `'프로젝트 ' + n + '건'`처럼 한 문자열로 이어 붙였는데, 그러면 이름과 숫자가 같은
 * 무게로 읽혀 「몇 건인가」가 제목 속에 묻혔다. 목록에서 먼저 알고 싶은 것은
 * 「무엇의 목록인가」와 「몇 개인가」 둘이고, 둘은 다른 정보다.
 */
defineProps({
  num: { type: String, default: '' },
  text: { type: String, required: true },
  /** 건수. 숫자만 넘긴다 — 「건」은 이 컴포넌트가 붙인다. */
  count: { type: Number, default: null },
})
</script>

<template>
  <!-- `eb`는 그냥 두는 클래스가 아니다. style.css의 `.card.list > .eb:first-child`가
       이 클래스로 카드 머리말의 여백과 바탕색을 건다 — 빼면 제목이 카드 테두리에
       달라붙는다. 글자 모양은 아래 scoped 규칙이 덮어쓴다. -->
  <div class="eb label">
    <span v-if="num" class="fig num">{{ num }}</span>
    <span class="text">{{ text }}</span>
    <span v-if="count !== null" class="count"><b class="fig">{{ count }}</b>건</span>
  </div>
</template>

<style scoped>
/* 카드 밖에서 목록 위에 홀로 서는 제목이다. 14px 라벨 크기로 두면 그 아래 카드
   제목(17px)보다 작아, 상위 제목이 하위보다 약해 보이는 역전이 생긴다. */
.label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 14px;
  font-size: var(--fs-h3);
  font-weight: 750;
  letter-spacing: -.015em;
  line-height: var(--lh-tight);
  color: var(--fg-950);
}
.num {
  font-size: var(--fs-sm); letter-spacing: 0;
  color: var(--accent-700); font-weight: 700;
}
.text { min-width: 0; }

/* 건수는 제목이 아니라 제목에 딸린 수치다. 「건」은 낮추고 숫자만 세운다. */
.count {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--fg-500);
  white-space: nowrap;
}
.count b {
  margin-right: 1px;
  font-size: var(--fs-h3);
  font-weight: 800;
  color: var(--accent-700);
}
</style>

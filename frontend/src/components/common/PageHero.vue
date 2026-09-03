<script setup>
/**
 * 화면 머리말.
 *
 * 예전에는 디더 파도와 배경 워터마크 글자를 깐 「히어로」였다. 팀 시안에 맞춰
 * 그래픽을 걷어내고 제목·부제·요약만 남긴다. 업무 화면에서는 상단 띠가 236px을
 * 차지할 이유가 없고, 매일 같은 화면을 여는 사람에게 장식은 첫날만 새롭다.
 *
 * 컴포넌트 이름과 props는 그대로 둔다 — 다섯 화면이 이미 이 인터페이스로 부른다.
 * `watermark`는 배경 글자를 그리던 값이라 더 이상 쓰지 않지만, 호출부를 한꺼번에
 * 고치는 대신 받아서 무시한다.
 */
defineProps({
  num: { type: String, default: '' },
  eyebrow: { type: String, required: true },
  /**
   * 화면 설명. **평소에는 쓰지 않는다.**
   * 안내가 필요한 순간은 데이터가 없을 때뿐이고, 그건 EmptyState가 맡는다.
   */
  intro: { type: String, default: '' },
  /** [{ value, label }] — 화면 규모를 한눈에 보여주는 숫자 줄 */
  chips: { type: Array, default: () => [] },
  /** 더 이상 그리지 않는다. 호출부 호환을 위해 받기만 한다. */
  watermark: { type: String, default: '' },
})
</script>

<template>
  <header class="hd-wrap" data-reveal>
    <div class="inner">
      <div class="lead">
        <div class="eb label">
          <span v-if="num" class="fig num">{{ num }}</span>{{ eyebrow }}
        </div>
        <h1 class="title"><slot name="title" /></h1>
        <p v-if="$slots.subject" class="subject"><slot name="subject" /></p>
        <p v-if="intro" class="intro">{{ intro }}</p>
      </div>

      <div class="side">
        <div class="actions"><slot name="actions" /></div>
        <!-- 숫자 줄은 오른쪽으로 보낸다. 그래픽이 빠져 상단이 짧아진 만큼
             제목 아래로 더 쌓으면 본문이 그만큼 밀린다. -->
        <div v-if="chips.length" class="chips">
          <div v-for="c in chips" :key="c.label" class="chip">
            <span class="v fig">{{ c.value }}</span>
            <span class="l">{{ c.label }}</span>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* 머리말과 본문은 괘선 하나로 가른다. 색이나 그림자를 쓰면 머리말이 카드처럼 보인다. */
.hd-wrap {
  padding: 26px 0 20px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--rule);
}

.inner {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 24px;
}
.lead { min-width: 0; }

.label { margin-bottom: 8px; font-size: var(--fs-sm); }
.num { font-size: var(--fs-sm); letter-spacing: 0; text-transform: none; color: var(--accent-700); margin-right: 8px; }

/* 제목은 먹빛 대신 브랜드의 짙은 남색으로 둔다. 흰 바탕에 검정 큰 글씨는
   본문과 같은 색이라 「제목」이 아니라 「굵은 문장」으로 읽힌다. */
.title {
  margin: 0;
  font-size: clamp(22px, 2.2vw, 30px);
  font-weight: 750;
  letter-spacing: -.022em;
  line-height: 1.25;
  color: var(--accent-700);
  word-break: keep-all;
}
/* 제목이 화면의 종류라면 부제는 그 화면이 다루는 대상(문서명 등)이다. */
.subject {
  margin: 7px 0 0; max-width: 52ch;
  font-size: var(--fs-title); color: var(--fg-600);
  word-break: keep-all;
}
.intro {
  margin: 9px 0 0; max-width: 52ch;
  font-size: var(--fs-sm); line-height: 1.65; color: var(--fg-500);
}

.side {
  display: flex; flex-direction: column; align-items: flex-end; gap: 14px;
  flex-shrink: 0; padding-top: 2px;
}
.actions { display: flex; align-items: center; gap: 10px; }

/* 숫자 줄. 항목 사이를 얇은 세로 괘선으로 갈라 표처럼 읽히게 한다. */
.chips { display: flex; align-items: stretch; }
.chip {
  display: flex; flex-direction: column; align-items: flex-end; gap: 2px;
  padding: 0 14px;
  border-left: 1px solid var(--rule);
}
.chip:first-child { border-left: 0; padding-left: 0; }
.chip:last-child { padding-right: 0; }
.chip .v { font-size: 19px; line-height: 1.2; color: var(--fg-950); }
.chip .l { font-size: var(--fs-micro); color: var(--fg-500); white-space: nowrap; }

@media (max-width: 900px) {
  .inner { flex-direction: column; align-items: flex-start; gap: 16px; }
  .side { align-items: flex-start; width: 100%; padding-top: 0; }
  .chip:first-child { padding-left: 0; }
}
</style>

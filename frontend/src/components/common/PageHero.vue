<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import DitherWave from '@/components/graphics/DitherWave.vue'
import WatermarkLetter from '@/components/graphics/WatermarkLetter.vue'
/**
 * 화면 상단 히어로 (nexus-reference의 PageHero 이식).
 *
 * nexus는 사진 + 워터마크 글자 + 칩 줄로 페이지의 정체성을 세운다.
 * 업무 화면이라 사진은 빼고, 그 자리를 「지금 이 화면의 숫자」(chips)가 대신한다.
 * 제목은 slot으로 받는다 — Vue는 JSX를 prop으로 넘길 수 없어서다.
 */
defineProps({
  num: { type: String, default: '' },
  eyebrow: { type: String, required: true },
  /**
   * 화면 설명. **평소에는 쓰지 않는다.**
   * 매일 쓰는 사람에게 온보딩 문구는 첫날 한 번 읽고 끝나는 영구 노이즈다.
   * 안내가 필요한 순간은 데이터가 없을 때뿐이고, 그건 EmptyState가 맡는다.
   * 시연처럼 처음 보는 사람에게 설명이 필요할 때만 넘긴다.
   */
  intro: { type: String, default: '' },
  /** [{ value, label }] — 화면 규모를 한눈에 보여주는 숫자 줄 */
  chips: { type: Array, default: () => [] },
  /** 배경에 크게 깔리는 페이지 정체성 글자. 이 글자 안에서 브랜드 그래픽이 흐른다. */
  watermark: { type: String, default: '' },
})

/**
 * 스크롤에 따라 히어로가 「비켜서는」 정도. 0(그대로) → 1(다 비켜섬).
 *
 * 업무 화면이라 작업을 시작하면 장식이 물러나야 한다. 다만 임계값을 넘는 순간 툭 접으면
 * 본문이 갑자기 위로 튀어 읽던 자리를 잃는다. 스크롤 양에 비례해 연속으로 줄여서,
 * 「내가 밀어낸 것」처럼 느껴지게 한다.
 *
 * 높이가 아니라 장식의 투명도를 주로 줄인다 — 시선을 뺏는 건 그래픽이지 여백이 아니다.
 */
const collapse = ref(0)
let ticking = false

const onScroll = () => {
  if (ticking) return
  ticking = true
  requestAnimationFrame(() => {
    collapse.value = Math.min(1, window.scrollY / 180)
    ticking = false
  })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <header class="hero" data-reveal :style="{ '--c': collapse }">
    <div class="deco">
      <div class="ditherwrap">
        <DitherWave />
        <div class="wash" />
      </div>
      <!-- 글자는 잉크가 아니라 빈 자리다 — 종이색으로 찍어 파도를 도려낸다 -->
      <WatermarkLetter v-if="watermark" class="wm" :letter="watermark" :size="240" />
    </div>

    <div class="inner">
      <div class="lead">
        <div class="eb label">
          <span v-if="num" class="fig num">{{ num }}</span>{{ eyebrow }}
        </div>
        <h1 class="hd-display title"><slot name="title" /></h1>
        <!-- 영문 제목은 「화면의 종류」를 말한다. 그 화면이 다루는 대상(문서명 등)은
             바로 아래 한 줄로 받아, 배경 워터마크 글자와 짝이 맞게 한다. -->
        <p v-if="$slots.subject" class="hd subject"><slot name="subject" /></p>
        <p v-if="intro" class="intro">{{ intro }}</p>

        <!-- 칩은 왼쪽 기둥에 붙인다. 오른쪽 끝에 떼어 놓으면 제목과 관계가 끊겨
             화면 규모를 「제목의 부연」으로 읽히게 하지 못한다. -->
        <div v-if="chips.length" class="chips">
          <div v-for="c in chips" :key="c.label" class="chip">
            <span class="v">{{ c.value }}</span>
            <span class="l">{{ c.label }}</span>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="actions"><slot name="actions" /></div>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* 아래 본문 카드와 확실히 떨어뜨린다 — 붙어 있으면 히어로 그래픽이 카드 위로 올라탄 것처럼 보인다. */
/* 업무 화면이라 첫 화면을 목록·폼에 내준다. 히어로는 「어느 화면인지」만 말하고 비켜선다.
   --c는 스크롤에 따라 0→1로 오르며, 아래 값들이 여기에 물려 연속으로 줄어든다. */
.hero {
  position: relative; overflow: hidden;
  /* 업무 화면이라 본문이 빨리 나와야 한다. 히어로는 「여기가 어디인가」와
     「지금 숫자」만 전하고 물러난다. 아래 여백을 56 -> 36px로 줄였다. */
  padding: 4px 0 calc(36px - 18px * var(--c, 0));
  /* 상단 띠의 높이를 화면끼리 맞춘다. 부제가 있고 없고에 따라 190~236px로 튀면
     화면을 옮길 때 같은 서비스로 안 읽힌다. 글은 위에서부터 자연스럽게 흐르고,
     남는 자리는 띠 아래쪽에 모여 그래픽이 채운다 — 글 사이가 비지 않는다. */
  min-height: calc(236px - 40px * var(--c, 0));
  box-sizing: border-box;
}
/* 디더 파도는 히어로 오른쪽을 덮고 화면 밖으로 흘려보낸다.
   사각 캔버스 그대로 두면 경계가 상자로 보여서 위·아래·왼쪽을 마스크로 녹인다. */
.ditherwrap {
  /* 아래쪽은 히어로 안에서 끝낸다. 음수로 두면 본문 영역까지 흘러내린다. */
  position: absolute; inset: -40px -40px 40px 48%;
  /* 작업을 시작하면 그래픽이 먼저 물러난다 — 시선을 붙잡는 건 이쪽이다. */
  opacity: calc(.5 - .5 * var(--c, 0));
  transform: translateY(calc(-14px * var(--c, 0)));
  /* 네 변을 모두 길게 녹인다. 오른쪽을 열어 두면 히어로 경계에서 잘려 세로선이 생긴다. */
  -webkit-mask-image:
    linear-gradient(90deg, transparent 0%, #000 30%, #000 74%, transparent 100%),
    linear-gradient(180deg, transparent 0%, #000 28%, #000 54%, transparent 96%);
  mask-image:
    linear-gradient(90deg, transparent 0%, #000 30%, #000 74%, transparent 100%),
    linear-gradient(180deg, transparent 0%, #000 28%, #000 54%, transparent 96%);
  -webkit-mask-composite: source-in;
  mask-composite: intersect;
}
/* 제목이 놓인 왼쪽을 바탕색으로 덮어 배경과 싸우지 않게 한다.
   반드시 「지반과 같은 색」이어야 한다. 예전 값(--bg-50)은 지반이 --bg-100이 된 뒤로
   제목 뒤에 한 톤 밝은 쐐기를 남겨, 얼룩처럼 보였다. */
.wash {
  position: absolute; inset: 0;
  background: linear-gradient(90deg, var(--bg-100) 0%, rgba(241, 244, 250, .45) 22%, rgba(241, 244, 250, 0) 62%);
}

/* 도려낸 글자. 가장자리를 흐려 파도와 경계 없이 섞이게 한다 —
   칼같이 떨어지면 스티커를 붙인 것처럼 보인다. */
.wm { position: absolute; right: 4%; top: -18px; filter: blur(3px); opacity: calc(.92 - .92 * var(--c, 0)); }

.inner {
  position: relative; z-index: 1;
  display: flex; align-items: flex-start; justify-content: space-between; gap: 34px;
}
.lead { min-width: 0; }
/* 프로젝트·문서 이름이 들어가 문장처럼 길어지는 자리라 라벨 크기로 두면 읽기 힘들다. */
.label { margin-bottom: 9px; font-size: var(--fs-sm); }
.num { font-size: var(--fs-sm); letter-spacing: 0; text-transform: none; color: var(--accent-700); margin-right: 8px; }

/* 굵기·자간은 스케일 토큰(.hd-display)에서 온다. 크기만 여기서 한 단 낮춘다 —
   42px는 업무 화면에서 제목이 아니라 표지처럼 읽힌다. */
.title { margin: 0; font-size: clamp(26px, 2.6vw, 34px); }
.subject {
  margin: 8px 0 0; max-width: 40ch;
  font-size: var(--fs-title); font-weight: var(--fw-title);
  color: var(--fg-600);
}
.intro {
  margin: 10px 0 0; max-width: 46ch;
  font-size: var(--fs-sm); line-height: 1.65; color: var(--fg-500);
}

/* 제목 기둥 아래로 붙는 숫자 줄. 위에 얇은 규칙선을 둬 제목과 묶어 준다. */
.chips {
  margin-top: 14px; padding-top: 12px;
  border-top: 1px solid var(--rule);
  max-width: 44ch;
}

.side { display: flex; flex-direction: column; align-items: flex-end; gap: 16px; flex-shrink: 0; padding-top: 6px; }
.actions { display: flex; align-items: center; gap: 10px; }

@media (max-width: 900px) {
  /* 좁은 화면에선 기둥이 세로로 쌓이므로 띠 높이를 고정할 이유가 없다. */
  .hero { min-height: 0; }
  .inner { flex-direction: column; align-items: flex-start; gap: 20px; }
  .side { align-items: flex-start; width: 100%; padding-top: 0; }
  .chips { max-width: none; }
  .wm { transform: scale(.62); transform-origin: top right; }
}
</style>

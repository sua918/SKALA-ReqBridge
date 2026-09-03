<script setup>
defineProps({
  variant: { type: String, default: 'primary' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})
</script>

<template>
  <button class="pill mi" :class="variant" :disabled="disabled || loading">
    <!-- nexus의 버튼 shimmer — hover 시 빛줄기가 좌에서 우로 지나간다 -->
    <span class="sweep" aria-hidden="true" />
    <svg v-if="loading" class="spin" width="13" height="13" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
      <path d="M12 3a9 9 0 1 0 9 9" />
    </svg>
    <span class="lbl"><slot /></span>
  </button>
</template>

<style scoped>
.pill {
  position: relative; overflow: hidden;
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 20px 11px; border-radius: 999px; border: 1px solid;
  font-size: var(--fs-micro); font-weight: 500; letter-spacing: var(--tr-label); text-transform: uppercase;
  white-space: nowrap; cursor: pointer;
  transition: background-color .3s var(--ease), color .3s var(--ease), border-color .3s var(--ease), transform .3s var(--ease);
}
.lbl, .spin { position: relative; z-index: 1; }
.sweep {
  position: absolute; inset: 0; transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .38), transparent);
  transition: transform .7s var(--ease);
}
.pill:not(:disabled):hover .sweep { transform: translateX(100%); }
.ghost .sweep, .quiet .sweep { background: linear-gradient(90deg, transparent, rgba(52, 72, 180, .14), transparent); }
/* 못 누르는 버튼은 색을 잃어야 한다. opacity만 낮추면 보라색이 옅게 남아
   「누를 수 있는데 흐린 것」처럼 보인다. 회색으로 바꿔 성질 자체를 바꾼다. */
.pill:disabled {
  background: var(--bg-200);
  border-color: var(--bg-200);
  color: var(--fg-400);
  cursor: not-allowed;
  box-shadow: none;
}

.pill:disabled .sweep { display: none; }
.pill:not(:disabled):hover { transform: translateY(-1px); }
.pill:focus-visible { outline: 2px solid var(--primary-600); outline-offset: 2px; }

.primary { background: var(--primary-600); color: var(--bg-50); border-color: var(--primary-600); }
.primary:not(:disabled):hover { background: var(--primary-700); border-color: var(--primary-700); }

.ghost { background: transparent; color: var(--fg-950); border-color: var(--fg-950); }
.ghost:not(:disabled):hover { background: var(--fg-950); color: var(--bg-50); }

.quiet { background: var(--bg-50); color: var(--fg-700); border-color: var(--bg-300); }
.quiet:not(:disabled):hover { border-color: var(--fg-400); }

.danger { background: var(--bg-50); color: var(--red-tx); border-color: var(--red-bd); }
.danger:not(:disabled):hover { background: var(--red-bg); }

.spin { animation: rb-spin 1s linear infinite; }
</style>

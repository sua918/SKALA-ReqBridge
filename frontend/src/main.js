import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import App from './App.vue'
import router from './router'
import './style.css'

/**
 * PrimeVue는 PDF 업로드(FileUpload) 하나 때문에 들인다. v5는 테마가 유료 라이선스라
 * 화면에 「Invalid PrimeUI License」 배지가 뜬다 — 무료인 v4 계열로 고정한다.
 * 기본 `<input type="file">`은 브라우저마다 다른 모양으로 그려지고, 무엇을 골랐는지
 * 파일명 말고는 알려주지 않는다 (프론트엔드-추가-요청사항 3.3).
 *
 * `darkModeSelector`를 실제로 쓰지 않는 선택자로 고정한다. 기본값은 `system`이라
 * OS가 다크 모드면 PrimeVue만 혼자 어두워져, 우리 토큰으로 칠한 화면 위에 검은
 * 위젯이 얹힌다. 이 서비스는 밝은 화면 한 벌만 쓴다.
 *
 * `cssLayer`로 PrimeVue 규칙을 layer 안에 가둔다. layer 밖 규칙이 항상 이기므로,
 * style.css의 토큰과 컴포넌트 스타일이 PrimeVue 기본값에 밀리지 않는다.
 */
createApp(App)
  .use(router)
  .use(PrimeVue, {
    theme: {
      preset: Aura,
      options: {
        darkModeSelector: '.reqbridge-never-dark',
        cssLayer: { name: 'primevue', order: 'primevue' },
      },
    },
  })
  .mount('#app')

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { registerSW } from 'virtual:pwa-register'

import App from './App.vue'
import router from './router'
import { resolveApiBaseUrl } from '@/services/api/appConfig'
import { apiClient } from '@/services/api/client'
import './assets/main.css'

document.documentElement.dataset.theme = 'lot-signal'

registerSW({ immediate: true })

async function bootstrap(): Promise<void> {
  apiClient.setBaseUrl(await resolveApiBaseUrl())

  const app = createApp(App)

  app.use(createPinia())
  app.use(router)

  app.mount('#app')
}

void bootstrap()

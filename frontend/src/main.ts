import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'
import preset from './theme/preset'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin)
// Decision 1.14: PrimeVue styles go into a `primevue` CSS layer between Tailwind's base and utilities.
app.use(PrimeVue, {
  theme: {
    preset,
    options: {
      darkModeSelector: false,
      cssLayer: { name: 'primevue', order: 'theme, base, primevue, components, utilities' },
    },
  },
})

app.mount('#app')

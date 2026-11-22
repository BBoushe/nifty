import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const activeCohort = ref('FH Dortmund Block Week 2026')
  const showFlaggedOnly = ref(false)

  return {
    activeCohort,
    showFlaggedOnly,
  }
})

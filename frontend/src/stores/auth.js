import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchCsrf, fetchCurrentUser, loginRequest, logoutRequest } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const initialized = ref(false)
  const authenticated = computed(() => Boolean(user.value))

  async function initialize() {
    if (initialized.value) return
    try {
      const response = await fetchCurrentUser()
      user.value = response.data.data
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function login(credentials) {
    await fetchCsrf()
    const response = await loginRequest(credentials)
    user.value = response.data.data
    initialized.value = true
  }

  async function logout() {
    try { await logoutRequest() } finally { user.value = null; initialized.value = true }
  }

  return { user, initialized, authenticated, initialize, login, logout }
})

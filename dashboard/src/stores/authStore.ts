// ============================================================
// FILE: XRC/dashboard/src/stores/authStore.ts
// ============================================================
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

interface User {
  id: string
  username: string
  role: string
}

interface AuthState {
  token: string | null
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (username: string, password: string) => Promise<boolean>
  logout: () => void
  checkAuth: () => Promise<void>
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      isLoading: true,

      login: async (username: string, password: string) => {
        try {
          const res = await api.post('/auth/login', { username, password })
          const { token, user } = res.data
          set({ token, user, isAuthenticated: true, isLoading: false })
          localStorage.setItem('xrc_token', token)
          return true
        } catch (err) {
          set({ isLoading: false })
          return false
        }
      },

      logout: () => {
        localStorage.removeItem('xrc_token')
        set({ token: null, user: null, isAuthenticated: false })
      },

      checkAuth: async () => {
        const token = localStorage.getItem('xrc_token')
        if (!token) {
          set({ isLoading: false })
          return
        }
        try {
          const res = await api.post('/auth/verify', null, {
            headers: { Authorization: `Bearer ${token}` }
          })
          if (res.data.valid) {
            set({ token, user: res.data.user, isAuthenticated: true, isLoading: false })
          } else {
            localStorage.removeItem('xrc_token')
            set({ isLoading: false })
          }
        } catch {
          localStorage.removeItem('xrc_token')
          set({ isLoading: false })
        }
      }
    }),
    {
      name: 'xrc-auth',
      partialize: (state) => ({ token: state.token, user: state.user }),
    }
  )
)

// Axios interceptor to attach token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      useAuthStore.getState().logout()
    }
    return Promise.reject(err)
  }
)

export { api }

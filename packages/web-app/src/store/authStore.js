import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authService } from '../services/api'
import { isJwtActive } from '../utils/session'

export const useAuthStore = create(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (username, password) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authService.login({ username, password })
          const { token, user } = response.data
          set({
            user,
            token,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          })
          return true
        } catch (error) {
          set({
            isLoading: false,
            error: error.response?.data?.message || 'Login failed',
          })
          return false
        }
      },

      register: async (username, email, password) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authService.register({ username, email, password })
          const { token, user } = response.data
          set({
            user,
            token,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          })
          return true
        } catch (error) {
          set({
            isLoading: false,
            error: error.response?.data?.message || 'Registration failed',
          })
          return false
        }
      },

      logout: () => {
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          error: null,
        })
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
      // Discard expired/malformed tokens before restoring navigation state.
      merge: (persisted, current) => {
        const stored = persisted && typeof persisted === 'object' ? persisted : {}
        const valid = isJwtActive(stored.token) && Boolean(stored.user)
        return {
          ...current,
          user: valid ? stored.user : null,
          token: valid ? stored.token : null,
          isAuthenticated: valid,
        }
      },
    }
  )
)
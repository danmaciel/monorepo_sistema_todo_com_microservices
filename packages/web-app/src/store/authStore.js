import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authService, sessionMode, sessionService } from '../services/api'
import { isJwtActive } from '../utils/session'

// One refresh attempt at a time per tab, shared by bootstrap and API requests.
let refreshInFlight = null

const emptySession = {
  user: null,
  token: null,
  csrf: null,
  isAuthenticated: false,
}

function applySessionResponse(response, set) {
  const { token, user } = response.data || {}
  const csrf = response.headers?.['x-csrf-token']
  if (!isJwtActive(token) || !user || typeof csrf !== 'string' || !csrf) {
    throw new Error('Resposta de sessão incompleta')
  }
  set({ user, token, csrf, isAuthenticated: true, error: null })
  return token
}

export const useAuthStore = create(
  persist(
    (set, get) => ({
      ...emptySession,
      isBootstrapping: sessionMode,
      isLoading: false,
      error: null,

      // Called once by the app on startup, never stores refresh material in JS.
      bootstrapSession: async () => {
        if (!sessionMode) return
        set({ isBootstrapping: true })
        try {
          const response = await sessionService.bootstrap()
          const csrf = response.headers?.['x-csrf-token']
          if (typeof csrf !== 'string' || !csrf) throw new Error('CSRF indisponível')
          set({ csrf })
          await get().refreshSession()
        } catch {
          set(emptySession)
        } finally {
          set({ isBootstrapping: false })
        }
      },

      refreshSession: async () => {
        if (!sessionMode) return null
        if (refreshInFlight) return refreshInFlight
        const csrf = get().csrf
        if (!csrf) return null

        refreshInFlight = (async () => {
          try {
            const response = await sessionService.refresh(csrf)
            return applySessionResponse(response, set)
          } catch {
            set(emptySession)
            return null
          } finally {
            refreshInFlight = null
          }
        })()
        return refreshInFlight
      },

      login: async (username, password) => {
        set({ isLoading: true, error: null })
        try {
          const response = sessionMode
            ? await sessionService.login({ username, password })
            : await authService.login({ username, password })
          if (sessionMode) {
            applySessionResponse(response, set)
          } else {
            const { token, user } = response.data
            set({ user, token, isAuthenticated: true })
          }
          set({ isLoading: false, error: null })
          return true
        } catch (error) {
          set({
            isLoading: false,
            error: error.response?.data?.message || 'Não foi possível entrar',
          })
          return false
        }
      },

      register: async (username, email, password) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authService.register({ username, email, password })
          if (sessionMode) {
            // Registration's legacy JWT is intentionally ignored.
            const loginResponse = await sessionService.login({ username, password })
            applySessionResponse(loginResponse, set)
          } else {
            const { token, user } = response.data
            set({ user, token, isAuthenticated: true })
          }
          set({ isLoading: false, error: null })
          return true
        } catch (error) {
          set({
            isLoading: false,
            error: error.response?.data?.message || 'Não foi possível concluir o cadastro',
          })
          return false
        }
      },

      clearSession: () => set(emptySession),

      logout: async () => {
        if (sessionMode) {
          const csrf = get().csrf
          try {
            if (csrf) await sessionService.logout(csrf)
          } catch {
            // Clear locally even if the connection fails. Server revocation is
            // not guaranteed in this case; do not claim it succeeded.
          }
        }
        set({ ...emptySession, error: null })
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'auth-storage',
      // Session mode keeps access JWT and CSRF strictly in memory.
      partialize: (state) => sessionMode ? {} : ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
      merge: (persisted, current) => {
        if (sessionMode) return current
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

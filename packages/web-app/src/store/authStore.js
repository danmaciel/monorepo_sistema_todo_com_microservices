import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authService, sessionMode, sessionService } from '../services/api'
import { isJwtActive } from '../utils/session'
import { withRefreshLock } from '../utils/refreshLock'

// One refresh attempt at a time per tab, shared by bootstrap and API requests.
let refreshInFlight = null
let bootstrapInFlight = null
let sessionVersion = 0

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
        if (bootstrapInFlight) return bootstrapInFlight
        const version = sessionVersion
        bootstrapInFlight = (async () => {
          set({ isBootstrapping: true })
          try {
            // refreshSession obtains the current CSRF and rotates under one
            // cross-tab lock. Doing bootstrap before locking is a TOCTOU race.
            await get().refreshSession()
            if (version !== sessionVersion) return
          } catch {
            if (version === sessionVersion) set(emptySession)
          } finally {
            set({ isBootstrapping: false })
            bootstrapInFlight = null
          }
        })()
        return bootstrapInFlight
      },

      refreshSession: async () => {
        if (!sessionMode) return null
        if (refreshInFlight) return refreshInFlight
        const version = sessionVersion

        refreshInFlight = (async () => {
          try {
            return await withRefreshLock(async () => {
              // Shared cookie can rotate in a different tab. Bootstrap again
              // after acquiring the lock instead of using a stale in-memory CSRF.
              if (version !== sessionVersion) return null
              const bootstrap = await sessionService.bootstrap()
              const csrf = bootstrap.headers?.['x-csrf-token']
              if (typeof csrf !== 'string' || !csrf) throw new Error('CSRF indisponível')
              if (version !== sessionVersion) return null
              const response = await sessionService.refresh(csrf)
              if (version !== sessionVersion) return null
              return applySessionResponse(response, set)
            })
          } catch {
            if (version === sessionVersion) set(emptySession)
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
            sessionVersion++
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
            sessionVersion++
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

      clearSession: () => {
        sessionVersion++
        set(emptySession)
      },

      logout: async () => {
        sessionVersion++
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

import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import { isJwtActive } from '../utils/session'

export const sessionMode = import.meta.env.VITE_SESSION_AUTH === 'true'
// Browser session mode always uses the frontend's same-origin /api proxy.
// This deliberately does not enable credentialed cross-origin CORS.
const API_URL = sessionMode ? '' : (import.meta.env.VITE_API_URL || 'http://localhost:8080')

const api = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
})

// This client deliberately has no bearer interceptors and never serializes
// the HttpOnly refresh cookie; the browser manages it.
const sessionHttp = axios.create({
  baseURL: '',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

export const sessionService = {
  login: (data) => sessionHttp.post('/api/users/session/login', data),
  bootstrap: () => sessionHttp.post('/api/users/session/csrf'),
  refresh: (csrf) => sessionHttp.post('/api/users/refresh', null, {
    headers: { 'X-CSRF-Token': csrf },
  }),
  logout: (csrf) => sessionHttp.post('/api/users/logout', null, {
    headers: { 'X-CSRF-Token': csrf },
  }),
}

api.interceptors.request.use(
  async (config) => {
    if (sessionMode) {
      let token = useAuthStore.getState().token
      if (!isJwtActive(token)) {
        token = await useAuthStore.getState().refreshSession()
      }
      if (token) config.headers.Authorization = `Bearer ${token}`
      return config
    }
    const token = useAuthStore.getState().token
    if (token && !isJwtActive(token)) {
      useAuthStore.getState().logout()
    } else if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (sessionMode && error.response?.status === 401 && original && !original._sessionRetried) {
      original._sessionRetried = true
      const token = await useAuthStore.getState().refreshSession()
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
    }
    if (error.response?.status === 401) {
      if (sessionMode) {
        useAuthStore.getState().clearSession()
      } else {
        useAuthStore.getState().logout()
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export const authService = {
  login: (data) => api.post('/api/users/login', data),
  register: (data) => api.post('/api/users/register', data),
  getCurrentUser: () => api.get('/api/users/me'),
}

export const todoService = {
  getAll: (completed) => {
    const params = completed !== undefined ? { completed } : {}
    return api.get('/api/todos', { params })
  },
  getById: (id) => api.get(`/api/todos/${id}`),
  create: (data) => api.post('/api/todos', data),
  update: (id, data) => api.put(`/api/todos/${id}`, data),
  delete: (id) => api.delete(`/api/todos/${id}`),
  complete: (id) => api.patch(`/api/todos/${id}/complete`),
  uncomplete: (id) => api.patch(`/api/todos/${id}/uncomplete`),
}

export default api

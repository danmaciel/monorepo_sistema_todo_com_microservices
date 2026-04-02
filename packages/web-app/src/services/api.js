import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
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
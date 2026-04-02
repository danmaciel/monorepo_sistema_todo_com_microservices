import { create } from 'zustand'
import { todoService } from '../services/api'

export const useTodoStore = create((set, get) => ({
  todos: [],
  stats: { total: 0, active: 0, completed: 0 },
  isLoading: false,
  error: null,
  filter: 'all',

  setFilter: (filter) => set({ filter }),

  fetchTodos: async () => {
    set({ isLoading: true, error: null })
    try {
      const { filter } = get()
      const completed = filter === 'all' ? undefined : filter === 'completed'
      const response = await todoService.getAll(completed)
      set({
        todos: response.data.todos,
        stats: {
          total: response.data.total,
          active: response.data.active,
          completed: response.data.completed,
        },
        isLoading: false,
      })
    } catch (error) {
      set({
        isLoading: false,
        error: error.response?.data?.message || 'Failed to fetch todos',
      })
    }
  },

  createTodo: async (title, description) => {
    set({ isLoading: true, error: null })
    try {
      await todoService.create({ title, description })
      await get().fetchTodos()
      return true
    } catch (error) {
      set({
        isLoading: false,
        error: error.response?.data?.message || 'Failed to create todo',
      })
      return false
    }
  },

  updateTodo: async (id, data) => {
    try {
      await todoService.update(id, data)
      await get().fetchTodos()
      return true
    } catch (error) {
      set({
        error: error.response?.data?.message || 'Failed to update todo',
      })
      return false
    }
  },

  deleteTodo: async (id) => {
    try {
      await todoService.delete(id)
      await get().fetchTodos()
      return true
    } catch (error) {
      set({
        error: error.response?.data?.message || 'Failed to delete todo',
      })
      return false
    }
  },

  toggleComplete: async (id, completed) => {
    try {
      if (completed) {
        await todoService.uncomplete(id)
      } else {
        await todoService.complete(id)
      }
      await get().fetchTodos()
      return true
    } catch (error) {
      set({
        error: error.response?.data?.message || 'Failed to update todo',
      })
      return false
    }
  },

  clearError: () => set({ error: null }),
}))
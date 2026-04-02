import { useEffect } from 'react'
import { useTodoStore } from '../store/todoStore'
import TodoForm from '../components/TodoForm'
import TodoItem from '../components/TodoItem'
import FilterTabs from '../components/FilterTabs'
import { Clipboard, RefreshCw } from 'lucide-react'
import clsx from 'clsx'

export default function TodoList() {
  const { todos, filter, isLoading, error, fetchTodos, clearError } = useTodoStore()

  useEffect(() => {
    fetchTodos()
  }, [filter])

  const getEmptyMessage = () => {
    switch (filter) {
      case 'active':
        return 'Parabéns! Você não tem tarefas pendentes 🎉'
      case 'completed':
        return 'Você ainda não completou nenhuma tarefa'
      default:
        return 'Comece adicionando sua primeira tarefa!'
    }
  }

  return (
    <div className="space-y-6">
      <div className="text-center mb-8">
        <h2 className="text-2xl font-bold text-gray-800 mb-2">Minhas Tarefas</h2>
        <p className="text-gray-500">Organize suas tarefas de forma simples e eficiente</p>
      </div>

      <TodoForm />

      <div className="flex justify-between items-center">
        <FilterTabs />
        <button
          onClick={fetchTodos}
          disabled={isLoading}
          className="p-2 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshCw size={20} className={clsx(isLoading && 'animate-spin')} />
        </button>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
          <p>{error}</p>
          <button
            onClick={clearError}
            className="mt-2 text-sm underline hover:no-underline"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="space-y-3">
        {isLoading && todos.length === 0 ? (
          <div className="text-center py-12">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full mx-auto mb-4"></div>
            <p className="text-gray-500">Carregando tarefas...</p>
          </div>
        ) : todos.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-xl border-2 border-dashed border-gray-200">
            <Clipboard size={48} className="mx-auto mb-4 text-gray-300" />
            <p className="text-gray-500">{getEmptyMessage()}</p>
          </div>
        ) : (
          todos.map((todo) => <TodoItem key={todo.id} todo={todo} />)
        )}
      </div>

      {todos.length > 0 && (
        <p className="text-center text-sm text-gray-400">
          Mostrando {todos.length} tarefa{todos.length !== 1 && 's'}
        </p>
      )}
    </div>
  )
}
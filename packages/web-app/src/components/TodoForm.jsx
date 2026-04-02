import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useTodoStore } from '../store/todoStore'

export default function TodoForm() {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [isExpanded, setIsExpanded] = useState(false)
  
  const { createTodo, isLoading } = useTodoStore()

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim()) return
    
    const success = await createTodo(title.trim(), description.trim())
    if (success) {
      setTitle('')
      setDescription('')
      setIsExpanded(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-sm p-4 border-2 border-indigo-200">
      {!isExpanded ? (
        <button
          type="button"
          onClick={() => setIsExpanded(true)}
          className="w-full py-3 flex items-center justify-center gap-2 text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
        >
          <Plus size={24} />
          <span className="font-medium">Nova Tarefa</span>
        </button>
      ) : (
        <div className="space-y-4">
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            placeholder="Qual é a sua nova tarefa?"
            autoFocus
          />
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
            rows={3}
            placeholder="Descrição (opcional)"
          />
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={isLoading || !title.trim()}
              className="flex-1 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors font-medium"
            >
              {isLoading ? 'Adicionando...' : 'Adicionar Tarefa'}
            </button>
            <button
              type="button"
              onClick={() => {
                setIsExpanded(false)
                setTitle('')
                setDescription('')
              }}
              className="px-6 py-3 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </form>
  )
}
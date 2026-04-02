import { Check, Trash2, Edit2, X } from 'lucide-react'
import { useState } from 'react'
import { useTodoStore } from '../store/todoStore'
import clsx from 'clsx'

export default function TodoItem({ todo }) {
  const [isEditing, setIsEditing] = useState(false)
  const [editTitle, setEditTitle] = useState(todo.title)
  const [editDescription, setEditDescription] = useState(todo.description || '')
  
  const { toggleComplete, updateTodo, deleteTodo } = useTodoStore()

  const handleSave = async () => {
    await updateTodo(todo.id, {
      title: editTitle,
      description: editDescription,
    })
    setIsEditing(false)
  }

  const handleCancel = () => {
    setEditTitle(todo.title)
    setEditDescription(todo.description || '')
    setIsEditing(false)
  }

  if (isEditing) {
    return (
      <div className="bg-white rounded-xl shadow-sm p-4 border-2 border-indigo-200">
        <input
          type="text"
          value={editTitle}
          onChange={(e) => setEditTitle(e.target.value)}
          className="w-full px-3 py-2 border rounded-lg mb-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          placeholder="Título"
        />
        <textarea
          value={editDescription}
          onChange={(e) => setEditDescription(e.target.value)}
          className="w-full px-3 py-2 border rounded-lg mb-3 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
          rows={3}
          placeholder="Descrição (opcional)"
        />
        <div className="flex gap-2">
          <button
            onClick={handleSave}
            className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
          >
            Salvar
          </button>
          <button
            onClick={handleCancel}
            className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
          >
            Cancelar
          </button>
        </div>
      </div>
    )
  }

  return (
    <div
      className={clsx(
        'bg-white rounded-xl shadow-sm p-4 border-2 transition-all',
        todo.completed ? 'border-green-200 bg-green-50' : 'border-gray-100'
      )}
    >
      <div className="flex items-start gap-4">
        <button
          onClick={() => toggleComplete(todo.id, todo.completed)}
          className={clsx(
            'flex-shrink-0 w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all',
            todo.completed
              ? 'bg-green-500 border-green-500 text-white'
              : 'border-gray-300 hover:border-indigo-500'
          )}
        >
          {todo.completed && <Check size={14} />}
        </button>
        
        <div className="flex-1 min-w-0">
          <h3
            className={clsx(
              'font-medium text-lg',
              todo.completed && 'line-through text-gray-500'
            )}
          >
            {todo.title}
          </h3>
          {todo.description && (
            <p
              className={clsx(
                'mt-1 text-gray-600',
                todo.completed && 'line-through text-gray-400'
              )}
            >
              {todo.description}
            </p>
          )}
          <p className="mt-2 text-xs text-gray-400">
            Criado em: {new Date(todo.createdAt).toLocaleDateString('pt-BR')}
          </p>
        </div>

        <div className="flex gap-2">
          <button
            onClick={() => setIsEditing(true)}
            className="p-2 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
          >
            <Edit2 size={18} />
          </button>
          <button
            onClick={() => deleteTodo(todo.id)}
            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
          >
            <Trash2 size={18} />
          </button>
        </div>
      </div>
    </div>
  )
}
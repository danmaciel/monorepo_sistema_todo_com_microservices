import { useTodoStore } from '../store/todoStore'
import clsx from 'clsx'

export default function FilterTabs() {
  const { filter, setFilter, stats } = useTodoStore()

  const tabs = [
    { key: 'all', label: 'Todas', count: stats.total },
    { key: 'active', label: 'Pendentes', count: stats.active },
    { key: 'completed', label: 'Concluídas', count: stats.completed },
  ]

  return (
    <div className="flex gap-2">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          onClick={() => setFilter(tab.key)}
          className={clsx(
            'px-4 py-2 rounded-lg font-medium transition-all',
            filter === tab.key
              ? 'bg-indigo-600 text-white shadow-md'
              : 'bg-white text-gray-600 hover:bg-gray-100'
          )}
        >
          {tab.label}
          <span
            className={clsx(
              'ml-2 px-2 py-0.5 rounded-full text-xs',
              filter === tab.key ? 'bg-indigo-500' : 'bg-gray-200'
            )}
          >
            {tab.count}
          </span>
        </button>
      ))}
    </div>
  )
}
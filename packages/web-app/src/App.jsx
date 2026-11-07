import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import Login from './pages/Login'
import Register from './pages/Register'
import TodoList from './pages/TodoList'
import Layout from './components/Layout'
import { isJwtActive } from './utils/session'
import { sessionMode } from './services/api'

function ProtectedRoute({ children }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const token = useAuthStore((state) => state.token)
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping)
  const csrf = useAuthStore((state) => state.csrf)
  const hasActiveSession = isAuthenticated && (sessionMode ? Boolean(csrf) : isJwtActive(token))

  if (sessionMode && isBootstrapping) {
    return <div role="status" className="p-8 text-center">Restaurando sessão...</div>
  }

  if (!hasActiveSession) {
    return <Navigate to="/login" replace />
  }
  
  return children
}

function PublicRoute({ children }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const token = useAuthStore((state) => state.token)
  const csrf = useAuthStore((state) => state.csrf)
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping)
  const hasActiveSession = isAuthenticated && (sessionMode ? Boolean(csrf) : isJwtActive(token))

  if (sessionMode && isBootstrapping) {
    return <div role="status" className="p-8 text-center">Restaurando sessão...</div>
  }

  if (hasActiveSession) {
    return <Navigate to="/" replace />
  }
  
  return children
}

function App() {
  useEffect(() => {
    if (sessionMode) {
      useAuthStore.getState().bootstrapSession()
    }
  }, [])

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={
            <PublicRoute>
              <Login />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <Register />
            </PublicRoute>
          }
        />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<TodoList />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
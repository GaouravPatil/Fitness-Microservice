import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { BootScreen } from './BootScreen'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth()
  const location = useLocation()

  if (!ready) return <BootScreen />
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />

  return <>{children}</>
}

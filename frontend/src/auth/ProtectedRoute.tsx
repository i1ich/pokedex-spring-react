import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * Guards routes that need a signed-in user. Use it as a layout route (`<Route element={<ProtectedRoute />}>`)
 * or wrap children directly. Anonymous visitors go to /login; the page they wanted is passed in
 * `location.state.from` so the login page can send them back.
 */
export function ProtectedRoute({ children }: { children?: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children ?? <Outlet />
}

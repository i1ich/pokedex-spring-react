import { useCallback, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import * as authApi from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import type { Credentials } from '../api/types'
import { AuthContext, type AuthContextValue } from './context'
import { clearSession, getSession, setSession, subscribeSession } from './session'

/** setTimeout cannot wait longer than this (about 24.8 days). */
const MAX_TIMER_MS = 2 ** 31 - 1

/**
 * Auth state for the whole app. The token itself is kept by `session.ts` (memory + sessionStorage) so
 * the API client can read it too; this component exposes it to React and handles side effects:
 * private cache cleanup on sign-out and automatic sign-out at token expiry.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribeSession, getSession)
  const queryClient = useQueryClient()
  const isAuthenticated = session !== null
  const expiresAt = session?.expiresAt

  useEffect(() => {
    if (!isAuthenticated) queryClient.removeQueries({ queryKey: queryKeys.localPokemon.all })
  }, [isAuthenticated, queryClient])

  useEffect(() => {
    if (expiresAt === undefined) return
    const delay = Date.parse(expiresAt) - Date.now()
    if (Number.isNaN(delay) || delay > MAX_TIMER_MS) return
    const timer = setTimeout(clearSession, Math.max(delay, 0))
    return () => clearTimeout(timer)
  }, [expiresAt])

  const login = useCallback(async (credentials: Credentials) => {
    setSession(await authApi.login(credentials))
  }, [])

  const register = useCallback(
    async (credentials: Credentials) => {
      await authApi.register(credentials)
      await login(credentials)
    },
    [login],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      username: session?.username ?? null,
      isAuthenticated,
      login,
      register,
      logout: clearSession,
    }),
    [session?.username, isAuthenticated, login, register],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

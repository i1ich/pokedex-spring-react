import { createContext } from 'react'
import type { Credentials } from '../api/types'

export interface AuthContextValue {
  /** Name of the signed-in user, or null when anonymous. */
  username: string | null
  isAuthenticated: boolean
  /** Rejects with an `ApiError` (e.g. 401 for bad credentials); the state stays anonymous. */
  login: (credentials: Credentials) => Promise<void>
  /** Creates the account and then signs in with the same credentials. */
  register: (credentials: Credentials) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

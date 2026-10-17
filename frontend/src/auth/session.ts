import type { LoginResponse } from '../api/types'

export type Session = LoginResponse

export const SESSION_STORAGE_KEY = 'pokedex.session'

/**
 * The JWT lives in memory (the parsed copy below) and in sessionStorage, so a reload keeps the user
 * signed in but closing the tab does not. It is a tiny external store so React (useSyncExternalStore)
 * and the plain-function API client see the same state.
 */
const listeners = new Set<() => void>()
let fallbackRaw: string | null = null // used only if sessionStorage is unavailable
let cachedRaw: string | null | undefined
let cachedSession: Session | null = null

function readRaw(): string | null {
  try {
    return sessionStorage.getItem(SESSION_STORAGE_KEY)
  } catch {
    return fallbackRaw
  }
}

function writeRaw(raw: string | null): void {
  fallbackRaw = raw
  try {
    if (raw === null) sessionStorage.removeItem(SESSION_STORAGE_KEY)
    else sessionStorage.setItem(SESSION_STORAGE_KEY, raw)
  } catch {
    // storage unavailable: the in-memory copy still works for this page load
  }
}

function isExpired(session: Session): boolean {
  const expires = Date.parse(session.expiresAt)
  return Number.isNaN(expires) || expires <= Date.now()
}

function parse(raw: string): Session | null {
  try {
    const value: unknown = JSON.parse(raw)
    if (typeof value !== 'object' || value === null) return null
    const { token, expiresAt, username } = value as Record<string, unknown>
    if (
      typeof token !== 'string' ||
      typeof expiresAt !== 'string' ||
      typeof username !== 'string'
    ) {
      return null
    }
    return { token, expiresAt, username }
  } catch {
    return null
  }
}

/** Current session, or null. Returns the same object until the stored value changes. */
export function getSession(): Session | null {
  const raw = readRaw()
  if (raw === cachedRaw) return cachedSession
  const session = raw === null ? null : parse(raw)
  if (raw !== null && (session === null || isExpired(session))) {
    // malformed or expired leftovers are dropped on load
    writeRaw(null)
    cachedRaw = null
    cachedSession = null
    return null
  }
  cachedRaw = raw
  cachedSession = session
  return session
}

/** The bearer token if there is a session that has not expired; clears the session otherwise. */
export function getAuthToken(): string | null {
  const session = getSession()
  if (session === null) return null
  if (isExpired(session)) {
    clearSession()
    return null
  }
  return session.token
}

export function setSession(session: Session): void {
  writeRaw(JSON.stringify(session))
  notify()
}

export function clearSession(): void {
  writeRaw(null)
  notify()
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function notify(): void {
  listeners.forEach((listener) => listener())
}

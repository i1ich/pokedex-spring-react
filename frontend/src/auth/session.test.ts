import { demoSession, pastIso } from '../test/fixtures'
import {
  SESSION_STORAGE_KEY,
  clearSession,
  getAuthToken,
  getSession,
  setSession,
  subscribeSession,
} from './session'

describe('session store', () => {
  it('is empty by default', () => {
    expect(getSession()).toBeNull()
    expect(getAuthToken()).toBeNull()
  })

  it('keeps the session in sessionStorage and returns it', () => {
    const session = demoSession()
    setSession(session)

    expect(getSession()).toEqual(session)
    expect(getAuthToken()).toBe(session.token)
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? 'null')).toEqual(session)
  })

  it('restores a session written earlier (page reload)', () => {
    const session = demoSession()
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))

    expect(getSession()).toEqual(session)
  })

  it('returns a stable object between calls so it can back useSyncExternalStore', () => {
    setSession(demoSession())
    expect(getSession()).toBe(getSession())
  })

  it('drops a stored session whose expiresAt is in the past', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ ...demoSession(), expiresAt: pastIso() }),
    )

    expect(getSession()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('does not hand out a token that expired after it was stored', () => {
    vi.useFakeTimers()
    setSession({ ...demoSession(), expiresAt: new Date(Date.now() + 1000).toISOString() })
    expect(getAuthToken()).not.toBeNull()

    vi.advanceTimersByTime(2000)

    expect(getAuthToken()).toBeNull()
    expect(getSession()).toBeNull()
  })

  it.each(['not json', '{"token":1}', '{}', 'null', '[]'])(
    'ignores malformed stored value %s',
    (raw) => {
      sessionStorage.setItem(SESSION_STORAGE_KEY, raw)
      expect(getSession()).toBeNull()
    },
  )

  it('clearSession removes the stored value', () => {
    setSession(demoSession())
    clearSession()

    expect(getSession()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('notifies subscribers on set and clear, and stops after unsubscribe', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeSession(listener)

    setSession(demoSession())
    clearSession()
    expect(listener).toHaveBeenCalledTimes(2)

    unsubscribe()
    setSession(demoSession())
    expect(listener).toHaveBeenCalledTimes(2)
  })
})

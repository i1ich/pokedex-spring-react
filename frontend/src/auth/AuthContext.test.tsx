import { act, screen } from '@testing-library/react'
import { ApiError } from '../api/client'
import { getCurrentUser } from '../api/auth'
import { demoSession, futureIso, pastIso } from '../test/fixtures'
import { json, problem, stubFetch } from '../test/http'
import { renderWithProviders } from '../test/helpers'
import { SESSION_STORAGE_KEY } from './session'
import { useAuth } from './useAuth'
import type { AuthContextValue } from './authContext'

let auth: AuthContextValue

function Capture() {
  auth = useAuth()
  return <p>{auth.isAuthenticated ? `signed in as ${auth.username}` : 'anonymous'}</p>
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('AuthProvider', () => {
  it('starts anonymous when nothing is stored', () => {
    renderWithProviders(<Capture />)

    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(auth.username).toBeNull()
  })

  it('restores the session from sessionStorage', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))

    renderWithProviders(<Capture />)

    expect(screen.getByText('signed in as demo')).toBeInTheDocument()
  })

  it('ignores a stored token whose expiresAt is in the past', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ ...demoSession(), expiresAt: pastIso() }),
    )

    renderWithProviders(<Capture />)

    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('login posts the credentials and keeps the token in sessionStorage', async () => {
    const expiresAt = futureIso()
    const fetchMock = stubFetch(() => json({ token: 'jwt-1', expiresAt, username: 'demo' }))
    renderWithProviders(<Capture />)

    await act(() => auth.login({ username: 'demo', password: 'demo12345' }))

    expect(screen.getByText('signed in as demo')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"username":"demo","password":"demo12345"}')
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}')).toEqual({
      token: 'jwt-1',
      expiresAt,
      username: 'demo',
    })
  })

  it('a failed login rejects with the ApiError and stays anonymous', async () => {
    stubFetch(() => problem(401, { title: 'Authentication failed', detail: 'Bad credentials' }))
    renderWithProviders(<Capture />)

    let error: unknown
    await act(async () => {
      error = await auth.login({ username: 'demo', password: 'nope' }).catch((e) => e)
    })

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 401, detail: 'Bad credentials' })
    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('logout clears the state and the stored token', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    renderWithProviders(<Capture />)

    act(() => auth.logout())

    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('register creates the account and then signs the user in', async () => {
    const calls: string[] = []
    stubFetch((url) => {
      calls.push(url.pathname)
      return url.pathname.endsWith('/register')
        ? json({ username: 'ash', role: 'USER' }, 201)
        : json({ token: 'jwt-2', expiresAt: futureIso(), username: 'ash' })
    })
    renderWithProviders(<Capture />)

    await act(() => auth.register({ username: 'ash', password: 'pikachu123' }))

    expect(calls).toEqual(['/api/v1/auth/register', '/api/v1/auth/login'])
    expect(screen.getByText('signed in as ash')).toBeInTheDocument()
  })

  it('returns to the anonymous state when an authenticated request gets 401', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    stubFetch(() => problem(401, { title: 'Unauthorized' }))
    renderWithProviders(<Capture />)
    expect(screen.getByText('signed in as demo')).toBeInTheDocument()

    await act(async () => {
      await getCurrentUser().catch(() => undefined)
    })

    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('signs the user out when the token expires while the app is open', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ ...demoSession(), expiresAt: futureIso(60_000) }),
    )
    vi.useFakeTimers()
    renderWithProviders(<Capture />)
    expect(screen.getByText('signed in as demo')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(61_000)
    })

    expect(screen.getByText('anonymous')).toBeInTheDocument()
  })
})

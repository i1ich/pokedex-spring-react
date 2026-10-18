import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { SESSION_STORAGE_KEY } from '../auth/session'
import { demoSession, futureIso } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, json, problem, stubFetch, urlOf } from '../test/http'
import { LoginPage } from './LoginPage'

function RegisterProbe() {
  const from = (useLocation().state as { from?: { pathname: string } } | null)?.from
  return <p>register page, from {from?.pathname ?? 'nowhere'}</p>
}

function renderLogin(route: Parameters<typeof renderWithProviders>[1] = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterProbe />} />
      <Route path="/collection" element={<p>collection page</p>} />
      <Route path="/pokemon/:idOrName" element={<p>detail page</p>} />
    </Routes>,
    { route: '/login', ...route },
  )
}

const loginOk = () => json({ token: 'jwt-abc', expiresAt: futureIso(), username: 'demo' })

async function fill(username: string, password: string) {
  await userEvent.type(screen.getByLabelText('Username'), username)
  await userEvent.type(screen.getByLabelText('Password'), password)
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('LoginPage', () => {
  it('shows labelled fields, a submit button and a link to register', () => {
    renderLogin()

    expect(screen.getByRole('heading', { level: 1, name: /log in/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveAttribute('autocomplete', 'username')
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
    expect(screen.getByRole('link', { name: /register/i })).toHaveAttribute('href', '/register')
  })

  it('tells a reviewer which demo account to use', () => {
    renderLogin()

    expect(screen.getByText('Demo: demo / demo12345')).toBeInTheDocument()
  })

  it('passes the return-to state on to the register page', async () => {
    renderLogin({
      route: { pathname: '/login', state: { from: { pathname: '/pokemon/pikachu' } } },
    })

    await userEvent.click(screen.getByRole('link', { name: /register/i }))

    expect(screen.getByText('register page, from /pokemon/pikachu')).toBeInTheDocument()
  })

  it('validates required fields before calling the server', async () => {
    const fetchMock = stubFetch(loginOk)
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    const username = screen.getByLabelText('Username')
    const password = screen.getByLabelText('Password')
    expect(username).toBeInvalid()
    expect(username).toHaveAccessibleDescription(/username is required/i)
    expect(password).toBeInvalid()
    expect(password).toHaveAccessibleDescription(/password is required/i)
    expect(username).toHaveFocus()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('clears a field error as soon as the field is fixed', async () => {
    stubFetch(loginOk)
    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    await userEvent.type(screen.getByLabelText('Username'), 'd')

    expect(screen.getByLabelText('Username')).toBeValid()
    expect(screen.getByLabelText('Password')).toBeInvalid()
  })

  it('logs in, stores the session and goes to the collection', async () => {
    const fetchMock = stubFetch(loginOk)
    renderLogin()

    await fill('demo', 'demo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('collection page')).toBeInTheDocument()
    expect(urlOf(fetchMock).pathname).toBe('/api/v1/auth/login')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      username: 'demo',
      password: 'demo12345',
    })
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY)!)).toMatchObject({
      token: 'jwt-abc',
      username: 'demo',
    })
  })

  it('submits with the Enter key', async () => {
    stubFetch(loginOk)
    renderLogin()

    await userEvent.type(screen.getByLabelText('Username'), 'demo')
    await userEvent.type(screen.getByLabelText('Password'), 'demo12345{Enter}')

    expect(await screen.findByText('collection page')).toBeInTheDocument()
  })

  it('goes back to the page the user came from', async () => {
    stubFetch(loginOk)
    renderLogin({
      route: { pathname: '/login', state: { from: { pathname: '/pokemon/pikachu' } } },
    })

    await fill('demo', 'demo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('detail page')).toBeInTheDocument()
  })

  it('shows the server message for wrong credentials and keeps the form usable', async () => {
    stubFetch(() => problem(401, { title: 'Unauthorized', detail: 'Invalid username or password' }))
    renderLogin()

    await fill('demo', 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(screen.getByLabelText('Username')).toHaveValue('demo')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('shows a network failure', async () => {
    stubFetch(() => {
      throw new TypeError('Failed to fetch')
    })
    renderLogin()

    await fill('demo', 'demo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i)
  })

  it('maps field errors of a 400 answer onto the fields', async () => {
    stubFetch(() =>
      problem(400, {
        title: 'Validation failed',
        errors: [{ field: 'password', message: 'must not be blank' }],
      }),
    )
    renderLogin()

    await fill('demo', 'demo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    await waitFor(() => expect(screen.getByLabelText('Password')).toBeInvalid())
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/must not be blank/i)
  })

  it('disables the submit button while the request is pending', async () => {
    const pending = deferred<Response>()
    stubFetch(() => pending.promise)
    renderLogin()

    await fill('demo', 'demo12345')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByRole('button', { name: /logging in/i })).toBeDisabled()
    pending.resolve(loginOk())
    expect(await screen.findByText('collection page')).toBeInTheDocument()
  })

  it('sends a user who is already signed in away from the form', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    renderLogin()

    expect(screen.getByText('collection page')).toBeInTheDocument()
  })
})

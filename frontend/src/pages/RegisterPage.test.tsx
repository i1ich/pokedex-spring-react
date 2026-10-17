import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { SESSION_STORAGE_KEY } from '../auth/session'
import { futureIso } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, json, problem, stubFetch, urlOf } from '../test/http'
import { RegisterPage } from './RegisterPage'

function renderRegister(route: Parameters<typeof renderWithProviders>[1] = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<p>login page</p>} />
      <Route path="/collection" element={<p>collection page</p>} />
      <Route path="/pokemon/:idOrName" element={<p>detail page</p>} />
    </Routes>,
    { route: '/register', ...route },
  )
}

/** Register answers 201, and the follow-up login answers 200. */
function backendAccepts() {
  return stubFetch((url) =>
    url.pathname.endsWith('/register')
      ? json({ username: 'ash', role: 'USER' }, 201)
      : json({ token: 'jwt-abc', expiresAt: futureIso(), username: 'ash' }),
  )
}

async function fill(username: string, password: string) {
  const user = screen.getByLabelText('Username')
  const pass = screen.getByLabelText('Password')
  if (username) await userEvent.type(user, username)
  if (password) await userEvent.type(pass, password)
}

async function submit() {
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('RegisterPage', () => {
  it('shows labelled fields with the rules as hints and a link to log in', () => {
    renderRegister()

    expect(screen.getByRole('heading', { level: 1, name: /register/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(/3.50 characters/i)
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/at least 8/i)
    expect(screen.getByRole('link', { name: /log in/i })).toHaveAttribute('href', '/login')
  })

  it.each([
    ['', 'password1', /username is required/i],
    ['ab', 'password1', /at least 3 characters/i],
    ['x'.repeat(51), 'password1', /at most 50 characters/i],
    ['ash ketchum', 'password1', /letters, digits, dots, underscores and hyphens/i],
    ['ash!', 'password1', /letters, digits, dots, underscores and hyphens/i],
  ])('rejects username %j before calling the server', async (username, password, message) => {
    const fetchMock = backendAccepts()
    renderRegister()

    await fill(username, password)
    await submit()

    const field = screen.getByLabelText('Username')
    expect(field).toBeInvalid()
    expect(field).toHaveAccessibleDescription(message)
    expect(field).toHaveFocus()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each([
    ['', /password is required/i],
    ['1234567', /at least 8 characters/i],
    ['a'.repeat(73), /at most 72 bytes/i],
    ['é'.repeat(37), /at most 72 bytes/i],
  ])('rejects password %j before calling the server', async (password, message) => {
    const fetchMock = backendAccepts()
    renderRegister()

    await fill('ash', password)
    await submit()

    const field = screen.getByLabelText('Password')
    expect(field).toBeInvalid()
    expect(field).toHaveAccessibleDescription(message)
    expect(screen.getByLabelText('Username')).toBeValid()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows both errors at once and clears each when fixed', async () => {
    backendAccepts()
    renderRegister()
    await submit()

    expect(screen.getByLabelText('Username')).toBeInvalid()
    expect(screen.getByLabelText('Password')).toBeInvalid()

    await userEvent.type(screen.getByLabelText('Username'), 'ash')
    expect(screen.getByLabelText('Username')).toBeValid()
    expect(screen.getByLabelText('Password')).toBeInvalid()
  })

  it('creates the account with the normalised username, then signs in and goes to the collection', async () => {
    const fetchMock = backendAccepts()
    renderRegister()

    await fill('  Ash_01 ', 'password1')
    await submit()

    expect(await screen.findByText('collection page')).toBeInTheDocument()
    expect(urlOf(fetchMock, 0).pathname).toBe('/api/v1/auth/register')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      username: 'ash_01',
      password: 'password1',
    })
    expect(urlOf(fetchMock, 1).pathname).toBe('/api/v1/auth/login')
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY)!)).toMatchObject({
      username: 'ash',
    })
  })

  it('goes back to the page the user came from', async () => {
    backendAccepts()
    renderRegister({
      route: { pathname: '/register', state: { from: { pathname: '/pokemon/pikachu' } } },
    })

    await fill('ash', 'password1')
    await submit()

    expect(await screen.findByText('detail page')).toBeInTheDocument()
  })

  it('reports a taken username on the username field (409)', async () => {
    stubFetch(() => problem(409, { title: 'Conflict', detail: "Username 'ash' is already taken" }))
    renderRegister()

    await fill('ash', 'password1')
    await submit()

    await waitFor(() => expect(screen.getByLabelText('Username')).toBeInvalid())
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(/already taken/i)
    expect(screen.getByLabelText('Username')).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()

    await userEvent.type(screen.getByLabelText('Username'), '2')
    expect(screen.getByLabelText('Username')).toBeValid()
  })

  it('maps field errors of a 400 answer onto the fields', async () => {
    stubFetch(() =>
      problem(400, {
        title: 'Validation failed',
        errors: [
          { field: 'username', message: 'size must be between 3 and 50' },
          { field: 'password', message: 'too weak' },
        ],
      }),
    )
    renderRegister()

    await fill('ash', 'password1')
    await submit()

    await waitFor(() => expect(screen.getByLabelText('Password')).toBeInvalid())
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(/size must be between/i)
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/too weak/i)
  })

  it('shows other server failures in an alert', async () => {
    stubFetch(() => problem(500, { title: 'Internal Server Error', detail: 'Try later' }))
    renderRegister()

    await fill('ash', 'password1')
    await submit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal Server Error')
  })

  it('disables the submit button while the request is pending', async () => {
    const pending = deferred<Response>()
    stubFetch((url) =>
      url.pathname.endsWith('/register')
        ? pending.promise
        : json({ token: 'jwt-abc', expiresAt: futureIso(), username: 'ash' }),
    )
    renderRegister()

    await fill('ash', 'password1')
    await submit()

    expect(screen.getByRole('button', { name: /creating account/i })).toBeDisabled()
    pending.resolve(json({ username: 'ash', role: 'USER' }, 201))
    expect(await screen.findByText('collection page')).toBeInTheDocument()
  })
})

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SESSION_STORAGE_KEY } from './auth/session'
import { AppRoutes } from './AppRoutes'
import { demoSession, localPokemon, pageOf, pikachu, pikachuDetails } from './test/fixtures'
import { renderWithProviders } from './test/helpers'
import { json, stubFetch } from './test/http'

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
  stubFetch(() => json(pageOf([pikachu])))
})

describe('AppRoutes', () => {
  it('shows the catalogue at / with the site header', async () => {
    renderWithProviders(<AppRoutes />)

    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    const nav = within(screen.getByRole('navigation', { name: /main/i }))
    expect(nav.getByRole('link', { name: 'Browse' })).toHaveAttribute('aria-current', 'page')
    expect(nav.getByRole('link', { name: 'My collection' })).toHaveAttribute('href', '/collection')
    expect(nav.getByRole('link', { name: 'Login' })).toHaveAttribute('href', '/login')
    expect(nav.queryByRole('button', { name: /log ?out/i })).not.toBeInTheDocument()
  })

  it('has a home link in the header', async () => {
    renderWithProviders(<AppRoutes />)
    await screen.findByRole('link', { name: 'Pikachu' })

    expect(
      within(screen.getByRole('banner')).getByRole('link', { name: /pokédex/i }),
    ).toHaveAttribute('href', '/')
  })

  it('shows the user name and a logout button when signed in, and logs out', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    renderWithProviders(<AppRoutes />)
    await screen.findByRole('link', { name: 'Pikachu' })

    const nav = within(screen.getByRole('navigation', { name: /main/i }))
    expect(nav.getByText('demo')).toBeInTheDocument()
    expect(nav.queryByRole('link', { name: 'Login' })).not.toBeInTheDocument()

    await userEvent.click(nav.getByRole('button', { name: /log ?out/i }))

    expect(nav.getByRole('link', { name: 'Login' })).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('registers the detail route so card links do not 404', async () => {
    stubFetch(() => json(pikachuDetails))
    renderWithProviders(<AppRoutes />, { route: '/pokemon/pikachu' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
  })

  it('redirects /collection to the login page for anonymous visitors', () => {
    renderWithProviders(<AppRoutes />, { route: '/collection' })

    expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument()
  })

  it('shows the collection to signed-in users', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    stubFetch(() => json(pageOf([localPokemon()], { totalPages: 1 })))

    renderWithProviders(<AppRoutes />, { route: '/collection' })

    expect(screen.getByRole('heading', { name: /my collection/i })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('redirects the edit page to the login page for anonymous visitors', () => {
    renderWithProviders(<AppRoutes />, { route: '/collection/25/edit' })

    expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument()
  })

  it('registers the edit route for signed-in users', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
    stubFetch(() => json(localPokemon()))

    renderWithProviders(<AppRoutes />, { route: '/collection/25/edit' })

    expect(await screen.findByRole('heading', { name: /edit pikachu/i })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown URLs', () => {
    renderWithProviders(<AppRoutes />, { route: '/nope' })

    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument()
  })
})

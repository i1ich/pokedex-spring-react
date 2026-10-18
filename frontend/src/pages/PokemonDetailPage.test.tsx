import { QueryClient } from '@tanstack/react-query'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { queryKeys } from '../api/queryKeys'
import { SESSION_STORAGE_KEY } from '../auth/session'
import { demoSession, eeveeDetails, localPokemon, pikachuDetails } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, headersOf, json, problem, stubFetch, urlOf } from '../test/http'
import { PokemonDetailPage } from './PokemonDetailPage'

function LoginProbe() {
  const from = (useLocation().state as { from?: { pathname: string } } | null)?.from
  return <p>login page, back to {from?.pathname}</p>
}

function renderDetail(route = '/pokemon/pikachu', queryClient?: QueryClient, state?: unknown) {
  return renderWithProviders(
    <Routes>
      <Route path="/pokemon/:idOrName" element={<PokemonDetailPage />} />
      <Route path="/login" element={<LoginProbe />} />
      <Route path="/" element={<p>browse page</p>} />
    </Routes>,
    { route: state === undefined ? route : { pathname: route, state }, queryClient },
  )
}

function signIn() {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('PokemonDetailPage: content', () => {
  it('shows a loading status first', async () => {
    const pending = deferred<Response>()
    stubFetch(() => pending.promise)

    renderDetail()

    expect(screen.getByRole('status')).toHaveTextContent(/loading/i)
    pending.resolve(json(pikachuDetails))
    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
  })

  it('shows artwork, id, types, height, weight and description', async () => {
    stubFetch(() => json(pikachuDetails))

    renderDetail()

    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByText('#025')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /artwork of pikachu/i })).toHaveAttribute(
      'src',
      pikachuDetails.imageUrl,
    )
    const types = within(screen.getByRole('list', { name: /types/i }))
    expect(types.getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Electric'])
    expect(screen.getByText('0.4 m')).toBeInTheDocument()
    expect(screen.getByText('6 kg')).toBeInTheDocument()
    expect(screen.getByText(/lightning storms/i)).toBeInTheDocument()
  })

  it('falls back to text when there is no artwork or description', async () => {
    stubFetch(() => json(eeveeDetails))

    renderDetail('/pokemon/eevee')

    expect(await screen.findByRole('heading', { level: 1, name: 'Eevee' })).toBeInTheDocument()
    expect(screen.getByText(/no artwork available/i)).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /artwork/i })).not.toBeInTheDocument()
    expect(screen.getByText(/no description available/i)).toBeInTheDocument()
  })

  it('renders each base stat as a labelled meter with its value, out of 255', async () => {
    stubFetch(() => json(pikachuDetails))

    renderDetail()

    const hp = await screen.findByRole('meter', { name: 'HP' })
    expect(hp).toHaveAttribute('aria-valuenow', '35')
    expect(hp).toHaveAttribute('aria-valuemin', '0')
    expect(hp).toHaveAttribute('aria-valuemax', '255')
    expect(hp).toHaveAttribute('aria-valuetext', '35 of 255')
    expect(screen.getByRole('meter', { name: 'Special Attack' })).toHaveAttribute(
      'aria-valuenow',
      '50',
    )
    expect(screen.getAllByRole('meter')).toHaveLength(3)
    expect(screen.getByText('55')).toBeInTheDocument()
  })

  it('renders the evolution chain as nested lists with links to the other species', async () => {
    stubFetch(() => json(pikachuDetails))

    renderDetail()

    const chain = await screen.findByRole('list', { name: /evolution chain/i })
    expect(within(chain).getByRole('link', { name: 'Pichu' })).toHaveAttribute(
      'href',
      '/pokemon/pichu',
    )
    expect(within(chain).getByRole('link', { name: 'Raichu' })).toHaveAttribute(
      'href',
      '/pokemon/raichu',
    )
    // the species being viewed is marked and is not a link to itself
    const current = within(chain).getByText('Pikachu', { selector: '[aria-current]' })
    expect(current).toHaveAttribute('aria-current', 'page')
    expect(within(chain).queryByRole('link', { name: 'Pikachu' })).not.toBeInTheDocument()
    // nesting: Raichu is inside Pikachu's item, Pichu is not
    const pikachuItem = current.closest('li')!
    expect(within(pikachuItem).getByRole('link', { name: 'Raichu' })).toBeInTheDocument()
    expect(within(pikachuItem).queryByRole('link', { name: 'Pichu' })).not.toBeInTheDocument()
    // how each step happens
    expect(within(pikachuItem).getByText('Use Item')).toBeInTheDocument()
    expect(within(chain).getByText('Level Up')).toBeInTheDocument()
  })

  it('renders a branching chain with level requirements', async () => {
    stubFetch(() => json(eeveeDetails))

    renderDetail('/pokemon/eevee')

    const chain = await screen.findByRole('list', { name: /evolution chain/i })
    for (const name of ['Vaporeon', 'Jolteon', 'Flareon']) {
      expect(within(chain).getByRole('link', { name })).toHaveAttribute(
        'href',
        `/pokemon/${name.toLowerCase()}`,
      )
    }
    expect(within(chain).getByText('Level 16')).toBeInTheDocument()
  })

  it('shows the viewed form inside its species chain', async () => {
    stubFetch(() => json({ ...pikachuDetails, name: 'pikachu-gmax', speciesName: 'pikachu' }))

    const view = renderDetail('/pokemon/pikachu-gmax')

    const current = await view.findByText('Pikachu Gmax', { selector: '.evolution__current' })
    expect(current).toHaveAttribute('aria-current', 'page')
    expect(view.getByRole('link', { name: 'Pichu' })).toBeInTheDocument()
  })

  it('goes back to the catalogue page the user came from', async () => {
    stubFetch(() => json(pikachuDetails))

    const view = renderDetail('/pokemon/pikachu', undefined, { browseSearch: '?page=7' })

    const back = await view.findByRole('link', { name: 'Back to Browse' })
    expect(back).toHaveAttribute('href', '/?page=7')
  })

  it('says so when the Pokémon does not evolve', async () => {
    stubFetch(() =>
      json({
        ...eeveeDetails,
        evolutionChain: { id: 133, name: 'eevee', minLevel: null, trigger: null, evolvesTo: [] },
      }),
    )

    renderDetail('/pokemon/eevee')

    expect(await screen.findByText(/does not evolve/i)).toBeInTheDocument()
  })

  it('lowercases the route parameter before calling the API', async () => {
    const fetchMock = stubFetch(() => json(pikachuDetails))

    renderDetail('/pokemon/Pikachu')

    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })
    expect(urlOf(fetchMock).pathname).toBe('/api/v1/pokemon/pikachu')
    expect(headersOf(fetchMock).has('authorization')).toBe(false)
  })
})

describe('PokemonDetailPage: errors', () => {
  it('shows a friendly not-found message with a link back to Browse on 404', async () => {
    stubFetch(() => problem(404, { title: 'Not Found', detail: 'Unknown Pokémon: nope' }))

    renderDetail('/pokemon/nope')

    expect(await screen.findByRole('heading', { name: /pokémon not found/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse/i })).toHaveAttribute('href', '/')
  })

  it('shows the server problem with a retry button when PokeAPI is down (502)', async () => {
    stubFetch(() => problem(502, { title: 'Bad Gateway', detail: 'PokeAPI is unavailable' }))

    renderDetail()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Bad Gateway')
    expect(alert).toHaveTextContent('PokeAPI is unavailable')
    expect(within(alert).getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})

describe('PokemonDetailPage: add to my collection', () => {
  it('sends anonymous visitors to the login page and remembers this page', async () => {
    const fetchMock = stubFetch(() => json(pikachuDetails))
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    expect(screen.getByText('login page, back to /pokemon/pikachu')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('syncs for a signed-in user and reports a new entry with a link to edit it', async () => {
    signIn()
    const fetchMock = stubFetch((_url, init) =>
      init.method === 'POST' ? json(localPokemon({ id: 25 }), 201) : json(pikachuDetails),
    )
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    const message = await screen.findByText(/added to your collection/i)
    expect(message).not.toHaveTextContent(/already/i)
    expect(screen.getByRole('status')).toContainElement(message)
    expect(screen.getByRole('link', { name: /edit/i })).toHaveAttribute(
      'href',
      '/collection/25/edit',
    )
    const post = fetchMock.mock.calls.findIndex(([, init]) => init?.method === 'POST')
    expect(urlOf(fetchMock, post).pathname).toBe('/api/v1/local-pokemon/sync')
    expect(fetchMock.mock.calls[post][1]?.body).toBe('{"idOrName":"pikachu"}')
    expect(headersOf(fetchMock, post).get('authorization')).toBe(`Bearer ${demoSession().token}`)
  })

  it('reports an already stored Pokémon as refreshed (200)', async () => {
    signIn()
    stubFetch((_url, init) =>
      init.method === 'POST' ? json(localPokemon({ id: 25 }), 200) : json(pikachuDetails),
    )
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    expect(await screen.findByText(/already in your collection.*refreshed/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /edit/i })).toHaveAttribute(
      'href',
      '/collection/25/edit',
    )
  })

  it('disables the button while the request is pending', async () => {
    signIn()
    const pending = deferred<Response>()
    stubFetch((_url, init) => (init.method === 'POST' ? pending.promise : json(pikachuDetails)))
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    expect(screen.getByRole('button', { name: /adding/i })).toBeDisabled()
    pending.resolve(json(localPokemon(), 201))
    expect(await screen.findByText(/added to your collection/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add to my collection/i })).toBeEnabled()
  })

  it('invalidates the local-pokemon queries after a sync', async () => {
    signIn()
    stubFetch((_url, init) =>
      init.method === 'POST' ? json(localPokemon(), 201) : json(pikachuDetails),
    )
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderDetail('/pokemon/pikachu', queryClient)

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.localPokemon.all }),
    )
  })

  it('shows the server problem when the sync fails', async () => {
    signIn()
    stubFetch((_url, init) =>
      init.method === 'POST'
        ? problem(502, { title: 'Bad Gateway', detail: 'PokeAPI is unavailable' })
        : json(pikachuDetails),
    )
    renderDetail()

    await userEvent.click(await screen.findByRole('button', { name: /add to my collection/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('PokeAPI is unavailable')
    expect(screen.getByRole('button', { name: /add to my collection/i })).toBeEnabled()
  })
})

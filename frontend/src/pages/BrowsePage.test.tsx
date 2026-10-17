import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { bulbasaur, mrMime, pageOf, pikachu } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, json, problem, stubFetch, urlOf } from '../test/http'
import { BrowsePage } from './BrowsePage'

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('BrowsePage', () => {
  it('shows loading skeletons until the first page arrives', async () => {
    const pending = deferred<Response>()
    stubFetch(() => pending.promise)

    const { container } = renderWithProviders(<BrowsePage />)

    expect(screen.getByRole('status')).toHaveTextContent(/loading/i)
    expect(container.querySelectorAll('[data-testid="pokemon-card-skeleton"]')).toHaveLength(20)

    pending.resolve(json(pageOf([pikachu])))
    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
    expect(container.querySelectorAll('[data-testid="pokemon-card-skeleton"]')).toHaveLength(0)
  })

  it('renders a card per Pokémon with id, name, category, weight, abilities and sprite', async () => {
    stubFetch(() => json(pageOf([pikachu, mrMime])))

    renderWithProviders(<BrowsePage />)

    const card = (await screen.findByRole('link', { name: 'Pikachu' })).closest('article')!
    const view = within(card)
    expect(view.getByRole('link', { name: 'Pikachu' })).toHaveAttribute('href', '/pokemon/pikachu')
    expect(view.getByText('#025')).toBeInTheDocument()
    expect(view.getByText('Mouse Pokémon')).toBeInTheDocument()
    expect(view.getByText('6 kg')).toBeInTheDocument()
    const abilities = view.getAllByRole('listitem').map((li) => li.textContent)
    expect(abilities).toEqual(['Static', 'Lightning Rod'])
    const sprite = view.getByRole('img', { name: 'Sprite of Pikachu' })
    expect(sprite).toHaveAttribute('src', pikachu.spriteUrl)

    expect(screen.getByRole('link', { name: 'Mr Mime' })).toHaveAttribute(
      'href',
      '/pokemon/mr-mime',
    )
    expect(screen.getByText('54.5 kg')).toBeInTheDocument()
  })

  it('does not break when a Pokémon has no sprite', async () => {
    stubFetch(() => json(pageOf([{ ...pikachu, spriteUrl: null }])))

    renderWithProviders(<BrowsePage />)

    expect(await screen.findByRole('img', { name: /no sprite.*pikachu/i })).toBeInTheDocument()
  })

  it('requests API page 0 (human page 1) by default and shows pagination', async () => {
    const fetchMock = stubFetch(() => json(pageOf([pikachu], { page: 0, totalPages: 3 })))

    renderWithProviders(<BrowsePage />)

    await screen.findByRole('link', { name: 'Pikachu' })
    const url = urlOf(fetchMock)
    expect(url.searchParams.get('page')).toBe('0')
    expect(url.searchParams.get('size')).toBe('20')
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /next/i })).toHaveAttribute('href', '/?page=2')
  })

  it('maps ?page=2 in the URL to API page 1', async () => {
    const fetchMock = stubFetch(() => json(pageOf([bulbasaur], { page: 1, totalPages: 3 })))

    renderWithProviders(<BrowsePage />, { route: '/?page=2' })

    await screen.findByRole('link', { name: 'Bulbasaur' })
    expect(urlOf(fetchMock).searchParams.get('page')).toBe('1')
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /previous/i })).toHaveAttribute('href', '/?page=1')
  })

  it.each(['abc', '0', '-3', '1.5', ''])('falls back to page 1 for ?page=%j', async (value) => {
    const fetchMock = stubFetch(() => json(pageOf([pikachu])))

    renderWithProviders(<BrowsePage />, { route: `/?page=${value}` })

    await screen.findByRole('link', { name: 'Pikachu' })
    expect(urlOf(fetchMock).searchParams.get('page')).toBe('0')
    expect(screen.getByText(/^Page 1 of/)).toBeInTheDocument()
  })

  it('moves to the next page when Next is clicked', async () => {
    const fetchMock = stubFetch((url) =>
      url.searchParams.get('page') === '0'
        ? json(pageOf([pikachu], { page: 0 }))
        : json(pageOf([bulbasaur], { page: 1 })),
    )
    renderWithProviders(<BrowsePage />)
    await screen.findByRole('link', { name: 'Pikachu' })

    await userEvent.click(screen.getByRole('link', { name: /next/i }))

    expect(await screen.findByRole('link', { name: 'Bulbasaur' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Pikachu' })).not.toBeInTheDocument()
    expect(urlOf(fetchMock, 1).searchParams.get('page')).toBe('1')
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
  })

  it('keeps the current cards on screen (marked busy) while the next page loads', async () => {
    const next = deferred<Response>()
    stubFetch((url) =>
      url.searchParams.get('page') === '0' ? json(pageOf([pikachu])) : next.promise,
    )
    renderWithProviders(<BrowsePage />)
    await screen.findByRole('link', { name: 'Pikachu' })

    await userEvent.click(screen.getByRole('link', { name: /next/i }))

    expect(screen.getByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('list', { name: /pokémon/i })).toHaveAttribute('aria-busy', 'true'),
    )
    next.resolve(json(pageOf([bulbasaur], { page: 1 })))
    await screen.findByRole('link', { name: 'Bulbasaur' })
  })

  it('scrolls to the top when the page changes', async () => {
    stubFetch(() => json(pageOf([pikachu])))
    renderWithProviders(<BrowsePage />)
    await screen.findByRole('link', { name: 'Pikachu' })

    await userEvent.click(screen.getByRole('link', { name: /next/i }))

    await waitFor(() => expect(window.scrollTo).toHaveBeenCalled())
  })

  it('shows an error banner with a retry button when the request fails', async () => {
    let calls = 0
    stubFetch(() => {
      calls += 1
      return calls === 1
        ? problem(500, { title: 'Internal Server Error', detail: 'PokeAPI is down' })
        : json(pageOf([pikachu]))
    })

    renderWithProviders(<BrowsePage />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Internal Server Error')
    expect(alert).toHaveTextContent('PokeAPI is down')

    await userEvent.click(within(alert).getByRole('button', { name: /try again/i }))

    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('explains an empty page and offers a way back', async () => {
    stubFetch(() => json(pageOf([], { page: 8, totalPages: 3 })))

    renderWithProviders(<BrowsePage />, { route: '/?page=9' })

    expect(await screen.findByText(/no pokémon on this page/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /first page/i })).toHaveAttribute('href', '/?page=1')
  })
})

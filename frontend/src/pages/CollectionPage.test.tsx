import { QueryClient } from '@tanstack/react-query'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { queryKeys } from '../api/queryKeys'
import { SESSION_STORAGE_KEY } from '../auth/session'
import { demoSession, localPokemon, pageOf } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, headersOf, json, problem, stubFetch, urlOf } from '../test/http'
import { CollectionPage } from './CollectionPage'

const pikachu = localPokemon({
  id: 25,
  name: 'pikachu',
  localName: 'Sparky',
  region: 'Kanto',
  tags: ['electric', 'starter'],
  version: 2,
})
const bulbasaur = localPokemon({
  id: 1,
  name: 'bulbasaur',
  spriteUrl: null,
  localName: null,
  region: null,
  tags: [],
})

function Search() {
  return <p>search: {useLocation().search}</p>
}

function renderCollection(route = '/collection', queryClient?: QueryClient) {
  return renderWithProviders(
    <Routes>
      <Route path="/collection" element={<CollectionPage />} />
      <Route path="/pokemon/:idOrName" element={<Search />} />
      <Route path="/" element={<p>browse page</p>} />
    </Routes>,
    { route, queryClient },
  )
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
})

/** Serves the list (GET) and DELETEs through the given handlers. */
function backend(options: {
  items?: ReturnType<typeof localPokemon>[]
  remove?: () => Response | Promise<Response>
}) {
  let items = options.items ?? [pikachu, bulbasaur]
  return stubFetch(async (url, init) => {
    if (init.method !== 'DELETE') {
      return json(pageOf(items, { totalElements: items.length, totalPages: 1 }))
    }
    const response = (await options.remove?.()) ?? new Response(null, { status: 204 })
    if (response.status === 204 || response.status === 404) {
      const id = Number(url.pathname.split('/').pop())
      items = items.filter((item) => item.id !== id) // later refetches no longer see it
    }
    return response
  })
}

describe('CollectionPage: list', () => {
  it('shows a loading status first', async () => {
    const pending = deferred<Response>()
    stubFetch(() => pending.promise)

    renderCollection()

    expect(screen.getByRole('status')).toHaveTextContent(/loading/i)
    pending.resolve(json(pageOf([pikachu], { totalPages: 1 })))
    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('requests API page 0 with the token and lists the stored Pokémon', async () => {
    const fetchMock = backend({})

    renderCollection()

    const card = (await screen.findByRole('link', { name: 'Pikachu' })).closest('article')!
    const view = within(card)
    expect(view.getByText('#025')).toBeInTheDocument()
    expect(view.getByRole('link', { name: 'Pikachu' })).toHaveAttribute('href', '/pokemon/pikachu')
    expect(view.getByText('Sparky')).toBeInTheDocument()
    expect(view.getByText('Kanto')).toBeInTheDocument()
    expect(view.getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'electric',
      'starter',
    ])
    expect(view.getByRole('img', { name: /sprite of pikachu/i })).toHaveAttribute(
      'src',
      pikachu.spriteUrl,
    )
    expect(view.getByRole('link', { name: /edit pikachu/i })).toHaveAttribute(
      'href',
      '/collection/25/edit',
    )
    expect(view.getByRole('button', { name: /delete pikachu/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Bulbasaur' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /no sprite.*bulbasaur/i })).toBeInTheDocument()

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/local-pokemon')
    expect(urlOf(fetchMock).searchParams.get('page')).toBe('0')
    expect(urlOf(fetchMock).searchParams.get('size')).toBe('20')
    expect(headersOf(fetchMock).get('authorization')).toBe(`Bearer ${demoSession().token}`)
  })

  it('maps ?page=2 to API page 1 and shows pagination', async () => {
    const fetchMock = stubFetch(() =>
      json(pageOf([pikachu], { page: 1, totalElements: 30, totalPages: 2 })),
    )

    renderCollection('/collection?page=2')

    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
    expect(urlOf(fetchMock).searchParams.get('page')).toBe('1')
    expect(screen.getByRole('link', { name: 'Previous' })).toHaveAttribute(
      'href',
      '/collection?page=1',
    )
  })

  it('shows an empty state with a link to Browse', async () => {
    stubFetch(() => json(pageOf([], { totalElements: 0, totalPages: 0 })))

    renderCollection()

    expect(await screen.findByText(/your collection is empty/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse/i })).toHaveAttribute('href', '/')
  })

  it('shows the server problem with a retry button', async () => {
    stubFetch(() => problem(500, { title: 'Internal Server Error', detail: 'Boom' }))

    renderCollection()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Internal Server Error')
    expect(within(alert).getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})

describe('CollectionPage: delete', () => {
  it('asks for confirmation in a dialog and does nothing on Cancel', async () => {
    const fetchMock = backend({})
    renderCollection()

    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    const dialog = screen.getByRole('alertdialog', { name: /delete pikachu/i })
    expect(dialog).toHaveAccessibleDescription(/cannot be undone|sparky/i)
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    expect(screen.getByRole('button', { name: /delete pikachu/i })).toHaveFocus()
  })

  it('closes the dialog with Escape', async () => {
    backend({})
    renderCollection()
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('keeps keyboard focus inside the dialog', async () => {
    backend({})
    renderCollection()
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))
    const dialog = screen.getByRole('alertdialog')

    await userEvent.tab()
    expect(within(dialog).getByRole('button', { name: 'Delete' })).toHaveFocus()
    await userEvent.tab()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    await userEvent.tab({ shift: true })
    expect(within(dialog).getByRole('button', { name: 'Delete' })).toHaveFocus()
  })

  it('deletes after confirmation and removes the entry before the server answers', async () => {
    const pending = deferred<Response>()
    const fetchMock = backend({ remove: () => pending.promise })
    renderCollection()
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }),
    )

    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Pikachu' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Bulbasaur' })).toBeInTheDocument()
    const call = fetchMock.mock.calls.findIndex(([, init]) => init?.method === 'DELETE')
    expect(urlOf(fetchMock, call).pathname).toBe('/api/v1/local-pokemon/25')
    expect(headersOf(fetchMock, call).get('authorization')).toBe(`Bearer ${demoSession().token}`)
    pending.resolve(new Response(null, { status: 204 }))
  })

  it('invalidates the collection queries when the delete has settled', async () => {
    backend({})
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderCollection('/collection', queryClient)
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }),
    )

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.localPokemon.all }),
    )
  })

  it('rolls the entry back and shows the problem when the delete fails', async () => {
    backend({ remove: () => problem(500, { title: 'Internal Server Error', detail: 'Boom' }) })
    renderCollection()
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal Server Error')
    expect(await screen.findByRole('link', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('treats a 404 as already deleted: no error, the entry stays gone', async () => {
    backend({ remove: () => problem(404, { title: 'Not Found', detail: 'gone' }) })
    renderCollection()
    await userEvent.click(await screen.findByRole('button', { name: /delete pikachu/i }))

    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }),
    )

    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Pikachu' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

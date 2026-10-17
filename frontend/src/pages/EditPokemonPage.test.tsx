import { QueryClient } from '@tanstack/react-query'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { queryKeys } from '../api/queryKeys'
import type { LocalPokemon } from '../api/types'
import { SESSION_STORAGE_KEY } from '../auth/session'
import { demoSession, localPokemon } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { deferred, headersOf, json, problem, stubFetch, urlOf } from '../test/http'
import { EditPokemonPage } from './EditPokemonPage'

const stored = localPokemon({
  id: 25,
  name: 'pikachu',
  localName: 'Sparky',
  region: 'Kanto',
  notes: 'Found it in the forest.',
  tags: ['electric', 'starter'],
  version: 3,
})

function CollectionProbe() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  return <p>collection page {notice}</p>
}

function renderEdit(route = '/collection/25/edit', queryClient?: QueryClient) {
  return renderWithProviders(
    <Routes>
      <Route path="/collection/:id/edit" element={<EditPokemonPage />} />
      <Route path="/collection" element={<CollectionProbe />} />
      <Route path="/pokemon/:idOrName" element={<p>detail page</p>} />
    </Routes>,
    { route, queryClient },
  )
}

interface Backend {
  /** Successive answers to GET /local-pokemon/25 (the last one repeats). */
  gets?: Response[]
  put?: () => Response | Promise<Response>
}

function backend({ gets = [json(stored)], put }: Backend = {}) {
  let getCount = 0
  return stubFetch((_url, init) => {
    if (init.method === 'PUT') return put ? put() : json({ ...stored, version: stored.version + 1 })
    const response = gets[Math.min(getCount, gets.length - 1)]
    getCount += 1
    return response.clone()
  })
}

function putCalls(fetchMock: ReturnType<typeof backend>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')
}

function lastPutBody(fetchMock: ReturnType<typeof backend>) {
  const calls = putCalls(fetchMock)
  return JSON.parse(String(calls[calls.length - 1][1]?.body))
}

async function openForm() {
  await screen.findByRole('heading', { level: 1, name: /edit pikachu/i })
}

async function paste(label: string, text: string) {
  const field = screen.getByLabelText(label)
  await userEvent.click(field)
  await userEvent.clear(field)
  await userEvent.paste(text)
}

async function save() {
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
}

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))
})

describe('EditPokemonPage: loading', () => {
  it('shows a loading status, then the record read-only and the form prefilled', async () => {
    const pending = deferred<Response>()
    const fetchMock = stubFetch(() => pending.promise)
    renderEdit()
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i)

    pending.resolve(json(stored))
    await openForm()

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/local-pokemon/25')
    expect(headersOf(fetchMock).get('authorization')).toBe(`Bearer ${demoSession().token}`)
    expect(screen.getByText('#025')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /artwork of pikachu/i })).toBeInTheDocument()
    expect(screen.getByText('Mouse Pokémon')).toBeInTheDocument()
    expect(screen.getByText('Static, Lightning Rod')).toBeInTheDocument()
    expect(screen.getByText('0.4 m')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /pokeapi details/i })).toHaveAttribute(
      'href',
      '/pokemon/pikachu',
    )
    expect(screen.getByLabelText('Local name')).toHaveValue('Sparky')
    expect(screen.getByLabelText('Region')).toHaveValue('Kanto')
    expect(screen.getByLabelText('Notes')).toHaveValue('Found it in the forest.')
    const tags = within(screen.getByRole('list', { name: 'Current tags' }))
    expect(
      tags.getAllByRole('listitem').map((li) => li.textContent?.replace('×', '').trim()),
    ).toEqual(['electric', 'starter'])
  })

  it('shows empty fields for a record without proprietary data', async () => {
    backend({ gets: [json(localPokemon({ version: 0 }))] })
    renderEdit()
    await openForm()

    expect(screen.getByLabelText('Local name')).toHaveValue('')
    expect(screen.getByLabelText('Notes')).toHaveValue('')
    expect(screen.queryByRole('list', { name: 'Current tags' })).not.toBeInTheDocument()
  })

  it('says so when the Pokémon is not in the collection (404)', async () => {
    backend({ gets: [problem(404, { title: 'Not Found', detail: 'no such' })] })
    renderEdit()

    expect(
      await screen.findByRole('heading', { name: /not in your collection/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to my collection/i })).toHaveAttribute(
      'href',
      '/collection',
    )
  })

  it('does not call the API for an id that is not a number', async () => {
    const fetchMock = backend()
    renderEdit('/collection/abc/edit')

    expect(
      await screen.findByRole('heading', { name: /not in your collection/i }),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows other load failures with a retry button', async () => {
    backend({ gets: [problem(500, { title: 'Internal Server Error', detail: 'Boom' })] })
    renderEdit()

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})

describe('EditPokemonPage: validation', () => {
  async function expectBlocked(
    fetchMock: ReturnType<typeof backend>,
    label: string,
    message: RegExp,
  ) {
    const field = screen.getByLabelText(label)
    expect(field).toBeInvalid()
    expect(field).toHaveAccessibleDescription(message)
    expect(field).toHaveFocus()
    expect(putCalls(fetchMock)).toHaveLength(0)
  }

  it('rejects a blank (whitespace only) local name', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Local name', '   ')
    await save()

    await expectBlocked(fetchMock, 'Local name', /must not be blank/i)
  })

  it('rejects a local name over 100 characters', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Local name', 'x'.repeat(101))
    await save()

    await expectBlocked(fetchMock, 'Local name', /at most 100/i)
  })

  it('rejects a region over 100 characters', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Region', 'x'.repeat(101))
    await save()

    await expectBlocked(fetchMock, 'Region', /at most 100/i)
  })

  it('rejects notes over 1000 characters', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Notes', 'x'.repeat(1001))
    await save()

    await expectBlocked(fetchMock, 'Notes', /at most 1000/i)
  })

  it('clears an error as soon as the field is fixed', async () => {
    backend()
    renderEdit()
    await openForm()
    await paste('Region', 'x'.repeat(101))
    await save()
    expect(screen.getByLabelText('Region')).toBeInvalid()

    await paste('Region', 'Kanto')

    expect(screen.getByLabelText('Region')).toBeValid()
  })

  it('accepts exactly the limits', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Local name', 'x'.repeat(100))
    await paste('Region', 'y'.repeat(100))
    await paste('Notes', 'z'.repeat(1000))
    await save()

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
  })
})

describe('EditPokemonPage: tags', () => {
  const tagNames = () =>
    screen
      .queryAllByRole('listitem')
      .filter((li) => li.closest('ul')?.getAttribute('aria-label') === 'Current tags')
      .map((li) => li.textContent?.replace('×', '').trim())

  async function openEmpty() {
    backend({ gets: [json(localPokemon({ version: 0 }))] })
    renderEdit()
    await openForm()
    return screen.getByLabelText('Tags')
  }

  it('adds a tag on Enter without submitting the form', async () => {
    const input = await openEmpty()

    await userEvent.type(input, 'Fire{Enter}')

    expect(tagNames()).toEqual(['fire'])
    expect(input).toHaveValue('')
    expect(input).toHaveFocus()
  })

  it('adds a tag on comma and splits pasted lists', async () => {
    const input = await openEmpty()

    await userEvent.type(input, 'fire,')
    await userEvent.click(input)
    await userEvent.paste('water, grass-type,')

    expect(tagNames()).toEqual(['fire', 'water', 'grass-type'])
  })

  it('collapses duplicates silently', async () => {
    const input = await openEmpty()

    await userEvent.type(input, 'fire{Enter}FIRE{Enter}')

    expect(tagNames()).toEqual(['fire'])
    expect(screen.queryByText(/not a valid tag/i)).not.toBeInTheDocument()
  })

  it('removes the last tag with Backspace on an empty input, but not while typing', async () => {
    const input = await openEmpty()
    await userEvent.type(input, 'a{Enter}b{Enter}')

    await userEvent.type(input, 'c{Backspace}')
    expect(tagNames()).toEqual(['a', 'b'])

    await userEvent.keyboard('{Backspace}')
    expect(tagNames()).toEqual(['a'])
  })

  it('removes a tag with its labelled x button', async () => {
    const input = await openEmpty()
    await userEvent.type(input, 'fire{Enter}water{Enter}')

    await userEvent.click(screen.getByRole('button', { name: 'Remove tag fire' }))

    expect(tagNames()).toEqual(['water'])
  })

  it.each(['two words', 'under_score', '-lead', 'a--b'])(
    'rejects the invalid tag %j',
    async (raw) => {
      const input = await openEmpty()

      await userEvent.type(input, `${raw}{Enter}`)

      expect(tagNames()).toEqual([])
      expect(input).toBeInvalid()
      expect(input).toHaveAccessibleDescription(/lowercase letters, digits and single hyphens/i)
    },
  )

  it('rejects an eleventh distinct tag', async () => {
    const input = await openEmpty()
    for (let i = 0; i < 10; i += 1) await userEvent.type(input, `t${i}{Enter}`)

    await userEvent.type(input, 'extra{Enter}')

    expect(tagNames()).toHaveLength(10)
    expect(input).toHaveAccessibleDescription(/at most 10/i)
  })

  it('also adds the text still typed in the tag input when saving', async () => {
    const fetchMock = backend({ gets: [json(localPokemon({ version: 0 }))] })
    renderEdit()
    await openForm()

    await userEvent.type(screen.getByLabelText('Tags'), 'Pending')
    await save()

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
    expect(lastPutBody(fetchMock).tags).toEqual(['pending'])
  })

  it('does not save while the typed tag is invalid', async () => {
    const fetchMock = backend({ gets: [json(localPokemon({ version: 0 }))] })
    renderEdit()
    await openForm()

    await userEvent.type(screen.getByLabelText('Tags'), 'not valid')
    await save()

    expect(screen.getByLabelText('Tags')).toBeInvalid()
    expect(putCalls(fetchMock)).toHaveLength(0)
  })
})

describe('EditPokemonPage: saving', () => {
  it('sends exactly {localName, region, notes, tags, version} and goes to the collection', async () => {
    const fetchMock = backend()
    renderEdit()
    await openForm()

    await paste('Local name', '  Zap  ')
    await paste('Region', '   ')
    await paste('Notes', '')
    await userEvent.type(screen.getByLabelText('Tags'), 'new-tag{Enter}')
    await save()

    expect(await screen.findByText(/collection page/i)).toHaveTextContent(/saved/i)
    expect(putCalls(fetchMock)).toHaveLength(1)
    const call = fetchMock.mock.calls.findIndex(([, init]) => init?.method === 'PUT')
    expect(urlOf(fetchMock, call).pathname).toBe('/api/v1/local-pokemon/25')
    expect(headersOf(fetchMock, call).get('authorization')).toBe(`Bearer ${demoSession().token}`)
    expect(lastPutBody(fetchMock)).toEqual({
      localName: 'Zap',
      region: null,
      notes: null,
      tags: ['electric', 'starter', 'new-tag'],
      version: 3,
    })
  })

  it('disables the button while the request is pending', async () => {
    const pending = deferred<Response>()
    backend({ put: () => pending.promise })
    renderEdit()
    await openForm()

    await save()

    expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled()
    pending.resolve(json({ ...stored, version: 4 }))
    expect(await screen.findByText(/collection page/i)).toBeInTheDocument()
  })

  it('invalidates the collection queries after a save', async () => {
    backend()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderEdit('/collection/25/edit', queryClient)
    await openForm()

    await save()

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.localPokemon.all }),
    )
  })

  it('maps the field errors of a 400 answer onto the fields', async () => {
    backend({
      put: () =>
        problem(400, {
          title: 'Validation failed',
          errors: [
            { field: 'localName', message: 'must not be blank' },
            { field: 'tags[1]', message: 'must be lowercase kebab-case' },
          ],
        }),
    })
    renderEdit()
    await openForm()

    await save()

    await waitFor(() => expect(screen.getByLabelText('Local name')).toBeInvalid())
    expect(screen.getByLabelText('Local name')).toHaveAccessibleDescription(/must not be blank/i)
    expect(screen.getByLabelText('Tags')).toBeInvalid()
    expect(screen.getByLabelText('Tags')).toHaveAccessibleDescription(/lowercase kebab-case/i)
    expect(screen.getByLabelText('Local name')).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled()
  })

  it('shows 400 errors that belong to no field (e.g. version) in an alert', async () => {
    backend({
      put: () =>
        problem(400, {
          title: 'Validation failed',
          errors: [{ field: 'version', message: 'must not be null' }],
        }),
    })
    renderEdit()
    await openForm()

    await save()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Validation failed')
    expect(alert).toHaveTextContent(/version.*must not be null/i)
  })

  it('says so when the record was deleted meanwhile (404)', async () => {
    backend({ put: () => problem(404, { title: 'Not Found', detail: 'gone' }) })
    renderEdit()
    await openForm()

    await save()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/no longer in your collection/i)
    expect(within(alert).getByRole('link', { name: /back to my collection/i })).toHaveAttribute(
      'href',
      '/collection',
    )
  })

  it('shows other failures in an alert and keeps the form editable', async () => {
    backend({ put: () => problem(500, { title: 'Internal Server Error', detail: 'Boom' }) })
    renderEdit()
    await openForm()

    await save()

    expect(await screen.findByRole('alert')).toHaveTextContent('Internal Server Error')
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled()
  })
})

describe('EditPokemonPage: edited elsewhere (409)', () => {
  const newer: LocalPokemon = { ...stored, localName: 'Zap', region: null, tags: [], version: 5 }

  function conflictBackend() {
    return backend({
      gets: [json(stored), json(newer)],
      put: () =>
        problem(409, {
          title: 'Conflict',
          detail: 'Pokémon 25 was modified by someone else (current version 5); reload and retry',
        }),
    })
  }

  it('shows a clear message with a Reload button and blocks saving the stale form', async () => {
    conflictBackend()
    renderEdit()
    await openForm()
    await paste('Notes', 'my edit')

    await save()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(
      'This Pokémon was edited elsewhere. Reload to get the latest version.',
    )
    expect(within(alert).getByRole('button', { name: 'Reload' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    // what the user typed is not thrown away until they choose to reload
    expect(screen.getByLabelText('Notes')).toHaveValue('my edit')
  })

  it('reloads the record, resets the form and saves with the new version', async () => {
    const fetchMock = conflictBackend()
    renderEdit()
    await openForm()
    await paste('Notes', 'my edit')
    await save()
    await userEvent.click(await screen.findByRole('button', { name: 'Reload' }))

    await waitFor(() => expect(screen.getByLabelText('Local name')).toHaveValue('Zap'))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Notes')).toHaveValue('Found it in the forest.')
    expect(screen.getByLabelText('Region')).toHaveValue('')
    expect(screen.queryByRole('list', { name: 'Current tags' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled()

    await save()

    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(2))
    expect(lastPutBody(fetchMock).version).toBe(5)
  })
})

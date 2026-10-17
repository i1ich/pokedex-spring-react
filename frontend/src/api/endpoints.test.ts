import { demoSession } from '../test/fixtures'
import { headersOf, json, stubFetch, urlOf } from '../test/http'
import { setSession } from '../auth/session'
import { getCurrentUser, login, register } from './auth'
import {
  deleteLocalPokemon,
  fetchLocalPokemon,
  fetchLocalPokemonPage,
  syncPokemon,
  updateLocalPokemon,
} from './localPokemon'
import { fetchPokemonDetails, fetchPokemonPage } from './pokemon'
import { queryKeys } from './queryKeys'

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('pokemon endpoints (public)', () => {
  it('fetchPokemonPage calls the catalogue with 0-based page and size, without a token', async () => {
    setSession(demoSession())
    const fetchMock = stubFetch(() => json({ items: [] }))

    await fetchPokemonPage(2, 20)

    const url = urlOf(fetchMock)
    expect(url.pathname).toBe('/api/v1/pokemon')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('20')
    expect(headersOf(fetchMock).has('authorization')).toBe(false)
  })

  it('fetchPokemonDetails lowercases and trims the identifier', async () => {
    const fetchMock = stubFetch(() => json({}))

    await fetchPokemonDetails('  Mr-Mime ')

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/pokemon/mr-mime')
  })
})

describe('local pokemon endpoints (protected)', () => {
  beforeEach(() => setSession({ ...demoSession(), token: 'tok' }))

  it('syncPokemon POSTs the lowercased identifier with the token', async () => {
    const fetchMock = stubFetch(() => json({ id: 25 }, 201))

    await syncPokemon('Pikachu')

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/local-pokemon/sync')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('POST')
    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"idOrName":"pikachu"}')
    expect(headersOf(fetchMock).get('authorization')).toBe('Bearer tok')
  })

  it('syncPokemon reports whether the Pokémon was created (201) or refreshed (200)', async () => {
    stubFetch(() => json({ id: 25 }, 201))
    await expect(syncPokemon('pikachu')).resolves.toEqual({ pokemon: { id: 25 }, created: true })

    stubFetch(() => json({ id: 25 }, 200))
    await expect(syncPokemon('pikachu')).resolves.toEqual({ pokemon: { id: 25 }, created: false })
  })

  it('updateLocalPokemon PUTs exactly the request body with the token', async () => {
    const fetchMock = stubFetch(() => json({ id: 25, version: 4 }))
    const request = { localName: 'Sparky', region: null, notes: null, tags: ['a'], version: 3 }

    await expect(updateLocalPokemon(25, request)).resolves.toEqual({ id: 25, version: 4 })

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/local-pokemon/25')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('PUT')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual(request)
    expect(headersOf(fetchMock).get('authorization')).toBe('Bearer tok')
  })

  it('deleteLocalPokemon DELETEs with the token and resolves on 204', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 204 }))

    await expect(deleteLocalPokemon(25)).resolves.toBeUndefined()

    expect(urlOf(fetchMock).pathname).toBe('/api/v1/local-pokemon/25')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('DELETE')
    expect(headersOf(fetchMock).get('authorization')).toBe('Bearer tok')
  })

  it('fetchLocalPokemonPage and fetchLocalPokemon send the token', async () => {
    const fetchMock = stubFetch(() => json({}))

    await fetchLocalPokemonPage(1, 10)
    await fetchLocalPokemon(25)

    expect(urlOf(fetchMock, 0).pathname).toBe('/api/v1/local-pokemon')
    expect(urlOf(fetchMock, 0).searchParams.get('page')).toBe('1')
    expect(urlOf(fetchMock, 1).pathname).toBe('/api/v1/local-pokemon/25')
    expect(headersOf(fetchMock, 0).get('authorization')).toBe('Bearer tok')
    expect(headersOf(fetchMock, 1).get('authorization')).toBe('Bearer tok')
  })
})

describe('auth endpoints', () => {
  it('login and register POST credentials without a token', async () => {
    setSession(demoSession())
    const fetchMock = stubFetch(() => json({}))

    await login({ username: 'demo', password: 'demo12345' })
    await register({ username: 'new', password: 'secret123' })

    expect(urlOf(fetchMock, 0).pathname).toBe('/api/v1/auth/login')
    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"username":"demo","password":"demo12345"}')
    expect(urlOf(fetchMock, 1).pathname).toBe('/api/v1/auth/register')
    expect(headersOf(fetchMock, 0).has('authorization')).toBe(false)
    expect(headersOf(fetchMock, 1).has('authorization')).toBe(false)
  })

  it('getCurrentUser sends the token', async () => {
    setSession({ ...demoSession(), token: 'tok' })
    const fetchMock = stubFetch(() => json({ username: 'demo', role: 'USER' }))

    await expect(getCurrentUser()).resolves.toEqual({ username: 'demo', role: 'USER' })

    expect(headersOf(fetchMock).get('authorization')).toBe('Bearer tok')
  })
})

describe('queryKeys', () => {
  it('nests page and detail keys under the resource root so they can be invalidated together', () => {
    expect(queryKeys.pokemon.page(0, 20).slice(0, 1)).toEqual(queryKeys.pokemon.all)
    expect(queryKeys.pokemon.detail('Pikachu')).toEqual([
      ...queryKeys.pokemon.all,
      'detail',
      'pikachu',
    ])
    expect(queryKeys.localPokemon.page(0, 20).slice(0, 1)).toEqual(queryKeys.localPokemon.all)
    expect(queryKeys.localPokemon.detail(25).slice(0, 1)).toEqual(queryKeys.localPokemon.all)
  })
})

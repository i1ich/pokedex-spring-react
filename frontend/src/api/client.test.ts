import { clearSession, getSession, setSession } from '../auth/session'
import { demoSession, pastIso } from '../test/fixtures'
import { headersOf, json, problem, stubFetch, urlOf } from '../test/http'
import { ApiError, apiRequest, apiUrl, resolveBaseUrl } from './client'

describe('resolveBaseUrl', () => {
  it('defaults to the local backend when VITE_API_URL is not defined', () => {
    expect(resolveBaseUrl(undefined)).toBe('http://localhost:8080')
  })

  it('treats an explicitly empty VITE_API_URL as same-origin', () => {
    expect(resolveBaseUrl('')).toBe('')
  })

  it('strips trailing slashes', () => {
    expect(resolveBaseUrl('https://api.example.com//')).toBe('https://api.example.com')
  })
})

describe('apiUrl', () => {
  it('reads VITE_API_URL and falls back to the default when it is undefined', () => {
    vi.stubEnv('VITE_API_URL', undefined)
    expect(apiUrl('/api/v1/pokemon')).toBe('http://localhost:8080/api/v1/pokemon')
  })

  it('produces a same-origin relative URL when VITE_API_URL is empty', () => {
    vi.stubEnv('VITE_API_URL', '')
    expect(apiUrl('/api/v1/pokemon', { page: 0, size: 20 })).toBe('/api/v1/pokemon?page=0&size=20')
  })

  it('skips undefined query values and encodes the others', () => {
    vi.stubEnv('VITE_API_URL', 'http://api.test')
    expect(apiUrl('/x', { q: 'a b&c', page: undefined, flag: false })).toBe(
      'http://api.test/x?q=a+b%26c&flag=false',
    )
  })
})

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_URL', 'http://api.test')
  })

  it('sends a JSON GET and returns the parsed body', async () => {
    const fetchMock = stubFetch(() => json({ hello: 'world' }))

    await expect(apiRequest('/api/v1/ping')).resolves.toEqual({ hello: 'world' })

    expect(urlOf(fetchMock).href).toBe('http://api.test/api/v1/ping')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('GET')
    expect(headersOf(fetchMock).get('accept')).toContain('application/json')
  })

  it('never attaches the token to public requests, even when one is stored', async () => {
    setSession(demoSession())
    const fetchMock = stubFetch(() => json({}))

    await apiRequest('/api/v1/pokemon')

    expect(headersOf(fetchMock).has('authorization')).toBe(false)
  })

  it('attaches the bearer token when auth is requested', async () => {
    setSession({ ...demoSession(), token: 'abc.def' })
    const fetchMock = stubFetch(() => json({}))

    await apiRequest('/api/v1/auth/me', { auth: true })

    expect(headersOf(fetchMock).get('authorization')).toBe('Bearer abc.def')
  })

  it('does not attach an expired token', async () => {
    sessionStorage.setItem(
      'pokedex.session',
      JSON.stringify({ ...demoSession(), expiresAt: pastIso() }),
    )
    const fetchMock = stubFetch(() => json({}))

    await apiRequest('/api/v1/auth/me', { auth: true })

    expect(headersOf(fetchMock).has('authorization')).toBe(false)
  })

  it('serialises a JSON body with the right content type', async () => {
    const fetchMock = stubFetch(() => json({ ok: true }, 201))

    await apiRequest('/api/v1/local-pokemon/sync', {
      method: 'POST',
      body: { idOrName: 'pikachu' },
    })

    const init = fetchMock.mock.calls[0][1]
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe('{"idOrName":"pikachu"}')
    expect(headersOf(fetchMock).get('content-type')).toBe('application/json')
  })

  it('resolves undefined for 204 No Content', async () => {
    stubFetch(() => new Response(null, { status: 204 }))
    await expect(
      apiRequest('/api/v1/local-pokemon/1', { method: 'DELETE' }),
    ).resolves.toBeUndefined()
  })

  it('parses application/problem+json into a typed ApiError', async () => {
    stubFetch(() =>
      problem(400, {
        type: 'about:blank',
        title: 'Validation failed',
        detail: 'One or more fields are invalid',
        instance: '/api/v1/auth/register',
        errors: [{ field: 'username', message: 'must not be blank' }],
      }),
    )

    const error = await apiRequest('/api/v1/auth/register', { method: 'POST' }).catch(
      (e: unknown) => e as ApiError,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      title: 'Validation failed',
      detail: 'One or more fields are invalid',
      errors: [{ field: 'username', message: 'must not be blank' }],
    })
  })

  it('defaults errors to an empty list and detail to null', async () => {
    stubFetch(() => problem(404, { title: 'Not Found' }))

    const error = await apiRequest('/api/v1/pokemon/zzz').catch((e: unknown) => e as ApiError)

    expect(error).toMatchObject({ status: 404, title: 'Not Found', detail: null, errors: [] })
  })

  it('turns a non-JSON error body (nginx 502 page) into a generic ApiError', async () => {
    stubFetch(
      () =>
        new Response('<html><body><h1>502 Bad Gateway</h1></body></html>', {
          status: 502,
          headers: { 'Content-Type': 'text/html' },
        }),
    )

    const error = await apiRequest<never>('/api/v1/pokemon').catch((e: unknown) => e as ApiError)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
    expect(error.title).toBe('Unexpected server response')
    expect(error.detail).toContain('502')
    expect(error.detail).not.toContain('<html')
    expect(error.errors).toEqual([])
  })

  it('turns a malformed JSON error body into a generic ApiError', async () => {
    stubFetch(
      () => new Response('{oops', { status: 500, headers: { 'Content-Type': 'application/json' } }),
    )

    const error = await apiRequest<never>('/api/v1/pokemon').catch((e: unknown) => e as ApiError)

    expect(error).toMatchObject({ status: 500, title: 'Unexpected server response' })
  })

  it('reports an unreachable server as a status 0 ApiError', async () => {
    stubFetch(() => {
      throw new TypeError('Failed to fetch')
    })

    const error = await apiRequest<never>('/api/v1/pokemon').catch((e: unknown) => e as ApiError)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 0, title: 'Network error' })
  })

  it('clears the stored session when an authenticated request gets 401', async () => {
    setSession(demoSession())
    stubFetch(() => problem(401, { title: 'Unauthorized' }))

    await expect(apiRequest('/api/v1/local-pokemon', { auth: true })).rejects.toMatchObject({
      status: 401,
    })

    expect(getSession()).toBeNull()
  })

  it('keeps the session when a request that did not use it gets 401', async () => {
    setSession(demoSession())
    stubFetch(() => problem(401, { title: 'Unauthorized' }))

    await expect(apiRequest('/api/v1/auth/login', { method: 'POST' })).rejects.toMatchObject({
      status: 401,
    })

    expect(getSession()).not.toBeNull()
    clearSession()
  })

  it('keeps the session on non-401 errors of authenticated requests', async () => {
    setSession(demoSession())
    stubFetch(() => problem(403, { title: 'Forbidden' }))

    await expect(apiRequest('/api/v1/local-pokemon', { auth: true })).rejects.toMatchObject({
      status: 403,
    })

    expect(getSession()).not.toBeNull()
  })
})

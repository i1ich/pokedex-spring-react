import { renderHook, waitFor } from '@testing-library/react'
import { ApiError } from '../api/client'
import type { Page, PokemonSummary } from '../api/types'
import { bulbasaur, pageOf, pikachu } from '../test/fixtures'
import { createWrapper } from '../test/helpers'
import { deferred, headersOf, json, problem, stubFetch, urlOf } from '../test/http'
import { usePokemonPage } from './usePokemonPage'

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', '')
})

describe('usePokemonPage', () => {
  it('loads a page of the public catalogue without a token', async () => {
    const fetchMock = stubFetch(() => json(pageOf([pikachu])))

    const { result } = renderHook(() => usePokemonPage(0, 20), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.items).toEqual([pikachu])
    const url = urlOf(fetchMock)
    expect(url.pathname).toBe('/api/v1/pokemon')
    expect(url.searchParams.get('page')).toBe('0')
    expect(url.searchParams.get('size')).toBe('20')
    expect(headersOf(fetchMock).has('authorization')).toBe(false)
  })

  it('keeps showing the previous page while the next one loads', async () => {
    const next = deferred<Response>()
    stubFetch((url) =>
      url.searchParams.get('page') === '0' ? json(pageOf([pikachu], { page: 0 })) : next.promise,
    )

    const { result, rerender } = renderHook(({ page }) => usePokemonPage(page, 20), {
      wrapper: createWrapper(),
      initialProps: { page: 0 },
    })
    await waitFor(() => expect(result.current.data?.page).toBe(0))

    rerender({ page: 1 })

    await waitFor(() => expect(result.current.isFetching).toBe(true))
    expect(result.current.isPlaceholderData).toBe(true)
    expect(result.current.data?.items).toEqual([pikachu])

    next.resolve(json(pageOf([bulbasaur], { page: 1 })))
    await waitFor(() => expect(result.current.isPlaceholderData).toBe(false))
    expect((result.current.data as Page<PokemonSummary>).items).toEqual([bulbasaur])
  })

  it('exposes failures as ApiError', async () => {
    stubFetch(() => problem(500, { title: 'Internal Server Error' }))

    const { result } = renderHook(() => usePokemonPage(0, 20), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.error).toMatchObject({ status: 500 })
  })
})

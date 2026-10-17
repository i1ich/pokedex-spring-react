import { ApiError } from './client'
import { createQueryClient, shouldRetry } from './queryClient'

const error = (status: number) => new ApiError({ status, title: 't', detail: null, errors: [] })

describe('shouldRetry', () => {
  it('does not retry client errors', () => {
    for (const status of [400, 401, 404, 409]) {
      expect(shouldRetry(0, error(status))).toBe(false)
    }
  })

  it('retries server and network errors, at most twice', () => {
    expect(shouldRetry(0, error(502))).toBe(true)
    expect(shouldRetry(1, error(0))).toBe(true)
    expect(shouldRetry(2, error(502))).toBe(false)
  })
})

describe('createQueryClient', () => {
  it('caches catalogue data for five minutes and ignores window focus refetches', () => {
    const defaults = createQueryClient().getDefaultOptions().queries
    expect(defaults?.staleTime).toBe(5 * 60 * 1000)
    expect(defaults?.refetchOnWindowFocus).toBe(false)
    expect(defaults?.retry).toBe(shouldRetry)
  })
})

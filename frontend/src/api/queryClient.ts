import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

const MAX_RETRIES = 2

/** Client errors (4xx) will not get better by asking again; server and network failures might. */
export function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < MAX_RETRIES
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 5 * 60 * 1000, // catalogue data barely changes
        retry: shouldRetry,
        refetchOnWindowFocus: false,
      },
    },
  })
}

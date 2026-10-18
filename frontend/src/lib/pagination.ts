export const PAGE_SIZE = 20
export const MAX_PAGE = 100_000
/** Same limit as the backend's `q` parameter. */
export const MAX_QUERY_LENGTH = 50

/** The `?page=&q=` query string of a catalogue page; `q` is left out when there is no search. */
export function browseSearch(page: number, query: string = ''): string {
  const params = new URLSearchParams({ page: String(page) })
  if (query) params.set('q', query)
  return `?${params.toString()}`
}

/** Reads the `?q=` query value: trimmed and cut to the length the API accepts. */
export function parseQueryParam(raw: string | null): string {
  return (raw ?? '').trim().slice(0, MAX_QUERY_LENGTH)
}

/**
 * Reads the `?page=` query value. It is 1-based for humans (`?page=1` is the first page, API page 0).
 * Anything that is not a plain positive integer falls back to page 1.
 */
export function parsePageParam(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 1
  const page = Number(raw)
  return page >= 1 && page <= MAX_PAGE ? page : 1
}

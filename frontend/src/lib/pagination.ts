export const PAGE_SIZE = 20
export const MAX_PAGE = 100_000

/**
 * Reads the `?page=` query value. It is 1-based for humans (`?page=1` is the first page, API page 0).
 * Anything that is not a plain positive integer falls back to page 1.
 */
export function parsePageParam(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 1
  const page = Number(raw)
  return page >= 1 && page <= MAX_PAGE ? page : 1
}

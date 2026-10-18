/** All TanStack Query keys in one place, so mutations can invalidate by prefix. */
export const queryKeys = {
  pokemon: {
    all: ['pokemon'] as const,
    page: (page: number, size: number, q: string = '') =>
      ['pokemon', 'page', { page, size, q }] as const,
    detail: (idOrName: string) => ['pokemon', 'detail', idOrName.trim().toLowerCase()] as const,
  },
  localPokemon: {
    all: ['local-pokemon'] as const,
    /** Prefix of every `page(...)` key, for updating all cached list pages at once. */
    pages: ['local-pokemon', 'page'] as const,
    page: (page: number, size: number) => ['local-pokemon', 'page', { page, size }] as const,
    detail: (id: number) => ['local-pokemon', 'detail', id] as const,
  },
}

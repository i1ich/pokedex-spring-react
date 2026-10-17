import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchLocalPokemon, fetchLocalPokemonPage } from '../api/localPokemon'
import { queryKeys } from '../api/queryKeys'
import { PAGE_SIZE } from '../lib/pagination'

/** One page of the signed-in user's collection. `page` is 0-based (API terms). */
export function useLocalPokemonPage(page: number, size: number = PAGE_SIZE) {
  return useQuery({
    queryKey: queryKeys.localPokemon.page(page, size),
    queryFn: ({ signal }) => fetchLocalPokemonPage(page, size, signal),
    placeholderData: keepPreviousData,
  })
}

/**
 * One stored Pokémon, for the edit form. Always fetched fresh when the page opens: editing from a
 * cached copy would send an old `version` and fail with a needless 409. A `null` id (a malformed URL)
 * makes no request.
 */
export function useLocalPokemon(id: number | null) {
  return useQuery({
    queryKey: queryKeys.localPokemon.detail(id ?? 0),
    queryFn: ({ signal }) => fetchLocalPokemon(id!, signal),
    enabled: id !== null,
    staleTime: 0,
    refetchOnMount: 'always',
  })
}

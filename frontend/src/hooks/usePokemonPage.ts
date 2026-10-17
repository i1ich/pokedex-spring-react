import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchPokemonPage } from '../api/pokemon'
import { queryKeys } from '../api/queryKeys'
import { PAGE_SIZE } from '../lib/pagination'

/** One page of the public catalogue. `page` is 0-based (API terms). */
export function usePokemonPage(page: number, size: number = PAGE_SIZE) {
  return useQuery({
    queryKey: queryKeys.pokemon.page(page, size),
    queryFn: ({ signal }) => fetchPokemonPage(page, size, signal),
    placeholderData: keepPreviousData,
  })
}

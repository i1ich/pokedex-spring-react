import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchPokemonPage } from '../api/pokemon'
import { queryKeys } from '../api/queryKeys'
import { PAGE_SIZE } from '../lib/pagination'

/** One page of the public catalogue, optionally only names containing `q`. `page` is 0-based. */
export function usePokemonPage(page: number, size: number = PAGE_SIZE, q: string = '') {
  return useQuery({
    queryKey: queryKeys.pokemon.page(page, size, q),
    queryFn: ({ signal }) => fetchPokemonPage(page, size, signal, q),
    placeholderData: keepPreviousData,
  })
}

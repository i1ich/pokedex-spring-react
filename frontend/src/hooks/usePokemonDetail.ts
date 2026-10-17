import { useQuery } from '@tanstack/react-query'
import { fetchPokemonDetails } from '../api/pokemon'
import { queryKeys } from '../api/queryKeys'

/** One Pokémon from the public catalogue. The identifier is normalised to lowercase by the API layer. */
export function usePokemonDetail(idOrName: string) {
  return useQuery({
    queryKey: queryKeys.pokemon.detail(idOrName),
    queryFn: ({ signal }) => fetchPokemonDetails(idOrName, signal),
  })
}

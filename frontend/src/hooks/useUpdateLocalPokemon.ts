import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateLocalPokemon } from '../api/localPokemon'
import { queryKeys } from '../api/queryKeys'
import type { UpdateLocalPokemonRequest } from '../api/types'

/** Saves the proprietary fields of one stored Pokémon, then invalidates the collection queries. */
export function useUpdateLocalPokemon(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateLocalPokemonRequest) => updateLocalPokemon(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.localPokemon.all }),
  })
}

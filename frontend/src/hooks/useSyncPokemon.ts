import { useMutation, useQueryClient } from '@tanstack/react-query'
import { syncPokemon } from '../api/localPokemon'
import { queryKeys } from '../api/queryKeys'

/** Adds a Pokémon to the user's collection (or refreshes it) and invalidates the collection queries. */
export function useSyncPokemon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: syncPokemon,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.localPokemon.all }),
  })
}

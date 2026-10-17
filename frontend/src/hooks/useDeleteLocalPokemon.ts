import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { deleteLocalPokemon } from '../api/localPokemon'
import { queryKeys } from '../api/queryKeys'
import type { LocalPokemon, Page } from '../api/types'

/**
 * Optimistic delete: the entry leaves every cached list page at once and comes back if the server
 * refuses. A 404 means someone else already deleted it, which is the outcome the user wanted, so the
 * entry stays gone. Either way the lists are refetched when the request has settled.
 */
export function useDeleteLocalPokemon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteLocalPokemon(id),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.localPokemon.all })
      const snapshot = queryClient.getQueriesData<Page<LocalPokemon>>({
        queryKey: queryKeys.localPokemon.pages,
      })
      queryClient.setQueriesData<Page<LocalPokemon>>(
        { queryKey: queryKeys.localPokemon.pages },
        (page) => {
          if (!page || !page.items.some((item) => item.id === id)) return page
          return {
            ...page,
            items: page.items.filter((item) => item.id !== id),
            totalElements: Math.max(0, page.totalElements - 1),
          }
        },
      )
      return { snapshot }
    },
    onError: (error, _id, context) => {
      if (error instanceof ApiError && error.status === 404) return
      context?.snapshot.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.localPokemon.all }),
  })
}

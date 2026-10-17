import { apiRequest } from './client'
import type { LocalPokemon, Page } from './types'

/** Creates (201) or refreshes (200) the local copy of a Pokémon from PokeAPI. */
export function syncPokemon(idOrName: string) {
  return apiRequest<LocalPokemon>('/api/v1/local-pokemon/sync', {
    method: 'POST',
    body: { idOrName: idOrName.trim().toLowerCase() },
    auth: true,
  })
}

export function fetchLocalPokemonPage(page: number, size: number, signal?: AbortSignal) {
  return apiRequest<Page<LocalPokemon>>('/api/v1/local-pokemon', {
    query: { page, size },
    auth: true,
    signal,
  })
}

export function fetchLocalPokemon(id: number, signal?: AbortSignal) {
  return apiRequest<LocalPokemon>(`/api/v1/local-pokemon/${id}`, { auth: true, signal })
}

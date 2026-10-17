import { apiRequest, apiRequestWithStatus } from './client'
import type { LocalPokemon, Page, UpdateLocalPokemonRequest } from './types'

export interface SyncResult {
  pokemon: LocalPokemon
  /** true when the Pokémon was added (201), false when an existing copy was refreshed (200). */
  created: boolean
}

/** Creates (201) or refreshes (200) the local copy of a Pokémon from PokeAPI. */
export async function syncPokemon(idOrName: string): Promise<SyncResult> {
  const { data, status } = await apiRequestWithStatus<LocalPokemon>('/api/v1/local-pokemon/sync', {
    method: 'POST',
    body: { idOrName: idOrName.trim().toLowerCase() },
    auth: true,
  })
  return { pokemon: data, created: status === 201 }
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

/** Sends exactly `{localName, region, notes, tags, version}`: the backend rejects any other property. */
export function updateLocalPokemon(id: number, request: UpdateLocalPokemonRequest) {
  return apiRequest<LocalPokemon>(`/api/v1/local-pokemon/${id}`, {
    method: 'PUT',
    body: request,
    auth: true,
  })
}

export function deleteLocalPokemon(id: number) {
  return apiRequest<void>(`/api/v1/local-pokemon/${id}`, { method: 'DELETE', auth: true })
}

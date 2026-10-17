import { apiRequest } from './client'
import type { Page, PokemonDetails, PokemonSummary } from './types'

/** Public catalogue. `page` is 0-based, as in the API. */
export function fetchPokemonPage(page: number, size: number, signal?: AbortSignal) {
  return apiRequest<Page<PokemonSummary>>('/api/v1/pokemon', { query: { page, size }, signal })
}

/** The backend only accepts lowercase `[a-z0-9-]` identifiers, so user input is normalised here. */
export function fetchPokemonDetails(idOrName: string, signal?: AbortSignal) {
  const id = encodeURIComponent(idOrName.trim().toLowerCase())
  return apiRequest<PokemonDetails>(`/api/v1/pokemon/${id}`, { signal })
}

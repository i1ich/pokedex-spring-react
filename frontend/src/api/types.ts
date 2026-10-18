/** DTOs of the backend REST API (docs/PLAN.md §3). Dates are ISO-8601 strings. */

export interface Page<T> {
  items: T[]
  /** 0-based page index, as in the API. */
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PokemonSummary {
  id: number
  name: string
  spriteUrl: string | null
  category: string | null
  weightKg: number
  abilities: string[]
}

export interface PokemonStat {
  name: string
  baseStat: number
}

export interface EvolutionNode {
  id: number
  name: string
  minLevel: number | null
  trigger: string | null
  evolvesTo: EvolutionNode[]
}

export interface PokemonDetails {
  id: number
  name: string
  imageUrl: string | null
  heightM: number
  weightKg: number
  types: string[]
  stats: PokemonStat[]
  description: string | null
  evolutionChain: EvolutionNode
  /** Species this form belongs to, e.g. "pikachu" for "pikachu-gmax". */
  speciesName: string
}

export interface LocalPokemon {
  id: number
  name: string
  category: string | null
  weightKg: number
  heightM: number
  spriteUrl: string | null
  artworkUrl: string | null
  abilities: string[]
  localName: string | null
  region: string | null
  notes: string | null
  tags: string[]
  syncedAt: string
  updatedAt: string
  /** Optimistic-locking version: send it back unchanged when updating. */
  version: number
}

export interface SyncPokemonRequest {
  idOrName: string
}

export interface UpdateLocalPokemonRequest {
  localName: string | null
  region: string | null
  notes: string | null
  tags: string[]
  version: number
}

export interface Credentials {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  username: string
}

export interface User {
  username: string
  role: string
}

/** One entry of the `errors` array of a 400 problem+json response. */
export interface FieldError {
  field: string
  message: string
}

/** RFC 7807 body as sent by the backend. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: FieldError[]
}

import type { UpdateLocalPokemonRequest } from '../api/types'

export function normalizeUsername(raw: string): string {
  throw new Error(`not implemented: ${raw}`)
}

export function validateUsername(raw: string): string | null {
  throw new Error(`not implemented: ${raw}`)
}

export function validatePassword(raw: string): string | null {
  throw new Error(`not implemented: ${raw}`)
}

export function normalizeTag(raw: string): string {
  throw new Error(`not implemented: ${raw}`)
}

export interface LocalPokemonFormValues {
  localName: string
  region: string
  notes: string
  tags: string[]
}

export type LocalPokemonFormField = 'localName' | 'region' | 'notes' | 'tags'
export type LocalPokemonFormErrors = Partial<Record<LocalPokemonFormField, string>>

export function validateLocalPokemonForm(values: LocalPokemonFormValues): LocalPokemonFormErrors {
  throw new Error(`not implemented: ${JSON.stringify(values)}`)
}

export function addTag(tags: string[], raw: string): { tags: string[]; error: string | null } {
  throw new Error(`not implemented: ${tags.length} ${raw}`)
}

export function toUpdateRequest(
  values: LocalPokemonFormValues,
  version: number,
): UpdateLocalPokemonRequest {
  throw new Error(`not implemented: ${JSON.stringify(values)} ${version}`)
}

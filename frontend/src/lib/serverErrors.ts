import { ApiError } from '../api/client'

/**
 * Picks the field errors of a 400 problem+json answer that belong to a form. The backend names
 * the offending property (`localName`, `tags[2]`); an index suffix is dropped so `tags[2]` lands on
 * the `tags` field. When one field has several messages the first wins.
 */
export function serverFieldErrors<K extends string>(
  error: unknown,
  fields: readonly K[],
): Partial<Record<K, string>> {
  const result: Partial<Record<K, string>> = {}
  if (!(error instanceof ApiError)) return result
  for (const { field, message } of error.errors) {
    const base = field.replace(/\[\d+\]$/, '')
    const known = fields.find((candidate) => candidate === base)
    if (known !== undefined && result[known] === undefined) result[known] = message
  }
  return result
}

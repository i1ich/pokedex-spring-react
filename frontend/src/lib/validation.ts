import type { UpdateLocalPokemonRequest } from '../api/types'

/**
 * Client-side mirror of the backend rules (docs/PLAN.md, UpdateLocalPokemonRequest, User). They give
 * fast feedback only; the server stays the authority and its answers are shown as well.
 */

const USERNAME_MIN = 3
const USERNAME_MAX = 50
const USERNAME_PATTERN = /^[a-z0-9_.-]+$/
const PASSWORD_MIN = 8
const PASSWORD_MAX_BYTES = 72 // BCrypt ignores everything after 72 bytes, so the backend refuses it
const NAME_MAX = 100
const NOTES_MAX = 1000
const TAG_MAX = 50
const TAGS_MAX = 10
const TAG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/
const TAG_RULE = 'Tags may only contain lowercase letters, digits and single hyphens between them.'

/** The backend trims and lowercases usernames; doing it here shows the user what will be stored. */
export function normalizeUsername(raw: string): string {
  return raw.trim().toLowerCase()
}

export function validateUsername(raw: string): string | null {
  const username = normalizeUsername(raw)
  if (username === '') return 'Username is required.'
  if (username.length < USERNAME_MIN) return `Username must be at least ${USERNAME_MIN} characters.`
  if (username.length > USERNAME_MAX) return `Username must be at most ${USERNAME_MAX} characters.`
  if (!USERNAME_PATTERN.test(username)) {
    return 'Username may only contain letters, digits, dots, underscores and hyphens.'
  }
  return null
}

/** Passwords are never trimmed. */
export function validatePassword(raw: string): string | null {
  if (raw === '') return 'Password is required.'
  if (raw.length < PASSWORD_MIN) return `Password must be at least ${PASSWORD_MIN} characters.`
  if (new TextEncoder().encode(raw).length > PASSWORD_MAX_BYTES) {
    return 'Password is too long. Please choose a shorter one.'
  }
  return null
}

export function normalizeTag(raw: string): string {
  return raw.trim().toLowerCase()
}

export interface LocalPokemonFormValues {
  localName: string
  region: string
  notes: string
  tags: string[]
}

export type LocalPokemonFormField = 'localName' | 'region' | 'notes' | 'tags'
export type LocalPokemonFormErrors = Partial<Record<LocalPokemonFormField, string>>

function tagError(tag: string): string | null {
  if (!TAG_PATTERN.test(tag)) return `"${tag}" is not a valid tag. ${TAG_RULE}`
  if (tag.length > TAG_MAX) return `Tags must be at most ${TAG_MAX} characters.`
  return null
}

export function validateLocalPokemonForm(values: LocalPokemonFormValues): LocalPokemonFormErrors {
  const errors: LocalPokemonFormErrors = {}
  const localName = values.localName.trim()
  if (values.localName !== '' && localName === '') {
    errors.localName = 'Local name must not be blank. Leave it empty to clear it.'
  } else if (localName.length > NAME_MAX) {
    errors.localName = `Local name must be at most ${NAME_MAX} characters.`
  }
  if (values.region.trim().length > NAME_MAX) {
    errors.region = `Region must be at most ${NAME_MAX} characters.`
  }
  if (values.notes.trim().length > NOTES_MAX) {
    errors.notes = `Notes must be at most ${NOTES_MAX} characters.`
  }
  const distinct = new Set(values.tags.map(normalizeTag))
  if (distinct.size > TAGS_MAX) {
    errors.tags = `At most ${TAGS_MAX} tags are allowed.`
  } else {
    for (const tag of distinct) {
      const error = tagError(tag)
      if (error) {
        errors.tags = error
        break
      }
    }
  }
  return errors
}

/**
 * Adds a draft to the chip list: normalised, duplicates collapse silently, an empty draft is ignored.
 * On a rule violation the list is returned unchanged with a message.
 */
export function addTag(tags: string[], raw: string): { tags: string[]; error: string | null } {
  const tag = normalizeTag(raw)
  if (tag === '' || tags.includes(tag)) return { tags, error: null }
  const error = tagError(tag)
  if (error) return { tags, error }
  if (tags.length >= TAGS_MAX) return { tags, error: `At most ${TAGS_MAX} tags are allowed.` }
  return { tags: [...tags, tag], error: null }
}

/** Builds the exact PUT body: trimmed text, blank region/notes/localName as null. */
export function toUpdateRequest(
  values: LocalPokemonFormValues,
  version: number,
): UpdateLocalPokemonRequest {
  const orNull = (text: string) => text.trim() || null
  return {
    localName: orNull(values.localName),
    region: orNull(values.region),
    notes: orNull(values.notes),
    tags: values.tags,
    version,
  }
}

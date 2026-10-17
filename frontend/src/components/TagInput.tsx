import type { KeyboardEvent } from 'react'
import { addTag } from '../lib/validation'
import { Field } from './Field'

interface TagInputProps {
  id: string
  label: string
  hint?: string
  tags: string[]
  onTagsChange: (tags: string[]) => void
  /** Text typed but not yet turned into a chip; owned by the form so Save can include it. */
  draft: string
  onDraftChange: (draft: string) => void
  /** Message to show under the field (a rejected draft, a server or form-level error). */
  error?: string | null
  /** Called with a message when a draft is rejected, and with null when the user types on. */
  onDraftError: (message: string | null) => void
}

/** Chip input: Enter or comma adds, Backspace on an empty input removes the last, each chip has a remove button. */
export function TagInput({
  id,
  label,
  hint,
  tags,
  onTagsChange,
  draft,
  onDraftChange,
  error,
  onDraftError,
}: TagInputProps) {
  /** Adds the parts in order; stops at the first rejected one and leaves it (and the rest) as draft. */
  function commit(parts: string[], leftover = '') {
    let next = tags
    for (let index = 0; index < parts.length; index += 1) {
      const result = addTag(next, parts[index])
      if (result.error) {
        onTagsChange(next)
        onDraftChange([...parts.slice(index), ...(leftover === '' ? [] : [leftover])].join(','))
        onDraftError(result.error)
        return
      }
      next = result.tags
    }
    onTagsChange(next)
    onDraftChange(leftover)
    onDraftError(null)
  }

  function handleChange(value: string) {
    onDraftError(null)
    if (!value.includes(',')) {
      onDraftChange(value)
      return
    }
    const parts = value.split(',')
    const leftover = parts.pop() ?? ''
    commit(parts, leftover)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === ',' || (event.key === 'Enter' && draft.trim() !== '')) {
      event.preventDefault()
      commit([draft])
    } else if (event.key === 'Backspace' && draft === '' && tags.length > 0) {
      onTagsChange(tags.slice(0, -1))
    }
  }

  return (
    <Field id={id} label={label} hint={hint} error={error}>
      {(control) => (
        <div className="tag-input">
          {tags.length > 0 && (
            <ul className="chips chips--start" aria-label="Current tags">
              {tags.map((tag) => (
                <li key={tag} className="chip chip--removable">
                  {tag}
                  <button
                    type="button"
                    className="chip__remove"
                    aria-label={`Remove tag ${tag}`}
                    onClick={() => onTagsChange(tags.filter((candidate) => candidate !== tag))}
                  >
                    <span aria-hidden="true">×</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <input
            {...control}
            className="input"
            type="text"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            value={draft}
            onChange={(event) => handleChange(event.target.value)}
            onKeyDown={handleKeyDown}
          />
        </div>
      )}
    </Field>
  )
}

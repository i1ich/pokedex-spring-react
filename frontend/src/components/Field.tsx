import type { ReactNode } from 'react'

export interface FieldControlProps {
  id: string
  'aria-invalid': true | undefined
  'aria-describedby': string | undefined
}

interface FieldProps {
  id: string
  label: string
  hint?: string
  error?: string | null
  /** Renders the actual control; spread the props on it to get the label, hint and error wiring. */
  children: (control: FieldControlProps) => ReactNode
}

/** Label, optional hint and error message around one control, linked with `for` and `aria-describedby`. */
export function Field({ id, label, hint, error, children }: FieldProps) {
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      {children({
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': describedBy || undefined,
      })}
      {hint && (
        <p className="field__hint" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field__error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  )
}

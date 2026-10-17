import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import type { Credentials } from '../api/types'
import { useFocusFirstInvalid } from '../hooks/useFocusFirstInvalid'
import { normalizeUsername } from '../lib/validation'
import { serverFieldErrors } from '../lib/serverErrors'
import { ErrorBanner } from './ErrorBanner'
import { Field } from './Field'

type AuthField = 'username' | 'password'
type FieldMessages = Partial<Record<AuthField, string>>

interface AuthFormProps {
  heading: string
  submitLabel: string
  pendingLabel: string
  usernameHint?: string
  passwordHint?: string
  passwordAutoComplete: 'current-password' | 'new-password'
  validateUsername: (raw: string) => string | null
  validatePassword: (raw: string) => string | null
  /** Performs the API call; rejects with an `ApiError`. Navigation after success is the page's job. */
  submit: (credentials: Credentials) => Promise<void>
  /** Lets a page turn a server answer (e.g. 409) into a message on a field instead of a banner. */
  mapServerError?: (error: ApiError) => FieldMessages | null
  footer: ReactNode
}

/** Username and password form shared by the login and register pages. */
export function AuthForm({
  heading,
  submitLabel,
  pendingLabel,
  usernameHint,
  passwordHint,
  passwordAutoComplete,
  validateUsername,
  validatePassword,
  submit,
  mapServerError,
  footer,
}: AuthFormProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [pending, setPending] = useState(false)
  const [serverFields, setServerFields] = useState<FieldMessages>({})
  const [serverError, setServerError] = useState<unknown>(null)
  const [attempt, setAttempt] = useState(0)
  const formRef = useFocusFirstInvalid(attempt)

  // Once the user has tried to submit, the rules are re-checked on every keystroke.
  const usernameError =
    serverFields.username ?? (submitted ? validateUsername(username) : null) ?? null
  const passwordError =
    serverFields.password ?? (submitted ? validatePassword(password) : null) ?? null

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setSubmitted(true)
    setServerError(null)
    setServerFields({})
    if (validateUsername(username) !== null || validatePassword(password) !== null) {
      setAttempt((n) => n + 1)
      return
    }
    setPending(true)
    try {
      await submit({ username: normalizeUsername(username), password })
    } catch (error) {
      const fields =
        (error instanceof ApiError ? mapServerError?.(error) : null) ??
        serverFieldErrors(error, ['username', 'password'] as const)
      if (fields.username || fields.password) {
        setServerFields(fields)
        setAttempt((n) => n + 1)
      } else {
        setServerError(error)
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="form-page">
      <h1>{heading}</h1>
      <form ref={formRef} className="form" onSubmit={handleSubmit} noValidate>
        {serverError !== null && <ErrorBanner error={serverError} />}
        <Field id="username" label="Username" hint={usernameHint} error={usernameError}>
          {(control) => (
            <input
              {...control}
              className="input"
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={username}
              onChange={(event) => {
                setUsername(event.target.value)
                setServerFields((current) => ({ ...current, username: undefined }))
              }}
            />
          )}
        </Field>
        <Field id="password" label="Password" hint={passwordHint} error={passwordError}>
          {(control) => (
            <input
              {...control}
              className="input"
              type="password"
              autoComplete={passwordAutoComplete}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value)
                setServerFields((current) => ({ ...current, password: undefined }))
              }}
            />
          )}
        </Field>
        <button type="submit" className="button" disabled={pending}>
          {pending ? pendingLabel : submitLabel}
        </button>
      </form>
      <p className="form-page__footer">{footer}</p>
    </section>
  )
}

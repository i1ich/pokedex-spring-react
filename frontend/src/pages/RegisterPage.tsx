import { Link, Navigate, useLocation } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { useAuth } from '../auth/useAuth'
import { getReturnPath } from '../lib/returnTo'
import { validatePassword, validateUsername } from '../lib/validation'

export function RegisterPage() {
  const { isAuthenticated, register } = useAuth()
  const location = useLocation()
  const returnTo = getReturnPath(location.state)

  if (isAuthenticated) return <Navigate to={returnTo} replace />

  return (
    <AuthForm
      heading="Register"
      submitLabel="Create account"
      pendingLabel="Creating account…"
      usernameHint="3–50 characters: letters, digits, dots, underscores or hyphens. Case is ignored."
      passwordHint="At least 8 characters, and at most 72 bytes."
      passwordAutoComplete="new-password"
      validateUsername={validateUsername}
      validatePassword={validatePassword}
      submit={register}
      mapServerError={(error) =>
        error.status === 409
          ? { username: 'That username is already taken. Try another one.' }
          : null
      }
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" state={location.state}>
            Log in
          </Link>
        </>
      }
    />
  )
}

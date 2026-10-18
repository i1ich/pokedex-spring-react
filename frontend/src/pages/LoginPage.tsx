import { Link, Navigate, useLocation } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { useAuth } from '../auth/useAuth'
import { getReturnPath } from '../lib/returnTo'

// Existing accounts must always be able to try: the format rules apply when registering only.
const required = (label: string) => (raw: string) =>
  raw.trim() === '' ? `${label} is required.` : null

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const location = useLocation()
  const returnTo = getReturnPath(location.state)

  if (isAuthenticated) return <Navigate to={returnTo} replace />

  return (
    <AuthForm
      heading="Log in"
      submitLabel="Log in"
      pendingLabel="Logging in…"
      passwordAutoComplete="current-password"
      validateUsername={required('Username')}
      validatePassword={(raw) => (raw === '' ? 'Password is required.' : null)}
      submit={login}
      footer={
        <>
          <span className="form-page__demo">Demo: demo / demo12345</span>
          <br />
          No account yet?{' '}
          <Link to="/register" state={location.state}>
            Register
          </Link>
        </>
      }
    />
  )
}

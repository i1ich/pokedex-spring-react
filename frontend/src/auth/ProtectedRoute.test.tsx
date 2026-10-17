import { screen } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { demoSession } from '../test/fixtures'
import { renderWithProviders } from '../test/helpers'
import { ProtectedRoute } from './ProtectedRoute'
import { SESSION_STORAGE_KEY } from './session'

function LoginStub() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string; search: string } } | null)?.from
  return (
    <p>
      login page, from {from?.pathname}
      {from?.search}
    </p>
  )
}

function renderAt(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginStub />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/collection" element={<p>my collection</p>} />
      </Route>
    </Routes>,
    { route },
  )
}

describe('ProtectedRoute', () => {
  it('redirects anonymous visitors to /login and remembers where they were going', () => {
    renderAt('/collection?page=2')

    expect(screen.getByText('login page, from /collection?page=2')).toBeInTheDocument()
    expect(screen.queryByText('my collection')).not.toBeInTheDocument()
  })

  it('renders the nested route for a signed-in user', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))

    renderAt('/collection')

    expect(screen.getByText('my collection')).toBeInTheDocument()
  })

  it('can also wrap children directly', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(demoSession()))

    renderWithProviders(
      <ProtectedRoute>
        <p>private child</p>
      </ProtectedRoute>,
    )

    expect(screen.getByText('private child')).toBeInTheDocument()
  })
})

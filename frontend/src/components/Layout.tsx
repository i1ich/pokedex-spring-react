import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

function PokeballIcon() {
  return (
    <svg className="logo__icon" viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
      <circle cx="16" cy="16" r="14" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <path d="M2 16a14 14 0 0 1 28 0z" fill="#e53935" stroke="currentColor" strokeWidth="2" />
      <circle cx="16" cy="16" r="4.5" fill="#fff" stroke="currentColor" strokeWidth="2" />
    </svg>
  )
}

export function Layout() {
  const { isAuthenticated, username, logout } = useAuth()

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="container site-header__inner">
          <Link to="/" className="logo">
            <PokeballIcon />
            <span>Pokédex</span>
          </Link>
          <nav aria-label="Main">
            <ul className="nav">
              <li>
                <NavLink to="/" end className="nav__link">
                  Browse
                </NavLink>
              </li>
              <li>
                <NavLink to="/collection" className="nav__link">
                  My collection
                </NavLink>
              </li>
              {isAuthenticated ? (
                <>
                  <li className="nav__user">{username}</li>
                  <li>
                    <button type="button" className="button button--ghost" onClick={logout}>
                      Log out
                    </button>
                  </li>
                </>
              ) : (
                <li>
                  <NavLink to="/login" className="nav__link">
                    Login
                  </NavLink>
                </li>
              )}
            </ul>
          </nav>
        </div>
      </header>
      <main id="main" className="container main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container">Data from PokeAPI.</div>
      </footer>
    </>
  )
}

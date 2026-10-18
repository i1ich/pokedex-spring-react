import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { PokeballIcon } from './PokeballIcon'

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
            <PokeballIcon className="logo__icon" />
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

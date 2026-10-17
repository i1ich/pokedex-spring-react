import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { BrowsePage } from './pages/BrowsePage'
import { CollectionPage } from './pages/CollectionPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PokemonDetailPage } from './pages/PokemonDetailPage'
import { RegisterPage } from './pages/RegisterPage'

/** The route table. Providers (query client, auth, router) are added by `App` or by tests. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<BrowsePage />} />
        <Route path="pokemon/:idOrName" element={<PokemonDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="collection" element={<CollectionPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

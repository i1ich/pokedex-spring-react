import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'

export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
}

interface WrapperOptions {
  /** A path, or an entry with router state (e.g. the `from` a protected route passes to /login). */
  route?: string | { pathname: string; state?: unknown }
  queryClient?: QueryClient
}

export function createWrapper({
  route = '/',
  queryClient = createTestQueryClient(),
}: WrapperOptions = {}) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    )
  }
}

export function renderWithProviders(ui: ReactElement, options: WrapperOptions = {}) {
  return render(ui, { wrapper: createWrapper(options) })
}

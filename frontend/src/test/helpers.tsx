import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'

export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
}

interface WrapperOptions {
  route?: string
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

import { apiRequest } from './client'
import type { Credentials, LoginResponse, User } from './types'

export function login(credentials: Credentials) {
  return apiRequest<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: credentials })
}

export function register(credentials: Credentials) {
  return apiRequest<User>('/api/v1/auth/register', { method: 'POST', body: credentials })
}

export function getCurrentUser() {
  return apiRequest<User>('/api/v1/auth/me', { auth: true })
}

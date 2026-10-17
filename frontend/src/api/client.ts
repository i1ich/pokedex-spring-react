import { clearSession, getAuthToken } from '../auth/session'
import type { FieldError, ProblemDetail } from './types'

const DEFAULT_BASE_URL = 'http://localhost:8080'

/**
 * `undefined` (variable not set) means the local default; an explicit empty string means same-origin,
 * which is what the Docker build uses behind the nginx proxy.
 */
export function resolveBaseUrl(raw: string | undefined): string {
  if (raw === undefined) return DEFAULT_BASE_URL
  return raw.replace(/\/+$/, '')
}

export type QueryParams = Record<string, string | number | boolean | undefined>

export function apiUrl(path: string, query?: QueryParams): string {
  const base = resolveBaseUrl(import.meta.env.VITE_API_URL)
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) params.set(key, String(value))
  }
  const queryString = params.toString()
  return `${base}${path}${queryString ? `?${queryString}` : ''}`
}

interface ApiErrorInit {
  status: number
  title: string
  detail: string | null
  errors: FieldError[]
}

/** Failure of an API call: a problem+json answer, an unexpected response, or an unreachable server. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string | null
  readonly errors: FieldError[]

  constructor({ status, title, detail, errors }: ApiErrorInit) {
    super(detail ? `${title}: ${detail}` : title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.errors = errors
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /**
   * Attach the JWT. Only for endpoints that need it: the backend answers 401 to an expired token even
   * on public routes. A 401 to such a request also signs the user out.
   */
  auth?: boolean
  query?: QueryParams
  signal?: AbortSignal
}

function unexpectedResponse(status: number): ApiError {
  return new ApiError({
    status,
    title: 'Unexpected server response',
    detail: `The server answered with HTTP ${status} and a body that is not a problem description.`,
    errors: [],
  })
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    try {
      const problem = (await response.json()) as ProblemDetail | null
      if (typeof problem === 'object' && problem !== null) {
        return new ApiError({
          status: response.status,
          title: problem.title || `Request failed (${response.status})`,
          detail: problem.detail ?? null,
          errors: Array.isArray(problem.errors) ? problem.errors : [],
        })
      }
    } catch {
      // not valid JSON after all: fall through to the generic error
    }
  }
  return unexpectedResponse(response.status)
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false, query, signal } = options
  const headers = new Headers({ Accept: 'application/json, application/problem+json' })
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (auth) {
    const token = getAuthToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(apiUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause
    throw new ApiError({
      status: 0,
      title: 'Network error',
      detail: 'Could not reach the server. Check your connection and try again.',
      errors: [],
    })
  }

  if (!response.ok) {
    if (response.status === 401 && auth) clearSession()
    throw await toApiError(response)
  }
  if (response.status === 204) return undefined as T
  try {
    return (await response.json()) as T
  } catch {
    throw unexpectedResponse(response.status)
  }
}

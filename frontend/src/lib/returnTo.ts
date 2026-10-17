export const DEFAULT_RETURN_PATH = '/collection'

const AUTH_PATHS = new Set(['/login', '/register'])

function text(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

/**
 * Where to go after signing in. Protected routes and the detail page pass the page the visitor wanted
 * in `location.state.from`; anything that is not a path inside this app (or is a login page itself)
 * falls back to the collection, so the state can never be used as an open redirect.
 */
export function getReturnPath(state: unknown): string {
  if (typeof state !== 'object' || state === null) return DEFAULT_RETURN_PATH
  const from = (state as Record<string, unknown>).from
  if (typeof from !== 'object' || from === null) return DEFAULT_RETURN_PATH
  const { pathname, search, hash } = from as Record<string, unknown>
  if (typeof pathname !== 'string') return DEFAULT_RETURN_PATH
  const insideApp = pathname === '/' || /^\/[^/\\]/.test(pathname)
  if (!insideApp || AUTH_PATHS.has(pathname)) return DEFAULT_RETURN_PATH
  return `${pathname}${text(search)}${text(hash)}`
}

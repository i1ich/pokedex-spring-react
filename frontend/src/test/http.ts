import type { Mock } from 'vitest'

export type FetchHandler = (url: URL, init: RequestInit) => Response | Promise<Response>

/** Replaces the global `fetch` with a mock; the handler receives the parsed URL. */
export function stubFetch(handler: FetchHandler): Mock<typeof fetch> {
  const mock = vi.fn<typeof fetch>((input, init) =>
    Promise.resolve(handler(new URL(String(input), 'http://localhost'), init ?? {})),
  )
  vi.stubGlobal('fetch', mock)
  return mock
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function problem(status: number, body: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

export function headersOf(mock: Mock<typeof fetch>, call = 0): Headers {
  return new Headers(mock.mock.calls[call][1]?.headers)
}

export function urlOf(mock: Mock<typeof fetch>, call = 0): URL {
  return new URL(String(mock.mock.calls[call][0]), 'http://localhost')
}

export function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

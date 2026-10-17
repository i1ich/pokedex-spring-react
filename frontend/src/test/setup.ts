import '@testing-library/jest-dom/vitest'

// jsdom does not implement scrolling; the Browse page scrolls to the top on page changes.
window.scrollTo = vi.fn()

let errorSpy: ReturnType<typeof vi.spyOn>
let warnSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  sessionStorage.clear()
  vi.mocked(window.scrollTo).mockClear()
  errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
})

// "Zero warnings in the browser console": React key warnings, act() warnings, router warnings and
// unhandled request errors all go through console.error/warn, so any of them fails the test.
afterEach(() => {
  const output = [...errorSpy.mock.calls, ...warnSpy.mock.calls]
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
  sessionStorage.clear()
  if (output.length > 0) {
    throw new Error(
      `Unexpected console output during test:\n${output.map((c) => c.join(' ')).join('\n')}`,
    )
  }
})

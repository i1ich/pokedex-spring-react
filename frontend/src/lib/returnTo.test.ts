import { DEFAULT_RETURN_PATH, getReturnPath } from './returnTo'

const from = (pathname: string, search = '', hash = '') => ({ from: { pathname, search, hash } })

describe('getReturnPath', () => {
  it('defaults to the collection', () => {
    expect(DEFAULT_RETURN_PATH).toBe('/collection')
    expect(getReturnPath(null)).toBe('/collection')
    expect(getReturnPath(undefined)).toBe('/collection')
    expect(getReturnPath({})).toBe('/collection')
    expect(getReturnPath('/pokemon/pikachu')).toBe('/collection')
  })

  it('returns path, query and hash of the page the user came from', () => {
    expect(getReturnPath(from('/pokemon/pikachu'))).toBe('/pokemon/pikachu')
    expect(getReturnPath(from('/collection', '?page=2', '#x'))).toBe('/collection?page=2#x')
  })

  it.each([
    '//evil.example',
    'https://evil.example/',
    'javascript:alert(1)',
    '',
    'pokemon/pikachu',
  ])('never leaves the app: ignores %j', (pathname) => {
    expect(getReturnPath(from(pathname))).toBe('/collection')
  })

  it.each(['/login', '/register'])('does not bounce back to %s', (pathname) => {
    expect(getReturnPath(from(pathname))).toBe('/collection')
  })

  it('tolerates a malformed from', () => {
    expect(getReturnPath({ from: { pathname: 42 } })).toBe('/collection')
    expect(getReturnPath({ from: null })).toBe('/collection')
  })
})

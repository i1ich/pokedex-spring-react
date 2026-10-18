import { MAX_QUERY_LENGTH, browseSearch, parseQueryParam } from './pagination'

describe('browseSearch', () => {
  it('builds the page query string', () => {
    expect(browseSearch(3)).toBe('?page=3')
  })

  it('adds the name search and encodes it', () => {
    expect(browseSearch(1, 'mr mime')).toBe('?page=1&q=mr+mime')
  })
})

describe('parseQueryParam', () => {
  it('treats a missing value as no search', () => {
    expect(parseQueryParam(null)).toBe('')
  })

  it('trims the text', () => {
    expect(parseQueryParam('  char ')).toBe('char')
  })

  it('cuts the text to the length the API accepts', () => {
    expect(parseQueryParam('a'.repeat(MAX_QUERY_LENGTH + 10))).toHaveLength(MAX_QUERY_LENGTH)
  })
})

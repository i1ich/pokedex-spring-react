import { formatName, formatPokemonId, formatWeight } from './format'
import { MAX_PAGE, parsePageParam } from './pagination'

describe('formatPokemonId', () => {
  it.each([
    [1, '#001'],
    [25, '#025'],
    [151, '#151'],
    [1025, '#1025'],
  ])('formats %i as %s', (id, expected) => {
    expect(formatPokemonId(id)).toBe(expected)
  })
})

describe('formatName', () => {
  it.each([
    ['pikachu', 'Pikachu'],
    ['mr-mime', 'Mr Mime'],
    ['lightning-rod', 'Lightning Rod'],
  ])('formats %s as %s', (raw, expected) => {
    expect(formatName(raw)).toBe(expected)
  })
})

describe('formatWeight', () => {
  it.each([
    [6, '6 kg'],
    [6.9, '6.9 kg'],
    [54.5, '54.5 kg'],
    [0.1, '0.1 kg'],
    [999.9, '999.9 kg'],
    [1000, '1,000 kg'],
  ])('formats %f as %s', (kg, expected) => {
    expect(formatWeight(kg)).toBe(expected)
  })
})

describe('parsePageParam (1-based, as shown to humans)', () => {
  it.each([
    ['1', 1],
    ['2', 2],
    ['37', 37],
  ])('accepts %s', (raw, expected) => {
    expect(parsePageParam(raw)).toBe(expected)
  })

  it.each([null, '', 'abc', '0', '-3', '1.5', '2e3', ' ', 'NaN', String(MAX_PAGE + 1)])(
    'falls back to page 1 for %j',
    (raw) => {
      expect(parsePageParam(raw)).toBe(1)
    },
  )
})

import {
  formatEvolutionStep,
  formatHeight,
  formatName,
  formatPokemonId,
  formatStatName,
  formatWeight,
} from './format'
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

describe('formatHeight', () => {
  it('shows metres with at most one decimal', () => {
    expect(formatHeight(0.4)).toBe('0.4 m')
    expect(formatHeight(1)).toBe('1 m')
    expect(formatHeight(1.25)).toBe('1.3 m')
  })
})

describe('formatStatName', () => {
  it.each([
    ['hp', 'HP'],
    ['attack', 'Attack'],
    ['special-attack', 'Special Attack'],
  ])('formats %s as %s', (name, expected) => {
    expect(formatStatName(name)).toBe(expected)
  })
})

describe('formatEvolutionStep', () => {
  it('prefers the minimum level', () => {
    expect(formatEvolutionStep({ minLevel: 16, trigger: 'level-up' })).toBe('Level 16')
  })

  it('falls back to the trigger', () => {
    expect(formatEvolutionStep({ minLevel: null, trigger: 'use-item' })).toBe('Use Item')
  })

  it('is null for the root of the chain', () => {
    expect(formatEvolutionStep({ minLevel: null, trigger: null })).toBeNull()
  })
})

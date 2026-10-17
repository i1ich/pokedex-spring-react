import {
  addTag,
  normalizeTag,
  normalizeUsername,
  toUpdateRequest,
  validateLocalPokemonForm,
  validatePassword,
  validateUsername,
} from './validation'

const valid = { localName: '', region: '', notes: '', tags: [] as string[] }

describe('username rules', () => {
  it('trims and lowercases like the backend', () => {
    expect(normalizeUsername('  Ash_Ketchum ')).toBe('ash_ketchum')
  })

  it.each(['abc', 'a.b-c_9', 'x'.repeat(50), '  Demo  '])('accepts %j', (value) => {
    expect(validateUsername(value)).toBeNull()
  })

  it('requires a value', () => {
    expect(validateUsername('   ')).toMatch(/required/i)
  })

  it('requires at least 3 characters', () => {
    expect(validateUsername('ab')).toMatch(/at least 3/i)
  })

  it('allows at most 50 characters', () => {
    expect(validateUsername('x'.repeat(51))).toMatch(/at most 50/i)
  })

  it.each(['ash ketchum', 'ash@home', 'ásh1', 'ash!'])('rejects the character set of %j', (v) => {
    expect(validateUsername(v)).toMatch(/letters, digits/i)
  })
})

describe('password rules', () => {
  it('requires a value', () => {
    expect(validatePassword('')).toMatch(/required/i)
  })

  it('requires at least 8 characters', () => {
    expect(validatePassword('1234567')).toMatch(/at least 8/i)
    expect(validatePassword('12345678')).toBeNull()
  })

  it('does not trim the password', () => {
    expect(validatePassword('       a')).toBeNull()
  })

  it('allows at most 72 UTF-8 bytes (BCrypt limit)', () => {
    expect(validatePassword('a'.repeat(72))).toBeNull()
    expect(validatePassword('a'.repeat(73))).toMatch(/72 bytes/i)
  })

  it('counts bytes, not characters', () => {
    expect(validatePassword('é'.repeat(36))).toBeNull() // 72 bytes
    expect(validatePassword('é'.repeat(37))).toMatch(/72 bytes/i) // 74 bytes
  })
})

describe('tag normalisation', () => {
  it('trims and lowercases', () => {
    expect(normalizeTag('  Fire-Type ')).toBe('fire-type')
  })
})

describe('validateLocalPokemonForm', () => {
  it('accepts an empty form and a full one', () => {
    expect(validateLocalPokemonForm(valid)).toEqual({})
    expect(
      validateLocalPokemonForm({
        localName: 'Sparky',
        region: 'Kanto',
        notes: 'n',
        tags: ['a', 'b-c'],
      }),
    ).toEqual({})
  })

  it('rejects a blank but non-empty local name', () => {
    expect(validateLocalPokemonForm({ ...valid, localName: '   ' }).localName).toMatch(/blank/i)
  })

  it('limits the local name to 100 characters after trimming', () => {
    expect(validateLocalPokemonForm({ ...valid, localName: ` ${'x'.repeat(100)} ` })).toEqual({})
    expect(validateLocalPokemonForm({ ...valid, localName: 'x'.repeat(101) }).localName).toMatch(
      /100/,
    )
  })

  it('limits region to 100 and notes to 1000 characters', () => {
    expect(validateLocalPokemonForm({ ...valid, region: 'x'.repeat(100) })).toEqual({})
    expect(validateLocalPokemonForm({ ...valid, region: 'x'.repeat(101) }).region).toMatch(/100/)
    expect(validateLocalPokemonForm({ ...valid, notes: 'x'.repeat(1000) })).toEqual({})
    expect(validateLocalPokemonForm({ ...valid, notes: 'x'.repeat(1001) }).notes).toMatch(/1000/)
  })

  it('allows blank region and notes (they become null)', () => {
    expect(validateLocalPokemonForm({ ...valid, region: '   ', notes: '  \n ' })).toEqual({})
  })

  it('rejects more than 10 tags', () => {
    const tags = Array.from({ length: 11 }, (_, i) => `t${i}`)
    expect(validateLocalPokemonForm({ ...valid, tags }).tags).toMatch(/at most 10/i)
  })

  it('rejects malformed tags', () => {
    expect(validateLocalPokemonForm({ ...valid, tags: ['ok', 'bad tag'] }).tags).toMatch(/bad tag/)
  })
})

describe('addTag', () => {
  it('adds a normalised tag', () => {
    expect(addTag(['a'], '  Fire ')).toEqual({ tags: ['a', 'fire'], error: null })
  })

  it('ignores an empty draft without an error', () => {
    expect(addTag(['a'], '   ')).toEqual({ tags: ['a'], error: null })
  })

  it('collapses duplicates silently', () => {
    expect(addTag(['fire'], 'FIRE')).toEqual({ tags: ['fire'], error: null })
  })

  it.each(['two words', 'under_score', '-lead', 'trail-', 'a--b', 'é'])('rejects %j', (raw) => {
    const result = addTag(['a'], raw)
    expect(result.tags).toEqual(['a'])
    expect(result.error).toMatch(/lowercase letters, digits and single hyphens/i)
  })

  it('rejects a tag longer than 50 characters', () => {
    expect(addTag([], 'x'.repeat(51)).error).toMatch(/at most 50/i)
    expect(addTag([], 'x'.repeat(50)).error).toBeNull()
  })

  it('rejects an eleventh distinct tag but still collapses a duplicate', () => {
    const ten = Array.from({ length: 10 }, (_, i) => `t${i}`)
    expect(addTag(ten, 'extra')).toEqual({ tags: ten, error: expect.stringMatching(/at most 10/i) })
    expect(addTag(ten, 't3')).toEqual({ tags: ten, error: null })
  })
})

describe('toUpdateRequest', () => {
  it('trims text, turns blanks into null and carries the version', () => {
    expect(
      toUpdateRequest({ localName: ' Sparky ', region: '  ', notes: '', tags: ['a'] }, 7),
    ).toEqual({ localName: 'Sparky', region: null, notes: null, tags: ['a'], version: 7 })
  })
})

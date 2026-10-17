import type { Page, PokemonSummary } from '../api/types'

export const pikachu: PokemonSummary = {
  id: 25,
  name: 'pikachu',
  spriteUrl: 'https://img.example/pikachu.png',
  category: 'Mouse Pokémon',
  weightKg: 6,
  abilities: ['static', 'lightning-rod'],
}

export const bulbasaur: PokemonSummary = {
  id: 1,
  name: 'bulbasaur',
  spriteUrl: 'https://img.example/bulbasaur.png',
  category: 'Seed Pokémon',
  weightKg: 6.9,
  abilities: ['overgrow', 'chlorophyll'],
}

export const mrMime: PokemonSummary = {
  id: 122,
  name: 'mr-mime',
  spriteUrl: 'https://img.example/mr-mime.png',
  category: 'Barrier Pokémon',
  weightKg: 54.5,
  abilities: ['soundproof'],
}

export function pageOf<T>(items: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: 60,
    totalPages: 3,
    ...overrides,
  }
}

export function futureIso(ms = 60 * 60 * 1000): string {
  return new Date(Date.now() + ms).toISOString()
}

export function pastIso(ms = 60 * 1000): string {
  return new Date(Date.now() - ms).toISOString()
}

export const demoSession = () => ({
  token: 'jwt-token-123',
  expiresAt: futureIso(),
  username: 'demo',
})

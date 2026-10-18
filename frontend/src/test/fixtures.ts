import type {
  EvolutionNode,
  LocalPokemon,
  Page,
  PokemonDetails,
  PokemonSummary,
} from '../api/types'

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

export function evolutionNode(
  id: number,
  name: string,
  evolvesTo: EvolutionNode[] = [],
  minLevel: number | null = null,
  trigger: string | null = null,
): EvolutionNode {
  return { id, name, minLevel, trigger, evolvesTo }
}

export const pikachuDetails: PokemonDetails = {
  id: 25,
  name: 'pikachu',
  imageUrl: 'https://img.example/pikachu-artwork.png',
  heightM: 0.4,
  weightKg: 6,
  types: ['electric'],
  stats: [
    { name: 'hp', baseStat: 35 },
    { name: 'attack', baseStat: 55 },
    { name: 'special-attack', baseStat: 50 },
  ],
  description:
    'When several of these Pokémon gather, their electricity could build and cause lightning storms.',
  evolutionChain: evolutionNode(172, 'pichu', [
    evolutionNode(
      25,
      'pikachu',
      [evolutionNode(26, 'raichu', [], null, 'use-item')],
      null,
      'level-up',
    ),
  ]),
  speciesName: 'pikachu',
}

/** Eevee branches into several evolutions: the chain is a tree, not a line. */
export const eeveeDetails: PokemonDetails = {
  id: 133,
  name: 'eevee',
  imageUrl: null,
  heightM: 0.3,
  weightKg: 6.5,
  types: ['normal'],
  stats: [{ name: 'hp', baseStat: 55 }],
  description: null,
  evolutionChain: evolutionNode(133, 'eevee', [
    evolutionNode(134, 'vaporeon', [], null, 'use-item'),
    evolutionNode(135, 'jolteon', [], null, 'use-item'),
    evolutionNode(136, 'flareon', [], 16, 'level-up'),
  ]),
  speciesName: 'eevee',
}

export function localPokemon(overrides: Partial<LocalPokemon> = {}): LocalPokemon {
  return {
    id: 25,
    name: 'pikachu',
    category: 'Mouse Pokémon',
    weightKg: 6,
    heightM: 0.4,
    spriteUrl: 'https://img.example/pikachu.png',
    artworkUrl: 'https://img.example/pikachu-artwork.png',
    abilities: ['static', 'lightning-rod'],
    localName: null,
    region: null,
    notes: null,
    tags: [],
    syncedAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    version: 0,
    ...overrides,
  }
}

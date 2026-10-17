export function formatPokemonId(id: number): string {
  return `#${String(id).padStart(3, '0')}`
}

/** `mr-mime` -> `Mr Mime`, `lightning-rod` -> `Lightning Rod`. */
export function formatName(name: string): string {
  return name
    .split('-')
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function formatWeight(kg: number): string {
  return `${kg.toLocaleString('en-US', { maximumFractionDigits: 1 })} kg`
}

export function formatHeight(m: number): string {
  throw new Error(`not implemented: ${m}`)
}

export function formatStatName(name: string): string {
  throw new Error(`not implemented: ${name}`)
}

export function formatEvolutionStep(step: {
  minLevel: number | null
  trigger: string | null
}): string | null {
  throw new Error(`not implemented: ${JSON.stringify(step)}`)
}

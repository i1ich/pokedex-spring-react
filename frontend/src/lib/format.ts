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
  return `${m.toLocaleString('en-US', { maximumFractionDigits: 1 })} m`
}

/** `hp` -> `HP`, `special-attack` -> `Special Attack`. */
export function formatStatName(name: string): string {
  return name === 'hp' ? 'HP' : formatName(name)
}

/** How an evolution happens: `Level 16`, or the trigger (`Use Item`); null for the first stage. */
export function formatEvolutionStep(step: {
  minLevel: number | null
  trigger: string | null
}): string | null {
  if (step.minLevel !== null) return `Level ${step.minLevel}`
  return step.trigger ? formatName(step.trigger) : null
}

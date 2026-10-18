import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { PokemonSummary } from '../api/types'
import { formatName, formatPokemonId, formatWeight } from '../lib/format'

const SPRITES = '/sprites/pokemon/'
const ARTWORK = '/sprites/pokemon/other/official-artwork/'

/** Some forms list a sprite URL that 404s upstream; the official artwork usually exists for them. */
function artworkFallback(spriteUrl: string): string | null {
  return spriteUrl.includes(SPRITES) && !spriteUrl.includes(ARTWORK)
    ? spriteUrl.replace(SPRITES, ARTWORK)
    : null
}

export function PokemonCard({ pokemon }: { pokemon: PokemonSummary }) {
  const name = formatName(pokemon.name)
  const location = useLocation()
  const [src, setSrc] = useState(pokemon.spriteUrl)
  const [failed, setFailed] = useState(false)

  function onImageError() {
    const fallback = src ? artworkFallback(src) : null
    if (fallback) {
      setSrc(fallback)
    } else {
      setFailed(true)
    }
  }

  return (
    <article className="card">
      <div className="card__media">
        {src && !failed ? (
          <img
            src={src}
            onError={onImageError}
            alt={`Sprite of ${name}`}
            width={96}
            height={96}
            loading="lazy"
          />
        ) : (
          <div className="card__no-image" role="img" aria-label={`No sprite available for ${name}`}>
            ?
          </div>
        )}
      </div>
      <p className="card__id">{formatPokemonId(pokemon.id)}</p>
      <h2 className="card__name">
        <Link
          className="card__link"
          to={`/pokemon/${encodeURIComponent(pokemon.name)}`}
          state={{ browseSearch: location.search }}
        >
          {name}
        </Link>
      </h2>
      {pokemon.category && <p className="card__category">{pokemon.category}</p>}
      <p className="card__weight">{formatWeight(pokemon.weightKg)}</p>
      <ul className="chips" aria-label="Abilities">
        {pokemon.abilities.map((ability) => (
          <li key={ability} className="chip">
            {formatName(ability)}
          </li>
        ))}
      </ul>
    </article>
  )
}

export function PokemonCardSkeleton() {
  return (
    <div className="card card--skeleton" data-testid="pokemon-card-skeleton" aria-hidden="true">
      <div className="skeleton skeleton--image" />
      <div className="skeleton skeleton--line skeleton--short" />
      <div className="skeleton skeleton--line" />
      <div className="skeleton skeleton--line skeleton--short" />
    </div>
  )
}

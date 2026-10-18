import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { PokemonSummary } from '../api/types'
import { formatName, formatPokemonId, formatWeight } from '../lib/format'

export function PokemonCard({ pokemon }: { pokemon: PokemonSummary }) {
  const name = formatName(pokemon.name)
  const location = useLocation()
  // Same picture as the detail page; some forms have a broken artwork or sprite upstream, so each
  // failed load moves on to the next candidate and the placeholder shows once none is left.
  const candidates = [pokemon.artworkUrl, pokemon.spriteUrl].filter((url): url is string => !!url)
  const [failures, setFailures] = useState(0)
  const src = candidates[failures]

  return (
    <article className="card">
      <div className="card__media">
        {src ? (
          <img
            src={src}
            onError={() => setFailures((count) => count + 1)}
            alt={`Artwork of ${name}`}
            width={96}
            height={96}
            loading="lazy"
          />
        ) : (
          <div className="card__no-image" role="img" aria-label={`No image available for ${name}`}>
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

import { Link } from 'react-router-dom'
import type { PokemonSummary } from '../api/types'
import { formatName, formatPokemonId, formatWeight } from '../lib/format'

export function PokemonCard({ pokemon }: { pokemon: PokemonSummary }) {
  const name = formatName(pokemon.name)

  return (
    <article className="card">
      <div className="card__media">
        {pokemon.spriteUrl ? (
          <img
            src={pokemon.spriteUrl}
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
        <Link className="card__link" to={`/pokemon/${encodeURIComponent(pokemon.name)}`}>
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

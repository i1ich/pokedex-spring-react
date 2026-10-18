import { Link } from 'react-router-dom'
import type { LocalPokemon } from '../api/types'
import { formatName, formatPokemonId } from '../lib/format'

interface CollectionItemProps {
  pokemon: LocalPokemon
  onDelete: (pokemon: LocalPokemon) => void
}

export function CollectionItem({ pokemon, onDelete }: CollectionItemProps) {
  const name = formatName(pokemon.name)

  return (
    <article className="collection-item">
      <div className="collection-item__media">
        {pokemon.spriteUrl ? (
          <img
            src={pokemon.spriteUrl}
            alt={`Sprite of ${name}`}
            width={72}
            height={72}
            loading="lazy"
          />
        ) : (
          <div className="card__no-image" role="img" aria-label={`No sprite available for ${name}`}>
            ?
          </div>
        )}
      </div>
      <div className="collection-item__body">
        <p className="card__id">{formatPokemonId(pokemon.id)}</p>
        <h2 className="collection-item__name">
          <Link to={`/pokemon/${encodeURIComponent(pokemon.name)}`}>{name}</Link>
        </h2>
        {pokemon.localName && <p className="collection-item__line">{pokemon.localName}</p>}
        {pokemon.region && <p className="collection-item__line">{pokemon.region}</p>}
        {pokemon.tags.length > 0 && (
          <ul className="chips chips--start" aria-label="Tags">
            {pokemon.tags.map((tag) => (
              <li key={tag} className="chip">
                {tag}
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="collection-item__actions">
        <Link
          className="button button--ghost"
          to={`/collection/${pokemon.id}/edit`}
          aria-label={`Edit ${name}`}
        >
          Edit
        </Link>
        <button
          type="button"
          className="button button--ghost"
          aria-label={`Delete ${name}`}
          onClick={() => onDelete(pokemon)}
        >
          Delete
        </button>
      </div>
    </article>
  )
}

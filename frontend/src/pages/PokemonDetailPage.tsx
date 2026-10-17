import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { ErrorBanner } from '../components/ErrorBanner'
import { EvolutionTree } from '../components/EvolutionTree'
import { StatBars } from '../components/StatBars'
import { usePokemonDetail } from '../hooks/usePokemonDetail'
import { useSyncPokemon } from '../hooks/useSyncPokemon'
import { formatHeight, formatName, formatPokemonId, formatWeight } from '../lib/format'

function BrowseLink() {
  return <Link to="/">Back to Browse</Link>
}

export function PokemonDetailPage() {
  const { idOrName = '' } = useParams()
  const key = idOrName.toLowerCase()
  const { data, error, isPending, isError, refetch } = usePokemonDetail(key)
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const sync = useSyncPokemon()

  if (isPending) {
    return (
      <p role="status" className="empty">
        Loading Pokémon…
      </p>
    )
  }

  if (isError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <>
          <h1>Pokémon not found</h1>
          <p>We could not find a Pokémon called “{key}”.</p>
          <p>
            <BrowseLink />
          </p>
        </>
      )
    }
    return (
      <>
        <ErrorBanner error={error} onRetry={() => void refetch()} />
        <p>
          <BrowseLink />
        </p>
      </>
    )
  }

  const name = formatName(data.name)

  function addToCollection() {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location } })
      return
    }
    sync.mutate(data!.name)
  }

  return (
    <article className="detail">
      <p>
        <BrowseLink />
      </p>
      <div className="detail__header">
        <div className="detail__media">
          {data.imageUrl ? (
            <img src={data.imageUrl} alt={`Artwork of ${name}`} width={240} height={240} />
          ) : (
            <p className="detail__no-image">No artwork available</p>
          )}
        </div>
        <div className="detail__summary">
          <p className="card__id">{formatPokemonId(data.id)}</p>
          <h1>{name}</h1>
          <ul className="chips chips--start" aria-label="Types">
            {data.types.map((type) => (
              <li key={type} className="chip">
                {formatName(type)}
              </li>
            ))}
          </ul>
          <dl className="facts">
            <div>
              <dt>Height</dt>
              <dd>{formatHeight(data.heightM)}</dd>
            </div>
            <div>
              <dt>Weight</dt>
              <dd>{formatWeight(data.weightKg)}</dd>
            </div>
          </dl>
          <div className="detail__actions">
            <button
              type="button"
              className="button"
              onClick={addToCollection}
              disabled={sync.isPending}
            >
              {sync.isPending ? 'Adding…' : 'Add to my collection'}
            </button>
            {!isAuthenticated && <p className="hint">You need to log in first.</p>}
          </div>
          <div role="status" className="detail__status">
            {sync.isSuccess && (
              <p className="success">
                {sync.data.created
                  ? 'Added to your collection.'
                  : 'Already in your collection — refreshed from PokeAPI.'}{' '}
                <Link to={`/collection/${sync.data.pokemon.id}/edit`}>Edit your copy</Link>
              </p>
            )}
          </div>
          {sync.isError && <ErrorBanner error={sync.error} />}
        </div>
      </div>

      <section aria-labelledby="description-heading">
        <h2 id="description-heading">Description</h2>
        {data.description ? (
          <p>{data.description}</p>
        ) : (
          <p className="empty">No description available.</p>
        )}
      </section>

      <section aria-labelledby="stats-heading">
        <h2 id="stats-heading">Base stats</h2>
        <StatBars stats={data.stats} />
      </section>

      <section aria-labelledby="evolution-heading">
        <h2 id="evolution-heading">Evolution</h2>
        <EvolutionTree root={data.evolutionChain} current={data.name} />
      </section>
    </article>
  )
}

import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { PokemonCard, PokemonCardSkeleton } from '../components/PokemonCard'
import { usePokemonPage } from '../hooks/usePokemonPage'
import { PAGE_SIZE, parsePageParam } from '../lib/pagination'

/**
 * Catalogue grid. The URL carries the page 1-based (`/?page=2` is the second page, API page 1); a
 * missing or invalid value means page 1.
 */
export function BrowsePage() {
  const [searchParams] = useSearchParams()
  const page = parsePageParam(searchParams.get('page'))
  const { data, error, isPending, isError, isPlaceholderData, refetch } = usePokemonPage(
    page - 1,
    PAGE_SIZE,
  )

  const shownPage = useRef(page)
  useEffect(() => {
    if (shownPage.current !== page) {
      shownPage.current = page
      window.scrollTo(0, 0)
    }
  }, [page])

  let content
  if (isPending) {
    content = (
      <>
        <p role="status" className="sr-only">
          Loading Pokémon…
        </p>
        <div className="grid" aria-hidden="true">
          {Array.from({ length: PAGE_SIZE }, (_, index) => (
            <PokemonCardSkeleton key={index} />
          ))}
        </div>
      </>
    )
  } else if (isError) {
    content = <ErrorBanner error={error} onRetry={() => void refetch()} />
  } else if (data.items.length === 0) {
    content = (
      <>
        <p className="empty">No Pokémon on this page.</p>
        <p>
          <Link to={{ search: '?page=1' }}>Back to the first page</Link>
        </p>
      </>
    )
  } else {
    content = (
      <ul className="grid" aria-label="Pokémon" aria-busy={isPlaceholderData}>
        {data.items.map((pokemon) => (
          <li key={pokemon.id} className="grid__item">
            <PokemonCard pokemon={pokemon} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <>
      <h1>Browse Pokémon</h1>
      {data && <p className="subtitle">{data.totalElements.toLocaleString('en-US')} Pokémon</p>}
      {content}
      {data && data.totalPages > 0 && <Pagination page={page} totalPages={data.totalPages} />}
    </>
  )
}

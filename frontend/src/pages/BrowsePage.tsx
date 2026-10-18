import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { PokemonCard, PokemonCardSkeleton } from '../components/PokemonCard'
import { usePokemonPage } from '../hooks/usePokemonPage'
import {
  MAX_QUERY_LENGTH,
  PAGE_SIZE,
  browseSearch,
  parsePageParam,
  parseQueryParam,
} from '../lib/pagination'

/** Search by part of a name; submitting always starts again from the first page of the results. */
function SearchForm({ query }: { query: string }) {
  const navigate = useNavigate()
  const [value, setValue] = useState(query)

  function submit(event: FormEvent) {
    event.preventDefault()
    navigate({ search: browseSearch(1, parseQueryParam(value)) })
  }

  return (
    <form className="search" role="search" onSubmit={submit}>
      <label htmlFor="pokemon-search" className="sr-only">
        Search by name
      </label>
      <input
        id="pokemon-search"
        className="input"
        type="search"
        placeholder="Search by name, e.g. char"
        maxLength={MAX_QUERY_LENGTH}
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <button type="submit" className="button">
        Search
      </button>
      {query && (
        <Link className="button button--ghost" to={{ search: browseSearch(1) }}>
          Clear
        </Link>
      )}
    </form>
  )
}

/**
 * Catalogue grid. The URL carries the page 1-based (`/?page=2` is the second page, API page 1) and an
 * optional name search (`/?page=1&q=char`); a missing or invalid page means page 1.
 */
export function BrowsePage() {
  const [searchParams] = useSearchParams()
  const page = parsePageParam(searchParams.get('page'))
  const query = parseQueryParam(searchParams.get('q'))
  const { data, error, isPending, isError, isPlaceholderData, refetch } = usePokemonPage(
    page - 1,
    PAGE_SIZE,
    query,
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
        <p className="empty">
          {query && page === 1 ? `No Pokémon match “${query}”.` : 'No Pokémon on this page.'}
        </p>
        <p>
          {query && page === 1 ? (
            <Link to={{ search: browseSearch(1) }}>Clear the search</Link>
          ) : (
            <Link to={{ search: browseSearch(1, query) }}>Back to the first page</Link>
          )}
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
      <SearchForm key={query} query={query} />
      {data && (
        <p className="subtitle">
          {data.totalElements.toLocaleString('en-US')} Pokémon{query && ` match “${query}”`}
        </p>
      )}
      {content}
      {data && data.totalPages > 0 && (
        <Pagination page={page} totalPages={data.totalPages} query={query} />
      )}
    </>
  )
}

import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { LocalPokemon } from '../api/types'
import { CollectionItem } from '../components/CollectionItem'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { useDeleteLocalPokemon } from '../hooks/useDeleteLocalPokemon'
import { useLocalPokemonPage } from '../hooks/useLocalPokemon'
import { formatName } from '../lib/format'
import { PAGE_SIZE, parsePageParam } from '../lib/pagination'

/** Shown once after the edit form saved, passed in the router state. */
function useNotice(): string | null {
  const state = useLocation().state as { notice?: unknown } | null
  return typeof state?.notice === 'string' ? state.notice : null
}

export function CollectionPage() {
  const [searchParams] = useSearchParams()
  const page = parsePageParam(searchParams.get('page'))
  const notice = useNotice()
  const { data, error, isPending, isError, isPlaceholderData, refetch } = useLocalPokemonPage(
    page - 1,
    PAGE_SIZE,
  )
  const remove = useDeleteLocalPokemon()
  const [pendingDelete, setPendingDelete] = useState<LocalPokemon | null>(null)

  const shownPage = useRef(page)
  useEffect(() => {
    if (shownPage.current !== page) {
      shownPage.current = page
      window.scrollTo(0, 0)
    }
  }, [page])

  // A 404 means it was already gone, which is what the user asked for: nothing to report.
  const deleteError =
    remove.isError && !(remove.error instanceof ApiError && remove.error.status === 404)
      ? remove.error
      : null

  let content
  if (isPending) {
    content = (
      <p role="status" className="empty">
        Loading your collection…
      </p>
    )
  } else if (isError) {
    content = <ErrorBanner error={error} onRetry={() => void refetch()} />
  } else if (data.totalElements === 0 && data.items.length === 0) {
    content = (
      <>
        <p className="empty">Your collection is empty.</p>
        <p>
          <Link to="/">Browse the Pokédex</Link> and add some Pokémon.
        </p>
      </>
    )
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
      <ul className="collection" aria-label="Your Pokémon" aria-busy={isPlaceholderData}>
        {data.items.map((pokemon) => (
          <li key={pokemon.id}>
            <CollectionItem pokemon={pokemon} onDelete={setPendingDelete} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <>
      <h1>My collection</h1>
      <p role="note" className="hint">
        This collection is shared: every user sees the same Pokémon, and edits are visible to
        everyone.
      </p>
      {data && data.totalElements > 0 && (
        <p className="subtitle">{data.totalElements.toLocaleString('en-US')} Pokémon</p>
      )}
      {notice && (
        <p role="status" className="success">
          {notice}
        </p>
      )}
      {deleteError !== null && <ErrorBanner error={deleteError} />}
      {content}
      {data && data.totalPages > 0 && <Pagination page={page} totalPages={data.totalPages} />}
      {pendingDelete && (
        <ConfirmDialog
          title={`Delete ${formatName(pendingDelete.name)}?`}
          message={`${formatName(pendingDelete.name)}${
            pendingDelete.localName ? ` (${pendingDelete.localName})` : ''
          } will be removed from the shared collection for every user, together with your local name, notes and tags. This cannot be undone.`}
          confirmLabel="Delete"
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => {
            remove.mutate(pendingDelete.id)
            setPendingDelete(null)
          }}
        />
      )}
    </>
  )
}

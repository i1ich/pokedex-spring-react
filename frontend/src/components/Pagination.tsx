import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { browseSearch } from '../lib/pagination'

interface PaginationProps {
  /** Current page, 1-based (as in the `?page=` query parameter). */
  page: number
  totalPages: number
  /** Active name search, kept in every link. */
  query?: string
}

interface PageLinkProps {
  target: number
  label: string
  enabled: boolean
  query: string
}

function PageLink({ target, label, enabled, query }: PageLinkProps) {
  if (!enabled) {
    return (
      <span className="pagination__link pagination__link--disabled" aria-disabled="true">
        {label}
      </span>
    )
  }
  return (
    <Link className="pagination__link" to={{ search: browseSearch(target, query) }}>
      {label}
    </Link>
  )
}

/** Jump straight to a page; with hundreds of pages, Next/Previous alone is too slow. */
function GoToPage({ totalPages, query }: { totalPages: number; query: string }) {
  const navigate = useNavigate()
  const [value, setValue] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    const requested = Number.parseInt(value, 10)
    if (Number.isNaN(requested)) return
    const target = Math.min(Math.max(requested, 1), totalPages)
    setValue('')
    navigate({ search: browseSearch(target, query) })
  }

  return (
    <form className="pagination__jump" onSubmit={submit} noValidate>
      <label htmlFor="go-to-page">Go to page</label>
      <input
        id="go-to-page"
        type="number"
        min={1}
        max={totalPages}
        inputMode="numeric"
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <button type="submit" className="button">
        Go
      </button>
    </form>
  )
}

export function Pagination({ page, totalPages, query = '' }: PaginationProps) {
  const hasPrevious = page > 1
  const hasNext = page < totalPages

  return (
    <nav className="pagination" aria-label="Pagination">
      <PageLink target={1} label="First" enabled={hasPrevious} query={query} />
      <PageLink target={page - 1} label="Previous" enabled={hasPrevious} query={query} />
      <span className="pagination__status" aria-current="page">
        Page {page} of {totalPages}
      </span>
      <PageLink target={page + 1} label="Next" enabled={hasNext} query={query} />
      <PageLink target={totalPages} label="Last" enabled={hasNext} query={query} />
      <GoToPage totalPages={totalPages} query={query} />
    </nav>
  )
}

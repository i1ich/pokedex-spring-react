import { Link } from 'react-router-dom'

interface PaginationProps {
  /** Current page, 1-based (as in the `?page=` query parameter). */
  page: number
  totalPages: number
}

function PageLink({ target, label, enabled }: { target: number; label: string; enabled: boolean }) {
  if (!enabled) {
    return (
      <span className="pagination__link pagination__link--disabled" aria-disabled="true">
        {label}
      </span>
    )
  }
  return (
    <Link className="pagination__link" to={{ search: `?page=${target}` }}>
      {label}
    </Link>
  )
}

export function Pagination({ page, totalPages }: PaginationProps) {
  const hasPrevious = page > 1
  const hasNext = page < totalPages

  return (
    <nav className="pagination" aria-label="Pagination">
      <PageLink target={1} label="First" enabled={hasPrevious} />
      <PageLink target={page - 1} label="Previous" enabled={hasPrevious} />
      <span className="pagination__status" aria-current="page">
        Page {page} of {totalPages}
      </span>
      <PageLink target={page + 1} label="Next" enabled={hasNext} />
      <PageLink target={totalPages} label="Last" enabled={hasNext} />
    </nav>
  )
}

import { ApiError } from '../api/client'

interface ErrorBannerProps {
  error: unknown
  /** When given, a "Try again" button is shown. */
  onRetry?: () => void
}

/** Announced to screen readers (role="alert"); shows the problem title and detail from the API. */
export function ErrorBanner({ error, onRetry }: ErrorBannerProps) {
  const title = error instanceof ApiError ? error.title : 'Something went wrong'
  const detail = error instanceof ApiError ? error.detail : null
  const fieldErrors = error instanceof ApiError ? error.errors : []

  return (
    <div className="error-banner" role="alert">
      <div className="error-banner__text">
        <strong className="error-banner__title">{title}</strong>
        {detail && <p className="error-banner__detail">{detail}</p>}
        {fieldErrors.length > 0 && (
          <ul className="error-banner__fields">
            {fieldErrors.map(({ field, message }, index) => (
              <li key={`${field}-${index}`}>
                {field}: {message}
              </li>
            ))}
          </ul>
        )}
      </div>
      {onRetry && (
        <button type="button" className="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

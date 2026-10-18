/** The site's pokéball; `currentColor` draws the outline and the centre ring. */
export function PokeballIcon({ className, size = 28 }: { className?: string; size?: number }) {
  return (
    <svg className={className} viewBox="0 0 32 32" width={size} height={size} aria-hidden="true">
      <circle cx="16" cy="16" r="14" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <path d="M2 16a14 14 0 0 1 28 0z" fill="#e53935" stroke="currentColor" strokeWidth="2" />
      <circle cx="16" cy="16" r="4.5" fill="#fff" stroke="currentColor" strokeWidth="2" />
    </svg>
  )
}

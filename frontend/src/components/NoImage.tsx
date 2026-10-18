import { PokeballIcon } from './PokeballIcon'

/** Stand-in for a missing picture: a faint pokéball with a question mark centred on it. */
export function NoImage({ label }: { label: string }) {
  return (
    <div className="no-image" role="img" aria-label={label}>
      <PokeballIcon className="no-image__ball" size={72} />
      <span className="no-image__mark" aria-hidden="true">
        ?
      </span>
    </div>
  )
}

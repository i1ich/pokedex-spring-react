import { useParams } from 'react-router-dom'
import { formatName } from '../lib/format'

/** Placeholder so card links resolve; the real detail page is built in T7. */
export function PokemonDetailPage() {
  const { idOrName = '' } = useParams()
  return (
    <>
      <h1>{formatName(idOrName)}</h1>
      <p>Details coming soon.</p>
    </>
  )
}

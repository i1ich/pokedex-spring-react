import { Link } from 'react-router-dom'
import type { EvolutionNode } from '../api/types'
import { formatEvolutionStep, formatName } from '../lib/format'

interface EvolutionTreeProps {
  root: EvolutionNode
  /** Name of the species being viewed: shown as plain text instead of a link. */
  current: string
}

function Branch({ node, current }: { node: EvolutionNode; current: string }) {
  const step = formatEvolutionStep(node)
  return (
    <li className="evolution__item">
      {node.name === current ? (
        <span className="evolution__current" aria-current="page">
          {formatName(node.name)}
        </span>
      ) : (
        <Link to={`/pokemon/${encodeURIComponent(node.name)}`}>{formatName(node.name)}</Link>
      )}
      {step && (
        <span className="evolution__step">
          <span className="sr-only">Evolves by </span>
          {step}
        </span>
      )}
      {node.evolvesTo.length > 0 && (
        <ul className="evolution__list">
          {node.evolvesTo.map((child) => (
            <Branch key={child.name} node={child} current={current} />
          ))}
        </ul>
      )}
    </li>
  )
}

/** The chain is a tree (Eevee has several branches), rendered as nested lists. */
export function EvolutionTree({ root, current }: EvolutionTreeProps) {
  if (root.evolvesTo.length === 0) {
    return <p className="empty">This Pokémon does not evolve.</p>
  }
  return (
    <ul className="evolution__list evolution__list--root" aria-label="Evolution chain">
      <Branch node={root} current={current} />
    </ul>
  )
}

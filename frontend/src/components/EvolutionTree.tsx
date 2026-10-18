import { Link, useLocation } from 'react-router-dom'
import type { EvolutionNode } from '../api/types'
import { formatEvolutionStep, formatName } from '../lib/format'

interface EvolutionTreeProps {
  root: EvolutionNode
  /** Species being viewed: shown as plain text instead of a link. */
  current: string
  /** The form being viewed ("pikachu-gmax"); shown in place of its species when they differ. */
  form?: string
}

function Branch({ node, current, form }: { node: EvolutionNode; current: string; form?: string }) {
  const step = formatEvolutionStep(node)
  const { state } = useLocation()
  return (
    <li className="evolution__item">
      {node.name === current ? (
        <span className="evolution__current" aria-current="page">
          {formatName(form ?? node.name)}
        </span>
      ) : (
        <Link to={`/pokemon/${encodeURIComponent(node.name)}`} state={state}>
          {formatName(node.name)}
        </Link>
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
            <Branch key={child.name} node={child} current={current} form={form} />
          ))}
        </ul>
      )}
    </li>
  )
}

/** The chain is a tree (Eevee has several branches), rendered as nested lists. */
export function EvolutionTree({ root, current, form }: EvolutionTreeProps) {
  if (root.evolvesTo.length === 0) {
    return <p className="empty">This Pokémon does not evolve.</p>
  }
  return (
    <ul className="evolution__list evolution__list--root" aria-label="Evolution chain">
      <Branch node={root} current={current} form={form} />
    </ul>
  )
}

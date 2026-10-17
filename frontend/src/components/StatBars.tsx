import type { PokemonStat } from '../api/types'
import { formatStatName } from '../lib/format'

/** Highest possible base stat in the games; the bars are scaled against it. */
export const MAX_BASE_STAT = 255

export function StatBars({ stats }: { stats: PokemonStat[] }) {
  return (
    <ul className="stats" aria-label="Base stats">
      {stats.map(({ name, baseStat }) => {
        const label = formatStatName(name)
        const percent = Math.min(100, Math.max(0, (baseStat / MAX_BASE_STAT) * 100))
        return (
          <li key={name} className="stats__row">
            <span className="stats__label" aria-hidden="true">
              {label}
            </span>
            <div
              className="stats__bar"
              role="meter"
              aria-label={label}
              aria-valuemin={0}
              aria-valuemax={MAX_BASE_STAT}
              aria-valuenow={baseStat}
              aria-valuetext={`${baseStat} of ${MAX_BASE_STAT}`}
            >
              <div className="stats__fill" style={{ width: `${percent}%` }} />
            </div>
            <span className="stats__value" aria-hidden="true">
              {baseStat}
            </span>
          </li>
        )
      })}
    </ul>
  )
}

import { formatCompact, formatNumber, formatWeekday } from '../lib/date'
import type { DayBucket } from '../lib/insights'

interface WeeklyBarsProps {
  data: DayBucket[]
  goal: number
}

export function WeeklyBars({ data, goal }: WeeklyBarsProps) {
  const max = Math.max(...data.map((day) => day.calories), goal, 1)
  const summary = data
    .map((day) => `${formatWeekday(day.date)} ${formatNumber(day.calories)} kilocalories`)
    .join(', ')

  return (
    <div className="weekly-bars" role="img" aria-label={`Last seven days: ${summary}`}>
      {data.map((day, index) => {
        const today = index === data.length - 1
        const height = day.calories > 0 ? Math.max((day.calories / max) * 100, 4) : 0
        return (
          <div className="bar-col" key={day.key}>
            <span className="bar-value">{day.calories > 0 ? formatCompact(day.calories) : ''}</span>
            <div className="bar-zone">
              <span className="bar-goal" style={{ bottom: `${(goal / max) * 100}%` }} />
              <span
                className={`bar-fill${today ? ' is-today' : ''}`}
                style={{ height: `${height}%` }}
              />
            </div>
            <span className={`bar-day${today ? ' is-today' : ''}`}>
              {today ? 'Today' : formatWeekday(day.date)}
            </span>
          </div>
        )
      })}
    </div>
  )
}

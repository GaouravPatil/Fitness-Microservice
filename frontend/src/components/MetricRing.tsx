import { useId } from 'react'
import { formatNumber } from '../lib/date'

interface MetricRingProps {
  value: number
  max: number
}

export function MetricRing({ value, max }: MetricRingProps) {
  const gradientId = `ring-${useId().replace(/:/g, '')}`
  const size = 220
  const stroke = 11
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const ratio = max > 0 ? Math.min(Math.max(value / max, 0), 1) : 0

  return (
    <svg
      className="metric-ring"
      viewBox={`0 0 ${size} ${size}`}
      role="img"
      aria-label={`${formatNumber(value)} of ${formatNumber(max)} kilocalories burned today`}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#f2efe7" />
          <stop offset="100%" stopColor="#b9a6ff" />
        </linearGradient>
      </defs>
      <circle className="ring-track" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} />
      <circle
        className="ring-progress"
        cx={size / 2}
        cy={size / 2}
        r={radius}
        strokeWidth={stroke}
        stroke={`url(#${gradientId})`}
        strokeDasharray={circumference}
        strokeDashoffset={circumference * (1 - ratio)}
      />
    </svg>
  )
}

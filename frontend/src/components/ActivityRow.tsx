import type { CSSProperties } from 'react'
import { ACTIVITY_META } from '../lib/activityMeta'
import { formatNumber, formatTime, minutesLabel, relativeDay } from '../lib/date'
import type { Activity } from '../lib/types'
import { ActivityIcon } from './ActivityIcon'

interface ActivityRowProps {
  activity: Activity
  showDate?: boolean
}

export function ActivityRow({ activity, showDate = true }: ActivityRowProps) {
  const meta = ACTIVITY_META[activity.type] ?? ACTIVITY_META.OTHER
  const style = { '--accent': meta.accent } as CSSProperties

  return (
    <div className="activity-row" style={style}>
      <span className="activity-icon">
        <ActivityIcon type={activity.type} size={19} />
      </span>
      <div className="activity-main">
        <span className="activity-name">{meta.label}</span>
        <span className="activity-meta">
          {showDate && `${relativeDay(activity.startTime)} · `}
          {formatTime(activity.startTime)} · {minutesLabel(activity.duration)}
        </span>
      </div>
      <div className="activity-burn">
        <strong>{formatNumber(activity.caloriesBurned)}</strong>
        <span>kcal</span>
      </div>
    </div>
  )
}

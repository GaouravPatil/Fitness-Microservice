import { ACTIVITY_META, estimateCalories, medianDuration } from './activityMeta'
import { addDays, dayKey, parseDate, startOfDay } from './date'
import type { Activity, ActivityType } from './types'

export interface DayBucket {
  date: Date
  key: string
  calories: number
  minutes: number
  sessions: number
}

export interface ActivityStats {
  weekCalories: number
  weekMinutes: number
  weekSessions: number
  previousWeekCalories: number
  calorieTrend: number
  lifetimeCalories: number
  lifetimeMinutes: number
  lifetimeSessions: number
  streak: number
  favoriteType: ActivityType | null
  averageSessionCalories: number
}

export type ForecastConfidence = 'high' | 'medium' | 'low' | 'none'

export interface BurnForecast {
  weeklyCalories: number
  perDay: number
  confidence: ForecastConfidence
  activeDays: number
  trendPercent: number
}

export interface ActivitySuggestion {
  type: ActivityType
  duration: number
  calories: number
  reason: string
  kind: 'starter' | 'balance' | 'rhythm'
}

function activityDate(activity: Activity): Date | null {
  return parseDate(activity.startTime) ?? parseDate(activity.createdAt)
}

function sumSeries(series: DayBucket[]): number {
  return series.reduce((sum, day) => sum + day.calories, 0)
}

export function buildDailySeries(
  activities: Activity[],
  days: number,
  end: Date = new Date(),
): DayBucket[] {
  const endDay = startOfDay(end)
  const buckets = new Map<string, DayBucket>()
  const series: DayBucket[] = []

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = addDays(endDay, -offset)
    const key = dayKey(date)
    const bucket: DayBucket = { date, key, calories: 0, minutes: 0, sessions: 0 }
    buckets.set(key, bucket)
    series.push(bucket)
  }

  for (const activity of activities) {
    const date = activityDate(activity)
    if (!date) continue
    const bucket = buckets.get(dayKey(date))
    if (!bucket) continue
    bucket.calories += Number(activity.caloriesBurned) || 0
    bucket.minutes += Number(activity.duration) || 0
    bucket.sessions += 1
  }

  return series
}

export function computeStats(activities: Activity[]): ActivityStats {
  const now = new Date()
  const weekStart = startOfDay(addDays(now, -6))
  const previousStart = startOfDay(addDays(now, -13))

  let weekCalories = 0
  let weekMinutes = 0
  let weekSessions = 0
  let previousWeekCalories = 0
  let lifetimeCalories = 0
  let lifetimeMinutes = 0
  const typeCounts = new Map<ActivityType, number>()
  const activeDays = new Set<string>()

  for (const activity of activities) {
    const date = activityDate(activity)
    if (!date) continue

    const calories = Number(activity.caloriesBurned) || 0
    const minutes = Number(activity.duration) || 0

    lifetimeCalories += calories
    lifetimeMinutes += minutes
    typeCounts.set(activity.type, (typeCounts.get(activity.type) ?? 0) + 1)
    activeDays.add(dayKey(date))

    if (date >= weekStart) {
      weekCalories += calories
      weekMinutes += minutes
      weekSessions += 1
    } else if (date >= previousStart) {
      previousWeekCalories += calories
    }
  }

  const favoriteEntry = [...typeCounts.entries()].sort((a, b) => b[1] - a[1])[0]
  const lifetimeSessions = activities.length

  let cursor = startOfDay(now)
  if (!activeDays.has(dayKey(cursor))) cursor = addDays(cursor, -1)
  let streak = 0
  while (activeDays.has(dayKey(cursor))) {
    streak += 1
    cursor = addDays(cursor, -1)
  }

  const calorieTrend =
    previousWeekCalories > 0
      ? Math.round(((weekCalories - previousWeekCalories) / previousWeekCalories) * 100)
      : weekCalories > 0
        ? 100
        : 0

  return {
    weekCalories,
    weekMinutes,
    weekSessions,
    previousWeekCalories,
    calorieTrend,
    lifetimeCalories,
    lifetimeMinutes,
    lifetimeSessions,
    streak,
    favoriteType: favoriteEntry?.[0] ?? null,
    averageSessionCalories: lifetimeSessions
      ? Math.round(lifetimeCalories / lifetimeSessions)
      : 0,
  }
}

export function buildForecast(activities: Activity[]): BurnForecast {
  const series = buildDailySeries(activities, 14)

  let weightedCalories = 0
  let weightTotal = 0
  let activeDays = 0

  series.forEach((day, index) => {
    if (day.sessions > 0) activeDays += 1
    const weight = 1 + index * 0.18
    weightedCalories += day.calories * weight
    weightTotal += weight
  })

  const perDay = weightTotal > 0 ? weightedCalories / weightTotal : 0
  const currentWeek = sumSeries(series.slice(7))
  const previousWeek = sumSeries(series.slice(0, 7))
  const trendPercent =
    previousWeek > 0
      ? Math.round(((currentWeek - previousWeek) / previousWeek) * 100)
      : currentWeek > 0
        ? 100
        : 0

  const confidence: ForecastConfidence =
    activeDays === 0 ? 'none' : activeDays >= 8 ? 'high' : activeDays >= 4 ? 'medium' : 'low'

  return {
    weeklyCalories: Math.round(perDay * 7),
    perDay: Math.round(perDay),
    confidence,
    activeDays,
    trendPercent,
  }
}

export function buildSuggestion(activities: Activity[], weightKg: number): ActivitySuggestion {
  const now = new Date()
  const byType = new Map<
    ActivityType,
    { last: Date; durations: number[]; count: number }
  >()

  for (const activity of activities) {
    const date = activityDate(activity)
    if (!date) continue
    const entry = byType.get(activity.type) ?? { last: date, durations: [], count: 0 }
    if (date > entry.last) entry.last = date
    entry.durations.push(Number(activity.duration) || 0)
    entry.count += 1
    byType.set(activity.type, entry)
  }

  if (byType.size === 0) {
    return {
      type: 'RUNNING',
      duration: 25,
      calories: estimateCalories('RUNNING', 25, weightKg),
      reason: 'A short run is a steady way to start shaping your pattern.',
      kind: 'starter',
    }
  }

  const favorite = [...byType.entries()].sort((a, b) => b[1].count - a[1].count)[0][0]

  let stalest: { type: ActivityType; days: number; durations: number[] } | null = null
  for (const [type, entry] of byType.entries()) {
    if (type === 'OTHER') continue
    const days = Math.max(
      0,
      Math.round((startOfDay(now).getTime() - startOfDay(entry.last).getTime()) / 86_400_000),
    )
    if (!stalest || days > stalest.days) {
      stalest = { type, days, durations: entry.durations }
    }
  }

  const balance = stalest && stalest.days >= 2 ? stalest : null
  const type = balance?.type ?? favorite
  const durations = balance?.durations ?? byType.get(favorite)?.durations ?? []
  const duration = Math.min(90, Math.max(15, Math.round(medianDuration(durations) / 5) * 5))
  const meta = ACTIVITY_META[type] ?? ACTIVITY_META.OTHER

  return {
    type,
    duration,
    calories: estimateCalories(type, duration, weightKg),
    reason: balance
      ? `${balance.days} days since your last ${meta.label.toLowerCase()}. A little balance goes a long way.`
      : `Keep the rhythm with another ${meta.label.toLowerCase()} — your most consistent movement.`,
    kind: balance ? 'balance' : 'rhythm',
  }
}

export function countByType(activities: Activity[]): Array<{ type: ActivityType; count: number }> {
  const counts = new Map<ActivityType, number>()
  for (const activity of activities) {
    counts.set(activity.type, (counts.get(activity.type) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([type, count]) => ({ type, count }))
    .sort((a, b) => b.count - a.count)
}

export function peakDay(series: DayBucket[]): DayBucket | null {
  let peak: DayBucket | null = null
  for (const day of series) {
    if (day.calories <= 0) continue
    if (!peak || day.calories > peak.calories) peak = day
  }
  return peak
}

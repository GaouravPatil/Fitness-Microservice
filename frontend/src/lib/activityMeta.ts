import type { ActivityType } from './types'

export interface ActivityMeta {
  label: string
  short: string
  met: number
  accent: string
}

export const ACTIVITY_META: Record<ActivityType, ActivityMeta> = {
  RUNNING: { label: 'Running', short: 'Run', met: 9.8, accent: '#b9a6ff' },
  WALKING: { label: 'Walking', short: 'Walk', met: 3.5, accent: '#9dd6b0' },
  CYCLING: { label: 'Cycling', short: 'Ride', met: 7.5, accent: '#8fc7e8' },
  WEIGHTLIFTING: { label: 'Weightlifting', short: 'Lift', met: 5, accent: '#e3b38c' },
  SWIMMING: { label: 'Swimming', short: 'Swim', met: 8, accent: '#88c9d4' },
  YOGA: { label: 'Yoga', short: 'Yoga', met: 2.8, accent: '#d6b6d9' },
  CARDIO: { label: 'Cardio', short: 'Cardio', met: 7, accent: '#e79a9a' },
  WEIGHT_TRAINING: { label: 'Weight training', short: 'Weights', met: 5, accent: '#dfb48f' },
  STEPS: { label: 'Steps', short: 'Steps', met: 3.8, accent: '#b8d18f' },
  PULLUPS: { label: 'Pull-ups', short: 'Pull-ups', met: 8, accent: '#b0c4e7' },
  SQUAT: { label: 'Squats', short: 'Squat', met: 6, accent: '#c9b18f' },
  PUSHUPS: { label: 'Push-ups', short: 'Push-ups', met: 8, accent: '#d6a6a6' },
  OTHER: { label: 'Other', short: 'Other', met: 4.5, accent: '#b6b3aa' },
}

export function estimateCalories(
  type: ActivityType,
  durationMinutes: number,
  weightKg: number,
): number {
  if (!Number.isFinite(durationMinutes) || durationMinutes <= 0) return 0
  const meta = ACTIVITY_META[type] ?? ACTIVITY_META.OTHER
  return Math.round(meta.met * weightKg * (durationMinutes / 60))
}

export function medianDuration(values: number[]): number {
  const sorted = values.filter((value) => Number.isFinite(value) && value > 0).sort((a, b) => a - b)
  if (sorted.length === 0) return 30
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle]
}

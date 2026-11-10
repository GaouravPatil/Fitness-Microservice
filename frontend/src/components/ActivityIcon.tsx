import {
  Activity,
  Bike,
  Dumbbell,
  Flower2,
  Footprints,
  HeartPulse,
  MoveUp,
  PersonStanding,
  Sparkles,
  Waves,
  Zap,
  type LucideIcon,
} from 'lucide-react'
import type { ActivityType } from '../lib/types'

const ICONS: Record<ActivityType, LucideIcon> = {
  RUNNING: Footprints,
  WALKING: Footprints,
  CYCLING: Bike,
  WEIGHTLIFTING: Dumbbell,
  SWIMMING: Waves,
  YOGA: Flower2,
  CARDIO: HeartPulse,
  WEIGHT_TRAINING: Dumbbell,
  STEPS: Footprints,
  PULLUPS: MoveUp,
  SQUAT: PersonStanding,
  PUSHUPS: Zap,
  OTHER: Sparkles,
}

interface ActivityIconProps {
  type: ActivityType
  size?: number
}

export function ActivityIcon({ type, size = 18 }: ActivityIconProps) {
  const Icon = ICONS[type] ?? Activity
  return <Icon size={size} strokeWidth={1.8} aria-hidden="true" />
}

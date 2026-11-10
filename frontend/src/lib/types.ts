export type UserRole = 'ADMIN' | 'USER'

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  createdAt: string
  updatedAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
  refreshExpiresInSeconds: number
  user: User
}

export const ACTIVITY_TYPES = [
  'RUNNING',
  'WALKING',
  'CYCLING',
  'WEIGHTLIFTING',
  'SWIMMING',
  'YOGA',
  'CARDIO',
  'WEIGHT_TRAINING',
  'STEPS',
  'PULLUPS',
  'SQUAT',
  'PUSHUPS',
  'OTHER',
] as const

export type ActivityType = (typeof ACTIVITY_TYPES)[number]

export interface Activity {
  id: string
  userId: string
  type: ActivityType
  duration: number
  caloriesBurned: number
  startTime: string
  additionalMetrics?: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface ActivityPayload {
  userId: string
  type: ActivityType
  duration: number
  caloriesBurned: number
  startTime: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  numberOfElements: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface Recommendation {
  id: string
  activityId: string
  userId: string
  activityType: string
  recommendation: string
  suggestions: string[]
  safety: string[]
  provider: string
  createdAt: string
}

export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  fieldErrors?: Record<string, string>
}

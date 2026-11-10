export function startOfDay(date: Date): Date {
  const next = new Date(date)
  next.setHours(0, 0, 0, 0)
  return next
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

export function dayKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function toDateTimeInput(date: Date = new Date()): string {
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${dayKey(date)}T${hours}:${minutes}`
}

export function parseDate(value: string | Date): Date | null {
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatTime(value: string | Date): string {
  const date = parseDate(value)
  if (!date) return '—'
  return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

export function formatDateTime(value: string | Date): string {
  const date = parseDate(value)
  if (!date) return '—'
  const day = date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  return `${day} · ${formatTime(date)}`
}

export function formatWeekday(date: Date): string {
  return date.toLocaleDateString(undefined, { weekday: 'short' })
}

export function formatFullDate(date: Date = new Date()): string {
  return date.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

export function formatMonthYear(value: string | Date): string {
  const date = parseDate(value)
  if (!date) return '—'
  return date.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

export function relativeDay(value: string | Date): string {
  const date = parseDate(value)
  if (!date) return '—'
  const today = startOfDay(new Date())
  const target = startOfDay(date)
  const difference = Math.round((today.getTime() - target.getTime()) / 86_400_000)
  if (difference === 0) return 'Today'
  if (difference === 1) return 'Yesterday'
  if (difference > 1 && difference < 7) return formatWeekday(date)
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export function greeting(date: Date = new Date()): string {
  const hour = date.getHours()
  if (hour < 5) return 'Still up'
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function formatNumber(value: number): string {
  if (!Number.isFinite(value)) return '0'
  return Math.round(value).toLocaleString()
}

export function formatCompact(value: number): string {
  if (value >= 10_000) return `${Math.round(value / 1000)}k`
  if (value >= 1000) return `${(value / 1000).toFixed(1).replace('.0', '')}k`
  return String(Math.round(value))
}

export function minutesLabel(minutes: number): string {
  if (!Number.isFinite(minutes) || minutes <= 0) return '0 min'
  const rounded = Math.round(minutes)
  if (rounded < 60) return `${rounded} min`
  const hours = Math.floor(rounded / 60)
  const rest = rounded % 60
  return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`
}

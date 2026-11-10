import type { User } from './types'

export function fullName(user: Pick<User, 'firstName' | 'lastName'>): string {
  return `${user.firstName} ${user.lastName}`.trim()
}

export function initials(user: Pick<User, 'firstName' | 'lastName' | 'email'>): string {
  const letters = `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.trim()
  if (letters) return letters.toUpperCase()
  return (user.email?.[0] ?? '?').toUpperCase()
}

import { ApiError } from './api'

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const fieldError = error.fieldErrors ? Object.values(error.fieldErrors)[0] : null
    if (fieldError) return fieldError
    return error.message
  }
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong. Please try again.'
}

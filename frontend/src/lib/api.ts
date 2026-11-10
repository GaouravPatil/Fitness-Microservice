import type {
  Activity,
  ActivityPayload,
  ApiErrorBody,
  LoginResponse,
  Page,
  Recommendation,
  RegisterRequest,
  User,
} from './types'

function normalizeBase(value: string | undefined, fallback: string): string {
  const base = value && value.trim() ? value.trim() : fallback
  return base.replace(/\/+$/, '')
}

export const USER_API = normalizeBase(import.meta.env.VITE_USER_API_URL, '/api/users')
export const ACTIVITY_API = normalizeBase(import.meta.env.VITE_ACTIVITY_API_URL, '/api/activities')
export const RECOMMENDATION_API = normalizeBase(
  import.meta.env.VITE_RECOMMENDATION_API_URL,
  '/api/recommendations',
)

export interface Session {
  accessToken: string
  refreshToken: string
  user: User
}

const SESSION_KEY = 'aura.session.v1'

type SessionListener = (session: Session | null) => void
const sessionListeners = new Set<SessionListener>()

export function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Session>
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.user?.id) return null
    return parsed as Session
  } catch {
    return null
  }
}

export function writeSession(session: Session): void {
  try {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // Storage may be unavailable (private mode); the in-memory session still works.
  }
  sessionListeners.forEach((listener) => listener(session))
}

export function clearSession(): void {
  try {
    localStorage.removeItem(SESSION_KEY)
  } catch {
    // Ignore storage failures.
  }
  sessionListeners.forEach((listener) => listener(null))
}

export function subscribeSession(listener: SessionListener): () => void {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors?: Record<string, string>

  constructor(message: string, status = 0, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function readResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T

  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!response.ok) {
    const body = (typeof data === 'object' && data !== null ? data : {}) as ApiErrorBody
    const fallback =
      response.status === 401
        ? 'Your session has expired. Please sign in again.'
        : response.status === 429
          ? 'Too many attempts. Give it a moment and try again.'
          : `Request failed (${response.status})`
    throw new ApiError(body.message || body.error || fallback, response.status, body.fieldErrors)
  }

  return data as T
}

async function networkError(): Promise<never> {
  throw new ApiError('Service unreachable. Check the backend and try again.', 0)
}

let refreshInFlight: Promise<Session | null> | null = null

async function refreshSession(): Promise<Session | null> {
  const current = readSession()
  if (!current) return null

  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${USER_API}/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: current.refreshToken }),
        })

        if (!response.ok) {
          if ([400, 401, 403].includes(response.status)) clearSession()
          return null
        }

        const refreshed = (await response.json()) as LoginResponse
        const next: Session = {
          accessToken: refreshed.accessToken,
          refreshToken: refreshed.refreshToken,
          user: refreshed.user ?? current.user,
        }
        writeSession(next)
        return next
      } catch {
        return null
      } finally {
        refreshInFlight = null
      }
    })()
  }

  return refreshInFlight
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  auth?: boolean
  idempotencyKey?: string
  retryOnUnauthorized?: boolean
}

async function request<T>(base: string, path: string, options: RequestOptions = {}): Promise<T> {
  const {
    body,
    auth = true,
    idempotencyKey,
    retryOnUnauthorized = true,
    headers: customHeaders,
    ...rest
  } = options

  const buildInit = (): RequestInit => {
    const headers = new Headers(customHeaders)
    if (body !== undefined) headers.set('Content-Type', 'application/json')
    if (auth) {
      const session = readSession()
      if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
    }
    if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey)
    return {
      ...rest,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    }
  }

  let response: Response
  try {
    response = await fetch(`${base}${path}`, buildInit())
  } catch {
    return networkError()
  }

  if (response.status === 401 && auth && retryOnUnauthorized) {
    const refreshed = await refreshSession()
    if (refreshed) {
      try {
        response = await fetch(`${base}${path}`, buildInit())
      } catch {
        return networkError()
      }
    }
  }

  return readResponse<T>(response)
}

function pageQuery(page: number, size: number, sort?: string): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (sort) params.set('sort', sort)
  return params.toString()
}

export const authApi = {
  login(email: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>(USER_API, '/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
      retryOnUnauthorized: false,
    })
  },

  register(values: RegisterRequest): Promise<User> {
    return request<User>(USER_API, '/register', {
      method: 'POST',
      body: values,
      auth: false,
      retryOnUnauthorized: false,
    })
  },

  logout(refreshToken: string): Promise<void> {
    return request<void>(USER_API, '/logout', {
      method: 'POST',
      body: { refreshToken },
      auth: false,
      retryOnUnauthorized: false,
    })
  },
}

export const userApi = {
  profile(userId: string): Promise<User> {
    return request<User>(USER_API, `/${encodeURIComponent(userId)}`)
  },
}

export const activityApi = {
  list(userId: string, page = 0, size = 100): Promise<Page<Activity>> {
    return request<Page<Activity>>(
      ACTIVITY_API,
      `/user/${encodeURIComponent(userId)}?${pageQuery(page, size, 'startTime,desc')}`,
    )
  },

  create(payload: ActivityPayload, idempotencyKey: string): Promise<Activity> {
    return request<Activity>(ACTIVITY_API, '', {
      method: 'POST',
      body: payload,
      idempotencyKey,
    })
  },
}

export const recommendationApi = {
  list(userId: string, page = 0, size = 3): Promise<Page<Recommendation>> {
    return request<Page<Recommendation>>(
      RECOMMENDATION_API,
      `/user/${encodeURIComponent(userId)}?${pageQuery(page, size, 'createdAt,desc')}`,
    )
  },
}

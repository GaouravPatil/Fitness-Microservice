import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  ApiError,
  authApi,
  clearSession,
  readSession,
  subscribeSession,
  userApi,
  writeSession,
  type Session,
} from '../lib/api'
import type { RegisterRequest, User } from '../lib/types'

interface AuthValue {
  user: User | null
  ready: boolean
  login: (email: string, password: string) => Promise<void>
  register: (values: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readSession())
  const [ready, setReady] = useState(false)

  useEffect(() => subscribeSession(setSession), [])

  useEffect(() => {
    let cancelled = false
    const current = readSession()

    if (!current) {
      setReady(true)
      return
    }

    userApi
      .profile(current.user.id)
      .then((user) => {
        if (cancelled) return
        writeSession({ ...current, user })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 401) clearSession()
      })
      .finally(() => {
        if (!cancelled) setReady(true)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const result = await authApi.login(email.trim(), password)
    writeSession({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user,
    })
  }, [])

  const register = useCallback(
    async (values: RegisterRequest) => {
      await authApi.register(values)
      await login(values.email, values.password)
    },
    [login],
  )

  const logout = useCallback(async () => {
    const current = readSession()
    if (current) {
      try {
        await authApi.logout(current.refreshToken)
      } catch {
        // Signing out locally is still the right outcome if the server is offline.
      }
    }
    clearSession()
  }, [])

  const value = useMemo(
    () => ({ user: session?.user ?? null, ready, login, register, logout }),
    [session, ready, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

export interface Preferences {
  weightKg: number
  dailyGoal: number
}

const DEFAULT_PREFERENCES: Preferences = {
  weightKg: 70,
  dailyGoal: 500,
}

const STORAGE_KEY = 'aura.preferences.v1'

interface SettingsValue {
  preferences: Preferences
  setPreferences: (patch: Partial<Preferences>) => void
}

const SettingsContext = createContext<SettingsValue | null>(null)

function clamp(value: number, min: number, max: number, fallback: number): number {
  if (!Number.isFinite(value)) return fallback
  return Math.min(max, Math.max(min, value))
}

function normalize(preferences: Preferences): Preferences {
  return {
    weightKg: clamp(preferences.weightKg, 25, 300, DEFAULT_PREFERENCES.weightKg),
    dailyGoal: clamp(preferences.dailyGoal, 100, 5000, DEFAULT_PREFERENCES.dailyGoal),
  }
}

function loadPreferences(): Preferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_PREFERENCES
    const parsed = JSON.parse(raw) as Partial<Preferences>
    return normalize({
      weightKg: parsed.weightKg ?? DEFAULT_PREFERENCES.weightKg,
      dailyGoal: parsed.dailyGoal ?? DEFAULT_PREFERENCES.dailyGoal,
    })
  } catch {
    return DEFAULT_PREFERENCES
  }
}

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [preferences, setState] = useState<Preferences>(loadPreferences)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences))
    } catch {
      // Preferences remain in memory when storage is unavailable.
    }
  }, [preferences])

  const setPreferences = useCallback((patch: Partial<Preferences>) => {
    setState((current) => normalize({ ...current, ...patch }))
  }, [])

  const value = useMemo(() => ({ preferences, setPreferences }), [preferences, setPreferences])

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

export function useSettings(): SettingsValue {
  const context = useContext(SettingsContext)
  if (!context) throw new Error('useSettings must be used within SettingsProvider')
  return context
}

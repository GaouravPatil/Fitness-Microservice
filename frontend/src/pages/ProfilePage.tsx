import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  Activity as ActivityGlyph,
  ArrowLeft,
  Bluetooth,
  Clock3,
  Flame,
  LoaderCircle,
  LogOut,
  Save,
  ShieldCheck,
  Sparkles,
  Watch,
} from 'lucide-react'
import { ErrorNotice } from '../components/Notice'
import { Toast } from '../components/Toast'
import { useAuth } from '../context/AuthContext'
import { useSettings } from '../context/SettingsContext'
import { ACTIVITY_META } from '../lib/activityMeta'
import { activityApi } from '../lib/api'
import { formatMonthYear, formatNumber, minutesLabel } from '../lib/date'
import { errorMessage } from '../lib/errors'
import { computeStats } from '../lib/insights'
import type { Activity } from '../lib/types'
import { fullName, initials } from '../lib/user'

export function ProfilePage() {
  const { user, logout } = useAuth()
  const { preferences, setPreferences } = useSettings()
  const navigate = useNavigate()

  const [activities, setActivities] = useState<Activity[]>([])
  const [statsLoading, setStatsLoading] = useState(true)
  const [statsError, setStatsError] = useState<string | null>(null)
  const [statsReload, setStatsReload] = useState(0)
  const [weight, setWeight] = useState(String(preferences.weightKg))
  const [goal, setGoal] = useState(String(preferences.dailyGoal))
  const [formError, setFormError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)

  useEffect(() => {
    if (!user) return
    let alive = true
    setStatsLoading(true)
    setStatsError(null)

    activityApi
      .list(user.id, 0, 200)
      .then((page) => {
        if (!alive) return
        setActivities(page.content)
      })
      .catch((loadError: unknown) => {
        if (!alive) return
        setStatsError(errorMessage(loadError))
      })
      .finally(() => {
        if (alive) setStatsLoading(false)
      })

    return () => {
      alive = false
    }
  }, [user, statsReload])

  const stats = useMemo(() => computeStats(activities), [activities])
  const favoriteMeta = stats.favoriteType ? ACTIVITY_META[stats.favoriteType] : null
  const dirty =
    weight !== String(preferences.weightKg) || goal !== String(preferences.dailyGoal)

  const closeToast = useCallback(() => setToast(null), [])

  if (!user) return null

  const savePreferences = () => {
    const weightValue = Number(weight)
    const goalValue = Number(goal)

    if (!Number.isFinite(weightValue) || weightValue < 25 || weightValue > 300) {
      setFormError('Weight must be between 25 and 300 kg.')
      return
    }
    if (!Number.isFinite(goalValue) || goalValue < 100 || goalValue > 5000) {
      setFormError('Daily goal must be between 100 and 5,000 kcal.')
      return
    }

    setPreferences({
      weightKg: Math.round(weightValue),
      dailyGoal: Math.round(goalValue),
    })
    setWeight(String(Math.round(weightValue)))
    setGoal(String(Math.round(goalValue)))
    setFormError(null)
    setToast('Preferences saved for this device.')
  }

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setLoggingOut(false)
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Account</p>
          <h1 className="page-title">Profile</h1>
          <p className="page-sub">
            Your identity, body settings and the quiet preferences that shape every estimate.
          </p>
        </div>
        <Link className="btn btn-ghost" to="/app">
          <ArrowLeft size={16} aria-hidden="true" />
          Back to overview
        </Link>
      </header>

      {statsError && (
        <ErrorNotice
          message={statsError}
          compact
          onRetry={() => setStatsReload((key) => key + 1)}
        />
      )}

      <div className="profile-grid">
        <section className="card identity-card">
          <div className="identity-top">
            <span className="avatar avatar-xl">{initials(user)}</span>
            <div className="identity-copy">
              <p className="eyebrow">Your space</p>
              <h2>{fullName(user)}</h2>
              <p>{user.email}</p>
            </div>
            <span className={`pill ${user.role === 'ADMIN' ? 'pill-lavender' : 'pill-soft'}`}>
              {user.role === 'ADMIN' ? 'Admin' : 'Member'}
            </span>
          </div>

          <div className="identity-meta">
            <div>
              <span>Member since</span>
              <strong>{formatMonthYear(user.createdAt)}</strong>
            </div>
            <div>
              <span>User ID</span>
              <strong className="mono" title={user.id}>
                {user.id}
              </strong>
            </div>
            <div>
              <span>Sign-in</span>
              <strong>Email &amp; password</strong>
            </div>
          </div>
        </section>

        <section className="card wearable-card">
          <div className="wearable-aura" aria-hidden="true">
            <span className="wearable-ring" />
            <span className="wearable-core">
              <Bluetooth size={24} />
            </span>
          </div>
          <p className="eyebrow">Next chapter</p>
          <h2 className="card-title">Wearable sync</h2>
          <p className="muted">
            Pair a smartwatch to stream heart rate and motion into AURA in real time. This
            dashboard is already shaped for live data.
          </p>
          <button type="button" className="btn btn-ghost btn-block" disabled>
            <Watch size={16} aria-hidden="true" />
            Pair device · soon
          </button>
        </section>

        <section className="card stats-card">
          <div className="strip-item">
            <span className="strip-icon" aria-hidden="true">
              <ActivityGlyph size={17} />
            </span>
            <span>Lifetime sessions</span>
            <strong>{statsLoading ? '—' : formatNumber(stats.lifetimeSessions)}</strong>
          </div>
          <div className="strip-item">
            <span className="strip-icon" aria-hidden="true">
              <Flame size={17} />
            </span>
            <span>Total burned</span>
            <strong>{statsLoading ? '—' : `${formatNumber(stats.lifetimeCalories)} kcal`}</strong>
          </div>
          <div className="strip-item">
            <span className="strip-icon" aria-hidden="true">
              <Clock3 size={17} />
            </span>
            <span>Time in motion</span>
            <strong>{statsLoading ? '—' : minutesLabel(stats.lifetimeMinutes)}</strong>
          </div>
          <div className="strip-item">
            <span className="strip-icon" aria-hidden="true">
              <Sparkles size={17} />
            </span>
            <span>Anchor activity</span>
            <strong>{statsLoading ? '—' : (favoriteMeta?.label ?? '—')}</strong>
          </div>
        </section>

        <section className="card preferences-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Estimation engine</p>
              <h2 className="card-title">Body &amp; goal</h2>
            </div>
          </div>

          <div className="form-grid">
            <div className="field">
              <label className="field-label" htmlFor="profile-weight">
                Body weight
              </label>
              <div className="input-wrap input-wrap-suffix">
                <input
                  id="profile-weight"
                  type="number"
                  min={25}
                  max={300}
                  step={0.5}
                  inputMode="decimal"
                  value={weight}
                  onChange={(event) => setWeight(event.target.value)}
                />
                <span className="input-suffix">kg</span>
              </div>
            </div>

            <div className="field">
              <label className="field-label" htmlFor="profile-goal">
                Daily burn goal
              </label>
              <div className="input-wrap input-wrap-suffix">
                <input
                  id="profile-goal"
                  type="number"
                  min={100}
                  max={5000}
                  step={25}
                  inputMode="numeric"
                  value={goal}
                  onChange={(event) => setGoal(event.target.value)}
                />
                <span className="input-suffix">kcal</span>
              </div>
            </div>
          </div>

          <p className="hint">
            Used only on this device to estimate calories with MET values. It is not sent to the
            server.
          </p>

          {formError && <ErrorNotice message={formError} compact />}

          <button
            type="button"
            className="btn btn-soft"
            onClick={savePreferences}
            disabled={!dirty}
          >
            <Save size={15} aria-hidden="true" />
            Save preferences
          </button>
        </section>

        <section className="card session-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Security</p>
              <h2 className="card-title">Session</h2>
            </div>
            <span className="icon-badge" aria-hidden="true">
              <ShieldCheck size={17} />
            </span>
          </div>

          <p className="muted">
            Access tokens rotate automatically in the background. Signing out revokes this
            device&apos;s refresh token.
          </p>

          <div className="session-meta">
            <span>Access token</span>
            <strong>15 min rotation</strong>
          </div>
          <div className="session-meta">
            <span>Refresh token</span>
            <strong>7 day expiry</strong>
          </div>

          <button
            type="button"
            className="btn btn-ghost btn-block"
            onClick={handleLogout}
            disabled={loggingOut}
          >
            {loggingOut ? (
              <LoaderCircle size={16} className="spin" aria-hidden="true" />
            ) : (
              <LogOut size={16} aria-hidden="true" />
            )}
            {loggingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </section>
      </div>

      <Toast message={toast} onClose={closeToast} />
    </div>
  )
}

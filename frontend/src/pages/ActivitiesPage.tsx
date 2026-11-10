import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ActivityRow } from '../components/ActivityRow'
import { EmptyState } from '../components/EmptyState'
import { LogActivityForm } from '../components/LogActivityForm'
import { ErrorNotice } from '../components/Notice'
import { Toast } from '../components/Toast'
import { useAuth } from '../context/AuthContext'
import { useSettings } from '../context/SettingsContext'
import { ACTIVITY_META } from '../lib/activityMeta'
import { activityApi, ApiError } from '../lib/api'
import { formatNumber } from '../lib/date'
import { errorMessage } from '../lib/errors'
import { countByType } from '../lib/insights'
import { ACTIVITY_TYPES, type Activity, type ActivityType } from '../lib/types'

type Filter = 'ALL' | ActivityType

function isActivityType(value: string | null): value is ActivityType {
  return value !== null && (ACTIVITY_TYPES as readonly string[]).includes(value)
}

export function ActivitiesPage() {
  const { user } = useAuth()
  const { preferences } = useSettings()
  const [searchParams] = useSearchParams()
  const requestedType = searchParams.get('type')
  const initialType: ActivityType = isActivityType(requestedType) ? requestedType : 'RUNNING'

  const [activities, setActivities] = useState<Activity[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!user) return
    let alive = true
    setLoading(true)
    setError(null)

    activityApi
      .list(user.id, 0, 100)
      .then((page) => {
        if (!alive) return
        setActivities(page.content)
        setTotal(page.totalElements)
      })
      .catch((loadError: unknown) => {
        if (!alive) return
        if (loadError instanceof ApiError && loadError.status === 401) return
        setError(errorMessage(loadError))
      })
      .finally(() => {
        if (alive) setLoading(false)
      })

    return () => {
      alive = false
    }
  }, [user, reloadKey])

  const counts = useMemo(() => countByType(activities), [activities])
  const filtered = useMemo(
    () => (filter === 'ALL' ? activities : activities.filter((a) => a.type === filter)),
    [activities, filter],
  )

  const handleCreated = useCallback((created: Activity) => {
    setActivities((current) =>
      [created, ...current].sort(
        (a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime(),
      ),
    )
    setTotal((current) => current + 1)
    setFilter('ALL')
    setToast('Session logged. Your pattern just got clearer.')
  }, [])

  const closeToast = useCallback(() => setToast(null), [])

  if (!user) return null

  const totalMinutes = activities.reduce((sum, activity) => sum + activity.duration, 0)
  const totalCalories = activities.reduce((sum, activity) => sum + activity.caloriesBurned, 0)

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Movement log</p>
          <h1 className="page-title">Activities</h1>
          <p className="page-sub">
            Every session sharpens your calorie forecast. Estimates use MET values and your body
            weight, then you can always adjust the number.
          </p>
        </div>
        <div className="head-summary">
          <div>
            <span>Loaded</span>
            <strong>{activities.length}</strong>
          </div>
          <div>
            <span>Minutes</span>
            <strong>{formatNumber(totalMinutes)}</strong>
          </div>
          <div>
            <span>Burned</span>
            <strong>{formatNumber(totalCalories)} kcal</strong>
          </div>
        </div>
      </header>

      {error && <ErrorNotice message={error} onRetry={() => setReloadKey((key) => key + 1)} />}

      <div className="activities-grid">
        <LogActivityForm
          userId={user.id}
          weightKg={preferences.weightKg}
          initialType={initialType}
          onCreated={handleCreated}
        />

        <section className="card history-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">History</p>
              <h2 className="card-title">Activity log</h2>
            </div>
            <span className="count-pill">{formatNumber(total)} sessions</span>
          </div>

          {counts.length > 0 && (
            <div className="chip-row" role="group" aria-label="Filter activities">
              <button
                type="button"
                className={`chip${filter === 'ALL' ? ' is-active' : ''}`}
                onClick={() => setFilter('ALL')}
              >
                All
                <span className="chip-count">{activities.length}</span>
              </button>
              {counts.map(({ type, count }) => (
                <button
                  key={type}
                  type="button"
                  className={`chip${filter === type ? ' is-active' : ''}`}
                  onClick={() => setFilter(type)}
                >
                  {ACTIVITY_META[type].short}
                  <span className="chip-count">{count}</span>
                </button>
              ))}
            </div>
          )}

          {loading ? (
            <div className="history-skeleton" aria-busy="true">
              <div className="skeleton skeleton-row" />
              <div className="skeleton skeleton-row" />
              <div className="skeleton skeleton-row" />
            </div>
          ) : filtered.length > 0 ? (
            <div className="activity-list">
              {filtered.map((activity) => (
                <ActivityRow key={activity.id} activity={activity} />
              ))}
            </div>
          ) : activities.length === 0 ? (
            <EmptyState title="Your log is wide open">
              Log your first session and AURA will start mapping calories, streaks and the
              sessions that fit your rhythm.
            </EmptyState>
          ) : (
            <EmptyState title="Nothing in this lane yet">
              No {filter === 'ALL' ? '' : ACTIVITY_META[filter].label.toLowerCase()} sessions
              in your loaded history. Try another filter or log a new one.
            </EmptyState>
          )}

          {!loading && total > activities.length && (
            <p className="history-note">
              Showing the {activities.length} most recent of {formatNumber(total)} sessions.
            </p>
          )}
        </section>
      </div>

      <Toast message={toast} onClose={closeToast} />
    </div>
  )
}

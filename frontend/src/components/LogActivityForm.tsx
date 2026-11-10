import { useEffect, useState, type FormEvent } from 'react'
import { LoaderCircle, Plus, Sparkles } from 'lucide-react'
import { ActivityIcon } from './ActivityIcon'
import { ErrorNotice } from './Notice'
import { ACTIVITY_META, estimateCalories } from '../lib/activityMeta'
import { activityApi } from '../lib/api'
import { parseDate, toDateTimeInput } from '../lib/date'
import { errorMessage } from '../lib/errors'
import { ACTIVITY_TYPES, type Activity, type ActivityType } from '../lib/types'

interface LogActivityFormProps {
  userId: string
  weightKg: number
  initialType?: ActivityType
  onCreated: (activity: Activity) => void
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `aura-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function LogActivityForm({
  userId,
  weightKg,
  initialType = 'RUNNING',
  onCreated,
}: LogActivityFormProps) {
  const [type, setType] = useState<ActivityType>(initialType)
  const [duration, setDuration] = useState('30')
  const [calories, setCalories] = useState(() =>
    String(estimateCalories(initialType, 30, weightKg)),
  )
  const [startTime, setStartTime] = useState(() => toDateTimeInput())
  const [caloriesDirty, setCaloriesDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const durationValue = Number(duration)
  const estimate = estimateCalories(type, durationValue, weightKg)

  useEffect(() => {
    if (!caloriesDirty) setCalories(String(estimate))
  }, [estimate, caloriesDirty])

  useEffect(() => {
    setType(initialType)
    setCaloriesDirty(false)
  }, [initialType])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (!Number.isFinite(durationValue) || durationValue < 1 || durationValue > 1440) {
      setError('Duration must be between 1 and 1440 minutes.')
      return
    }

    const caloriesValue = Number(calories)
    if (!Number.isFinite(caloriesValue) || caloriesValue < 0) {
      setError('Calories must be zero or more.')
      return
    }

    if (!parseDate(startTime)) {
      setError('Choose when this session happened.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      const created = await activityApi.create(
        {
          userId,
          type,
          duration: Math.round(durationValue),
          caloriesBurned: Math.round(caloriesValue),
          startTime,
        },
        newIdempotencyKey(),
      )
      onCreated(created)
      setDuration('30')
      setStartTime(toDateTimeInput())
      setCaloriesDirty(false)
      setCalories(String(estimateCalories(type, 30, weightKg)))
    } catch (submitError) {
      setError(errorMessage(submitError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card log-card">
      <div className="card-head">
        <div>
          <p className="eyebrow">New entry</p>
          <h2 className="card-title">Log activity</h2>
        </div>
        <span className="icon-badge icon-badge-lavender" aria-hidden="true">
          <Sparkles size={17} />
        </span>
      </div>

      <form className="log-form" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <span className="field-label">Movement</span>
          <div className="type-grid" role="radiogroup" aria-label="Activity type">
            {ACTIVITY_TYPES.map((activityType) => {
              const meta = ACTIVITY_META[activityType]
              return (
                <label
                  key={activityType}
                  className={`type-option${type === activityType ? ' is-active' : ''}`}
                >
                  <input
                    className="sr-only"
                    type="radio"
                    name="activity-type"
                    value={activityType}
                    checked={type === activityType}
                    onChange={() => {
                      setType(activityType)
                      setCaloriesDirty(false)
                    }}
                    disabled={busy}
                  />
                  <ActivityIcon type={activityType} size={17} />
                  <span>{meta.short}</span>
                </label>
              )
            })}
          </div>
        </div>

        <div className="form-grid">
          <div className="field">
            <label className="field-label" htmlFor="activity-duration">
              Duration
            </label>
            <div className="input-wrap input-wrap-suffix">
              <input
                id="activity-duration"
                type="number"
                min={1}
                max={1440}
                inputMode="numeric"
                value={duration}
                onChange={(event) => setDuration(event.target.value)}
                disabled={busy}
              />
              <span className="input-suffix">min</span>
            </div>
          </div>

          <div className="field">
            <label className="field-label" htmlFor="activity-calories">
              Calories
            </label>
            <div className="input-wrap input-wrap-suffix">
              <input
                id="activity-calories"
                type="number"
                min={0}
                step={1}
                inputMode="numeric"
                value={calories}
                onChange={(event) => {
                  setCalories(event.target.value)
                  setCaloriesDirty(true)
                }}
                disabled={busy}
              />
              <span className="input-suffix">kcal</span>
            </div>
          </div>
        </div>

        <p className="estimate-hint">
          <Sparkles size={13} aria-hidden="true" />
          <span>
            Estimate: <strong>{estimate} kcal</strong> at {weightKg} kg
          </span>
          <button
            type="button"
            className="link-button"
            onClick={() => {
              setCalories(String(estimate))
              setCaloriesDirty(false)
            }}
            disabled={busy}
          >
            Use estimate
          </button>
        </p>

        <div className="field">
          <label className="field-label" htmlFor="activity-start">
            When
          </label>
          <div className="input-wrap">
            <input
              id="activity-start"
              type="datetime-local"
              value={startTime}
              onChange={(event) => setStartTime(event.target.value)}
              disabled={busy}
            />
          </div>
        </div>

        {error && <ErrorNotice message={error} compact />}

        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
          {busy ? (
            <LoaderCircle size={16} className="spin" aria-hidden="true" />
          ) : (
            <Plus size={16} aria-hidden="true" />
          )}
          {busy ? 'Logging session…' : 'Log session'}
        </button>
      </form>
    </section>
  )
}

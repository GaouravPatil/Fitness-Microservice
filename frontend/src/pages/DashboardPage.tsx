import type { CSSProperties } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Activity as ActivityGlyph,
  ArrowUpRight,
  Clock3,
  Flame,
  Plus,
  Sparkles,
  TrendingDown,
  TrendingUp,
  Zap,
} from 'lucide-react'
import { ActivityIcon } from '../components/ActivityIcon'
import { ActivityRow } from '../components/ActivityRow'
import { EmptyState } from '../components/EmptyState'
import { ErrorNotice } from '../components/Notice'
import { WeeklyBars } from '../components/WeeklyBars'
import { MetricRing } from '../components/MetricRing'
import { useAuth } from '../context/AuthContext'
import { useSettings } from '../context/SettingsContext'
import { ACTIVITY_META } from '../lib/activityMeta'
import { activityApi, recommendationApi } from '../lib/api'
import {
  formatFullDate,
  formatNumber,
  formatWeekday,
  greeting,
  minutesLabel,
} from '../lib/date'
import { errorMessage } from '../lib/errors'
import {
  buildDailySeries,
  buildForecast,
  buildSuggestion,
  computeStats,
  peakDay,
} from '../lib/insights'
import type { Activity, ActivityType, Recommendation } from '../lib/types'

interface DashboardData {
  activities: Activity[]
  recommendations: Recommendation[]
}

const EMPTY_DATA: DashboardData = { activities: [], recommendations: [] }

function confidenceLabel(confidence: string): string {
  if (confidence === 'none') return 'Awaiting data'
  return `${confidence[0].toUpperCase()}${confidence.slice(1)} confidence`
}

function DashboardSkeleton() {
  return (
    <div className="page" aria-busy="true">
      <div className="skeleton skeleton-heading" />
      <div className="dash-grid">
        <div className="skeleton skeleton-hero" />
        <div className="skeleton skeleton-side" />
        <div className="skeleton skeleton-stat" />
        <div className="skeleton skeleton-stat" />
        <div className="skeleton skeleton-stat" />
        <div className="skeleton skeleton-stat" />
        <div className="skeleton skeleton-chart" />
        <div className="skeleton skeleton-side" />
      </div>
    </div>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const { preferences } = useSettings()
  const [data, setData] = useState<DashboardData>(EMPTY_DATA)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!user) return
    let alive = true
    setLoading(true)
    setError(null)

    Promise.allSettled([
      activityApi.list(user.id, 0, 200),
      recommendationApi.list(user.id, 0, 3),
    ])
      .then(([activityResult, recommendationResult]) => {
        if (!alive) return
        if (activityResult.status === 'rejected') {
          setError(errorMessage(activityResult.reason))
          return
        }
        setData({
          activities: activityResult.value.content,
          recommendations:
            recommendationResult.status === 'fulfilled'
              ? recommendationResult.value.content
              : [],
        })
      })
      .finally(() => {
        if (alive) setLoading(false)
      })

    return () => {
      alive = false
    }
  }, [user, reloadKey])

  const activities = data.activities

  const today = useMemo(() => buildDailySeries(activities, 1)[0], [activities])
  const week = useMemo(() => buildDailySeries(activities, 7), [activities])
  const stats = useMemo(() => computeStats(activities), [activities])
  const forecast = useMemo(() => buildForecast(activities), [activities])
  const suggestion = useMemo(
    () => buildSuggestion(activities, preferences.weightKg),
    [activities, preferences.weightKg],
  )
  const peak = useMemo(() => peakDay(week), [week])
  const latest = data.recommendations[0] ?? null
  const latestMeta = latest
    ? (ACTIVITY_META[latest.activityType as ActivityType] ?? ACTIVITY_META.OTHER)
    : null

  const coachNotes = useMemo(() => {
    const notes: string[] = []
    if (peak) {
      notes.push(
        `Your strongest day is ${formatWeekday(peak.date)} — ${formatNumber(peak.calories)} kcal.`,
      )
    }
    if (stats.favoriteType) {
      notes.push(`${ACTIVITY_META[stats.favoriteType].label} is your anchor activity.`)
    }
    if (stats.streak > 0) {
      notes.push(`${stats.streak}-day streak. Keep tomorrow easy to win.`)
    } else {
      notes.push('Log one session today to start a streak.')
    }
    return notes.slice(0, 3)
  }, [peak, stats.favoriteType, stats.streak])

  if (!user) return null
  if (loading) return <DashboardSkeleton />

  const goal = preferences.dailyGoal
  const goalProgress = goal > 0 ? Math.round((today.calories / goal) * 100) : 0
  const remaining = Math.max(goal - today.calories, 0)
  const suggestionMeta = ACTIVITY_META[suggestion.type]

  const subtitle =
    stats.weekSessions > 0
      ? `${stats.weekSessions} session${stats.weekSessions === 1 ? '' : 's'} this week · ${formatNumber(stats.weekCalories)} kcal moved`
      : 'A fresh week. One session is all it takes to start a pattern.'

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">{formatFullDate()}</p>
          <h1 className="page-title">
            {greeting()}, <span className="accent">{user.firstName}.</span>
          </h1>
          <p className="page-sub">{subtitle}</p>
        </div>
        <Link className="btn btn-primary" to="/app/activities">
          <Plus size={16} aria-hidden="true" />
          Log activity
        </Link>
      </header>

      {error && (
        <ErrorNotice message={error} onRetry={() => setReloadKey((key) => key + 1)} />
      )}

      <div className="dash-grid">
        <section className="card burn-card">
          <div className="ring-wrap">
            <MetricRing value={today.calories} max={goal} />
            <div className="ring-center">
              <span className="eyebrow">Today</span>
              <strong className="ring-value">{formatNumber(today.calories)}</strong>
              <span className="ring-sub">
                {goalProgress}% of {formatNumber(goal)} kcal
              </span>
            </div>
          </div>

          <div className="burn-copy">
            <span className="pill pill-lavender">
              <span className="pulse-dot" aria-hidden="true" />
              Live overview
            </span>
            <h2 className="card-title burn-title">
              {today.sessions > 0
                ? today.calories >= goal
                  ? 'Goal met. Beautifully done.'
                  : `${formatNumber(remaining)} kcal from today’s goal.`
                : 'The day is still yours.'}
            </h2>
            <p className="muted burn-text">
              {today.sessions > 0
                ? `You have moved for ${minutesLabel(today.minutes)} across ${today.sessions} session${today.sessions === 1 ? '' : 's'}.`
                : 'Move a little, log it, and the pattern begins. Even a walk counts.'}
            </p>

            <div className="mini-stats">
              <div>
                <span>Active</span>
                <strong>{minutesLabel(today.minutes)}</strong>
              </div>
              <div>
                <span>Sessions</span>
                <strong>{today.sessions}</strong>
              </div>
              <div>
                <span>Avg burn</span>
                <strong>
                  {today.sessions > 0 ? formatNumber(today.calories / today.sessions) : '0'} kcal
                </strong>
              </div>
            </div>

            <div className="button-row">
              <Link className="btn btn-primary btn-sm" to="/app/activities">
                <Plus size={15} aria-hidden="true" />
                Log a session
              </Link>
              <Link className="btn btn-ghost btn-sm" to="/app/profile">
                Set daily goal
              </Link>
            </div>
          </div>
        </section>

        <section className="card forecast-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Guesswork, grounded</p>
              <h2 className="card-title">Burn forecast</h2>
            </div>
            <span className="icon-badge" aria-hidden="true">
              <Sparkles size={17} />
            </span>
          </div>

          <p className="forecast-value">
            {forecast.weeklyCalories > 0 ? formatNumber(forecast.weeklyCalories) : '—'}
            {forecast.weeklyCalories > 0 && <span> kcal</span>}
          </p>
          <p className="muted forecast-copy">
            {forecast.activeDays > 0
              ? 'Projected over your next 7 days from the last two weeks of movement.'
              : 'Log a session and AURA will start reading your pattern.'}
          </p>

          <div className="forecast-meta">
            {forecast.activeDays > 0 ? (
              <span className={`trend ${forecast.trendPercent >= 0 ? 'up' : 'down'}`}>
                {forecast.trendPercent >= 0 ? (
                  <TrendingUp size={14} aria-hidden="true" />
                ) : (
                  <TrendingDown size={14} aria-hidden="true" />
                )}
                {forecast.trendPercent >= 0 ? '+' : ''}
                {forecast.trendPercent}% vs last week
              </span>
            ) : (
              <span className="trend neutral">No baseline yet</span>
            )}
            <span className="confidence">{confidenceLabel(forecast.confidence)}</span>
          </div>

          <div className="forecast-foot">
            <span>Daily average</span>
            <strong>{formatNumber(forecast.perDay)} kcal</strong>
          </div>
        </section>

        <article className="card stat-card">
          <span className="stat-icon" aria-hidden="true">
            <Flame size={18} />
          </span>
          <span className="eyebrow">7-day burn</span>
          <strong className="stat-value">
            {formatNumber(stats.weekCalories)} <span>kcal</span>
          </strong>
          <span className={`stat-hint ${stats.calorieTrend >= 0 ? 'up' : 'down'}`}>
            {stats.calorieTrend >= 0 ? '+' : ''}
            {stats.calorieTrend}% vs prior week
          </span>
        </article>

        <article className="card stat-card">
          <span className="stat-icon" aria-hidden="true">
            <ActivityGlyph size={18} />
          </span>
          <span className="eyebrow">Sessions</span>
          <strong className="stat-value">
            {stats.weekSessions} <span>this week</span>
          </strong>
          <span className="stat-hint">{stats.lifetimeSessions} all time</span>
        </article>

        <article className="card stat-card">
          <span className="stat-icon" aria-hidden="true">
            <Clock3 size={18} />
          </span>
          <span className="eyebrow">Active minutes</span>
          <strong className="stat-value">
            {formatNumber(stats.weekMinutes)} <span>min</span>
          </strong>
          <span className="stat-hint">
            {minutesLabel(
              Math.round(stats.weekMinutes / Math.max(stats.weekSessions, 1)),
            )}{' '}
            per session
          </span>
        </article>

        <article className="card stat-card">
          <span className="stat-icon" aria-hidden="true">
            <Zap size={18} />
          </span>
          <span className="eyebrow">Day streak</span>
          <strong className="stat-value">
            {stats.streak} <span>{stats.streak === 1 ? 'day' : 'days'}</span>
          </strong>
          <span className="stat-hint">
            {stats.streak > 0 ? 'Keep the rhythm alive' : 'Start today'}
          </span>
        </article>

        <section className="card chart-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Last 7 days</p>
              <h2 className="card-title">Kilocalories, by day</h2>
            </div>
            <span className="chart-legend">
              <span className="legend-dash" aria-hidden="true" />
              Daily goal
            </span>
          </div>

          <WeeklyBars data={week} goal={goal} />

          <div className="chart-foot">
            <span>
              {peak
                ? `Peak: ${formatWeekday(peak.date)} · ${formatNumber(peak.calories)} kcal`
                : 'No movement logged in this window yet'}
            </span>
            <Link className="text-link" to="/app/activities">
              Full history
              <ArrowUpRight size={14} aria-hidden="true" />
            </Link>
          </div>
        </section>

        <section className="card suggestion-card">
          <div className="suggestion-top">
            <span
              className="activity-icon activity-icon-lg"
              style={{ '--accent': suggestionMeta.accent } as CSSProperties}
            >
              <ActivityIcon type={suggestion.type} size={22} />
            </span>
            <span className="pill pill-soft">
              {suggestion.kind === 'starter'
                ? 'First move'
                : suggestion.kind === 'balance'
                  ? 'Pattern nudge'
                  : 'Keep rhythm'}
            </span>
          </div>

          <p className="eyebrow">Suggested next</p>
          <h2 className="suggestion-name">{suggestionMeta.label}</h2>
          <div className="suggestion-meta">
            <span>{suggestion.duration} min</span>
            <span className="dot" aria-hidden="true" />
            <span>≈ {formatNumber(suggestion.calories)} kcal</span>
          </div>
          <p className="muted suggestion-copy">{suggestion.reason}</p>

          <Link
            className="btn btn-soft btn-block"
            to={`/app/activities?type=${suggestion.type}`}
          >
            <Plus size={15} aria-hidden="true" />
            Log this session
          </Link>
        </section>

        <section className="card recent-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Recent</p>
              <h2 className="card-title">Latest sessions</h2>
            </div>
            <Link className="text-link" to="/app/activities">
              See all
              <ArrowUpRight size={14} aria-hidden="true" />
            </Link>
          </div>

          {activities.length > 0 ? (
            <div className="activity-list">
              {activities.slice(0, 4).map((activity) => (
                <ActivityRow key={activity.id} activity={activity} />
              ))}
            </div>
          ) : (
            <EmptyState
              title="No sessions yet"
              action={
                <Link className="btn btn-soft btn-sm" to="/app/activities">
                  <Plus size={15} aria-hidden="true" />
                  Log your first session
                </Link>
              }
            >
              Your first entry will unlock weekly burn, streaks and a calorie forecast.
            </EmptyState>
          )}
        </section>

        <section className="card coach-card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Coach notes</p>
              <h2 className="card-title">
                {latest ? 'From your latest session' : 'From your pattern'}
              </h2>
            </div>
            <span className="icon-badge icon-badge-lavender" aria-hidden="true">
              <Sparkles size={17} />
            </span>
          </div>

          {latest ? (
            <>
              <span className="provider-pill">
                {latest.provider === 'rules' ? 'Rule-based coach' : 'AI coach'}
                {latestMeta ? ` · ${latestMeta.label}` : ''}
              </span>
              <p className="coach-quote">“{latest.recommendation}”</p>
              {(latest.suggestions?.length ?? 0) > 0 && (
                <ul className="coach-list">
                  {latest.suggestions.slice(0, 3).map((item) => (
                    <li key={item}>
                      <span className="list-dot" aria-hidden="true" />
                      {item}
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <>
              <p className="coach-quote">
                “Consistency beats intensity. Your pattern is still being written.”
              </p>
              <ul className="coach-list">
                {coachNotes.map((note) => (
                  <li key={note}>
                    <span className="list-dot" aria-hidden="true" />
                    {note}
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>
    </div>
  )
}

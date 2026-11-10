import { ArrowRight, Eye, EyeOff, LoaderCircle, Lock, Mail, ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { AuthShell } from '../components/AuthShell'
import { BootScreen } from '../components/BootScreen'
import { ErrorNotice } from '../components/Notice'
import { useAuth } from '../context/AuthContext'
import { errorMessage } from '../lib/errors'

export function LoginPage() {
  const { user, ready, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!ready) return <BootScreen />
  if (user) return <Navigate to="/app" replace />

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!email.trim() || !password) {
      setError('Enter your email and password to continue.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      await login(email, password)
      const from = (location.state as { from?: { pathname?: string; search?: string } } | null)
        ?.from
      const target = from?.pathname ? `${from.pathname}${from.search ?? ''}` : '/app'
      navigate(target, { replace: true })
    } catch (loginError) {
      setError(errorMessage(loginError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell footer="© 2026 Gaourav Patil. All rights reserved.">
      <div className="auth-form-wrap">
        <div className="auth-heading">
          <p className="eyebrow">Welcome back</p>
          <h1>Sign in</h1>
          <p>Pick up where your body left off.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          {error && <ErrorNotice message={error} compact />}

          <div className="field">
            <label className="field-label" htmlFor="login-email">
              Email
            </label>
            <div className="input-wrap">
              <Mail size={17} aria-hidden="true" />
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={busy}
              />
            </div>
          </div>

          <div className="field">
            <label className="field-label" htmlFor="login-password">
              Password
            </label>
            <div className="input-wrap">
              <Lock size={17} aria-hidden="true" />
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Your password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={busy}
              />
              <button
                type="button"
                className="input-action"
                onClick={() => setShowPassword((current) => !current)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? (
              <LoaderCircle size={17} className="spin" aria-hidden="true" />
            ) : (
              <ArrowRight size={17} aria-hidden="true" />
            )}
            {busy ? 'Signing in…' : 'Enter your space'}
          </button>
        </form>

        <p className="auth-switch">
          New to AURA? <Link to="/signup">Create an account</Link>
        </p>

        <p className="auth-footnote">
          <ShieldCheck size={14} aria-hidden="true" />
          Short-lived tokens. Rotating sessions. Your activity stays yours.
        </p>
      </div>
    </AuthShell>
  )
}

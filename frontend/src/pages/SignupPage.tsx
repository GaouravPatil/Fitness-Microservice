import {
  ArrowRight,
  Eye,
  EyeOff,
  LoaderCircle,
  Lock,
  Mail,
  ShieldCheck,
  UserRound,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { AuthShell } from '../components/AuthShell'
import { BootScreen } from '../components/BootScreen'
import { ErrorNotice } from '../components/Notice'
import { useAuth } from '../context/AuthContext'
import { errorMessage } from '../lib/errors'

export function SignupPage() {
  const { user, ready, register } = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!ready) return <BootScreen />
  if (user) return <Navigate to="/app" replace />

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (!firstName.trim() || !lastName.trim()) {
      setError('Add your first and last name.')
      return
    }
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Enter a valid email address.')
      return
    }
    if (password.length < 8 || password.length > 72) {
      setError('Password must be between 8 and 72 characters.')
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    setBusy(true)
    setError(null)
    try {
      await register({
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
      })
      navigate('/app', { replace: true })
    } catch (registerError) {
      setError(errorMessage(registerError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <div className="auth-form-wrap">
        <div className="auth-heading">
          <p className="eyebrow">Start your rhythm</p>
          <h1>Create account</h1>
          <p>A calm home for every run, lift and long walk.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          {error && <ErrorNotice message={error} compact />}

          <div className="form-grid">
            <div className="field">
              <label className="field-label" htmlFor="signup-first-name">
                First name
              </label>
              <div className="input-wrap">
                <UserRound size={17} aria-hidden="true" />
                <input
                  id="signup-first-name"
                  type="text"
                  autoComplete="given-name"
                  placeholder="Jane"
                  value={firstName}
                  onChange={(event) => setFirstName(event.target.value)}
                  disabled={busy}
                />
              </div>
            </div>

            <div className="field">
              <label className="field-label" htmlFor="signup-last-name">
                Last name
              </label>
              <div className="input-wrap">
                <UserRound size={17} aria-hidden="true" />
                <input
                  id="signup-last-name"
                  type="text"
                  autoComplete="family-name"
                  placeholder="Doe"
                  value={lastName}
                  onChange={(event) => setLastName(event.target.value)}
                  disabled={busy}
                />
              </div>
            </div>
          </div>

          <div className="field">
            <label className="field-label" htmlFor="signup-email">
              Email
            </label>
            <div className="input-wrap">
              <Mail size={17} aria-hidden="true" />
              <input
                id="signup-email"
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
            <label className="field-label" htmlFor="signup-password">
              Password
            </label>
            <div className="input-wrap">
              <Lock size={17} aria-hidden="true" />
              <input
                id="signup-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="At least 8 characters"
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

          <div className="field">
            <label className="field-label" htmlFor="signup-confirm-password">
              Confirm password
            </label>
            <div className="input-wrap">
              <Lock size={17} aria-hidden="true" />
              <input
                id="signup-confirm-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Repeat your password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={busy}
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? (
              <LoaderCircle size={17} className="spin" aria-hidden="true" />
            ) : (
              <ArrowRight size={17} aria-hidden="true" />
            )}
            {busy ? 'Building your space…' : 'Create my space'}
          </button>
        </form>

        <p className="auth-switch">
          Already moving with us? <Link to="/login">Sign in</Link>
        </p>

        <p className="auth-footnote">
          <ShieldCheck size={14} aria-hidden="true" />
          Passwords are hashed with BCrypt and never leave the server.
        </p>
      </div>
    </AuthShell>
  )
}

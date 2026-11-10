import { lazy, Suspense, type ReactNode } from 'react'
import { useMediaQuery } from '../lib/useMediaQuery'
import { Logo } from './Logo'

const AuraScene = lazy(() => import('./AuraScene'))

interface AuthShellProps {
  children: ReactNode
  footer?: ReactNode
}

export function AuthShell({ children, footer }: AuthShellProps) {
  const showScene = useMediaQuery('(min-width: 901px)')
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)')

  return (
    <div className="auth-shell">
      <aside className="auth-visual">
        <div className="auth-visual-scene" aria-hidden="true">
          {showScene && !reduceMotion && (
            <Suspense fallback={null}>
              <AuraScene variant="hero" />
            </Suspense>
          )}
        </div>
        <div className="auth-visual-top">
          <Logo />
        </div>
        <div className="auth-visual-copy">
          <p className="eyebrow">Movement, measured</p>
          <h1>
            Every body
            <br />
            keeps a <em>rhythm.</em>
          </h1>
          <p className="auth-visual-text">
            Calorie intelligence, activity patterns and quiet coaching — in one considered
            space.
          </p>
        </div>
        <div className="auth-visual-foot">
          <span>01 / Track</span>
          <span>02 / Understand</span>
          <span>03 / Improve</span>
        </div>
      </aside>

      <main className="auth-panel">
        <div className="auth-panel-inner">
          <div className="auth-mobile-brand">
            <Logo />
          </div>
          {children}
        </div>
        {footer && <div className="auth-rights">{footer}</div>}
      </main>
    </div>
  )
}

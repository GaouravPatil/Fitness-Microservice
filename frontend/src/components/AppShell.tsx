import { Suspense } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import {
  Activity,
  Bluetooth,
  ChevronRight,
  LayoutDashboard,
  UserRound,
  type LucideIcon,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { initials } from '../lib/user'
import { Logo } from './Logo'

interface NavEntry {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

const NAV: NavEntry[] = [
  { to: '/app', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/app/activities', label: 'Activities', icon: Activity },
  { to: '/app/profile', label: 'Profile', icon: UserRound },
]

export function AppShell() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <div className="shell">
      <div className="shell-scene" aria-hidden="true" />

      <aside className="sidebar">
        <div className="sidebar-top">
          <Logo to="/app" />
        </div>

        <nav className="side-nav" aria-label="Primary">
          {NAV.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`}
              >
                <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
                <span>{item.label}</span>
                <ChevronRight className="nav-arrow" size={14} aria-hidden="true" />
              </NavLink>
            )
          })}
        </nav>

        <div className="sidebar-bottom">
          <div className="watch-teaser">
            <span className="watch-teaser-icon" aria-hidden="true">
              <Bluetooth size={16} />
            </span>
            <div>
              <strong>Watch sync</strong>
              <p>Live heart rate and motion — next release.</p>
            </div>
          </div>

          <Link to="/app/profile" className="user-chip">
            <span className="avatar">{initials(user)}</span>
            <span className="user-chip-text">
              <strong>
                {user.firstName} {user.lastName}
              </strong>
              <span>{user.email}</span>
            </span>
          </Link>
        </div>
      </aside>

      <header className="mobile-bar">
        <Logo to="/app" compact />
        <Link to="/app/profile" className="avatar avatar-sm" aria-label="Open profile">
          {initials(user)}
        </Link>
      </header>

      <main className="shell-main">
        <Suspense
          fallback={
            <div className="page" aria-busy="true">
              <div className="skeleton skeleton-heading" />
              <div className="skeleton skeleton-chart" />
            </div>
          }
        >
          <Outlet />
        </Suspense>
      </main>

      <nav className="mobile-nav" aria-label="Primary mobile">
        {NAV.map((item) => {
          const Icon = item.icon
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `mobile-nav-link${isActive ? ' is-active' : ''}`}
            >
              <Icon size={19} strokeWidth={1.8} aria-hidden="true" />
              <span>{item.label}</span>
            </NavLink>
          )
        })}
      </nav>
    </div>
  )
}

import { Link } from 'react-router-dom'

interface LogoProps {
  to?: string
  compact?: boolean
}

export function Logo({ to = '/', compact = false }: LogoProps) {
  return (
    <Link className="brand" to={to} aria-label="AURA home">
      <span className="brand-mark" aria-hidden="true">
        <span className="brand-core" />
      </span>
      <span className="brand-name">AURA</span>
      {!compact && <span className="brand-pill">fitness</span>}
    </Link>
  )
}

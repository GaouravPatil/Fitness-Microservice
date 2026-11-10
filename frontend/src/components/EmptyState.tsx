import { Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  children: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-icon" aria-hidden="true">
        <Sparkles size={20} />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  )
}

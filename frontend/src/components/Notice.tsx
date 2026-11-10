import { AlertCircle, RefreshCw } from 'lucide-react'

interface NoticeProps {
  message: string
  onRetry?: () => void
  compact?: boolean
}

export function ErrorNotice({ message, onRetry, compact = false }: NoticeProps) {
  return (
    <div className={`notice notice-error${compact ? ' is-compact' : ''}`} role="alert">
      <AlertCircle size={16} aria-hidden="true" />
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="notice-action" onClick={onRetry}>
          <RefreshCw size={13} aria-hidden="true" />
          Retry
        </button>
      )}
    </div>
  )
}

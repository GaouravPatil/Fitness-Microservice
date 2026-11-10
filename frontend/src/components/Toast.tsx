import { useEffect } from 'react'
import { CheckCircle2, X } from 'lucide-react'

interface ToastProps {
  message: string | null
  onClose: () => void
}

export function Toast({ message, onClose }: ToastProps) {
  useEffect(() => {
    if (!message) return
    const timer = window.setTimeout(onClose, 3600)
    return () => window.clearTimeout(timer)
  }, [message, onClose])

  if (!message) return null

  return (
    <div className="toast" role="status">
      <CheckCircle2 size={16} aria-hidden="true" />
      <span>{message}</span>
      <button type="button" className="toast-close" onClick={onClose} aria-label="Dismiss message">
        <X size={14} aria-hidden="true" />
      </button>
    </div>
  )
}

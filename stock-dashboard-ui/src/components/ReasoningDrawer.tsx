import { X, Loader2 } from 'lucide-react'

interface Props {
  open: boolean
  loading: boolean
  title: string | null
  data: unknown | null
  error: string | null
  onClose: () => void
}

export function ReasoningDrawer({ open, loading, title, data, error, onClose }: Props) {
  return (
    <div
      className={
        'fixed top-0 right-0 h-full w-96 z-50 flex flex-col bg-card border-l border-border shadow-2xl transition-transform duration-300 ' +
        (open ? 'translate-x-0' : 'translate-x-full')
      }
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3 px-4 py-3 border-b border-border shrink-0">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-0.5">
            Reasoning Agent
          </p>
          {title && (
            <p className="text-xs text-text-primary leading-snug line-clamp-2">{title}</p>
          )}
        </div>
        <button
          onClick={onClose}
          className="shrink-0 mt-0.5 text-muted hover:text-text-primary transition-colors"
          aria-label="Close"
        >
          <X size={16} />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto px-4 py-4 text-sm">
        {loading && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted">
            <Loader2 size={24} className="animate-spin" />
            <span className="text-xs">Calling reasoning agent…</span>
          </div>
        )}

        {!loading && error && (
          <div className="bg-negative/10 border border-negative/30 rounded-lg p-3 text-negative text-xs">
            {error}
          </div>
        )}

        {!loading && !error && data != null && (
          <pre className="text-xs text-text-primary whitespace-pre-wrap break-words leading-relaxed font-mono bg-surface border border-border rounded-lg p-3">
            {typeof data === 'string' ? data : JSON.stringify(data, null, 2)}
          </pre>
        )}

        {!loading && !error && data == null && (
          <div className="text-muted text-xs text-center mt-8">No response yet.</div>
        )}
      </div>
    </div>
  )
}

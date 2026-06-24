import type { ReactNode } from 'react'
import { X, Loader2, TrendingUp, TrendingDown, Minus, AlertTriangle } from 'lucide-react'
import type { OutlookEntry, OutlookHorizon, ReasoningResponse } from '../types'

interface Props {
  open: boolean
  loading: boolean
  title: string | null
  data: ReasoningResponse | null
  error: string | null
  onClose: () => void
}

const HORIZON_LABEL: Record<OutlookHorizon, string> = {
  short: 'Short-term',
  medium: 'Medium-term',
  large: 'Long-term',
}

function DirectionBadge({ entry }: { entry: OutlookEntry }) {
  const isUp = entry.direction === 'up'
  const isDown = entry.direction === 'down'
  const Icon = isUp ? TrendingUp : isDown ? TrendingDown : Minus
  const cls = isUp
    ? 'text-positive bg-positive/10 border-positive/30'
    : isDown
    ? 'text-negative bg-negative/10 border-negative/30'
    : 'text-muted bg-border/40 border-border'
  return (
    <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded border text-[10px] font-semibold uppercase tracking-wide ${cls}`}>
      <Icon size={11} />
      {entry.direction} · {entry.magnitude}
    </span>
  )
}

function ConfidenceBar({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(1, value)) * 100
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-border/40 rounded-full overflow-hidden">
        <div
          className="h-full bg-accent rounded-full transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-[10px] font-mono text-muted tabular-nums">{pct.toFixed(0)}%</span>
    </div>
  )
}

function OutlookCard({ horizon, entry }: { horizon: OutlookHorizon; entry: OutlookEntry }) {
  return (
    <div className="bg-surface border border-border rounded-lg p-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-text-primary">
          {HORIZON_LABEL[horizon]}
        </span>
        <DirectionBadge entry={entry} />
      </div>
      <ConfidenceBar value={entry.confidence} />
      <p dir="auto" className="text-xs text-text-primary leading-relaxed">
        {entry.explanation}
      </p>
    </div>
  )
}

function Section({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <section className="space-y-1.5">
      <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted">{label}</h3>
      {children}
    </section>
  )
}

function Prose({ children }: { children: ReactNode }) {
  return (
    <p
      dir="auto"
      className="text-xs text-text-primary leading-relaxed bg-surface border border-border rounded-lg p-3"
    >
      {children}
    </p>
  )
}

export function ReasoningDrawer({ open, loading, title, data, error, onClose }: Props) {
  return (
    <div
      className={
        'fixed top-0 right-0 h-full w-96 z-50 flex flex-col bg-card border-l border-border shadow-2xl transition-transform duration-300 ' +
        (open ? 'translate-x-0' : 'translate-x-full')
      }
    >
      <div className="flex items-start justify-between gap-3 px-4 py-3 border-b border-border shrink-0">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-0.5">
            Reasoning Agent
            {data?.ticker && (
              <span className="ml-1.5 text-text-primary">· {data.ticker}</span>
            )}
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

        {!loading && !error && data && (
          <div className="space-y-4">
            <Section label="Summary">
              <Prose>{data.summary}</Prose>
            </Section>

            <Section label="Technical view">
              <Prose>{data.technical_view}</Prose>
            </Section>

            <Section label="Sentiment">
              <Prose>{data.sentiment_note}</Prose>
            </Section>

            <Section label="Outlook">
              <div className="space-y-2">
                {(['short', 'medium', 'large'] as OutlookHorizon[]).map((h) => (
                  <OutlookCard key={h} horizon={h} entry={data.outlook[h]} />
                ))}
              </div>
            </Section>

            <Section label="Risks">
              <div
                dir="auto"
                className="flex gap-2 bg-negative/5 border border-negative/30 rounded-lg p-3 text-xs text-text-primary leading-relaxed"
              >
                <AlertTriangle size={14} className="shrink-0 mt-0.5 text-negative" />
                <span>{data.risks}</span>
              </div>
            </Section>
          </div>
        )}

        {!loading && !error && !data && (
          <div className="text-muted text-xs text-center mt-8">No response yet.</div>
        )}
      </div>
    </div>
  )
}

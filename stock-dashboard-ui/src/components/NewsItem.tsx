import { ExternalLink } from 'lucide-react'
import type { NewsEvent } from '../types'

export function NewsItem({ event }: { event: NewsEvent }) {
  const time = new Date(event.datetime).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div className="bg-card border border-border rounded-xl p-4 space-y-2 hover:border-border/80 transition-colors">
      <div className="flex justify-between items-start gap-4">
        <p className="text-sm font-medium leading-snug">{event.title}</p>
        {event.url && (
          <a
            href={event.url}
            target="_blank"
            rel="noreferrer"
            className="text-muted hover:text-positive transition-colors shrink-0 mt-0.5"
            onClick={(e) => e.stopPropagation()}
          >
            <ExternalLink size={13} />
          </a>
        )}
      </div>

      {event.teaser && (
        <p className="text-xs text-muted line-clamp-2 leading-relaxed">{event.teaser}</p>
      )}

      <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted pt-0.5">
        {event.source && <span className="font-medium">{event.source}</span>}
        {event.source && <span>·</span>}
        <span>{time}</span>
        {event.categories.slice(0, 3).map((c) => (
          <span key={c} className="bg-border/60 px-1.5 py-0.5 rounded text-[10px]">
            {c}
          </span>
        ))}
      </div>
    </div>
  )
}

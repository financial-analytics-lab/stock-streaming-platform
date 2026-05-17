import { ExternalLink } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Article } from '../types'

interface Props {
  article: Article
  /** Slot reserved for future per-article actions (e.g. sentiment analysis button). */
  action?: ReactNode
}

export function NewsItem({ article, action }: Props) {
  const time = new Date(article.publishedAt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div className="bg-card border border-border rounded-xl p-4 space-y-2 hover:border-border/80 transition-colors">
      <div className="flex justify-between items-start gap-4">
        <p className="text-sm font-medium leading-snug">{article.title}</p>
        <div className="flex items-center gap-2 shrink-0 mt-0.5">
          {action}
          {article.url && (
            <a
              href={article.url}
              target="_blank"
              rel="noreferrer"
              className="text-muted hover:text-positive transition-colors"
              onClick={(e) => e.stopPropagation()}
            >
              <ExternalLink size={13} />
            </a>
          )}
        </div>
      </div>

      {article.teaser && (
        <p className="text-xs text-muted line-clamp-2 leading-relaxed">{article.teaser}</p>
      )}

      <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted pt-0.5">
        {article.source && <span className="font-medium">{article.source}</span>}
        {article.source && <span>·</span>}
        <span>{time}</span>
        {article.section && (
          <span className="bg-border/60 px-1.5 py-0.5 rounded text-[10px]">{article.section}</span>
        )}
      </div>
    </div>
  )
}

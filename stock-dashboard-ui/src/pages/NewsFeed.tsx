import { useEffect, useState } from 'react'
import { useMarketStore } from '../store/useMarketStore'
import { NewsItem } from '../components/NewsItem'
import { api } from '../lib/api'

export function NewsFeed() {
  const news = useMarketStore((s) => s.news)
  const addNews = useMarketStore((s) => s.addNews)
  const [filter, setFilter] = useState<string | null>(null)

  useEffect(() => {
    api.getNews(100).then((events) => {
      events.forEach((e) => addNews(e))
    }).catch(() => {})
  }, [addNews])

  const allCategories = [...new Set(news.flatMap((n) => n.categories))].slice(0, 12)
  const filtered = filter ? news.filter((n) => n.categories.includes(filter)) : news

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h1 className="text-xl font-bold">News Feed</h1>
        <div className="text-sm text-muted mt-0.5">{news.length} articles received</div>
      </div>

      {allCategories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setFilter(null)}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              !filter
                ? 'bg-positive text-surface'
                : 'bg-border/60 text-muted hover:text-text-primary hover:bg-border'
            }`}
          >
            All ({news.length})
          </button>
          {allCategories.map((cat) => {
            const count = news.filter((n) => n.categories.includes(cat)).length
            return (
              <button
                key={cat}
                onClick={() => setFilter(cat === filter ? null : cat)}
                className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                  filter === cat
                    ? 'bg-positive text-surface'
                    : 'bg-border/60 text-muted hover:text-text-primary hover:bg-border'
                }`}
              >
                {cat} ({count})
              </button>
            )
          })}
        </div>
      )}

      <div className="space-y-3">
        {filtered.length === 0 ? (
          <div className="bg-card border border-border rounded-xl p-8 text-center text-muted">
            <div className="text-3xl mb-2">📰</div>
            <div className="text-sm">
              {news.length === 0
                ? 'No news events received yet. Start the news service to see articles here.'
                : 'No articles match the selected filter.'}
            </div>
          </div>
        ) : (
          filtered.map((n) => <NewsItem key={n.id} event={n} />)
        )}
      </div>
    </div>
  )
}

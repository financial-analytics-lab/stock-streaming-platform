import { useEffect, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { NewsItem } from '../components/NewsItem'
import { ReasoningDrawer } from '../components/ReasoningDrawer'
import { api } from '../lib/api'
import { postReasoning } from '../lib/reasoningApi'
import type { Article, NewsResponse, ReasoningRequest } from '../types'

export function NewsFeed() {
  const [data, setData] = useState<NewsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [drawerLoading, setDrawerLoading] = useState(false)
  const [drawerData, setDrawerData] = useState<unknown | null>(null)
  const [drawerError, setDrawerError] = useState<string | null>(null)
  const [activeArticle, setActiveArticle] = useState<Article | null>(null)

  const handleReason = (article: Article, company = '') => {
    const dt = new Date(article.publishedAt)
    const req: ReasoningRequest = {
      symbol: article.symbol,
      company,
      source: article.source,
      isin: (article.raw?.isin as string) ?? '',
      id: article.id,
      date: dt.toISOString().slice(0, 10),
      time: dt.toISOString().slice(11, 19),
      datetime: article.publishedAt,
      title: article.title,
      teaser: article.teaser,
      body: article.body ?? '',
      url: article.url,
      imageUrl: article.imageUrl,
      section: article.section,
      raw: article.raw,
    }
    setActiveArticle(article)
    setDrawerOpen(true)
    setDrawerLoading(true)
    setDrawerData(null)
    setDrawerError(null)
    postReasoning(req)
      .then((resp) => setDrawerData(resp.data))
      .catch((err) => setDrawerError(err?.message ?? 'Request failed'))
      .finally(() => setDrawerLoading(false))
  }

  useEffect(() => {
    setLoading(true)
    setError(null)
    api
      .getNews()
      .then((resp) => {
        setData(resp)
        // Expand the first group by default for discoverability.
        if (resp.groups.length > 0) setExpanded(new Set([resp.groups[0].symbol]))
      })
      .catch(() => setError('Failed to load news'))
      .finally(() => setLoading(false))
  }, [])

  const toggle = (symbol: string) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(symbol)) next.delete(symbol)
      else next.add(symbol)
      return next
    })

  const asOf = data?.asOf
  const asOfLabel = asOf && asOf !== '1970-01-01T00:00:00Z'
    ? new Date(asOf).toLocaleString()
    : null

  const totalArticles = data?.groups.reduce((sum, g) => sum + g.articles.length, 0) ?? 0

  return (
    <>
    <div className="space-y-5 max-w-3xl">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold">News</h1>
          <div className="text-sm text-muted mt-0.5">
            {totalArticles} articles across {data?.groups.length ?? 0} symbols
          </div>
        </div>
        {asOfLabel && (
          <div className="text-xs text-muted bg-border/40 px-2 py-1 rounded">
            As of <span className="text-text-primary font-mono">{asOfLabel}</span>
          </div>
        )}
      </div>

      {loading && (
        <div className="bg-card border border-border rounded-xl p-8 text-center text-muted text-sm">
          Loading…
        </div>
      )}

      {!loading && error && (
        <div className="bg-card border border-negative/40 rounded-xl p-6 text-center text-negative text-sm">
          {error}
        </div>
      )}

      {!loading && !error && totalArticles === 0 && (
        <div className="bg-card border border-border rounded-xl p-8 text-center text-muted">
          <div className="text-3xl mb-2">📰</div>
          <div className="text-sm">
            No news available up to the current simulation timestamp yet.
          </div>
        </div>
      )}

      <div className="space-y-3">
        {data?.groups.map((group) => {
          const isOpen = expanded.has(group.symbol)
          return (
            <section key={group.symbol} className="bg-card border border-border rounded-xl overflow-hidden">
              <button
                onClick={() => toggle(group.symbol)}
                className="w-full flex items-center justify-between gap-3 px-4 py-3 hover:bg-border/30 transition-colors text-left"
              >
                <div className="flex items-center gap-2 min-w-0">
                  {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                  <span className="font-bold text-sm">{group.symbol}</span>
                  {group.company && (
                    <span className="text-xs text-muted truncate">· {group.company}</span>
                  )}
                </div>
                <span className="text-xs text-muted shrink-0">{group.articles.length}</span>
              </button>
              {isOpen && (
                <div className="px-3 pb-3 space-y-2">
                  {group.articles.map((a) => (
                    <NewsItem key={a.id} article={a} onReason={(art) => handleReason(art, group.company)} />
                  ))}
                </div>
              )}
            </section>
          )
        })}
      </div>
    </div>

    <ReasoningDrawer
      open={drawerOpen}
      loading={drawerLoading}
      title={activeArticle?.title ?? null}
      data={drawerData}
      error={drawerError}
      onClose={() => setDrawerOpen(false)}
    />
    </>
  )
}

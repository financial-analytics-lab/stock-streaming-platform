import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, TrendingUp, TrendingDown, Clock, Zap } from 'lucide-react'
import { useMarketStore } from '../store/useMarketStore'
import { PriceChart } from '../components/PriceChart'
import { CandleChart } from '../components/CandleChart'
import { IntervalSelector } from '../components/IntervalSelector'
import { NewsItem } from '../components/NewsItem'
import { api } from '../lib/api'
import type { Article, Candle } from '../types'

type ChartType = 'line' | 'candle'

const FALLBACK_INTERVALS = ['1s', '5s', '1m', '5m', '15m']
const EMPTY_CANDLES: Candle[] = []

export function SymbolDetail() {
  const { symbol } = useParams<{ symbol: string }>()
  const navigate = useNavigate()

  const latest = useMarketStore((s) => (symbol ? s.latestBySymbol[symbol] : undefined))
  const history = useMarketStore((s) => (symbol ? (s.historyBySymbol[symbol] ?? []) : []))
  const [news, setNews] = useState<Article[]>([])
  const setHistory = useMarketStore((s) => s.setHistory)
  const seedCandles = useMarketStore((s) => s.seedCandles)

  const [chartType, setChartType] = useState<ChartType>('line')
  const [intervals, setIntervals] = useState<string[]>(FALLBACK_INTERVALS)
  const [candleInterval, setCandleInterval] = useState<string>('1m')

  const candleHistoryMap = useMarketStore((s) => s.candleHistory)
  const liveCandleMap = useMarketStore((s) => s.liveCandle)
  const candleHistory = symbol
    ? (candleHistoryMap[symbol]?.[candleInterval] ?? EMPTY_CANDLES)
    : EMPTY_CANDLES
  const liveCandle = symbol ? (liveCandleMap[symbol]?.[candleInterval] ?? null) : null

  useEffect(() => {
    if (symbol) {
      api.getHistory(symbol, 200).then((h) => setHistory(symbol, h)).catch(() => {})
      api.getNews()
        .then((resp) => {
          const group = resp.groups.find((g) => g.symbol === symbol.toUpperCase())
          setNews(group?.articles ?? [])
        })
        .catch(() => setNews([]))
    }
  }, [symbol, setHistory])

  useEffect(() => {
    api.getCandleIntervals()
      .then((list) => {
        if (list.length > 0) {
          setIntervals(list)
          setCandleInterval((current) => (list.includes(current) ? current : list[0]))
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!symbol || chartType !== 'candle') return
    api.getCandles(symbol, candleInterval, 200)
      .then((res) => seedCandles(symbol, candleInterval, res.history, res.live))
      .catch(() => {})
  }, [symbol, candleInterval, chartType, seedCandles])

  if (!symbol || !latest) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-muted">
        <div className="text-4xl">🔍</div>
        <div>No data for <strong>{symbol}</strong></div>
        <button onClick={() => navigate('/')} className="text-sm text-positive hover:underline">
          Back to overview
        </button>
      </div>
    )
  }

  const prices = history.map((t) => t.price)
  const current = latest.price
  const first = prices[0] ?? current
  const sessionHigh = prices.length ? Math.max(...prices) : current
  const sessionLow = prices.length ? Math.min(...prices) : current
  const totalVol = history.reduce((s, t) => s + t.volume, 0)
  const avgLag = history.length
    ? history.reduce((s, t) => s + t.lag_ms, 0) / history.length
    : 0
  const change = ((current - first) / first) * 100
  const isPos = change >= 0

  const stats = [
    { label: 'Session High', value: sessionHigh.toFixed(2), icon: TrendingUp, color: 'text-positive' },
    { label: 'Session Low', value: sessionLow.toFixed(2), icon: TrendingDown, color: 'text-negative' },
    { label: 'Total Volume', value: totalVol.toLocaleString(), icon: Clock, color: 'text-text-primary' },
    { label: 'Avg Lag', value: `${avgLag.toFixed(1)} ms`, icon: Zap, color: avgLag > 1000 ? 'text-negative' : 'text-positive' },
  ]

  const candleEmpty = candleHistory.length === 0 && !liveCandle

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/')}
          className="p-1.5 rounded-lg text-muted hover:text-text-primary hover:bg-border/60 transition-colors"
        >
          <ArrowLeft size={18} />
        </button>
        <div className="flex-1">
          <div className="flex items-baseline gap-3">
            <h1 className="text-2xl font-bold">{symbol}</h1>
            <span className="text-muted text-sm">{latest.security_name}</span>
          </div>
        </div>
        <div className="text-right">
          <div className="text-3xl font-mono font-bold">{current.toFixed(2)}</div>
          <div className={`text-sm font-semibold ${isPos ? 'text-positive' : 'text-negative'}`}>
            {isPos ? '▲' : '▼'} {Math.abs(change).toFixed(2)}%
          </div>
        </div>
      </div>

      {/* Chart controls */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="inline-flex items-center gap-1 bg-card border border-border rounded-lg p-1">
          {(['line', 'candle'] as ChartType[]).map((t) => {
            const active = t === chartType
            return (
              <button
                key={t}
                onClick={() => setChartType(t)}
                className={
                  'px-3 py-1 text-xs font-semibold rounded-md transition-colors capitalize ' +
                  (active
                    ? 'bg-positive/20 text-positive'
                    : 'text-muted hover:text-text-primary hover:bg-border/60')
                }
              >
                {t}
              </button>
            )
          })}
        </div>
        {chartType === 'candle' && (
          <IntervalSelector intervals={intervals} value={candleInterval} onChange={setCandleInterval} />
        )}
      </div>

      {/* Chart */}
      <div className="bg-card border border-border rounded-xl p-4">
        {chartType === 'line' ? (
          history.length === 0 ? (
            <div className="h-[340px] flex items-center justify-center text-muted text-sm">
              Loading chart data…
            </div>
          ) : (
            <PriceChart history={history} />
          )
        ) : candleEmpty ? (
          <div className="h-[340px] flex items-center justify-center text-muted text-sm">
            Waiting for {candleInterval} candles…
          </div>
        ) : (
          <CandleChart history={candleHistory} live={liveCandle} />
        )}
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="bg-card border border-border rounded-xl p-4">
            <div className="flex items-center gap-1.5 text-xs text-muted mb-2">
              <Icon size={12} />
              {label}
            </div>
            <div className={`text-lg font-mono font-bold ${color}`}>{value}</div>
          </div>
        ))}
      </div>

      {/* News */}
      <div>
        <h2 className="text-sm font-semibold text-muted uppercase tracking-wider mb-3">
          Latest News
        </h2>
        <div className="space-y-3">
          {news.length === 0 ? (
            <div className="text-muted text-sm bg-card border border-border rounded-xl p-4">
              No news available for {symbol} up to the current simulation timestamp.
            </div>
          ) : (
            news.slice(0, 5).map((n) => <NewsItem key={n.id} article={n} />)
          )}
        </div>
      </div>
    </div>
  )
}

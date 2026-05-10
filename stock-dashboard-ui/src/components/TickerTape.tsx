import { useMarketStore } from '../store/useMarketStore'
import type { Tick } from '../types'

function pctChange(tick: Tick, history: Tick[] | undefined): number | null {
  const first = history?.[0]?.price
  if (!first) return null
  return ((tick.price - first) / first) * 100
}

export function TickerTape() {
  const latest = useMarketStore((s) => s.latestBySymbol)
  const history = useMarketStore((s) => s.historyBySymbol)
  const symbols = Object.keys(latest)

  if (symbols.length === 0) {
    return (
      <div className="px-4 text-xs text-muted flex items-center h-full">
        Waiting for market data…
      </div>
    )
  }

  const items = symbols.map((sym) => {
    const tick = latest[sym]
    const change = pctChange(tick, history[sym])
    const isPos = (change ?? 0) >= 0
    return (
      <span key={sym} className="inline-flex items-center gap-2 px-4 shrink-0 border-r border-border/40 h-10">
        <span className="text-xs font-bold text-text-primary">{sym}</span>
        <span className="text-xs font-mono">{Number(tick.price).toFixed(2)}</span>
        {change !== null && (
          <span className={`text-xs font-medium ${isPos ? 'text-positive' : 'text-negative'}`}>
            {isPos ? '▲' : '▼'}{Math.abs(change).toFixed(2)}%
          </span>
        )}
      </span>
    )
  })

  return (
    <div className="overflow-hidden whitespace-nowrap h-10 flex items-center">
      <div className="inline-flex animate-ticker">
        {items}
        {items}
      </div>
    </div>
  )
}

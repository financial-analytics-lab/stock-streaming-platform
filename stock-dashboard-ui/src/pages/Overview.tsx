import { useEffect } from 'react'
import { useMarketStore } from '../store/useMarketStore'
import { SymbolCard } from '../components/SymbolCard'
import { api } from '../lib/api'

export function Overview() {
  const latest = useMarketStore((s) => s.latestBySymbol)
  const history = useMarketStore((s) => s.historyBySymbol)
  const setHistory = useMarketStore((s) => s.setHistory)
  const connected = useMarketStore((s) => s.connected)
  const symbols = Object.keys(latest)

  useEffect(() => {
    api.getSymbols().then((ticks) => {
      ticks.forEach((tick) => {
        api.getHistory(tick.symbol, 60).then((h) => setHistory(tick.symbol, h)).catch(() => {})
      })
    }).catch(() => {})
  }, [setHistory])

  if (symbols.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center">
        <div className="text-5xl">📡</div>
        <div>
          <div className="text-lg font-semibold text-text-primary">Waiting for market data</div>
          <div className="text-sm text-muted mt-1">
            {connected
              ? 'Connected — waiting for the publisher to start streaming'
              : 'Connecting to the dashboard backend…'}
          </div>
        </div>
        <div className="text-xs text-muted/60 max-w-sm">
          Start Kafka, then run the publisher replay to see live stock prices here.
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold">Market Overview</h1>
          <div className="text-sm text-muted mt-0.5">{symbols.length} symbols streaming</div>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        {symbols.map((sym) => (
          <SymbolCard
            key={sym}
            tick={latest[sym]}
            history={history[sym] ?? []}
          />
        ))}
      </div>
    </div>
  )
}

import { useEffect } from 'react'
import { useMarketStore } from '../store/useMarketStore'
import { MetricGauge } from '../components/MetricGauge'
import { api } from '../lib/api'

export function Metrics() {
  const metrics = useMarketStore((s) => s.metrics)
  const setMetrics = useMarketStore((s) => s.setMetrics)
  const connected = useMarketStore((s) => s.connected)

  useEffect(() => {
    const fetch = () => api.getMetrics().then(setMetrics).catch(() => {})
    fetch()
    const interval = setInterval(fetch, 5000)
    return () => clearInterval(interval)
  }, [setMetrics])

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-xl font-bold">System Metrics</h1>
        <div className="text-sm text-muted mt-0.5">Refreshes every 5 seconds</div>
      </div>

      {/* Connection status */}
      <div className={`rounded-xl border p-4 flex items-center gap-3 ${
        connected
          ? 'bg-positive/5 border-positive/20'
          : 'bg-negative/5 border-negative/20'
      }`}>
        <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${
          connected ? 'bg-positive animate-pulse' : 'bg-negative'
        }`} />
        <div>
          <div className={`text-sm font-medium ${connected ? 'text-positive' : 'text-negative'}`}>
            WebSocket {connected ? 'Connected' : 'Disconnected'}
          </div>
          <div className="text-xs text-muted">
            {connected ? 'Receiving live tick and news events' : 'Attempting to reconnect…'}
          </div>
        </div>
      </div>

      {metrics ? (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <MetricGauge
            label="Total Events Received"
            value={metrics.totalReceived}
            color="#E2E8F0"
            subtext="Tick + news events since startup"
          />
          <MetricGauge
            label="Average Kafka Lag"
            value={metrics.averageLagMs.toFixed(1)}
            unit="ms"
            color={metrics.averageLagMs > 1000 ? '#FF4560' : '#00D4AA'}
            subtext={metrics.averageLagMs > 1000 ? 'High lag — check publisher' : 'Healthy latency'}
          />
          <MetricGauge
            label="Active Symbols"
            value={metrics.symbolCount}
            color="#F5A623"
            subtext="Unique tickers seen in stream"
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-card border border-border rounded-xl p-5 animate-pulse">
              <div className="h-3 bg-border rounded w-24 mb-3" />
              <div className="h-8 bg-border rounded w-16" />
            </div>
          ))}
        </div>
      )}

      <div className="bg-card border border-border rounded-xl p-5">
        <h2 className="text-sm font-semibold text-muted uppercase tracking-wider mb-4">
          Architecture Overview
        </h2>
        <div className="space-y-2 text-sm text-muted font-mono">
          {[
            ['Publisher', 'CSV → Kafka stock-ticks', '#00D4AA'],
            ['Tick Consumer', 'Kafka → TickStore → WebSocket', '#00D4AA'],
            ['News Consumer', 'Kafka → NewsStore → WebSocket', '#00D4AA'],
            ['REST API', 'GET /api/symbols, /ticks/{sym}/history, /news', '#F5A623'],
            ['WebSocket', 'ws://.../ws/live  (tick | news events)', '#F5A623'],
          ].map(([label, desc, color]) => (
            <div key={label} className="flex gap-3">
              <span className="shrink-0 w-28" style={{ color }}>{label}</span>
              <span className="text-muted/70">{desc}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

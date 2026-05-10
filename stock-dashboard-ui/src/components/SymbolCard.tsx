import { useNavigate } from 'react-router-dom'
import type { Tick } from '../types'

function Sparkline({ prices }: { prices: number[] }) {
  if (prices.length < 2) return <div className="w-20 h-8" />
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const range = max - min || 1
  const W = 80
  const H = 32
  const pts = prices
    .map((v, i) => {
      const x = (i / (prices.length - 1)) * W
      const y = H - ((v - min) / range) * (H - 4) - 2
      return `${x},${y}`
    })
    .join(' ')
  const isUp = prices[prices.length - 1] >= prices[0]
  return (
    <svg width={W} height={H} className="overflow-visible">
      <polyline
        points={pts}
        fill="none"
        stroke={isUp ? '#00D4AA' : '#FF4560'}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

interface Props {
  tick: Tick
  history: Tick[]
}

export function SymbolCard({ tick, history }: Props) {
  const navigate = useNavigate()
  const prices = history.map((t) => t.price)
  const current = tick.price
  const first = prices[0] ?? current
  const change = ((current - first) / first) * 100
  const isPos = change >= 0

  return (
    <button
      onClick={() => navigate(`/symbol/${tick.symbol}`)}
      className="bg-card border border-border rounded-xl p-4 text-left hover:border-positive/40 hover:shadow-lg hover:shadow-positive/5 transition-all w-full group"
    >
      <div className="flex justify-between items-start mb-3">
        <div className="min-w-0">
          <div className="font-bold text-sm group-hover:text-positive transition-colors">
            {tick.symbol}
          </div>
          <div className="text-[11px] text-muted truncate max-w-[100px]">
            {tick.security_name}
          </div>
        </div>
        <Sparkline prices={prices.slice(-60)} />
      </div>
      <div className="flex items-baseline justify-between">
        <span className="text-lg font-mono font-bold">
          {current.toFixed(2)}
        </span>
        <span className={`text-xs font-semibold px-1.5 py-0.5 rounded ${
          isPos
            ? 'bg-positive/10 text-positive'
            : 'bg-negative/10 text-negative'
        }`}>
          {isPos ? '+' : ''}{change.toFixed(2)}%
        </span>
      </div>
      <div className="text-[10px] text-muted mt-1">
        Vol {tick.volume.toLocaleString()}
      </div>
    </button>
  )
}

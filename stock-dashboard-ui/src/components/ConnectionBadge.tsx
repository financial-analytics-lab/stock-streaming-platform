import { useMarketStore } from '../store/useMarketStore'

export function ConnectionBadge() {
  const connected = useMarketStore((s) => s.connected)
  return (
    <span className="flex items-center gap-1.5 text-xs whitespace-nowrap">
      <span
        className={`w-2 h-2 rounded-full shrink-0 ${
          connected ? 'bg-positive animate-pulse' : 'bg-negative'
        }`}
      />
      <span className={connected ? 'text-positive' : 'text-muted'}>
        {connected ? 'Live' : 'Reconnecting…'}
      </span>
    </span>
  )
}

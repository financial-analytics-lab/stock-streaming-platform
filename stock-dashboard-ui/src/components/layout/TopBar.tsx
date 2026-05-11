import { TickerTape } from '../TickerTape'
import { ConnectionBadge } from '../ConnectionBadge'

export function TopBar() {
  return (
    <header className="h-10 bg-card border-b border-border flex items-center overflow-hidden shrink-0">
      <div className="flex-1 overflow-hidden">
        <TickerTape />
      </div>
      <div className="px-4 shrink-0 border-l border-border h-full flex items-center">
        <ConnectionBadge />
      </div>
    </header>
  )
}

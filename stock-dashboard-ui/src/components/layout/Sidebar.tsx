import { NavLink } from 'react-router-dom'
import { BarChart2, Newspaper, Activity, TrendingUp } from 'lucide-react'

const links = [
  { to: '/', icon: BarChart2, label: 'Overview' },
  { to: '/news', icon: Newspaper, label: 'News' },
  { to: '/metrics', icon: Activity, label: 'System Metrics' },
]

export function Sidebar() {
  return (
    <aside className="w-56 bg-card border-r border-border flex flex-col shrink-0">
      <div className="flex items-center gap-2.5 px-4 py-5 border-b border-border">
        <TrendingUp className="text-positive" size={20} />
        <div>
          <div className="font-bold text-sm leading-tight">EGX Stream</div>
          <div className="text-[10px] text-muted leading-tight">Live Market Dashboard</div>
        </div>
      </div>

      <nav className="flex-1 px-2 py-4 space-y-0.5">
        {links.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all ${
                isActive
                  ? 'bg-positive/10 text-positive font-medium'
                  : 'text-muted hover:bg-border/60 hover:text-text-primary'
              }`
            }
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-4 py-3 border-t border-border">
        <div className="text-[10px] text-muted">EGX Stock Streaming Platform</div>
        <div className="text-[10px] text-muted/60">Graduation Project 2026</div>
      </div>
    </aside>
  )
}

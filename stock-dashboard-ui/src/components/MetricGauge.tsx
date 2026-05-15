interface Props {
  label: string
  value: number | string
  unit?: string
  color?: string
  subtext?: string
}

export function MetricGauge({ label, value, unit = '', color = '#00D4AA', subtext }: Props) {
  const display = typeof value === 'number' ? value.toLocaleString() : value
  return (
    <div className="bg-card border border-border rounded-xl p-5">
      <div className="text-xs text-muted mb-2 font-medium uppercase tracking-wide">{label}</div>
      <div className="text-3xl font-mono font-bold" style={{ color }}>
        {display}
        {unit && <span className="text-base text-muted ml-1.5 font-normal">{unit}</span>}
      </div>
      {subtext && <div className="text-xs text-muted mt-1">{subtext}</div>}
    </div>
  )
}

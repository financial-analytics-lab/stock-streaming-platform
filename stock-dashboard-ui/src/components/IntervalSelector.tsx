interface Props {
  intervals: string[]
  value: string
  onChange: (interval: string) => void
}

export function IntervalSelector({ intervals, value, onChange }: Props) {
  return (
    <div className="inline-flex items-center gap-1 bg-card border border-border rounded-lg p-1">
      {intervals.map((iv) => {
        const active = iv === value
        return (
          <button
            key={iv}
            onClick={() => onChange(iv)}
            className={
              'px-3 py-1 text-xs font-mono rounded-md transition-colors ' +
              (active
                ? 'bg-positive/20 text-positive'
                : 'text-muted hover:text-text-primary hover:bg-border/60')
            }
          >
            {iv}
          </button>
        )
      })}
    </div>
  )
}

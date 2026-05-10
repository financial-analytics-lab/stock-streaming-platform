import { useEffect, useMemo, useRef } from 'react'
import { createChart, ColorType } from 'lightweight-charts'
import type { Candle } from '../types'

interface Props {
  history: Candle[]
  live: Candle | null
}

interface BarPoint {
  time: number
  open: number
  high: number
  low: number
  close: number
}

function toPoint(c: Candle): BarPoint {
  return {
    time: Math.floor(new Date(c.window_start).getTime() / 1000),
    open: c.open,
    high: c.high,
    low: c.low,
    close: c.close,
  }
}

export function CandleChart({ history, live }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const seriesRef = useRef<any>(null)

  useEffect(() => {
    if (!containerRef.current) return
    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#1A1D27' },
        textColor: '#E2E8F0',
      },
      grid: {
        vertLines: { color: '#2D3148' },
        horzLines: { color: '#2D3148' },
      },
      crosshair: {
        vertLine: { color: '#64748B', labelBackgroundColor: '#2D3148' },
        horzLine: { color: '#64748B', labelBackgroundColor: '#2D3148' },
      },
      rightPriceScale: { borderColor: '#2D3148' },
      timeScale: {
        borderColor: '#2D3148',
        timeVisible: true,
        secondsVisible: true,
      },
      width: containerRef.current.clientWidth,
      height: 340,
    })
    chartRef.current = chart

    const series = chart.addCandlestickSeries({
      upColor: '#00D4AA',
      downColor: '#EF4444',
      borderUpColor: '#00D4AA',
      borderDownColor: '#EF4444',
      wickUpColor: '#00D4AA',
      wickDownColor: '#EF4444',
    })
    seriesRef.current = series

    const observer = new ResizeObserver(() => {
      if (containerRef.current) {
        chart.applyOptions({ width: containerRef.current.clientWidth })
      }
    })
    observer.observe(containerRef.current)

    return () => {
      observer.disconnect()
      chart.remove()
    }
  }, [])

  // Bulk-load history whenever the underlying candle array changes identity.
  // Sorted + de-duped by window_start so lightweight-charts is happy.
  const seedData = useMemo(() => {
    const seen = new Set<number>()
    return history
      .map(toPoint)
      .filter((p) => {
        if (seen.has(p.time)) return false
        seen.add(p.time)
        return true
      })
      .sort((a, b) => a.time - b.time)
  }, [history])

  useEffect(() => {
    if (!seriesRef.current) return
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    seriesRef.current.setData(seedData as any)
    chartRef.current?.timeScale().fitContent()
  }, [seedData])

  // Trail the in-progress (OPEN) candle in place.
  useEffect(() => {
    if (!seriesRef.current || !live) return
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    seriesRef.current.update(toPoint(live) as any)
  }, [live])

  return <div ref={containerRef} className="w-full" />
}

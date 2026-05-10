import { useEffect, useRef } from 'react'
import { createChart, ColorType, LineType } from 'lightweight-charts'
import type { Tick } from '../types'

interface Props {
  history: Tick[]
}

export function PriceChart({ history }: Props) {
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

    const series = chart.addAreaSeries({
      lineColor: '#00D4AA',
      topColor: 'rgba(0, 212, 170, 0.18)',
      bottomColor: 'rgba(0, 212, 170, 0)',
      lineWidth: 2,
      lineType: LineType.Curved,
      priceLineColor: '#00D4AA',
      priceLineWidth: 1,
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

  useEffect(() => {
    if (!seriesRef.current || history.length === 0) return
    const seen = new Set<number>()
    const data = history
      .map((t) => ({
        time: Math.floor(new Date(t.timestamp).getTime() / 1000) as number,
        value: t.price,
      }))
      .filter((d) => {
        if (seen.has(d.time)) return false
        seen.add(d.time)
        return true
      })
      .sort((a, b) => a.time - b.time)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    seriesRef.current.setData(data as any)
    chartRef.current?.timeScale().fitContent()
  }, [history])

  return <div ref={containerRef} className="w-full" />
}

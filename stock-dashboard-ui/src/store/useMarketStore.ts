import { create } from 'zustand'
import type { Tick, MetricsData, Candle } from '../types'

const MAX_HISTORY = 500
const MAX_CANDLES = 500

interface MarketState {
  latestBySymbol: Record<string, Tick>
  historyBySymbol: Record<string, Tick[]>
  connected: boolean
  metrics: MetricsData | null

  candleHistory: Record<string, Record<string, Candle[]>>
  liveCandle: Record<string, Record<string, Candle | null>>

  addTick: (tick: Tick) => void
  setHistory: (symbol: string, ticks: Tick[]) => void
  setConnected: (connected: boolean) => void
  setMetrics: (metrics: MetricsData) => void

  seedCandles: (symbol: string, interval: string, history: Candle[], live: Candle | null) => void
  addCandle: (candle: Candle) => void
}

function mergeClosed(existing: Candle[], incoming: Candle): Candle[] {
  for (let i = existing.length - 1; i >= 0; i--) {
    if (existing[i].window_start === incoming.window_start) {
      const next = existing.slice()
      next[i] = incoming
      return next
    }
  }
  const appended = [...existing, incoming]
  return appended.length > MAX_CANDLES ? appended.slice(appended.length - MAX_CANDLES) : appended
}

export const useMarketStore = create<MarketState>((set) => ({
  latestBySymbol: {},
  historyBySymbol: {},
  connected: false,
  metrics: null,
  candleHistory: {},
  liveCandle: {},

  addTick: (tick) =>
    set((state) => {
      const prev = state.historyBySymbol[tick.symbol] ?? []
      const history = prev.length >= MAX_HISTORY
        ? [...prev.slice(1), tick]
        : [...prev, tick]
      return {
        latestBySymbol: { ...state.latestBySymbol, [tick.symbol]: tick },
        historyBySymbol: { ...state.historyBySymbol, [tick.symbol]: history },
      }
    }),

  setHistory: (symbol, ticks) =>
    set((state) => ({
      historyBySymbol: { ...state.historyBySymbol, [symbol]: ticks },
      latestBySymbol: ticks.length
        ? { ...state.latestBySymbol, [symbol]: ticks[ticks.length - 1] }
        : state.latestBySymbol,
    })),

  setConnected: (connected) => set({ connected }),

  setMetrics: (metrics) => set({ metrics }),

  seedCandles: (symbol, interval, history, live) =>
    set((state) => ({
      candleHistory: {
        ...state.candleHistory,
        [symbol]: {
          ...(state.candleHistory[symbol] ?? {}),
          [interval]: history,
        },
      },
      liveCandle: {
        ...state.liveCandle,
        [symbol]: {
          ...(state.liveCandle[symbol] ?? {}),
          [interval]: live,
        },
      },
    })),

  addCandle: (candle) =>
    set((state) => {
      const { symbol, interval } = candle
      const symbolHistory = state.candleHistory[symbol] ?? {}
      const symbolLive = state.liveCandle[symbol] ?? {}

      if (candle.status === 'CLOSED') {
        const existingHistory = symbolHistory[interval] ?? []
        const nextHistory = mergeClosed(existingHistory, candle)
        const currentLive = symbolLive[interval] ?? null
        const nextLive =
          currentLive && currentLive.window_start === candle.window_start ? null : currentLive
        return {
          candleHistory: {
            ...state.candleHistory,
            [symbol]: { ...symbolHistory, [interval]: nextHistory },
          },
          liveCandle: {
            ...state.liveCandle,
            [symbol]: { ...symbolLive, [interval]: nextLive },
          },
        }
      }

      // OPEN
      return {
        liveCandle: {
          ...state.liveCandle,
          [symbol]: { ...symbolLive, [interval]: candle },
        },
      }
    }),
}))

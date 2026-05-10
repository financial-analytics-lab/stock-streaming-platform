import { create } from 'zustand'
import type { Tick, NewsEvent, MetricsData } from '../types'

const MAX_HISTORY = 500

interface MarketState {
  latestBySymbol: Record<string, Tick>
  historyBySymbol: Record<string, Tick[]>
  news: NewsEvent[]
  connected: boolean
  metrics: MetricsData | null

  addTick: (tick: Tick) => void
  addNews: (event: NewsEvent) => void
  setHistory: (symbol: string, ticks: Tick[]) => void
  setConnected: (connected: boolean) => void
  setMetrics: (metrics: MetricsData) => void
}

export const useMarketStore = create<MarketState>((set) => ({
  latestBySymbol: {},
  historyBySymbol: {},
  news: [],
  connected: false,
  metrics: null,

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

  addNews: (event) =>
    set((state) => ({
      news: [event, ...state.news].slice(0, 100),
    })),

  setHistory: (symbol, ticks) =>
    set((state) => ({
      historyBySymbol: { ...state.historyBySymbol, [symbol]: ticks },
      latestBySymbol: ticks.length
        ? { ...state.latestBySymbol, [symbol]: ticks[ticks.length - 1] }
        : state.latestBySymbol,
    })),

  setConnected: (connected) => set({ connected }),

  setMetrics: (metrics) => set({ metrics }),
}))

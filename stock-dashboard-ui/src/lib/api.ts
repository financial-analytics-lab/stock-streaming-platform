import axios from 'axios'
import type { Tick, NewsEvent, MetricsData, Candle } from '../types'

const client = axios.create({ baseURL: '/api' })

export interface CandleHistoryResponse {
  history: Candle[]
  live: Candle | null
}

export const api = {
  getSymbols: () =>
    client.get<Tick[]>('/symbols').then((r) => r.data),

  getHistory: (symbol: string, limit = 200) =>
    client.get<Tick[]>(`/ticks/${symbol}/history`, { params: { limit } }).then((r) => r.data),

  getNews: (limit = 50) =>
    client.get<NewsEvent[]>('/news', { params: { limit } }).then((r) => r.data),

  getMetrics: () =>
    client.get<MetricsData>('/metrics').then((r) => r.data),

  getCandleIntervals: () =>
    client.get<string[]>('/candles/intervals').then((r) => r.data),

  getCandles: (symbol: string, interval: string, limit = 200) =>
    client
      .get<CandleHistoryResponse>(`/candles/${symbol}/${interval}/history`, { params: { limit } })
      .then((r) => r.data),
}

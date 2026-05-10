import axios from 'axios'
import type { Tick, NewsEvent, MetricsData } from '../types'

const client = axios.create({ baseURL: '/api' })

export const api = {
  getSymbols: () =>
    client.get<Tick[]>('/symbols').then((r) => r.data),

  getHistory: (symbol: string, limit = 200) =>
    client.get<Tick[]>(`/ticks/${symbol}/history`, { params: { limit } }).then((r) => r.data),

  getNews: (limit = 50) =>
    client.get<NewsEvent[]>('/news', { params: { limit } }).then((r) => r.data),

  getMetrics: () =>
    client.get<MetricsData>('/metrics').then((r) => r.data),
}

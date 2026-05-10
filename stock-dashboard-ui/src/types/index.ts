export interface Tick {
  tick_id: number
  security_name: string
  symbol: string
  price: number
  volume: number
  timestamp: string      // ISO 8601
  published_at: string   // ISO 8601
  lag_ms: number
}

export interface NewsEvent {
  id: string
  datetime: string       // ISO 8601
  title: string
  body: string
  teaser: string
  section: string
  source: string
  categories: string[]
  country: string
  reads: number
  url: string
  image_url: string
  date_raw: string
}

export interface MetricsData {
  totalReceived: number
  averageLagMs: number
  symbolCount: number
}

export type CandleStatus = 'OPEN' | 'CLOSED'

export interface Candle {
  symbol: string
  interval: string
  window_start: string   // ISO 8601
  window_end: string     // ISO 8601
  open: number
  high: number
  low: number
  close: number
  volume: number
  tick_count: number
  published_at: string
  status: CandleStatus
}

export interface WsMessage {
  type: 'tick' | 'news' | 'candle'
  data: Tick | NewsEvent | Candle
}

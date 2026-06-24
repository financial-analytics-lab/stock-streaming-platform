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

export interface Article {
  id: string
  symbol: string
  source: string
  publishedAt: string    // ISO 8601
  title: string
  teaser: string
  body?: string
  url: string
  imageUrl: string
  section: string
  raw?: Record<string, unknown>
}

export interface NewsGroup {
  symbol: string
  company: string
  articles: Article[]
}

export interface NewsResponse {
  asOf: string           // ISO 8601
  groups: NewsGroup[]
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
  type: 'tick' | 'candle'
  data: Tick | Candle
}

export interface ReasoningRequest {
  symbol: string
  company: string
  source: string
  isin: string
  id: string
  date: string
  time: string
  datetime: string
  title: string
  teaser: string
  body: string
  url: string
  imageUrl: string
  section: string
  raw?: Record<string, unknown>
}

export type OutlookDirection = 'up' | 'down' | 'flat'
export type OutlookMagnitude = 'small' | 'medium' | 'large'
export type OutlookHorizon = 'short' | 'medium' | 'large'

export interface OutlookEntry {
  direction: OutlookDirection
  magnitude: OutlookMagnitude
  confidence: number
  explanation: string
}

export interface ReasoningResponse {
  status: string
  ticker: string
  summary: string
  technical_view: string
  sentiment_note: string
  outlook: Record<OutlookHorizon, OutlookEntry>
  risks: string
}


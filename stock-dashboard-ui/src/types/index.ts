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

export interface WsMessage {
  type: 'tick' | 'news'
  data: Tick | NewsEvent
}

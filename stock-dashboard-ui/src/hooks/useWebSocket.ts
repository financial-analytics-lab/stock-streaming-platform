import { useEffect, useRef } from 'react'
import { useMarketStore } from '../store/useMarketStore'
import type { WsMessage, Tick, Candle } from '../types'

export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null)
  const addTick = useMarketStore((s) => s.addTick)
  const addCandle = useMarketStore((s) => s.addCandle)
  const setConnected = useMarketStore((s) => s.setConnected)

  useEffect(() => {
    let reconnectTimer: ReturnType<typeof setTimeout>
    let active = true

    function connect() {
      if (!active) return
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const ws = new WebSocket(`${protocol}//${window.location.host}/ws/live`)
      wsRef.current = ws

      ws.onopen = () => setConnected(true)

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data as string) as WsMessage
          if (msg.type === 'tick') addTick(msg.data as Tick)
          else if (msg.type === 'candle') addCandle(msg.data as Candle)
        } catch {
          // ignore malformed messages
        }
      }

      ws.onclose = () => {
        setConnected(false)
        if (active) reconnectTimer = setTimeout(connect, 3000)
      }

      ws.onerror = () => ws.close()
    }

    connect()

    return () => {
      active = false
      clearTimeout(reconnectTimer)
      wsRef.current?.close()
    }
  }, [addTick, addCandle, setConnected])
}

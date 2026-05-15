import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { Overview } from './pages/Overview'
import { SymbolDetail } from './pages/SymbolDetail'
import { NewsFeed } from './pages/NewsFeed'
import { Metrics } from './pages/Metrics'
import { useWebSocket } from './hooks/useWebSocket'

function AppRoutes() {
  useWebSocket()
  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<Overview />} />
        <Route path="/symbol/:symbol" element={<SymbolDetail />} />
        <Route path="/news" element={<NewsFeed />} />
        <Route path="/metrics" element={<Metrics />} />
      </Routes>
    </AppLayout>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}

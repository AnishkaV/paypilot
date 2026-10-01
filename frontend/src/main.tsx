import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HashRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import { Layout } from './components/Layout'
import { Toaster } from './lib/toast'
import { Overview } from './pages/Overview'
import { Payments } from './pages/Payments'
import { Checkout } from './pages/Checkout'
import { Ledger } from './pages/Ledger'
import { ChaosLab } from './pages/ChaosLab'
import { Architecture } from './pages/Architecture'
import { SettingsPage } from './pages/Settings'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 1000, refetchOnWindowFocus: true },
  },
})

// HashRouter keeps routing in the browser (#/payments), so the gateway only has to serve index.html.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<Overview />} />
            <Route path="payments" element={<Payments />} />
            <Route path="checkout" element={<Checkout />} />
            <Route path="ledger" element={<Ledger />} />
            <Route path="chaos" element={<ChaosLab />} />
            <Route path="architecture" element={<Architecture />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="*" element={<Overview />} />
          </Route>
        </Routes>
      </HashRouter>
      <Toaster />
    </QueryClientProvider>
  </StrictMode>,
)

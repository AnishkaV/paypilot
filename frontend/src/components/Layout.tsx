import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import clsx from 'clsx'
import {
  Activity,
  BookOpenCheck,
  CreditCard,
  FlaskConical,
  LayoutDashboard,
  Menu,
  Network,
  Plus,
  Settings as SettingsIcon,
  ShoppingBag,
  Square,
  X,
} from 'lucide-react'
import { useStats } from '../lib/queries'
import { simulator, useSimulator } from '../lib/simulator'
import { panels } from '../lib/panels'
import { Button } from './ui'
import { PaymentDrawer } from './PaymentDrawer'
import { NewPaymentModal } from './NewPaymentModal'

const NAV = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/checkout', label: 'Checkout demo', icon: ShoppingBag },
  { to: '/ledger', label: 'Ledger', icon: BookOpenCheck },
  { to: '/chaos', label: 'Chaos lab', icon: FlaskConical },
  { to: '/architecture', label: 'Architecture', icon: Network },
  { to: '/settings', label: 'Settings', icon: SettingsIcon },
]

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="grid size-8 place-items-center rounded-lg bg-brand-600 shadow-sm shadow-brand-900/40">
        <svg viewBox="0 0 32 32" className="size-5">
          <path d="M10 23V9h7a5 5 0 0 1 0 10h-3" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="22.5" cy="23" r="2" fill="#a5b4fc" />
        </svg>
      </div>
      <div className="leading-tight">
        <p className={clsx('text-[15px] font-semibold tracking-tight', light ? 'text-white' : 'text-slate-900')}>PayPilot</p>
        <p className={clsx('text-[11px]', light ? 'text-slate-400' : 'text-slate-500')}>Payments console</p>
      </div>
    </div>
  )
}

function EnvironmentCard() {
  const stats = useStats()
  const online = !stats.isError && !!stats.data
  return (
    <div className="rounded-lg border border-white/10 bg-white/5 p-3 text-xs">
      <div className="flex items-center gap-2">
        <span className={clsx('size-2 rounded-full', online ? 'bg-emerald-400' : stats.isLoading ? 'bg-amber-400' : 'bg-rose-400')} />
        <span className="font-medium text-slate-200">{online ? 'All services online' : stats.isLoading ? 'Connecting…' : 'Gateway unreachable'}</span>
      </div>
      {online && (
        <p className="mt-1.5 text-slate-400">
          Events via <span className="font-medium text-slate-300">{stats.data.eventTransport === 'kafka' ? 'Kafka' : 'HTTP outbox'}</span>
          {' · '}outbox lag {stats.data.outboxLag}
        </p>
      )}
    </div>
  )
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col bg-slate-950 px-3 py-5">
      <div className="px-2">
        <Logo light />
      </div>
      <nav className="mt-8 flex-1 space-y-0.5">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              clsx(
                'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition',
                isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200',
              )
            }
          >
            <Icon className="size-[18px] shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>
      <EnvironmentCard />
    </div>
  )
}

function SimulatorPill() {
  const sim = useSimulator()
  if (!sim.running) return null
  return (
    <div className="hidden items-center gap-2 rounded-full border border-brand-200 bg-brand-50 py-1 pr-1 pl-3 text-xs font-medium text-brand-700 sm:flex">
      <Activity className="size-3.5 animate-pulse" />
      Simulating {sim.config.ratePerSecond}/s · {sim.sent} sent
      <button onClick={() => simulator.stop()} className="rounded-full bg-white p-1 text-brand-600 shadow-sm hover:text-rose-600" title="Stop traffic">
        <Square className="size-3 fill-current" />
      </button>
    </div>
  )
}

export function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  const current = NAV.find((n) => (n.end ? location.pathname === n.to : location.pathname.startsWith(n.to)))

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 lg:block">
        <div className="fixed inset-y-0 w-60">
          <Sidebar />
        </div>
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMobileOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-64 animate-fade-in">
            <Sidebar onNavigate={() => setMobileOpen(false)} />
            <button className="absolute top-4 right-3 text-slate-400" onClick={() => setMobileOpen(false)} aria-label="Close menu">
              <X className="size-5" />
            </button>
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-slate-200 bg-white/80 px-4 backdrop-blur sm:px-6">
          <button className="text-slate-500 lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Open menu">
            <Menu className="size-5" />
          </button>
          <div className="lg:hidden">
            <Logo />
          </div>
          <p className="hidden text-sm text-slate-500 lg:block">
            PayPilot <span className="mx-1.5 text-slate-300">/</span>
            <span className="font-medium text-slate-900">{current?.label ?? 'Console'}</span>
          </p>
          <div className="ml-auto flex items-center gap-3">
            <SimulatorPill />
            <Button size="sm" icon={<Plus className="size-4" />} onClick={() => panels.openNewPayment()}>
              New payment
            </Button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </main>
      </div>

      <PaymentDrawer />
      <NewPaymentModal />
    </div>
  )
}

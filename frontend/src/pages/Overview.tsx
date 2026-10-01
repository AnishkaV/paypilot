import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Activity, CheckCircle2, FlaskConical, Gauge, IndianRupee, Plus, Radio, Receipt } from 'lucide-react'
import { api } from '../lib/api'
import { clock, count, money, moneyCompact, shortId, timeAgo } from '../lib/format'
import { panels } from '../lib/panels'
import { useRecentAttempts, useStats } from '../lib/queries'
import { merchantName } from '../lib/settings'
import type { OpsStats, PspStats } from '../lib/types'
import { Badge, Button, Card, CardHeader, CircuitBadge, Empty, ErrorNote, OutcomeBadge, PageHeader, Skeleton, Stat, StatusBadge } from '../components/ui'

export function Overview() {
  const stats = useStats()
  const s = stats.data

  return (
    <>
      <PageHeader
        title="Overview"
        description="Every payment across all merchants, updated live. Break a PSP in the chaos lab and watch routing, circuit breakers and the ledger react."
        actions={
          <>
            {s && (
              <Badge tone="green" dot pulse>
                Live · {clock(s.generatedAt)}
              </Badge>
            )}
            <Link to="/chaos">
              <Button variant="secondary" size="sm" icon={<FlaskConical className="size-4" />}>
                Chaos lab
              </Button>
            </Link>
          </>
        }
      />

      {stats.isError && <ErrorNote error={stats.error} />}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {s ? (
          <>
            <Stat
              label="Gross volume"
              value={moneyCompact(s.totals.grossVolume)}
              hint={`${money(s.totals.refundedVolume)} refunded`}
              icon={<IndianRupee className="size-4" />}
            />
            <Stat
              label="Payments"
              value={count(s.totals.payments)}
              hint={`${count(s.totals.succeeded)} succeeded · ${count(s.totals.failed)} failed`}
              icon={<Receipt className="size-4" />}
              accent="slate"
            />
            <Stat
              label="Success rate"
              value={`${s.totals.successRate.toFixed(1)}%`}
              hint="of completed payments"
              icon={<CheckCircle2 className="size-4" />}
              accent={s.totals.successRate >= 90 || s.totals.payments === 0 ? 'green' : s.totals.successRate >= 75 ? 'amber' : 'red'}
            />
            <Stat
              label="Avg PSP latency"
              value={`${s.totals.avgPspLatencyMs} ms`}
              hint={`${s.totals.inFlight} in flight · outbox lag ${s.outboxLag}`}
              icon={<Gauge className="size-4" />}
              accent="amber"
            />
          </>
        ) : (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[118px] rounded-xl" />)
        )}
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Payments per minute" subtitle="Last 30 minutes, by outcome" />
          <div className="px-2 pt-4 pb-2">{s ? <VolumeChart stats={s} /> : <Skeleton className="mx-3 h-[260px]" />}</div>
        </Card>
        <Card>
          <CardHeader title="PSP health" subtitle="Circuit breaker per provider" right={<Link to="/chaos" className="text-xs font-medium text-brand-600 hover:text-brand-700">Break one</Link>} />
          <div className="divide-y divide-slate-100">
            {s ? s.psps.map((p) => <PspRow key={p.psp} psp={p} />) : <Skeleton className="m-5 h-40" />}
          </div>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <RecentPayments empty={s?.totals.payments === 0} />
        <LiveFeed />
      </div>
    </>
  )
}

function VolumeChart({ stats }: { stats: OpsStats }) {
  const data = useMemo(
    () =>
      stats.timeline.map((p) => ({
        ...p,
        label: new Date(p.minute).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }),
      })),
    [stats.timeline],
  )
  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={data} margin={{ top: 8, right: 16, left: -18, bottom: 0 }}>
        <defs>
          <linearGradient id="fill-ok" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#10b981" stopOpacity={0.02} />
          </linearGradient>
          <linearGradient id="fill-fail" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#f43f5e" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#f43f5e" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} interval={4} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} />
        <Tooltip
          contentStyle={{ borderRadius: 10, border: '1px solid #e2e8f0', fontSize: 12, boxShadow: '0 8px 24px rgba(15,23,42,0.08)' }}
          labelStyle={{ fontWeight: 600, color: '#0f172a' }}
        />
        <Area type="monotone" dataKey="succeeded" name="Succeeded" stackId="1" stroke="#10b981" strokeWidth={2} fill="url(#fill-ok)" />
        <Area type="monotone" dataKey="failed" name="Failed" stackId="1" stroke="#f43f5e" strokeWidth={2} fill="url(#fill-fail)" />
        <Area type="monotone" dataKey="inFlight" name="In flight" stackId="1" stroke="#f59e0b" strokeWidth={1.5} fill="#fef3c7" fillOpacity={0.5} />
      </AreaChart>
    </ResponsiveContainer>
  )
}

function PspRow({ psp }: { psp: PspStats }) {
  const total = Math.max(1, psp.succeeded + psp.declined + psp.notProcessed + psp.unknown)
  const segments = [
    { value: psp.succeeded, className: 'bg-emerald-500', label: 'succeeded' },
    { value: psp.declined, className: 'bg-rose-400', label: 'declined' },
    { value: psp.notProcessed, className: 'bg-orange-400', label: 'not processed' },
    { value: psp.unknown, className: 'bg-amber-400', label: 'unknown' },
  ]
  return (
    <div className="px-5 py-4">
      <div className="flex items-center justify-between">
        <p className="font-mono text-sm font-medium">{psp.psp}</p>
        <CircuitBadge state={psp.circuitState} />
      </div>
      <div className="mt-3 flex h-1.5 overflow-hidden rounded-full bg-slate-100">
        {psp.calls > 0 &&
          segments.map((seg) => (
            <div key={seg.label} className={seg.className} style={{ width: `${(seg.value / total) * 100}%` }} title={`${seg.value} ${seg.label}`} />
          ))}
      </div>
      <div className="mt-2 flex justify-between text-xs text-slate-500">
        <span>{count(psp.calls)} calls</span>
        <span>{psp.avgLatencyMs} ms avg</span>
        <span>{psp.failureRate < 0 ? 'breaker warming up' : `${psp.failureRate.toFixed(0)}% failing`}</span>
      </div>
      {psp.circuitOpenSkips > 0 && <p className="mt-1 text-xs text-slate-400">{count(psp.circuitOpenSkips)} payments routed around it while open</p>}
    </div>
  )
}

function RecentPayments({ empty }: { empty?: boolean }) {
  const payments = useQuery({ queryKey: ['payments', 'recent'], queryFn: () => api.payments({ limit: 8 }), refetchInterval: 2500 })
  return (
    <Card className="xl:col-span-2">
      <CardHeader title="Recent payments" right={<Link to="/payments" className="text-xs font-medium text-brand-600 hover:text-brand-700">View all</Link>} />
      {empty ? (
        <Empty
          icon={<Activity className="size-5" />}
          title="No payments yet"
          description="Create one by hand, or start the traffic simulator to see the whole system move."
          action={
            <div className="flex gap-2">
              <Button size="sm" icon={<Plus className="size-4" />} onClick={() => panels.openNewPayment()}>
                New payment
              </Button>
              <Link to="/chaos">
                <Button size="sm" variant="secondary">
                  Start simulator
                </Button>
              </Link>
            </div>
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="border-b border-slate-100">
              <tr>
                <th className="table-head">Payment</th>
                <th className="table-head">Merchant</th>
                <th className="table-head text-right">Amount</th>
                <th className="table-head">Status</th>
                <th className="table-head">PSP</th>
                <th className="table-head text-right">When</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(payments.data ?? []).map((p) => (
                <tr key={p.id} onClick={() => panels.openPayment(p.id)} className="cursor-pointer transition hover:bg-slate-50">
                  <td className="table-cell mono text-slate-600">{shortId(p.id, 16)}</td>
                  <td className="table-cell text-slate-600">{merchantName(p.merchantId)}</td>
                  <td className="table-cell text-right font-medium tabular-nums">{money(p.amount)}</td>
                  <td className="table-cell">
                    <StatusBadge status={p.status} />
                  </td>
                  <td className="table-cell mono text-slate-600">{p.psp ?? '—'}</td>
                  <td className="table-cell text-right text-slate-500">{timeAgo(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function LiveFeed() {
  const attempts = useRecentAttempts()
  const items = (attempts.data ?? []).slice(0, 12)
  return (
    <Card>
      <CardHeader title="PSP calls" subtitle="Every attempt, newest first" right={<Radio className="size-4 animate-pulse text-emerald-500" />} />
      {items.length === 0 ? (
        <Empty icon={<Radio className="size-5" />} title="Waiting for traffic" />
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((a, i) => (
            <li key={`${a.paymentId}-${a.psp}-${i}`} className="flex cursor-pointer items-center justify-between gap-3 px-5 py-2.5 hover:bg-slate-50" onClick={() => panels.openPayment(a.paymentId)}>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-medium text-slate-700">{a.psp}</span>
                  <OutcomeBadge outcome={a.outcome} />
                </div>
                <p className="mt-0.5 truncate font-mono text-[11px] text-slate-400">{a.paymentId}</p>
              </div>
              <div className="shrink-0 text-right text-xs text-slate-500">
                <p className="tabular-nums">{a.latencyMs} ms</p>
                <p>{timeAgo(a.at)}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

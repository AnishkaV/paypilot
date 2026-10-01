import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CloudOff, Gauge, Hourglass, Play, PowerOff, RotateCcw, Square, Zap } from 'lucide-react'
import { api } from '../lib/api'
import { count } from '../lib/format'
import { useChaos, useStats } from '../lib/queries'
import { simulator, useSimulator, type SimulatorConfig } from '../lib/simulator'
import { toast } from '../lib/toast'
import type { PspBehavior, PspStats } from '../lib/types'
import { Button, Card, CardHeader, CircuitBadge, ErrorNote, PageHeader, Skeleton, Slider, Toggle } from '../components/ui'

const HEALTHY: PspBehavior = { down: false, declineRate: 0, errorRate: 0, timeoutRate: 0, latencyMs: 50 }

const SCENARIOS: { name: string; description: string; icon: typeof Zap; apply: Record<string, Partial<PspBehavior>> }[] = [
  { name: 'Alpha outage', description: 'alpha refuses every call', icon: PowerOff, apply: { alpha: { down: true } } },
  { name: 'Flaky beta', description: '40% of beta calls fail', icon: CloudOff, apply: { beta: { errorRate: 0.4 } } },
  { name: 'Slow network', description: '+800 ms on every PSP', icon: Gauge, apply: { alpha: { latencyMs: 800 }, beta: { latencyMs: 800 }, gamma: { latencyMs: 800 } } },
  { name: 'Alpha timeouts', description: '30% answer too late', icon: Hourglass, apply: { alpha: { timeoutRate: 0.3 } } },
  { name: 'Total outage', description: 'every PSP is down', icon: Zap, apply: { alpha: { down: true }, beta: { down: true }, gamma: { down: true } } },
]

export function ChaosLab() {
  const chaos = useChaos()
  const stats = useStats()
  const queryClient = useQueryClient()

  async function applyScenario(scenario: (typeof SCENARIOS)[number]) {
    try {
      await api.resetChaos()
      await Promise.all(Object.entries(scenario.apply).map(([psp, patch]) => api.updateChaos(psp, { ...HEALTHY, ...patch })))
      toast.info(`Scenario on: ${scenario.name}`, 'Watch the circuit breakers and the success rate on the overview page.')
    } catch (e) {
      toast.error('Could not apply scenario', e instanceof Error ? e.message : String(e))
    }
    void queryClient.invalidateQueries({ queryKey: ['chaos'] })
  }

  async function resetAll() {
    try {
      await api.resetChaos()
      toast.success('All PSPs healthy again')
    } catch (e) {
      toast.error('Reset failed', e instanceof Error ? e.message : String(e))
    }
    void queryClient.invalidateQueries({ queryKey: ['chaos'] })
  }

  return (
    <>
      <PageHeader
        title="Chaos lab"
        description="Generate traffic, then break the payment providers on purpose. PayPilot should fail over, open circuit breakers, resolve timeouts without double charging, and keep the books balanced."
        actions={
          <Button variant="secondary" size="sm" icon={<RotateCcw className="size-4" />} onClick={resetAll}>
            Reset all PSPs
          </Button>
        }
      />

      <TrafficSimulator />

      <h2 className="mt-8 mb-3 text-sm font-semibold text-slate-900">Scenarios</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {SCENARIOS.map((s) => (
          <button key={s.name} onClick={() => applyScenario(s)} className="card group p-4 text-left transition hover:border-brand-300 hover:shadow-md">
            <s.icon className="size-5 text-slate-400 transition group-hover:text-brand-600" />
            <p className="mt-3 text-sm font-semibold">{s.name}</p>
            <p className="mt-0.5 text-xs text-slate-500">{s.description}</p>
          </button>
        ))}
      </div>

      <h2 className="mt-8 mb-3 text-sm font-semibold text-slate-900">Providers</h2>
      {chaos.isError && <ErrorNote error={chaos.error} />}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {chaos.data
          ? Object.entries(chaos.data).map(([psp, behavior]) => (
              <PspControl key={psp} psp={psp} behavior={behavior} stats={stats.data?.psps.find((p) => p.psp === psp)} />
            ))
          : Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-80 rounded-xl" />)}
      </div>

      <Card className="mt-6 p-5">
        <p className="text-sm font-semibold">What to look for</p>
        <ul className="mt-2 grid gap-2 text-sm text-slate-600 md:grid-cols-2">
          <li><span className="font-medium text-slate-900">Failover.</span> A PSP that refuses a call (503 or connection refused) did not charge anyone, so the router tries the next one.</li>
          <li><span className="font-medium text-slate-900">Circuit breaker.</span> When at least 50% of a PSP's last 10 calls fail, its circuit opens for 20 seconds and payments skip it without waiting.</li>
          <li><span className="font-medium text-slate-900">Timeouts.</span> A timed-out call may have charged the customer, so PayPilot never fails over. The payment stays Processing until the sweeper asks the PSP.</li>
          <li><span className="font-medium text-slate-900">Ledger.</span> However much you break, the ledger page must still say "Books balanced".</li>
        </ul>
      </Card>
    </>
  )
}

function TrafficSimulator() {
  const sim = useSimulator()
  const [config, setConfig] = useState<SimulatorConfig>(sim.config)
  const [, forceTick] = useState(0)

  useEffect(() => {
    if (!sim.running) return
    const t = setInterval(() => forceTick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [sim.running])

  const update = (patch: Partial<SimulatorConfig>) => {
    const next = { ...config, ...patch }
    setConfig(next)
    simulator.configure(next)
  }
  const elapsed = sim.startedAt ? Math.round((Date.now() - sim.startedAt) / 1000) : 0

  const counters = [
    { label: 'Sent', value: sim.sent, tone: 'text-slate-900' },
    { label: 'Succeeded', value: sim.succeeded, tone: 'text-emerald-600' },
    { label: 'Failed', value: sim.failed, tone: 'text-rose-600' },
    { label: 'Pending (timeout)', value: sim.processing, tone: 'text-amber-600' },
    { label: 'Rate limited (429)', value: sim.rateLimited, tone: 'text-violet-600' },
    { label: 'Errors', value: sim.errors, tone: 'text-slate-500' },
  ]

  return (
    <Card>
      <CardHeader
        title="Traffic simulator"
        subtitle="Sends realistic payments from your browser through the gateway, as both demo merchants."
        right={
          sim.running ? (
            <Button variant="danger" size="sm" icon={<Square className="size-3.5 fill-current" />} onClick={() => simulator.stop()}>
              Stop
            </Button>
          ) : (
            <Button size="sm" icon={<Play className="size-3.5 fill-current" />} onClick={() => simulator.start(config)}>
              Start traffic
            </Button>
          )
        }
      />
      <div className="grid gap-6 p-5 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-2">
          <Slider label="Payments per second" value={config.ratePerSecond} min={1} max={15} onChange={(v) => update({ ratePerSecond: v })} />
          <Slider label="Payers that get declined" value={config.declinePercent} min={0} max={50} unit="%" onChange={(v) => update({ declinePercent: v })} />
          <Slider label="Payers whose PSP times out" value={config.timeoutPercent} min={0} max={30} unit="%" onChange={(v) => update({ timeoutPercent: v })} />
          <p className="text-xs text-slate-500">The gateway allows each merchant 20 requests per second (burst 40). Push past it to see 429s.</p>
        </div>
        <div className="lg:col-span-3">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {counters.map((c) => (
              <div key={c.label} className="rounded-lg border border-slate-200 p-3">
                <p className="text-xs text-slate-500">{c.label}</p>
                <p className={clsx('mt-1 text-xl font-semibold tabular-nums', c.tone)}>{count(c.value)}</p>
              </div>
            ))}
          </div>
          <p className="mt-3 text-xs text-slate-500">
            {sim.running ? `Running for ${elapsed}s. Keeps going while you look at other pages.` : 'Stopped.'}
            {sim.lastError && <span className="text-rose-600"> Last error: {sim.lastError}</span>}
          </p>
        </div>
      </div>
    </Card>
  )
}

function PspControl({ psp, behavior, stats }: { psp: string; behavior: PspBehavior; stats?: PspStats }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState(behavior)
  const [saving, setSaving] = useState(false)
  const dirty = useRef(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Follow server-side changes (scenarios, reset) unless the user is mid-edit.
  useEffect(() => {
    if (!dirty.current) setDraft(behavior)
  }, [behavior])

  function change(patch: Partial<PspBehavior>) {
    const next = { ...draft, ...patch }
    setDraft(next)
    dirty.current = true
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(async () => {
      setSaving(true)
      try {
        await api.updateChaos(psp, next)
      } catch (e) {
        toast.error(`Could not update ${psp}`, e instanceof Error ? e.message : String(e))
      } finally {
        dirty.current = false
        setSaving(false)
        void queryClient.invalidateQueries({ queryKey: ['chaos'] })
      }
    }, 350)
  }

  const broken = draft.down || draft.errorRate > 0 || draft.timeoutRate > 0 || draft.declineRate > 0 || draft.latencyMs > 200

  return (
    <Card className={clsx('transition', draft.down && 'border-rose-200 ring-2 ring-rose-100')}>
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
        <div>
          <p className="font-mono text-sm font-semibold">{psp}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {stats ? `${count(stats.calls)} calls · ${stats.avgLatencyMs} ms avg` : '—'}
            {saving && ' · saving…'}
          </p>
        </div>
        {stats && <CircuitBadge state={stats.circuitState} />}
      </div>
      <div className="space-y-4 p-5">
        <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2.5">
          <div>
            <p className="text-sm font-medium">Simulate outage</p>
            <p className="text-xs text-slate-500">Every call gets 503, nothing is charged</p>
          </div>
          <Toggle checked={draft.down} onChange={(down) => change({ down })} label={`Take ${psp} down`} />
        </div>
        <Slider label="Error rate (503)" value={Math.round(draft.errorRate * 100)} min={0} max={100} unit="%" onChange={(v) => change({ errorRate: v / 100 })} />
        <Slider label="Decline rate" value={Math.round(draft.declineRate * 100)} min={0} max={100} unit="%" onChange={(v) => change({ declineRate: v / 100 })} />
        <Slider label="Timeout rate (charged, answers late)" value={Math.round(draft.timeoutRate * 100)} min={0} max={100} unit="%" onChange={(v) => change({ timeoutRate: v / 100 })} />
        <Slider label="Extra latency" value={draft.latencyMs} min={0} max={3000} step={50} unit=" ms" onChange={(v) => change({ latencyMs: v })} />
        {broken && (
          <button onClick={() => change(HEALTHY)} className="text-xs font-medium text-brand-600 hover:text-brand-700">
            Make {psp} healthy
          </button>
        )}
      </div>
    </Card>
  )
}

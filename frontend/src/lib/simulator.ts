import { useSyncExternalStore } from 'react'
import { api, ApiError, newIdempotencyKey } from './api'
import { getSettings } from './settings'
import type { PaymentMethod } from './types'

// Browser-side traffic generator. It lives outside React so it keeps running while you move
// between pages; components subscribe with useSimulator().

export interface SimulatorConfig {
  ratePerSecond: number
  declinePercent: number
  timeoutPercent: number
}

export interface SimulatorState {
  running: boolean
  config: SimulatorConfig
  sent: number
  succeeded: number
  failed: number
  processing: number
  rateLimited: number
  errors: number
  startedAt: number | null
  lastError: string | null
}

const FIRST_NAMES = ['aarav', 'diya', 'kabir', 'meera', 'vihaan', 'ananya', 'arjun', 'isha', 'rohan', 'sara', 'neel', 'tara']
const HANDLES = ['okaxis', 'ybl', 'paytm', 'okhdfcbank', 'oksbi', 'ibl']
const METHODS: PaymentMethod[] = ['UPI', 'UPI', 'UPI', 'CARD', 'NETBANKING']

const initial: SimulatorState = {
  running: false,
  config: { ratePerSecond: 4, declinePercent: 8, timeoutPercent: 3 },
  sent: 0,
  succeeded: 0,
  failed: 0,
  processing: 0,
  rateLimited: 0,
  errors: 0,
  startedAt: null,
  lastError: null,
}

let state: SimulatorState = initial
let timer: ReturnType<typeof setInterval> | null = null
let orderNumber = 1000 + Math.floor(Math.random() * 9000)
const listeners = new Set<() => void>()

function update(patch: Partial<SimulatorState> | ((s: SimulatorState) => Partial<SimulatorState>)) {
  const next = typeof patch === 'function' ? patch(state) : patch
  state = { ...state, ...next }
  listeners.forEach((l) => l())
}

const pick = <T,>(items: T[]) => items[Math.floor(Math.random() * items.length)]

/** Mostly small UPI payments, some mid-size, a few large ones. Returned in paise. */
function randomAmount(): number {
  const r = Math.random()
  const rupees = r < 0.7 ? 49 + Math.random() * 950 : r < 0.95 ? 1000 + Math.random() * 9000 : 10000 + Math.random() * 40000
  return Math.round(rupees) * 100
}

function randomPayer(config: SimulatorConfig): string {
  const roll = Math.random() * 100
  const n = Math.floor(Math.random() * 999)
  if (roll < config.declinePercent) return `decline.${n}@upi`
  if (roll < config.declinePercent + config.timeoutPercent) return `timeout.${n}@upi`
  return `${pick(FIRST_NAMES)}.${n}@${pick(HANDLES)}`
}

async function fireOne() {
  const config = state.config
  const merchant = pick(getSettings().merchants)
  update((s) => ({ sent: s.sent + 1 }))
  try {
    const { payment } = await api.createPayment(merchant.key, newIdempotencyKey(), {
      amount: randomAmount(),
      currency: 'INR',
      method: pick(METHODS),
      payer: randomPayer(config),
      description: `Order #${orderNumber++}`,
    })
    update((s) =>
      payment.status === 'SUCCEEDED'
        ? { succeeded: s.succeeded + 1 }
        : payment.status === 'FAILED'
          ? { failed: s.failed + 1 }
          : { processing: s.processing + 1 },
    )
  } catch (e) {
    if (e instanceof ApiError && e.status === 429) {
      update((s) => ({ rateLimited: s.rateLimited + 1 }))
    } else {
      update((s) => ({ errors: s.errors + 1, lastError: e instanceof Error ? e.message : String(e) }))
    }
  }
}

export const simulator = {
  start(config: SimulatorConfig) {
    simulator.stop()
    update({ ...initial, running: true, config, startedAt: Date.now() })
    timer = setInterval(() => void fireOne(), Math.max(50, 1000 / config.ratePerSecond))
  },
  stop() {
    if (timer) clearInterval(timer)
    timer = null
    if (state.running) update({ running: false })
  },
  configure(config: SimulatorConfig) {
    if (state.running) simulator.start({ ...config })
    else update({ config })
  },
}

export function useSimulator(): SimulatorState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => state,
  )
}

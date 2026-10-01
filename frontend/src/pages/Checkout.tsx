import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Building2, Check, Clock3, CreditCard, Headphones, Loader2, Lock, Minus, Plus, ShieldCheck, Smartphone, X } from 'lucide-react'
import { api, ApiError, newIdempotencyKey } from '../lib/api'
import { money } from '../lib/format'
import { panels } from '../lib/panels'
import { useSettings } from '../lib/settings'
import type { Payment, PaymentMethod } from '../lib/types'
import { PageHeader } from '../components/ui'

const PRICE_PAISE = 249_900
const BANKS = ['HDFC Bank', 'ICICI Bank', 'State Bank of India', 'Axis Bank', 'Kotak Mahindra Bank']

const DECLINE_MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Your bank declined the payment: insufficient funds.',
  AMOUNT_LIMIT_EXCEEDED: 'This amount is above the limit your bank allows.',
  DO_NOT_HONOR: 'Your bank declined the payment. Try another method.',
  NO_PSP_AVAILABLE: 'All payment providers are unavailable right now. Nothing was charged.',
  PSP_TIMEOUT_NOT_CHARGED: 'The bank did not respond in time. Nothing was charged.',
}

type Phase =
  | { name: 'idle' }
  | { name: 'processing' }
  | { name: 'done'; payment: Payment }
  | { name: 'error'; message: string }

export function Checkout() {
  const settings = useSettings()
  const merchant = settings.merchants.find((m) => m.id === 'm_acme') ?? settings.merchants[0]
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [upiId, setUpiId] = useState('anishka@okaxis')
  const [bank, setBank] = useState(BANKS[0])
  const [phase, setPhase] = useState<Phase>({ name: 'idle' })
  const idempotencyKey = useRef(newIdempotencyKey())

  const total = PRICE_PAISE * quantity
  const payer = method === 'UPI' ? upiId.trim() : method === 'CARD' ? 'tok_visa_4242' : `netbanking:${bank.toLowerCase().replace(/\s+/g, '-')}`

  // A payment the PSP has not confirmed yet (PSP timeout) is resolved by the sweeper: poll until it is.
  useEffect(() => {
    if (phase.name !== 'done' || phase.payment.status !== 'PROCESSING') return
    const timer = setInterval(async () => {
      try {
        const latest = await api.merchantPayment(merchant.key, phase.payment.id)
        if (latest.status !== 'PROCESSING') setPhase({ name: 'done', payment: latest })
      } catch {
        // keep polling
      }
    }, 1500)
    return () => clearInterval(timer)
  }, [phase, merchant.key])

  async function pay() {
    setPhase({ name: 'processing' })
    try {
      const { payment } = await api.createPayment(merchant.key, idempotencyKey.current, {
        amount: total,
        currency: 'INR',
        method,
        payer,
        description: `Aurora ANC headphones x${quantity}`,
      })
      setPhase({ name: 'done', payment })
    } catch (e) {
      setPhase({ name: 'error', message: e instanceof ApiError ? e.message : 'Something went wrong' })
    } finally {
      void queryClient.invalidateQueries()
    }
  }

  function reset() {
    idempotencyKey.current = newIdempotencyKey()
    setPhase({ name: 'idle' })
  }

  return (
    <>
      <PageHeader
        title="Checkout demo"
        description={`What a shopper sees. This page calls the real merchant API as ${merchant.name}, with an Idempotency-Key so a double click can never charge twice.`}
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        {/* Storefront */}
        <div className="card overflow-hidden lg:col-span-3">
          <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
            <p className="text-lg font-semibold tracking-tight">acme<span className="text-brand-600">.store</span></p>
            <p className="text-xs text-slate-500">Secure checkout</p>
          </div>
          <div className="grid gap-6 p-6 sm:grid-cols-2">
            <div className="grid aspect-square place-items-center rounded-2xl bg-gradient-to-br from-slate-900 via-brand-900 to-brand-600 text-white shadow-inner">
              <Headphones className="size-28 opacity-90" strokeWidth={1.2} />
            </div>
            <div className="flex flex-col">
              <p className="text-xs font-medium tracking-wide text-brand-600 uppercase">Audio</p>
              <h2 className="mt-1 text-xl font-semibold tracking-tight">Aurora ANC Headphones</h2>
              <p className="mt-2 text-sm text-slate-500">Adaptive noise cancelling, 40 hour battery, USB-C fast charging.</p>
              <p className="mt-4 text-2xl font-semibold tabular-nums">{money(PRICE_PAISE)}</p>
              <div className="mt-4 flex items-center gap-3">
                <span className="text-sm text-slate-600">Quantity</span>
                <div className="flex items-center rounded-lg border border-slate-300">
                  <button className="p-2 text-slate-500 hover:text-slate-900 disabled:opacity-40" disabled={quantity <= 1 || phase.name !== 'idle'} onClick={() => setQuantity((q) => q - 1)} aria-label="Less">
                    <Minus className="size-4" />
                  </button>
                  <span className="w-8 text-center text-sm font-medium tabular-nums">{quantity}</span>
                  <button className="p-2 text-slate-500 hover:text-slate-900 disabled:opacity-40" disabled={quantity >= 50 || phase.name !== 'idle'} onClick={() => setQuantity((q) => q + 1)} aria-label="More">
                    <Plus className="size-4" />
                  </button>
                </div>
              </div>
              <div className="mt-auto space-y-1.5 border-t border-slate-100 pt-4 text-sm">
                <div className="flex justify-between text-slate-500">
                  <span>Subtotal</span>
                  <span className="tabular-nums">{money(total)}</span>
                </div>
                <div className="flex justify-between text-slate-500">
                  <span>Delivery</span>
                  <span>Free</span>
                </div>
                <div className="flex justify-between text-base font-semibold">
                  <span>Total</span>
                  <span className="tabular-nums">{money(total)}</span>
                </div>
              </div>
            </div>
          </div>
          <div className="border-t border-slate-100 bg-slate-50 px-6 py-3 text-xs text-slate-500">
            Try <span className="font-mono">decline@upi</span> for a decline, <span className="font-mono">timeout@upi</span> for a slow bank, or a quantity above 40 to hit the bank's limit.
          </div>
        </div>

        {/* Hosted checkout widget */}
        <div className="lg:col-span-2">
          <div className="card overflow-hidden shadow-lg shadow-slate-200/60">
            <div className="bg-slate-950 px-5 py-4 text-white">
              <div className="flex items-center justify-between">
                <p className="text-sm text-slate-300">Paying {merchant.name}</p>
                <span className="flex items-center gap-1 text-[11px] text-slate-400">
                  <Lock className="size-3" /> PayPilot
                </span>
              </div>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{money(total)}</p>
            </div>

            {phase.name === 'idle' && (
              <div className="space-y-4 p-5">
                <div className="grid grid-cols-3 gap-2">
                  {(
                    [
                      { m: 'UPI', label: 'UPI', icon: Smartphone },
                      { m: 'CARD', label: 'Card', icon: CreditCard },
                      { m: 'NETBANKING', label: 'Netbanking', icon: Building2 },
                    ] as const
                  ).map(({ m, label, icon: Icon }) => (
                    <button
                      key={m}
                      onClick={() => setMethod(m)}
                      className={clsx(
                        'flex flex-col items-center gap-1.5 rounded-lg border py-3 text-xs font-medium transition',
                        method === m ? 'border-brand-500 bg-brand-50 text-brand-700 ring-2 ring-brand-100' : 'border-slate-200 text-slate-600 hover:border-slate-300',
                      )}
                    >
                      <Icon className="size-5" />
                      {label}
                    </button>
                  ))}
                </div>

                {method === 'UPI' && (
                  <label className="block">
                    <span className="label">UPI ID</span>
                    <input className="input mt-1 font-mono" value={upiId} onChange={(e) => setUpiId(e.target.value)} placeholder="name@bank" />
                  </label>
                )}
                {method === 'CARD' && (
                  <div className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-3">
                    <div className="flex items-center gap-3">
                      <div className="grid h-7 w-10 place-items-center rounded bg-gradient-to-br from-brand-600 to-violet-600 text-[9px] font-bold text-white">VISA</div>
                      <div>
                        <p className="text-sm font-medium">Saved card •••• 4242</p>
                        <p className="text-xs text-slate-500">Test token, no real card data</p>
                      </div>
                    </div>
                    <Check className="size-4 text-brand-600" />
                  </div>
                )}
                {method === 'NETBANKING' && (
                  <label className="block">
                    <span className="label">Bank</span>
                    <select className="input mt-1" value={bank} onChange={(e) => setBank(e.target.value)}>
                      {BANKS.map((b) => (
                        <option key={b}>{b}</option>
                      ))}
                    </select>
                  </label>
                )}

                <button
                  onClick={pay}
                  disabled={method === 'UPI' && !upiId.includes('@')}
                  className="w-full rounded-lg bg-brand-600 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                >
                  Pay {money(total)}
                </button>
                <p className="flex items-center justify-center gap-1.5 text-[11px] text-slate-400">
                  <ShieldCheck className="size-3.5" /> Idempotent request · safe to retry
                </p>
              </div>
            )}

            {phase.name === 'processing' && (
              <div className="flex flex-col items-center px-5 py-12 text-center">
                <Loader2 className="size-10 animate-spin text-brand-600" />
                <p className="mt-4 font-medium">Contacting your bank…</p>
                <p className="mt-1 text-sm text-slate-500">PayPilot is routing this payment to the healthiest provider.</p>
              </div>
            )}

            {phase.name === 'done' && <Result payment={phase.payment} onReset={reset} />}

            {phase.name === 'error' && (
              <div className="flex flex-col items-center px-5 py-10 text-center">
                <div className="grid size-14 animate-pop place-items-center rounded-full bg-rose-100 text-rose-600">
                  <X className="size-7" />
                </div>
                <p className="mt-4 font-semibold">Payment could not be started</p>
                <p className="mt-1 text-sm text-slate-500">{phase.message}</p>
                <button onClick={reset} className="mt-5 text-sm font-medium text-brand-600 hover:text-brand-700">
                  Try again
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

function Result({ payment, onReset }: { payment: Payment; onReset: () => void }) {
  const tone =
    payment.status === 'SUCCEEDED' || payment.status === 'REFUNDED'
      ? { bg: 'bg-emerald-100 text-emerald-600', icon: <Check className="size-7" />, title: 'Payment successful' }
      : payment.status === 'FAILED'
        ? { bg: 'bg-rose-100 text-rose-600', icon: <X className="size-7" />, title: 'Payment failed' }
        : { bg: 'bg-amber-100 text-amber-600', icon: <Clock3 className="size-7" />, title: 'Waiting for your bank' }
  const message =
    payment.status === 'FAILED'
      ? (DECLINE_MESSAGES[payment.failureCode ?? ''] ?? `Declined (${payment.failureCode}).`)
      : payment.status === 'PROCESSING'
        ? 'The bank is slow to answer. We never retry elsewhere in this state (that could charge you twice); we ask the bank what happened instead.'
        : `Paid via ${payment.method}, processed by PSP "${payment.psp}".`

  return (
    <div className="flex flex-col items-center px-5 py-10 text-center">
      <div className={clsx('grid size-14 animate-pop place-items-center rounded-full', tone.bg)}>{tone.icon}</div>
      <p className="mt-4 font-semibold">{tone.title}</p>
      <p className="mt-1 max-w-xs text-sm text-slate-500">{message}</p>
      <p className="mt-3 font-mono text-[11px] text-slate-400">{payment.id}</p>
      {payment.status === 'PROCESSING' && <Loader2 className="mt-3 size-4 animate-spin text-amber-500" />}
      <div className="mt-5 flex gap-4 text-sm font-medium">
        <button onClick={() => panels.openPayment(payment.id)} className="text-brand-600 hover:text-brand-700">
          View in console
        </button>
        <button onClick={onReset} className="text-slate-500 hover:text-slate-700">
          New order
        </button>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowRight, RefreshCw } from 'lucide-react'
import { api, ApiError, newIdempotencyKey } from '../lib/api'
import { money } from '../lib/format'
import { panels, usePanels } from '../lib/panels'
import { useSettings } from '../lib/settings'
import type { CreatePaymentResult, PaymentMethod } from '../lib/types'
import { Button, Modal, StatusBadge } from './ui'

const PAYER_PRESETS = [
  { value: 'anishka@okaxis', hint: 'succeeds' },
  { value: 'decline@upi', hint: 'declined' },
  { value: 'timeout@upi', hint: 'PSP times out' },
]

interface Outcome {
  label: string
  result?: CreatePaymentResult
  error?: string
}

export function NewPaymentModal() {
  const { newPaymentOpen } = usePanels()
  return (
    <Modal open={newPaymentOpen} onClose={panels.closeNewPayment} title="Create a payment">
      {newPaymentOpen && <NewPaymentForm />}
    </Modal>
  )
}

function NewPaymentForm() {
  const settings = useSettings()
  const queryClient = useQueryClient()
  const [merchantId, setMerchantId] = useState(settings.merchants[0].id)
  const [rupees, setRupees] = useState('499')
  const [method, setMethod] = useState<PaymentMethod>('UPI')
  const [payer, setPayer] = useState(PAYER_PRESETS[0].value)
  const [description, setDescription] = useState('Order #1042')
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey)
  const [sendTwice, setSendTwice] = useState(true)
  const [busy, setBusy] = useState(false)
  const [outcomes, setOutcomes] = useState<Outcome[]>([])

  const amount = Math.round(Number(rupees) * 100)
  const valid = amount > 0 && amount <= 100_000_000 && payer.trim().length > 0

  async function submit() {
    const merchant = settings.merchants.find((m) => m.id === merchantId)!
    const body = { amount, currency: 'INR' as const, method, payer: payer.trim(), description }
    setBusy(true)
    setOutcomes([])
    const results: Outcome[] = []
    for (let i = 0; i < (sendTwice ? 2 : 1); i++) {
      const label = i === 0 ? 'First request' : 'Same request again (retry)'
      try {
        results.push({ label, result: await api.createPayment(merchant.key, idempotencyKey, body) })
      } catch (e) {
        results.push({ label, error: e instanceof ApiError ? `${e.status} ${e.title ?? ''} ${e.message}` : String(e) })
      }
      setOutcomes([...results])
    }
    setBusy(false)
    void queryClient.invalidateQueries()
  }

  return (
    <div className="space-y-4 px-5 py-5">
      <div className="grid grid-cols-2 gap-3">
        <label className="block">
          <span className="label">Merchant</span>
          <select className="input mt-1" value={merchantId} onChange={(e) => setMerchantId(e.target.value)}>
            {settings.merchants.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="label">Amount (₹)</span>
          <input className="input mt-1 tabular-nums" inputMode="decimal" value={rupees} onChange={(e) => setRupees(e.target.value)} />
        </label>
      </div>

      <div>
        <span className="label">Method</span>
        <div className="mt-1 grid grid-cols-3 gap-1 rounded-lg bg-slate-100 p-1">
          {(['UPI', 'CARD', 'NETBANKING'] as PaymentMethod[]).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setMethod(m)}
              className={clsx('rounded-md py-1.5 text-xs font-medium transition', method === m ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700')}
            >
              {m === 'NETBANKING' ? 'Netbanking' : m}
            </button>
          ))}
        </div>
      </div>

      <label className="block">
        <span className="label">Payer (UPI ID or card token)</span>
        <input className="input mt-1 font-mono" value={payer} onChange={(e) => setPayer(e.target.value)} />
        <div className="mt-2 flex flex-wrap gap-1.5">
          {PAYER_PRESETS.map((p) => (
            <button
              key={p.value}
              type="button"
              onClick={() => setPayer(p.value)}
              className={clsx(
                'rounded-full border px-2.5 py-0.5 text-xs transition',
                payer === p.value ? 'border-brand-300 bg-brand-50 text-brand-700' : 'border-slate-200 text-slate-600 hover:border-slate-300',
              )}
            >
              {p.value} <span className="text-slate-400">· {p.hint}</span>
            </button>
          ))}
        </div>
      </label>

      <label className="block">
        <span className="label">Description</span>
        <input className="input mt-1" value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>

      <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
        <div className="flex items-center justify-between">
          <span className="label">Idempotency-Key</span>
          <button type="button" onClick={() => setIdempotencyKey(newIdempotencyKey())} className="flex items-center gap-1 text-xs text-brand-600 hover:text-brand-700">
            <RefreshCw className="size-3" /> New key
          </button>
        </div>
        <p className="mt-1 font-mono text-xs break-all text-slate-700">{idempotencyKey}</p>
        <label className="mt-3 flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" className="mt-0.5 accent-brand-600" checked={sendTwice} onChange={(e) => setSendTwice(e.target.checked)} />
          <span>
            Send the request twice with the same key
            <span className="block text-xs text-slate-500">Simulates a client retry. The second call must return the same payment without charging again.</span>
          </span>
        </label>
      </div>

      {outcomes.length > 0 && (
        <div className="space-y-2">
          {outcomes.map((o, i) => (
            <div key={i} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2.5 text-sm">
              <div className="min-w-0">
                <p className="text-xs text-slate-500">{o.label}</p>
                {o.result ? (
                  <p className="mt-0.5 flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-medium">HTTP {o.result.httpStatus}</span>
                    <StatusBadge status={o.result.payment.status} />
                    {o.result.replayed && <span className="rounded bg-brand-50 px-1.5 py-0.5 text-[11px] font-medium text-brand-700">Idempotent-Replayed</span>}
                    <span className="font-mono text-xs text-slate-500">{o.result.payment.id.slice(0, 16)}…</span>
                  </p>
                ) : (
                  <p className="mt-0.5 text-xs text-rose-700">{o.error}</p>
                )}
              </div>
              {o.result && (
                <button onClick={() => panels.openPayment(o.result!.payment.id)} className="shrink-0 text-xs font-medium text-brand-600 hover:text-brand-700">
                  View
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={panels.closeNewPayment}>
          Close
        </Button>
        <Button onClick={submit} loading={busy} disabled={!valid} icon={!busy ? <ArrowRight className="size-4" /> : undefined}>
          Charge {valid ? money(amount) : ''}
        </Button>
      </div>
    </div>
  )
}

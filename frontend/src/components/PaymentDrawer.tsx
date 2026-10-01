import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { BookOpenCheck, History, Check, CircleDot, Copy, RotateCcw, Send, Server } from 'lucide-react'
import { api } from '../lib/api'
import { clock, dateTime, money, titleCase } from '../lib/format'
import { panels, usePanels } from '../lib/panels'
import { merchantName } from '../lib/settings'
import { toast } from '../lib/toast'
import type { Attempt, JournalEntry, PaymentEventView } from '../lib/types'
import { Button, Drawer, ErrorNote, OutcomeBadge, Skeleton, StatusBadge } from './ui'

export function PaymentDrawer() {
  const { paymentId } = usePanels()
  return (
    <Drawer open={!!paymentId} onClose={panels.closePayment}>
      {paymentId && <PaymentDetailView key={paymentId} id={paymentId} />}
    </Drawer>
  )
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      onClick={() => {
        void navigator.clipboard?.writeText(value)
        setCopied(true)
        setTimeout(() => setCopied(false), 1200)
      }}
      className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
      title="Copy"
    >
      {copied ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />}
    </button>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="label">{label}</dt>
      <dd className="mt-1 text-sm text-slate-900">{children}</dd>
    </div>
  )
}

type TimelineItem =
  | { kind: 'event'; at: string; event: PaymentEventView }
  | { kind: 'attempt'; at: string; attempt: Attempt }

const EVENT_TITLES: Record<string, string> = {
  PAYMENT_CREATED: 'Payment created',
  PAYMENT_SUCCEEDED: 'Payment succeeded',
  PAYMENT_FAILED: 'Payment failed',
  PAYMENT_REFUNDED: 'Payment refunded',
}

function Timeline({ events, attempts }: { events: PaymentEventView[]; attempts: Attempt[] }) {
  const items: TimelineItem[] = [
    ...events.map((event) => ({ kind: 'event' as const, at: event.createdAt, event })),
    ...attempts.map((attempt) => ({ kind: 'attempt' as const, at: attempt.at, attempt })),
  ].sort((a, b) => {
    const diff = new Date(a.at).getTime() - new Date(b.at).getTime()
    if (diff !== 0) return diff
    return a.kind === 'event' && a.event.type === 'PAYMENT_CREATED' ? -1 : 1
  })

  return (
    <ol className="relative space-y-4 border-l border-slate-200 pl-6">
      {items.map((item, i) => (
        <li key={i} className="relative">
          <span
            className={clsx(
              'absolute top-0.5 -left-[33px] grid size-5 place-items-center rounded-full ring-4 ring-white',
              item.kind === 'event' ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-600',
            )}
          >
            {item.kind === 'event' ? <CircleDot className="size-3" /> : <Server className="size-3" />}
          </span>
          {item.kind === 'event' ? (
            <div>
              <p className="text-sm font-medium text-slate-900">{EVENT_TITLES[item.event.type] ?? titleCase(item.event.type)}</p>
              <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                <span>{clock(item.event.createdAt)}</span>
                <span className="text-slate-300">·</span>
                {item.event.publishedAt ? (
                  <span className="inline-flex items-center gap-1 text-emerald-700">
                    <Send className="size-3" /> delivered from outbox at {clock(item.event.publishedAt)}
                  </span>
                ) : (
                  <span className="text-amber-700">waiting in outbox</span>
                )}
              </p>
            </div>
          ) : (
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm font-medium text-slate-900">
                  PSP <span className="font-mono">{item.attempt.psp}</span>
                </p>
                <OutcomeBadge outcome={item.attempt.outcome} />
              </div>
              <p className="mt-0.5 text-xs text-slate-500">
                {clock(item.attempt.at)} · {item.attempt.latencyMs} ms
                {item.attempt.code && (
                  <>
                    {' · '}
                    <span className="font-mono">{item.attempt.code}</span>
                  </>
                )}
              </p>
            </div>
          )}
        </li>
      ))}
    </ol>
  )
}

function LedgerEntries({ entries }: { entries: JournalEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-sm text-slate-500">No money has moved for this payment, so the ledger has no entries.</p>
  }
  return (
    <div className="space-y-3">
      {entries.map((entry) => {
        const debits = entry.lines.filter((l) => l.direction === 'DEBIT').reduce((s, l) => s + l.amount, 0)
        return (
          <div key={entry.id} className="overflow-hidden rounded-lg border border-slate-200">
            <div className="flex items-center justify-between bg-slate-50 px-3 py-2 text-xs">
              <span className="font-medium text-slate-700">{titleCase(entry.entryType)}</span>
              <span className="text-slate-500">{dateTime(entry.createdAt)}</span>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-slate-500">
                  <th className="px-3 py-1.5 text-left font-medium">Account</th>
                  <th className="px-3 py-1.5 text-right font-medium">Debit</th>
                  <th className="px-3 py-1.5 text-right font-medium">Credit</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {entry.lines.map((line, i) => (
                  <tr key={i}>
                    <td className="px-3 py-1.5 font-mono text-xs text-slate-700">{line.account}</td>
                    <td className="px-3 py-1.5 text-right tabular-nums">{line.direction === 'DEBIT' ? money(line.amount) : ''}</td>
                    <td className="px-3 py-1.5 text-right tabular-nums">{line.direction === 'CREDIT' ? money(line.amount) : ''}</td>
                  </tr>
                ))}
                <tr className="bg-emerald-50/50 text-xs font-medium text-emerald-800">
                  <td className="px-3 py-1.5">Balanced</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{money(debits)}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{money(debits)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        )
      })}
    </div>
  )
}

function PaymentDetailView({ id }: { id: string }) {
  const queryClient = useQueryClient()
  const detail = useQuery({
    queryKey: ['payment', id],
    queryFn: () => api.payment(id),
    refetchInterval: (q) => (q.state.data?.payment.status === 'PROCESSING' ? 1500 : 5000),
  })
  const ledger = useQuery({ queryKey: ['ledger', 'payment', id], queryFn: () => api.entriesForPayment(id), refetchInterval: 3000 })
  const refund = useMutation({
    mutationFn: () => api.refund(id),
    onSuccess: () => {
      toast.success('Refund confirmed', 'The PSP confirmed it and a reversing ledger entry is on its way.')
      void queryClient.invalidateQueries()
    },
    onError: (e: Error) => toast.error('Refund failed', e.message),
  })

  if (detail.isLoading) {
    return (
      <div className="space-y-4 p-6">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-4 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }
  if (detail.isError || !detail.data) {
    return (
      <div className="p-6">
        <ErrorNote error={detail.error ?? 'Payment not found'} />
      </div>
    )
  }

  const { payment, attempts, events } = detail.data
  return (
    <div className="flex h-full flex-col overflow-y-auto">
      <div className="border-b border-slate-100 px-6 pt-6 pb-5">
        <p className="label">Payment</p>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <p className="text-3xl font-semibold tracking-tight tabular-nums">{money(payment.amount)}</p>
          <StatusBadge status={payment.status} />
        </div>
        <div className="mt-2 flex items-center gap-1 font-mono text-xs text-slate-500">
          {payment.id}
          <CopyButton value={payment.id} />
        </div>
        {payment.status === 'SUCCEEDED' && (
          <div className="mt-4">
            <Button variant="secondary" size="sm" icon={<RotateCcw className="size-3.5" />} loading={refund.isPending} onClick={() => refund.mutate()}>
              Refund {money(payment.amount)}
            </Button>
          </div>
        )}
      </div>

      <div className="space-y-8 px-6 py-6">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4">
          <Field label="Merchant">{merchantName(payment.merchantId)}</Field>
          <Field label="Method">{payment.method}</Field>
          <Field label="Payer">
            <span className="font-mono text-xs">{payment.payer}</span>
          </Field>
          <Field label="PSP">{payment.psp ? <span className="font-mono">{payment.psp}</span> : '—'}</Field>
          <Field label="PSP reference">
            <span className="font-mono text-xs">{payment.pspReference ?? '—'}</span>
          </Field>
          <Field label="Failure code">
            {payment.failureCode ? <span className="font-mono text-xs text-rose-700">{payment.failureCode}</span> : '—'}
          </Field>
          <Field label="Created">{dateTime(payment.createdAt)}</Field>
          <Field label="Description">{payment.description || '—'}</Field>
        </dl>

        <section>
          <h3 className="mb-4 flex items-center gap-2 text-sm font-semibold">
            <History className="size-4 text-slate-400" /> Timeline
          </h3>
          <Timeline events={events} attempts={attempts} />
        </section>

        <section>
          <h3 className="mb-4 flex items-center gap-2 text-sm font-semibold">
            <BookOpenCheck className="size-4 text-slate-400" /> Ledger entries
          </h3>
          {ledger.isLoading ? <Skeleton className="h-24 w-full" /> : <LedgerEntries entries={ledger.data ?? []} />}
        </section>
      </div>
    </div>
  )
}

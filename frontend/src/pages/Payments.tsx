import { useEffect, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { CreditCard, Plus, Search } from 'lucide-react'
import { api } from '../lib/api'
import { dateTime, money, shortId, timeAgo } from '../lib/format'
import { panels } from '../lib/panels'
import { merchantName, useSettings } from '../lib/settings'
import type { PaymentStatus } from '../lib/types'
import { Button, Card, Empty, ErrorNote, PageHeader, Skeleton, StatusBadge } from '../components/ui'

const TABS: { label: string; value: PaymentStatus | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Succeeded', value: 'SUCCEEDED' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Processing', value: 'PROCESSING' },
  { label: 'Refunded', value: 'REFUNDED' },
]

function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

export function Payments() {
  const settings = useSettings()
  const [status, setStatus] = useState<PaymentStatus | ''>('')
  const [merchantId, setMerchantId] = useState('')
  const [search, setSearch] = useState('')
  const [limit, setLimit] = useState(50)
  const q = useDebounced(search.trim())

  const payments = useQuery({
    queryKey: ['payments', { status, merchantId, q, limit }],
    queryFn: () => api.payments({ status, merchantId, q, limit }),
    refetchInterval: 3000,
    placeholderData: keepPreviousData,
  })
  const rows = payments.data ?? []

  return (
    <>
      <PageHeader
        title="Payments"
        description="Search every payment across merchants. Click a row to see its PSP attempts, outbox events and ledger entries."
        actions={
          <Button icon={<Plus className="size-4" />} onClick={() => panels.openNewPayment()}>
            New payment
          </Button>
        }
      />

      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex gap-1 overflow-x-auto rounded-lg bg-slate-100 p-1">
            {TABS.map((t) => (
              <button
                key={t.label}
                onClick={() => setStatus(t.value)}
                className={clsx(
                  'rounded-md px-3 py-1.5 text-sm font-medium whitespace-nowrap transition',
                  status === t.value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
              <input className="input pl-9 sm:w-64" placeholder="Search id, payer or description" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
            <select className="input sm:w-44" value={merchantId} onChange={(e) => setMerchantId(e.target.value)}>
              <option value="">All merchants</option>
              {settings.merchants.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {payments.isError && (
          <div className="p-4">
            <ErrorNote error={payments.error} />
          </div>
        )}

        {payments.isLoading ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <Empty
            icon={<CreditCard className="size-5" />}
            title="No payments match"
            description={q || status || merchantId ? 'Try a different filter.' : 'Create a payment or start the traffic simulator in the chaos lab.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr>
                  <th className="table-head">Payment</th>
                  <th className="table-head">Merchant</th>
                  <th className="table-head text-right">Amount</th>
                  <th className="table-head">Method</th>
                  <th className="table-head">Payer</th>
                  <th className="table-head">PSP</th>
                  <th className="table-head">Status</th>
                  <th className="table-head">Reason</th>
                  <th className="table-head text-right">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((p) => (
                  <tr key={p.id} onClick={() => panels.openPayment(p.id)} className="cursor-pointer transition hover:bg-slate-50">
                    <td className="table-cell mono text-slate-600">{shortId(p.id, 18)}</td>
                    <td className="table-cell text-slate-600">{merchantName(p.merchantId)}</td>
                    <td className="table-cell text-right font-medium tabular-nums">{money(p.amount)}</td>
                    <td className="table-cell text-slate-600">{p.method}</td>
                    <td className="table-cell mono max-w-[180px] truncate text-slate-600">{p.payer}</td>
                    <td className="table-cell mono text-slate-600">{p.psp ?? '—'}</td>
                    <td className="table-cell">
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="table-cell mono text-rose-700">{p.failureCode ?? ''}</td>
                    <td className="table-cell text-right text-slate-500" title={dateTime(p.createdAt)}>
                      {timeAgo(p.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-xs text-slate-500">
          <span>
            Showing {rows.length} payment{rows.length === 1 ? '' : 's'}
            {payments.isFetching && !payments.isLoading && ' · refreshing'}
          </span>
          <label className="flex items-center gap-2">
            Rows
            <select className="rounded-md border border-slate-300 bg-white px-2 py-1" value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              {[50, 100, 200].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
        </div>
      </Card>
    </>
  )
}

import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { AlertTriangle, BookOpenCheck, Landmark, Scale, Store, TrendingUp } from 'lucide-react'
import { api } from '../lib/api'
import { count, dateTime, money, titleCase } from '../lib/format'
import { panels } from '../lib/panels'
import type { AccountBalance, AccountType } from '../lib/types'
import { Card, CardHeader, Empty, ErrorNote, PageHeader, Skeleton } from '../components/ui'

const GROUPS: { type: AccountType; title: string; blurb: string; icon: typeof Landmark }[] = [
  { type: 'ASSET', title: 'Assets', blurb: 'Money PSPs collected and still owe PayPilot', icon: Landmark },
  { type: 'LIABILITY', title: 'Liabilities', blurb: 'Money PayPilot owes merchants at settlement', icon: Store },
  { type: 'REVENUE', title: 'Revenue', blurb: 'Platform fees earned (2% per payment)', icon: TrendingUp },
]

export function Ledger() {
  const integrity = useQuery({ queryKey: ['ledger', 'integrity'], queryFn: api.integrity, refetchInterval: 3000 })
  const accounts = useQuery({ queryKey: ['ledger', 'accounts'], queryFn: api.accounts, refetchInterval: 3000 })
  const entries = useQuery({ queryKey: ['ledger', 'entries'], queryFn: () => api.recentEntries(12), refetchInterval: 3000 })

  return (
    <>
      <PageHeader
        title="Ledger"
        description="Append-only, double-entry books fed by payment events. Every entry's debits equal its credits, so money is only ever moved, never created or lost."
      />

      {integrity.isError && <ErrorNote error={integrity.error} />}
      {integrity.data && (
        <div
          className={clsx(
            'mb-4 flex flex-col gap-3 rounded-xl border p-5 sm:flex-row sm:items-center sm:justify-between',
            integrity.data.balanced ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50',
          )}
        >
          <div className="flex items-center gap-3">
            <div className={clsx('rounded-lg p-2', integrity.data.balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700')}>
              {integrity.data.balanced ? <Scale className="size-5" /> : <AlertTriangle className="size-5" />}
            </div>
            <div>
              <p className={clsx('font-semibold', integrity.data.balanced ? 'text-emerald-900' : 'text-rose-900')}>
                {integrity.data.balanced ? 'Books balanced' : 'Books do NOT balance'}
              </p>
              <p className={clsx('text-sm', integrity.data.balanced ? 'text-emerald-800' : 'text-rose-800')}>
                Checked across {count(integrity.data.entries)} journal entries, per entry and in total.
              </p>
            </div>
          </div>
          <div className="flex gap-6 text-sm">
            <div>
              <p className="label">Total debits</p>
              <p className="mt-0.5 font-semibold tabular-nums">{money(integrity.data.totalDebits)}</p>
            </div>
            <div>
              <p className="label">Total credits</p>
              <p className="mt-0.5 font-semibold tabular-nums">{money(integrity.data.totalCredits)}</p>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {GROUPS.map((group) => (
          <AccountGroup key={group.type} group={group} accounts={(accounts.data ?? []).filter((a) => a.type === group.type)} loading={accounts.isLoading} />
        ))}
      </div>

      <Card className="mt-4">
        <CardHeader title="Journal" subtitle="Latest entries, newest first. Click a payment id to open it." />
        {entries.isLoading ? (
          <Skeleton className="m-5 h-40" />
        ) : (entries.data ?? []).length === 0 ? (
          <Empty icon={<BookOpenCheck className="size-5" />} title="No entries yet" description="Entries appear as soon as a payment succeeds or is refunded." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr>
                  <th className="table-head">Entry</th>
                  <th className="table-head">Account</th>
                  <th className="table-head text-right">Debit</th>
                  <th className="table-head text-right">Credit</th>
                </tr>
              </thead>
              <tbody>
                {(entries.data ?? []).map((entry) =>
                  entry.lines.map((line, i) => (
                    <tr key={`${entry.id}-${i}`} className={clsx(i === entry.lines.length - 1 && 'border-b border-slate-100')}>
                      {i === 0 && (
                        <td className="table-cell align-top" rowSpan={entry.lines.length}>
                          <p className="font-medium">{titleCase(entry.entryType)}</p>
                          <button onClick={() => panels.openPayment(entry.paymentId)} className="mt-0.5 font-mono text-xs text-brand-600 hover:text-brand-700">
                            {entry.paymentId.slice(0, 18)}…
                          </button>
                          <p className="mt-0.5 text-xs text-slate-400">{dateTime(entry.createdAt)}</p>
                        </td>
                      )}
                      <td className="table-cell mono text-slate-700">{line.account}</td>
                      <td className="table-cell text-right tabular-nums">{line.direction === 'DEBIT' ? money(line.amount) : ''}</td>
                      <td className="table-cell text-right tabular-nums">{line.direction === 'CREDIT' ? money(line.amount) : ''}</td>
                    </tr>
                  )),
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

function AccountGroup({ group, accounts, loading }: { group: (typeof GROUPS)[number]; accounts: AccountBalance[]; loading: boolean }) {
  const Icon = group.icon
  const total = accounts.reduce((sum, a) => sum + a.balance, 0)
  return (
    <Card>
      <div className="flex items-start justify-between px-5 pt-5">
        <div>
          <p className="text-sm font-semibold">{group.title}</p>
          <p className="mt-0.5 text-xs text-slate-500">{group.blurb}</p>
        </div>
        <div className="rounded-lg bg-slate-100 p-2 text-slate-600">
          <Icon className="size-4" />
        </div>
      </div>
      <p className="px-5 pt-3 text-2xl font-semibold tracking-tight tabular-nums">{money(total)}</p>
      <ul className="mt-3 divide-y divide-slate-100 border-t border-slate-100">
        {loading && <Skeleton className="m-4 h-12" />}
        {!loading && accounts.length === 0 && <li className="px-5 py-3 text-xs text-slate-400">No accounts yet</li>}
        {accounts.map((a) => (
          <li key={a.code} className="flex items-center justify-between px-5 py-2.5 text-sm">
            <span className="truncate font-mono text-xs text-slate-600" title={a.code}>{a.code}</span>
            <span className="font-medium tabular-nums">{money(a.balance)}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

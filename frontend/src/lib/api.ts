import { getSettings } from './settings'
import type {
  AccountBalance,
  Attempt,
  CreatePaymentBody,
  CreatePaymentResult,
  IntegrityReport,
  JournalEntry,
  OpsStats,
  Payment,
  PaymentDetail,
  PaymentStatus,
  PspBehavior,
} from './types'

/** Error with the RFC 9457 problem details the backend returns. */
export class ApiError extends Error {
  readonly status: number
  readonly title?: string

  constructor(status: number, title: string | undefined, detail: string) {
    super(detail)
    this.status = status
    this.title = title
  }
}

interface CallOptions {
  method?: string
  key: string
  body?: unknown
  headers?: Record<string, string>
}

async function call<T>(path: string, { method = 'GET', key, body, headers = {} }: CallOptions) {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: {
        'X-Api-Key': key,
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the PayPilot gateway on port 8080. Is it running?')
  }
  const text = await response.text()
  const data = text ? safeJson(text) : null
  if (!response.ok) {
    const problem = (data ?? {}) as { title?: string; detail?: string }
    const detail = problem.detail ?? (response.status === 429 ? 'Rate limit reached, slow down.' : response.statusText)
    throw new ApiError(response.status, problem.title, detail || `HTTP ${response.status}`)
  }
  return { data: data as T, status: response.status, headers: response.headers }
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return { detail: text }
  }
}

const admin = () => getSettings().adminKey

export function newIdempotencyKey(): string {
  return `ui-${crypto.randomUUID()}`
}

export const api = {
  // --- merchant API --------------------------------------------------------------------------
  async createPayment(merchantKey: string, idempotencyKey: string, body: CreatePaymentBody): Promise<CreatePaymentResult> {
    const { data, status, headers } = await call<Payment>('/api/v1/payments', {
      method: 'POST',
      key: merchantKey,
      body,
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return { httpStatus: status, replayed: headers.get('Idempotent-Replayed') === 'true', payment: data }
  },

  async merchantPayment(merchantKey: string, id: string): Promise<Payment> {
    return (await call<Payment>(`/api/v1/payments/${id}`, { key: merchantKey })).data
  },

  // --- ops API (admin key) --------------------------------------------------------------------
  async stats(): Promise<OpsStats> {
    return (await call<OpsStats>('/api/v1/ops/stats', { key: admin() })).data
  },

  async payments(filter: { status?: PaymentStatus | ''; merchantId?: string; q?: string; limit?: number } = {}): Promise<Payment[]> {
    const params = new URLSearchParams()
    if (filter.status) params.set('status', filter.status)
    if (filter.merchantId) params.set('merchantId', filter.merchantId)
    if (filter.q) params.set('q', filter.q)
    params.set('limit', String(filter.limit ?? 50))
    return (await call<Payment[]>(`/api/v1/ops/payments?${params}`, { key: admin() })).data
  },

  async payment(id: string): Promise<PaymentDetail> {
    return (await call<PaymentDetail>(`/api/v1/ops/payments/${id}`, { key: admin() })).data
  },

  async refund(id: string): Promise<Payment> {
    return (await call<Payment>(`/api/v1/ops/payments/${id}/refund`, { method: 'POST', key: admin() })).data
  },

  async recentAttempts(): Promise<Attempt[]> {
    return (await call<Attempt[]>('/api/v1/ops/attempts/recent', { key: admin() })).data
  },

  async chaos(): Promise<Record<string, PspBehavior>> {
    return (await call<Record<string, PspBehavior>>('/api/v1/ops/chaos', { key: admin() })).data
  },

  async updateChaos(psp: string, behavior: PspBehavior): Promise<PspBehavior> {
    return (await call<PspBehavior>(`/api/v1/ops/chaos/${psp}`, { method: 'PUT', key: admin(), body: behavior })).data
  },

  async resetChaos(): Promise<Record<string, PspBehavior>> {
    return (await call<Record<string, PspBehavior>>('/api/v1/ops/chaos/reset', { method: 'POST', key: admin() })).data
  },

  // --- ledger API (admin key) -----------------------------------------------------------------
  async accounts(): Promise<AccountBalance[]> {
    return (await call<AccountBalance[]>('/api/v1/ledger/accounts', { key: admin() })).data
  },

  async integrity(): Promise<IntegrityReport> {
    return (await call<IntegrityReport>('/api/v1/ledger/integrity', { key: admin() })).data
  },

  async recentEntries(limit = 15): Promise<JournalEntry[]> {
    return (await call<JournalEntry[]>(`/api/v1/ledger/entries/recent?limit=${limit}`, { key: admin() })).data
  },

  async entriesForPayment(paymentId: string): Promise<JournalEntry[]> {
    return (await call<JournalEntry[]>(`/api/v1/ledger/entries?paymentId=${encodeURIComponent(paymentId)}`, { key: admin() })).data
  },
}

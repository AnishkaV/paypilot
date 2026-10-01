// Shapes returned by the PayPilot APIs (see the Java records with the same names).

export type PaymentStatus = 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED'
export type PaymentMethod = 'UPI' | 'CARD' | 'NETBANKING'
export type PspOutcome = 'SUCCEEDED' | 'DECLINED' | 'NOT_PROCESSED' | 'UNKNOWN' | 'CIRCUIT_OPEN'
export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | string

export interface Payment {
  id: string
  merchantId: string
  status: PaymentStatus
  /** paise */
  amount: number
  currency: string
  method: PaymentMethod
  payer: string
  description: string | null
  psp: string | null
  pspReference: string | null
  failureCode: string | null
  createdAt: string
  updatedAt: string
}

export interface Attempt {
  paymentId: string
  psp: string
  outcome: PspOutcome
  code: string | null
  latencyMs: number
  at: string
}

export interface PaymentEventView {
  eventId: string
  type: string
  createdAt: string
  publishedAt: string | null
}

export interface PaymentDetail {
  payment: Payment
  attempts: Attempt[]
  events: PaymentEventView[]
}

export interface OpsStats {
  totals: {
    payments: number
    succeeded: number
    failed: number
    inFlight: number
    refunded: number
    grossVolume: number
    refundedVolume: number
    successRate: number
    avgPspLatencyMs: number
  }
  psps: PspStats[]
  timeline: TimelinePoint[]
  eventTransport: string
  outboxLag: number
  generatedAt: string
}

export interface PspStats {
  psp: string
  circuitState: CircuitState
  failureRate: number
  calls: number
  succeeded: number
  declined: number
  notProcessed: number
  unknown: number
  circuitOpenSkips: number
  avgLatencyMs: number
}

export interface TimelinePoint {
  minute: string
  succeeded: number
  failed: number
  inFlight: number
  volume: number
}

export interface PspBehavior {
  down: boolean
  declineRate: number
  errorRate: number
  timeoutRate: number
  latencyMs: number
}

export type AccountType = 'ASSET' | 'LIABILITY' | 'REVENUE' | 'EXPENSE'

export interface AccountBalance {
  code: string
  type: AccountType
  currency: string
  debits: number
  credits: number
  balance: number
}

export interface LedgerLine {
  account: string
  direction: 'DEBIT' | 'CREDIT'
  amount: number
}

export interface JournalEntry {
  id: number
  eventId: string
  paymentId: string
  entryType: string
  description: string
  createdAt: string
  lines: LedgerLine[]
}

export interface IntegrityReport {
  balanced: boolean
  totalDebits: number
  totalCredits: number
  entries: number
  unbalancedEntryIds: number[]
}

export interface CreatePaymentBody {
  amount: number
  currency: 'INR'
  method: PaymentMethod
  payer: string
  description?: string
}

export interface CreatePaymentResult {
  httpStatus: number
  replayed: boolean
  payment: Payment
}

import { BookOpenCheck, CircuitBoard, Fingerprint, GitBranch, Inbox, Plug, TimerReset } from 'lucide-react'
import { Card, PageHeader } from '../components/ui'

interface BoxProps {
  x: number
  y: number
  w: number
  h: number
  title: string
  port?: string
  items: string[]
  tone: 'brand' | 'slate' | 'amber' | 'green'
  footer?: string
}

const TONES = {
  brand: { fill: '#eef2ff', stroke: '#818cf8', title: '#312e81' },
  slate: { fill: '#f8fafc', stroke: '#94a3b8', title: '#0f172a' },
  amber: { fill: '#fffbeb', stroke: '#f59e0b', title: '#78350f' },
  green: { fill: '#ecfdf5', stroke: '#34d399', title: '#064e3b' },
}

function Box({ x, y, w, h, title, port, items, tone, footer }: BoxProps) {
  const c = TONES[tone]
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={12} fill={c.fill} stroke={c.stroke} strokeWidth={1.5} />
      <text x={x + 14} y={y + 24} fontSize={14} fontWeight={600} fill={c.title}>
        {title}
      </text>
      {port && (
        <text x={x + w - 14} y={y + 24} fontSize={11} textAnchor="end" fill="#64748b" fontFamily="ui-monospace, monospace">
          {port}
        </text>
      )}
      {items.map((item, i) => (
        <text key={item} x={x + 14} y={y + 48 + i * 19} fontSize={12} fill="#334155">
          • {item}
        </text>
      ))}
      {footer && (
        <text x={x + 14} y={y + h - 12} fontSize={11} fill="#64748b" fontFamily="ui-monospace, monospace">
          {footer}
        </text>
      )}
    </g>
  )
}

function Diagram() {
  return (
    <svg viewBox="0 0 980 400" className="h-auto w-full" role="img" aria-label="PayPilot architecture diagram">
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" fill="#64748b" />
        </marker>
      </defs>

      <Box x={10} y={150} w={150} h={100} title="Browser" tone="slate" items={['Ops console', 'Checkout demo']} />
      <Box x={210} y={135} w={170} h={130} title="API gateway" port=":8080" tone="brand" items={['API key auth', 'Rate limit / merchant', 'Routing, serves UI']} />
      <Box
        x={440}
        y={20}
        w={240}
        h={210}
        title="Payment service"
        port=":8081"
        tone="brand"
        items={['Idempotency keys', 'Payment state machine', 'PSP router + circuit breakers', 'Transactional outbox', 'Timeout sweeper']}
        footer="H2 (local) · Postgres (infra)"
      />
      <Box x={750} y={20} w={210} h={150} title="Mock PSPs" port=":8083" tone="amber" items={['alpha', 'beta', 'gamma', 'failure injection API']} />
      <Box x={750} y={230} w={210} h={110} title="Ledger service" port=":8082" tone="green" items={['Idempotent consumer', 'Double-entry books']} footer="H2 (local) · Postgres (infra)" />

      <line x1={160} y1={200} x2={206} y2={200} stroke="#64748b" strokeWidth={1.5} markerEnd="url(#arrow)" />
      <path d="M380,170 C410,170 410,110 436,110" fill="none" stroke="#64748b" strokeWidth={1.5} markerEnd="url(#arrow)" />
      <text x={392} y={100} fontSize={11} fill="#64748b">payments</text>

      <line x1={680} y1={80} x2={746} y2={80} stroke="#64748b" strokeWidth={1.5} markerEnd="url(#arrow)" />
      <text x={690} y={72} fontSize={11} fill="#64748b">charge</text>

      <path d="M680,200 C715,200 715,285 746,285" fill="none" stroke="#10b981" strokeWidth={2} strokeDasharray="6 4" markerEnd="url(#arrow)" />
      <text x={600} y={262} fontSize={11} fill="#047857">outbox events</text>
      <text x={600} y={277} fontSize={11} fill="#047857">(HTTP or Kafka)</text>

      <path d="M295,265 V372 H855 V344" fill="none" stroke="#94a3b8" strokeWidth={1.5} markerEnd="url(#arrow)" />
      <text x={470} y={366} fontSize={11} fill="#64748b">ledger + ops APIs (admin key only)</text>
    </svg>
  )
}

const DECISIONS = [
  {
    icon: Fingerprint,
    title: 'Idempotency keys',
    body: 'Every POST /payments carries an Idempotency-Key. A retry returns the original payment; the same key with a different body is rejected (422). A unique constraint is the guarantee, a short lock handles concurrent duplicates.',
    file: 'payment-service/.../PaymentService.java',
  },
  {
    icon: Inbox,
    title: 'Transactional outbox',
    body: 'The payment change and its event are written in one database transaction, then a poller publishes the event. No lost events, no events for rolled-back changes. Delivery is at-least-once.',
    file: 'payment-service/.../outbox/',
  },
  {
    icon: GitBranch,
    title: 'Safe failover',
    body: 'Fail over only when the PSP provably did not charge (connection refused, 503). After a timeout the payment stays Processing and a sweeper asks the PSP, because retrying elsewhere could charge twice.',
    file: 'payment-service/.../PaymentProcessor.java',
  },
  {
    icon: CircuitBoard,
    title: 'Circuit breakers',
    body: 'One Resilience4j breaker per PSP. Declines count as healthy calls; 503s and timeouts count as failures. An open breaker skips the PSP instantly instead of waiting on it.',
    file: 'payment-service/.../PspRouter.java',
  },
  {
    icon: BookOpenCheck,
    title: 'Double-entry ledger',
    body: 'Append-only journal where debits always equal credits. Refunds post a reversing entry instead of editing history. The consumer is idempotent on event id, so duplicates never double-post.',
    file: 'ledger-service/.../posting/',
  },
  {
    icon: Plug,
    title: 'Ports and adapters',
    body: 'The same code runs with no infrastructure (H2, HTTP events, in-memory locks) or production-style (Postgres, Kafka, Redis). Switching is a Spring profile, not a rewrite.',
    file: 'application-local.yml / application-infra.yml',
  },
]

const STACK = ['Java 17+', 'Spring Boot 3.5', 'Spring Cloud Gateway', 'Spring Data JPA', 'Flyway', 'Resilience4j', 'H2 / PostgreSQL', 'Kafka (infra profile)', 'Redis (infra profile)', 'JUnit 5 · Mockito · Testcontainers', 'React 19 · TypeScript', 'TanStack Query · Tailwind CSS · Recharts']

const ROADMAP = [
  { phase: 'Phase 2', text: 'Docker Compose and the infra profile by default, Kafka Streams risk engine, success-rate based routing' },
  { phase: 'Phase 3', text: 'AI ops copilot: Spring AI agent with read-only tools, RAG over runbooks, exposed as an MCP server' },
  { phase: 'Phase 4', text: 'OpenTelemetry tracing, Prometheus and Grafana dashboards, k6 load tests with published numbers' },
  { phase: 'Phase 5', text: 'Kubernetes deployment on AWS, CI/CD with GitHub Actions' },
]

export function Architecture() {
  return (
    <>
      <PageHeader
        title="Architecture"
        description="Four Spring Boot services behind a gateway. Each owns its database; they talk over HTTP and events only."
      />

      <Card className="overflow-x-auto p-4 sm:p-6">
        <div className="min-w-[720px]">
          <Diagram />
        </div>
      </Card>

      <h2 className="mt-8 mb-3 text-sm font-semibold text-slate-900">Design decisions</h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {DECISIONS.map((d) => (
          <Card key={d.title} className="flex flex-col p-5">
            <div className="flex items-center gap-2.5">
              <div className="rounded-lg bg-brand-50 p-2 text-brand-600">
                <d.icon className="size-4" />
              </div>
              <p className="font-semibold">{d.title}</p>
            </div>
            <p className="mt-3 flex-1 text-sm leading-relaxed text-slate-600">{d.body}</p>
            <p className="mt-3 font-mono text-[11px] text-slate-400">{d.file}</p>
          </Card>
        ))}
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <p className="font-semibold">Tech stack</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {STACK.map((s) => (
              <span key={s} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-700">
                {s}
              </span>
            ))}
          </div>
        </Card>
        <Card className="p-5">
          <p className="flex items-center gap-2 font-semibold">
            <TimerReset className="size-4 text-slate-400" /> Roadmap
          </p>
          <ol className="mt-3 space-y-2.5">
            {ROADMAP.map((r) => (
              <li key={r.phase} className="flex gap-3 text-sm">
                <span className="shrink-0 rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">{r.phase}</span>
                <span className="text-slate-600">{r.text}</span>
              </li>
            ))}
          </ol>
        </Card>
      </div>
    </>
  )
}

import { useEffect, type ButtonHTMLAttributes, type ReactNode } from 'react'
import clsx from 'clsx'
import { Loader2, X } from 'lucide-react'
import type { CircuitState, PaymentStatus, PspOutcome } from '../lib/types'
import { titleCase } from '../lib/format'

// ---------------------------------------------------------------------------------- layout

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={clsx('card', className)}>{children}</div>
}

export function CardHeader({ title, subtitle, right }: { title: string; subtitle?: string; right?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
      <div>
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </div>
      {right}
    </div>
  )
}

// ---------------------------------------------------------------------------------- buttons

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white shadow-sm hover:bg-brand-700 focus-visible:ring-brand-200',
  secondary: 'border border-slate-300 bg-white text-slate-700 shadow-sm hover:bg-slate-50 focus-visible:ring-slate-200',
  ghost: 'text-slate-600 hover:bg-slate-100 focus-visible:ring-slate-200',
  danger: 'bg-rose-600 text-white shadow-sm hover:bg-rose-700 focus-visible:ring-rose-200',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  icon,
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md'; loading?: boolean; icon?: ReactNode }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition outline-none focus-visible:ring-4 disabled:cursor-not-allowed disabled:opacity-60',
        size === 'sm' ? 'px-2.5 py-1.5 text-xs' : 'px-3.5 py-2 text-sm',
        VARIANTS[variant],
        className,
      )}
    >
      {loading ? <Loader2 className="size-4 animate-spin" /> : icon}
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------------- badges

type Tone = 'green' | 'red' | 'amber' | 'slate' | 'violet' | 'blue' | 'orange'
const TONES: Record<Tone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  red: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/25',
  slate: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  violet: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  blue: 'bg-brand-50 text-brand-700 ring-brand-600/20',
  orange: 'bg-orange-50 text-orange-700 ring-orange-600/20',
}
const DOTS: Record<Tone, string> = {
  green: 'bg-emerald-500',
  red: 'bg-rose-500',
  amber: 'bg-amber-500',
  slate: 'bg-slate-400',
  violet: 'bg-violet-500',
  blue: 'bg-brand-500',
  orange: 'bg-orange-500',
}

export function Badge({ tone, children, dot, pulse }: { tone: Tone; children: ReactNode; dot?: boolean; pulse?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', TONES[tone])}>
      {dot && (
        <span className="relative flex size-1.5">
          {pulse && <span className={clsx('absolute inline-flex size-full animate-ping rounded-full opacity-75', DOTS[tone])} />}
          <span className={clsx('relative inline-flex size-1.5 rounded-full', DOTS[tone])} />
        </span>
      )}
      {children}
    </span>
  )
}

const STATUS_TONE: Record<PaymentStatus, Tone> = {
  SUCCEEDED: 'green',
  FAILED: 'red',
  PROCESSING: 'amber',
  CREATED: 'slate',
  REFUNDED: 'violet',
}

export function StatusBadge({ status }: { status: PaymentStatus }) {
  return (
    <Badge tone={STATUS_TONE[status]} dot pulse={status === 'PROCESSING'}>
      {titleCase(status)}
    </Badge>
  )
}

const OUTCOME_TONE: Record<PspOutcome, Tone> = {
  SUCCEEDED: 'green',
  DECLINED: 'red',
  NOT_PROCESSED: 'orange',
  UNKNOWN: 'amber',
  CIRCUIT_OPEN: 'slate',
}

export function OutcomeBadge({ outcome }: { outcome: PspOutcome }) {
  return <Badge tone={OUTCOME_TONE[outcome]}>{titleCase(outcome)}</Badge>
}

export function CircuitBadge({ state }: { state: CircuitState }) {
  if (state === 'CLOSED') return <Badge tone="green" dot>Circuit closed</Badge>
  if (state === 'OPEN') return <Badge tone="red" dot pulse>Circuit open</Badge>
  if (state === 'HALF_OPEN') return <Badge tone="amber" dot pulse>Half-open · probing</Badge>
  return <Badge tone="slate">{state}</Badge>
}

// ---------------------------------------------------------------------------------- data display

export function Stat({
  label,
  value,
  hint,
  icon,
  accent = 'brand',
}: {
  label: string
  value: ReactNode
  hint?: ReactNode
  icon?: ReactNode
  accent?: 'brand' | 'green' | 'red' | 'amber' | 'slate'
}) {
  const accents = {
    brand: 'bg-brand-50 text-brand-600',
    green: 'bg-emerald-50 text-emerald-600',
    red: 'bg-rose-50 text-rose-600',
    amber: 'bg-amber-50 text-amber-600',
    slate: 'bg-slate-100 text-slate-600',
  }
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        {icon && <div className={clsx('rounded-lg p-2', accents[accent])}>{icon}</div>}
      </div>
      <p className="mt-2 text-2xl font-semibold tracking-tight text-slate-900 tabular-nums">{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </Card>
  )
}

export function Empty({ icon, title, description, action }: { icon: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-400">{icon}</div>
      <p className="text-sm font-medium text-slate-900">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-md bg-slate-200/70', className)} />
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return (
    <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
      {message}
    </div>
  )
}

// ---------------------------------------------------------------------------------- inputs

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label?: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors outline-none focus-visible:ring-4 focus-visible:ring-brand-100',
        checked ? 'bg-rose-500' : 'bg-slate-300',
      )}
    >
      <span
        className={clsx(
          'pointer-events-none mt-0.5 ml-0.5 inline-block size-4 rounded-full bg-white shadow transition-transform',
          checked && 'translate-x-4',
        )}
      />
    </button>
  )
}

export function Slider({
  label,
  value,
  min,
  max,
  step = 1,
  unit = '',
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  step?: number
  unit?: string
  onChange: (v: number) => void
}) {
  return (
    <label className="block">
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="font-medium text-slate-600">{label}</span>
        <span className="font-mono text-slate-900 tabular-nums">
          {value}
          {unit}
        </span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-1.5 w-full cursor-pointer appearance-none rounded-full bg-slate-200 accent-brand-600"
      />
    </label>
  )
}

// ---------------------------------------------------------------------------------- overlays

function useEscape(onClose: () => void) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])
}

export function Drawer({ open, onClose, children, width = 'max-w-2xl' }: { open: boolean; onClose: () => void; children: ReactNode; width?: string }) {
  useEscape(onClose)
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 animate-fade-in bg-slate-900/30 backdrop-blur-[1px]" onClick={onClose} />
      <div className={clsx('relative flex h-full w-full animate-slide-in flex-col bg-white shadow-2xl', width)}>
        <button onClick={onClose} className="absolute top-4 right-4 z-10 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600" aria-label="Close">
          <X className="size-5" />
        </button>
        {children}
      </div>
    </div>
  )
}

export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  useEscape(onClose)
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-4 sm:p-8">
      <div className="fixed inset-0 animate-fade-in bg-slate-900/40 backdrop-blur-[1px]" onClick={onClose} />
      <div className="card relative mt-8 w-full max-w-lg animate-fade-in shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <h2 className="text-base font-semibold">{title}</h2>
          <button onClick={onClose} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600" aria-label="Close">
            <X className="size-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

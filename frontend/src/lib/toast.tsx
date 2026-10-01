import { useSyncExternalStore } from 'react'
import { CheckCircle2, Info, X, XCircle } from 'lucide-react'
import clsx from 'clsx'

type Tone = 'success' | 'error' | 'info'
interface Toast {
  id: number
  tone: Tone
  title: string
  detail?: string
}

let toasts: Toast[] = []
let nextId = 1
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((l) => l())
}

function push(tone: Tone, title: string, detail?: string) {
  const id = nextId++
  toasts = [...toasts, { id, tone, title, detail }].slice(-4)
  emit()
  setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4000)
}

function dismiss(id: number) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

export const toast = {
  success: (title: string, detail?: string) => push('success', title, detail),
  error: (title: string, detail?: string) => push('error', title, detail),
  info: (title: string, detail?: string) => push('info', title, detail),
}

const ICONS = { success: CheckCircle2, error: XCircle, info: Info }
const TONES = {
  success: 'text-emerald-600',
  error: 'text-rose-600',
  info: 'text-brand-600',
}

export function Toaster() {
  const items = useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => toasts,
  )
  return (
    <div className="pointer-events-none fixed right-4 bottom-4 z-[60] flex w-80 flex-col gap-2">
      {items.map((t) => {
        const Icon = ICONS[t.tone]
        return (
          <div key={t.id} className="card pointer-events-auto flex animate-slide-in items-start gap-3 p-3.5 shadow-lg">
            <Icon className={clsx('mt-0.5 size-5 shrink-0', TONES[t.tone])} />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-slate-900">{t.title}</p>
              {t.detail && <p className="mt-0.5 text-xs break-words text-slate-500">{t.detail}</p>}
            </div>
            <button onClick={() => dismiss(t.id)} className="text-slate-400 hover:text-slate-600" aria-label="Dismiss">
              <X className="size-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}

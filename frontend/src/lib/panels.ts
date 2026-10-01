import { useSyncExternalStore } from 'react'

// Global UI state for the payment drawer and the "new payment" dialog, so any page can open them.

interface PanelState {
  paymentId: string | null
  newPaymentOpen: boolean
}

let state: PanelState = { paymentId: null, newPaymentOpen: false }
const listeners = new Set<() => void>()

function set(patch: Partial<PanelState>) {
  state = { ...state, ...patch }
  listeners.forEach((l) => l())
}

export const panels = {
  openPayment: (paymentId: string) => set({ paymentId, newPaymentOpen: false }),
  closePayment: () => set({ paymentId: null }),
  openNewPayment: () => set({ newPaymentOpen: true }),
  closeNewPayment: () => set({ newPaymentOpen: false }),
}

export function usePanels(): PanelState {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => state,
  )
}

import { useSyncExternalStore } from 'react'

// API keys the console uses. These are the local demo keys from api-gateway/application.yml.
// They are stored in this browser only and can be changed on the Settings page.

export interface MerchantProfile {
  id: string
  name: string
  key: string
}

export interface Settings {
  adminKey: string
  merchants: MerchantProfile[]
}

const STORAGE_KEY = 'paypilot.settings.v1'

export const DEFAULT_SETTINGS: Settings = {
  adminKey: 'sk_admin_demo_only',
  merchants: [
    { id: 'm_demo', name: 'Demo Merchant', key: 'pk_test_demo_merchant' },
    { id: 'm_acme', name: 'Acme Store', key: 'pk_test_acme_store' },
  ],
}

const listeners = new Set<() => void>()
let current: Settings = load()

function load(): Settings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Settings
      if (parsed.adminKey && Array.isArray(parsed.merchants) && parsed.merchants.length > 0) {
        return parsed
      }
    }
  } catch {
    // storage unavailable or corrupt: fall back to defaults
  }
  return DEFAULT_SETTINGS
}

export function getSettings(): Settings {
  return current
}

export function saveSettings(next: Settings) {
  current = next
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // ignore: settings still apply for this session
  }
  listeners.forEach((l) => l())
}

export function resetSettings() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
  saveSettings(DEFAULT_SETTINGS)
}

export function useSettings(): Settings {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => current,
  )
}

export function merchantName(id: string): string {
  return current.merchants.find((m) => m.id === id)?.name ?? id
}

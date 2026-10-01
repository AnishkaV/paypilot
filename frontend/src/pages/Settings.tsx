import { useState } from 'react'
import { KeyRound, RotateCcw, Save } from 'lucide-react'
import { DEFAULT_SETTINGS, resetSettings, saveSettings, useSettings, type Settings } from '../lib/settings'
import { toast } from '../lib/toast'
import { Button, Card, CardHeader, PageHeader } from '../components/ui'

export function SettingsPage() {
  const settings = useSettings()
  const [draft, setDraft] = useState<Settings>(settings)

  function updateMerchant(index: number, patch: Partial<Settings['merchants'][number]>) {
    setDraft({ ...draft, merchants: draft.merchants.map((m, i) => (i === index ? { ...m, ...patch } : m)) })
  }

  return (
    <>
      <PageHeader
        title="Settings"
        description="API keys this console uses. The defaults match the demo keys in api-gateway/src/main/resources/application.yml. They are kept in this browser only."
      />

      <Card className="max-w-3xl">
        <CardHeader title="API keys" subtitle="The gateway turns each merchant key into a trusted merchant id; the admin key unlocks the ops and ledger APIs." />
        <div className="space-y-5 p-5">
          <label className="block">
            <span className="label flex items-center gap-1.5">
              <KeyRound className="size-3.5" /> Admin key
            </span>
            <input className="input mt-1 font-mono" value={draft.adminKey} onChange={(e) => setDraft({ ...draft, adminKey: e.target.value })} />
          </label>

          {draft.merchants.map((m, i) => (
            <div key={i} className="grid gap-3 rounded-lg border border-slate-200 p-4 sm:grid-cols-3">
              <label className="block">
                <span className="label">Merchant id</span>
                <input className="input mt-1 font-mono" value={m.id} onChange={(e) => updateMerchant(i, { id: e.target.value })} />
              </label>
              <label className="block">
                <span className="label">Display name</span>
                <input className="input mt-1" value={m.name} onChange={(e) => updateMerchant(i, { name: e.target.value })} />
              </label>
              <label className="block">
                <span className="label">API key</span>
                <input className="input mt-1 font-mono" value={m.key} onChange={(e) => updateMerchant(i, { key: e.target.value })} />
              </label>
            </div>
          ))}

          <div className="flex justify-end gap-2">
            <Button
              variant="secondary"
              icon={<RotateCcw className="size-4" />}
              onClick={() => {
                resetSettings()
                setDraft(structuredClone(DEFAULT_SETTINGS))
                toast.info('Settings reset to the demo keys')
              }}
            >
              Reset to defaults
            </Button>
            <Button
              icon={<Save className="size-4" />}
              onClick={() => {
                saveSettings(draft)
                toast.success('Settings saved')
              }}
            >
              Save
            </Button>
          </div>
        </div>
      </Card>

      <Card className="mt-4 max-w-3xl p-5 text-sm text-slate-600">
        <p className="font-semibold text-slate-900">Useful links while the stack is running</p>
        <ul className="mt-2 space-y-1">
          <li>
            Payment API docs: <a className="text-brand-600 hover:underline" href="http://localhost:8081/swagger-ui.html" target="_blank" rel="noreferrer">localhost:8081/swagger-ui.html</a>
          </li>
          <li>
            Ledger API docs: <a className="text-brand-600 hover:underline" href="http://localhost:8082/swagger-ui.html" target="_blank" rel="noreferrer">localhost:8082/swagger-ui.html</a>
          </li>
          <li>
            Payments database (H2 console): <a className="text-brand-600 hover:underline" href="http://localhost:8081/h2-console" target="_blank" rel="noreferrer">localhost:8081/h2-console</a>
            <span className="text-slate-400"> · JDBC URL jdbc:h2:file:./data/payments, user sa, empty password</span>
          </li>
        </ul>
      </Card>
    </>
  )
}


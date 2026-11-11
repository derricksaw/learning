import { useState } from 'react'
import { FlaskConical, RefreshCw, ArrowRight } from 'lucide-react'
import { ApiError, request } from '../api'
import type { Item, Replica } from '../api'
import { Badge } from '../components/ui'

export default function ConcurrencyLab({
  items,
  onChange,
}: {
  items: Item[]
  onChange: () => void
}) {
  const [id, setId] = useState(1)
  const [snapshots, setSnapshots] = useState<
    Partial<Record<Replica, { available: number; version: number }>>
  >({})
  const [values, setValues] = useState({ a: 10, b: 12 })
  const [results, setResults] = useState({ a: '', b: '' })
  const [pending, setPending] = useState({ a: false, b: false })
  async function capture() {
    try {
      const [a, b] = await Promise.all([
        request<{ available: number; version: number }>('a', `/inventory/${id}`),
        request<{ available: number; version: number }>('b', `/inventory/${id}`),
      ])
      setSnapshots({ a, b })
      setResults({ a: '', b: '' })
    } catch (error) {
      setResults({ a: (error as Error).message, b: '' })
    }
  }
  async function save(replica: Replica) {
    const snapshot = snapshots[replica]
    if (!snapshot) return
    setPending((old) => ({ ...old, [replica]: true }))
    try {
      const next = await request<{ available: number; version: number }>(
        replica,
        `/inventory/${id}/stock`,
        'PUT',
        { available: values[replica], expectedVersion: snapshot.version },
      )
      setResults((old) => ({
        ...old,
        [replica]: `Saved ${next.available} units · new version ${next.version}`,
      }))
      onChange()
    } catch (error) {
      const e = error as ApiError
      setResults((old) => ({
        ...old,
        [replica]: `${e.status || 'Connection error'} · ${e.message}`,
      }))
    } finally {
      setPending((old) => ({ ...old, [replica]: false }))
    }
  }
  return (
    <>
      <div className="info-strip">
        <FlaskConical size={22} />
        <div>
          <strong>Capture both snapshots. Save A, then save B.</strong>
          <p>
            The second save should conflict because its snapshot is stale. These actions change real
            local stock.
          </p>
        </div>
      </div>
      <div className="lab-toolbar">
        <label className="field">
          Product
          <select
            value={id}
            onChange={(e) => {
              setId(Number(e.target.value))
              setSnapshots({})
              setResults({ a: '', b: '' })
            }}
          >
            {items.map((item) => (
              <option value={item.productId} key={item.productId}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <button className="button primary" onClick={() => void capture()}>
          <RefreshCw size={16} />
          Capture both snapshots
        </button>
      </div>
      <div className="lab-grid">
        {(['a', 'b'] as const).map((replica) => (
          <section className="panel lab-card" key={replica}>
            <div className="panel-heading">
              <h2>Instance {replica.toUpperCase()}</h2>
              <Badge tone="neutral">Port {replica === 'a' ? '8080' : '8081'}</Badge>
            </div>
            <div className="lab-content">
              <p className="snapshot">
                {snapshots[replica]
                  ? `${snapshots[replica]!.available} units · captured version ${snapshots[replica]!.version}`
                  : 'Capture a snapshot to begin.'}
              </p>
              <label className="field">
                Corrected stock quantity
                <input
                  type="number"
                  min="0"
                  value={values[replica]}
                  onChange={(e) =>
                    setValues((old) => ({ ...old, [replica]: Number(e.target.value) }))
                  }
                />
              </label>
              <button
                className="button primary"
                disabled={!snapshots[replica] || pending[replica]}
                onClick={() => void save(replica)}
              >
                {pending[replica] ? 'Saving…' : `Save through instance ${replica.toUpperCase()}`}
                <ArrowRight size={16} />
              </button>
              {results[replica] && (
                <p className="lab-result" role="status">
                  {results[replica]}
                </p>
              )}
            </div>
          </section>
        ))}
      </div>
      <p className="quiet-note">
        Both instances use the same PostgreSQL row. Separate Java locks cannot coordinate them; the
        database version predicate does.
      </p>
    </>
  )
}

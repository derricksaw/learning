import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { request, shortId, sku } from '../api'
import type { Order, Replica } from '../api'

type Cursor = { createdAt: string; id: string }
type Page = { items: Order[]; hasMore: boolean; nextCursor: Cursor | null; nextOffset: number | null }
type Selection = {
  version: 'v1' | 'v2'; mode: 'offset' | 'cursor'; limit: string; offset: string; productId: string
  from: string; to: string; q: string; cursor?: Cursor
}
type Report = { productId: number; productName: string; orders: number; units: number; invoices: number; billedMinor: number }
type ReportResult = { items: Report[]; source: string; version: string; generatedAt: string; cacheStatus: string }
const initial: Selection = { version: 'v1', mode: 'offset', limit: '25', offset: '0', productId: '', from: '', to: '', q: '' }

export default function OrderHistory({ replica }: { replica: Replica }) {
  const [draft, setDraft] = useState<Selection>(initial)
  const [selection, setSelection] = useState<Selection>(initial)
  const [trail, setTrail] = useState<Selection[]>([])
  const [page, setPage] = useState<Page | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [elapsed, setElapsed] = useState<number | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [report, setReport] = useState<ReportResult | null>(null)
  const [reportVersion, setReportVersion] = useState<'v1' | 'v2'>('v1')
  const [source, setSource] = useState('live')
  const [reportReload, setReportReload] = useState(0)
  const [reportBusy, setReportBusy] = useState(false)
  const reportActionSequence = useRef(0)

  useEffect(() => {
    reportActionSequence.current += 1
    setReportBusy(false)
  }, [replica, source, reportVersion])
  const [reportError, setReportError] = useState('')

  useEffect(() => {
    // A new replica starts at a fresh boundary; never carry a pending page across instances.
    setTrail([])
    setSelection((old) => ({ ...old, offset: '0', cursor: undefined }))
  }, [replica])

  useEffect(() => {
    let ignore = false
    setBusy(true)
    setPage(null)
    setError('')
    setElapsed(null)
    const parameters = new URLSearchParams({ mode: selection.mode, version: selection.version, limit: selection.limit })
    if (selection.mode === 'offset') parameters.set('offset', selection.offset)
    for (const key of ['productId', 'from', 'to', 'q'] as const) {
      if (selection[key]) parameters.set(key, selection[key])
    }
    if (selection.mode === 'cursor' && selection.cursor) {
      // Keep the original timestamp string: browser date conversions can lose precision.
      parameters.set('afterCreatedAt', selection.cursor.createdAt)
      parameters.set('afterId', selection.cursor.id)
    }
    const started = performance.now()
    request<Page>(replica, `/workspace/order-history?${parameters}`)
      .then((value) => { if (!ignore) { setPage(value); setElapsed(Math.round(performance.now() - started)) } })
      .catch((cause: Error) => { if (!ignore) setError(cause.message) })
      .finally(() => { if (!ignore) setBusy(false) })
    return () => { ignore = true }
  }, [replica, selection])

  useEffect(() => {
    let ignore = false
    setReport(null)
    setReportError('')
    if (reportOpen) {
      request<ReportResult>(replica, `/workspace/order-report-view?source=${source}&version=${reportVersion}`)
        .then((value) => { if (!ignore) setReport(value) })
        .catch((cause: Error) => { if (!ignore) setReportError(cause.message) })
    }
    return () => { ignore = true }
  }, [replica, reportOpen, source, reportVersion, reportReload])

  async function reportAction(action: 'refresh' | 'cache') {
    const sequence = ++reportActionSequence.current
    setReportBusy(true)
    setReport(null)
    setReportError('')
    try {
      await request(replica, `/workspace/order-report-view/${action}?version=${reportVersion}`,
        action === 'refresh' ? 'POST' : 'DELETE')
      if (sequence === reportActionSequence.current) setReportReload((old) => old + 1)
    } catch (cause) {
      if (sequence === reportActionSequence.current) setReportError((cause as Error).message)
    } finally {
      if (sequence === reportActionSequence.current) setReportBusy(false)
    }
  }

  function apply(event: FormEvent) {
    event.preventDefault()
    setTrail([])
    setSelection({ ...draft, offset: draft.mode === 'offset' ? draft.offset : '0', cursor: undefined })
  }
  function next() {
    if (!page?.hasMore) return
    setTrail((old) => [...old, selection])
    setSelection({ ...selection, offset: String(page.nextOffset ?? 0),
      cursor: selection.mode === 'cursor' ? page.nextCursor ?? undefined : undefined })
  }
  function previous() {
    const last = trail.at(-1)
    if (!last) return
    setSelection(last)
    setTrail((old) => old.slice(0, -1))
  }

  return <>
    <section className="panel order-history">
      <div className="panel-heading"><div><h2>Order history</h2><p>Browse earlier orders with product, date and name filters. Dates use UTC.</p></div></div>
      <form onSubmit={apply}>
        <div className="history-filters">
          <label className="field">Query version<select value={draft.version} onChange={(e) => setDraft({ ...draft, version: e.target.value as Selection['version'], mode: e.target.value === 'v1' ? 'offset' : draft.mode })}><option value="v1">V1 baseline</option><option value="v2">V2 candidate</option></select></label>
          <label className="field">Pagination<select value={draft.mode} onChange={(e) => setDraft({ ...draft, mode: e.target.value as Selection['mode'], version: e.target.value === 'cursor' ? 'v2' : draft.version })}><option value="offset">Page offset</option><option value="cursor">Continue by cursor</option></select></label>
          <label className="field">Page size<select value={draft.limit} onChange={(e) => setDraft({ ...draft, limit: e.target.value })}><option>25</option><option>50</option><option>100</option></select></label>
          <label className="field">Starting offset<input type="number" min="0" max="1000000" disabled={draft.mode === 'cursor'} value={draft.offset} onChange={(e) => setDraft({ ...draft, offset: e.target.value })} /></label>
          <label className="field">Product ID<input type="number" min="1" placeholder="Any" value={draft.productId} onChange={(e) => setDraft({ ...draft, productId: e.target.value })} /></label>
          <label className="field">From date<input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} /></label>
          <label className="field">Through date<input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} /></label>
          <label className="field">Product name contains<input maxLength={120} value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} /></label>
        </div>
        <div className="history-actions"><button className="button primary" disabled={busy}>Apply filters / refresh</button><span aria-live="polite">{busy ? 'Loading orders…' : elapsed !== null ? `Request completed in ${elapsed} ms` : ''}</span></div>
      </form>
      {error && <p className="history-error" role="alert">{error}</p>}
      {page && <>
        <div className="table-scroll"><table><thead><tr><th>Order</th><th>Product</th><th>Quantity</th><th>Placed at (UTC)</th><th>Invoice</th></tr></thead><tbody>
          {page.items.map((order) => <tr key={order.id}><td title={order.id}>{shortId(order.id)}</td><td>{order.productName}<br /><span className="muted">{sku(order.productId)}</span></td><td>{order.quantity}</td><td>{order.createdAt}</td><td>{order.hasInvoice ? 'Created' : 'Not created'}</td></tr>)}
          {page.items.length === 0 && <tr><td colSpan={5}>No orders match these filters.</td></tr>}
        </tbody></table></div>
        <div className="history-actions"><button type="button" className="button secondary" onClick={previous} disabled={busy || trail.length === 0}>Previous</button><span>{page.items.length} orders shown{selection.mode === 'offset' ? ` · offset ${selection.offset}` : ''}</span><button type="button" className="button secondary" onClick={next} disabled={busy || !page.hasMore}>Next</button></div>
      </>}
    </section>
    <section className="panel order-history"><div className="panel-heading"><div><h2>Order totals by product</h2><p>Compare the live report with materialized and cached results for the first 100 catalog products.</p></div><button type="button" className="button secondary" onClick={() => setReportOpen(!reportOpen)}>{reportOpen ? 'Hide report' : 'Load report'}</button></div>
      <div className="history-actions">
        <label className="field">Report version<select disabled={reportBusy} value={reportVersion} onChange={(e) => setReportVersion(e.target.value as 'v1' | 'v2')}><option value="v1">V1 baseline</option><option value="v2">V2 candidate</option></select></label>
        <label className="field">Report source<select disabled={reportBusy} value={source} onChange={(e) => setSource(e.target.value)}><option value="live">Live SQL</option><option value="materialized">Materialized view</option><option value="redis">Redis cache</option></select></label>
        <button className="button secondary" disabled={reportBusy || !reportOpen} onClick={() => setReportReload((old) => old + 1)}>Reload report</button>
        {source === 'materialized' && <button className="button secondary" disabled={reportBusy} onClick={() => void reportAction('refresh')}>Refresh materialized view</button>}
        {source === 'redis' && <button className="button secondary" disabled={reportBusy} onClick={() => void reportAction('cache')}>Invalidate cache</button>}
      </div>
      {reportError && <p className="history-error" role="alert">{reportError}</p>}
      {reportOpen && !report && !reportError && <p>Loading report…</p>}
      {report && <p className="history-actions">Source: {report.source} · Version: {report.version} · Generated at: {report.generatedAt} · Cache: {report.cacheStatus}</p>}
      {report && <div className="table-scroll"><table><thead><tr><th>Product</th><th>Orders</th><th>Units ordered</th><th>Invoices</th><th>Billed (MYR)</th></tr></thead><tbody>{report.items.map((row) => <tr key={row.productId}><td>{row.productName}</td><td>{row.orders}</td><td>{row.units}</td><td>{row.invoices}</td><td>{(row.billedMinor / 100).toFixed(2)}</td></tr>)}{report.items.length === 0 && <tr><td colSpan={5}>No products.</td></tr>}</tbody></table></div>}
    </section>
  </>
}

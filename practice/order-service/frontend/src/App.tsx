import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Box,
  Check,
  ChevronDown,
  CircleHelp,
  Download,
  FileText,
  FlaskConical,
  Layers3,
  LayoutDashboard,
  Package,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  ShoppingBag,
  SlidersHorizontal,
  Sparkles,
  Truck,
  X,
} from 'lucide-react'
import { ApiError, request, shortId, sku } from './api'
import { Badge, Product, Dialog } from './components/ui'
import ConcurrencyLab from './pages/ConcurrencyLab'
import LearningGuide from './pages/LearningGuide'
import type { Invoice, Item, Order, Replica, Summary } from './api'

type Page = 'Overview' | 'Inventory' | 'Orders' | 'Invoices' | 'Concurrency lab' | 'Learning guide'
type Modal = { type: 'order' } | { type: 'adjust'; item: Item } | null
type Notice = { kind: 'success' | 'error'; title: string; detail?: string }
type Event = { title: string; detail: string; time: string; ok: boolean }
const pages: { title: Page; icon: typeof Box }[] = [
  { title: 'Overview', icon: LayoutDashboard },
  { title: 'Inventory', icon: Package },
  { title: 'Orders', icon: ShoppingBag },
  { title: 'Invoices', icon: FileText },
  { title: 'Concurrency lab', icon: FlaskConical },
]
const descriptions: Record<Page, string> = {
  Overview: 'A clear view of your orders, stock, and what needs attention.',
  Inventory: 'Keep your catalog in balance, one careful adjustment at a time.',
  Orders: 'From a reserved item to a shipping label. All in one place.',
  Invoices: 'One order, one invoice. Durable records you can return to.',
  'Concurrency lab': 'Two instances. One inventory record. See coordination in action.',
  'Learning guide': 'Explore the mechanisms behind this working application.',
}
export default function App() {
  const [page, setPage] = useState<Page>('Overview')
  const [replica, setReplica] = useState<Replica>('a')
  const [items, setItems] = useState<Item[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [summary, setSummary] = useState<Summary>({ orders: 0, units: 0, lowStock: 0, invoices: 0 })
  const refreshSequence = useRef(0)
  const [online, setOnline] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [query, setQuery] = useState('')
  const [onlyLow, setOnlyLow] = useState(false)
  const [modal, setModal] = useState<Modal>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [busy, setBusy] = useState('')
  const [events, setEvents] = useState<Event[]>([])
  const [document, setDocument] = useState<{ documentId: string; content: string } | null>(null)
  const refresh = useCallback(async () => {
    const sequence = ++refreshSequence.current
    try {
      const [stock, recent, billing, total] = await Promise.all([
        request<Item[]>(replica, '/workspace/inventory'),
        request<Order[]>(replica, '/workspace/orders'),
        request<Invoice[]>(replica, '/workspace/invoices'),
        request<Summary>(replica, '/workspace/summary'),
      ])
      if (sequence !== refreshSequence.current) return
      setItems(stock)
      setOrders(recent)
      setInvoices(billing)
      setSummary(total)
      setOnline(true)
      setLoadError('')
    } catch (error) {
      if (sequence === refreshSequence.current) {
        setOnline(false)
        setLoadError((error as Error).message)
      }
    } finally {
      if (sequence === refreshSequence.current) setLoading(false)
    }
  }, [replica])
  useEffect(() => {
    setLoading(true)
    void refresh()
    const timer = setInterval(() => void refresh(), 10000)
    return () => clearInterval(timer)
  }, [refresh])
  useEffect(() => {
    if (!notice) return
    const timer = setTimeout(() => setNotice(null), 8000)
    return () => clearTimeout(timer)
  }, [notice])
  const record = (title: string, detail: string, ok: boolean) =>
    setEvents((old) =>
      [
        {
          title,
          detail,
          ok,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
        ...old,
      ].slice(0, 6),
    )
  async function act<T>(
    key: string,
    title: string,
    operation: () => Promise<T>,
  ): Promise<T | undefined> {
    setBusy(key)
    try {
      const value = await operation()
      setNotice({ kind: 'success', title })
      record(title, `Completed on instance ${replica.toUpperCase()}`, true)
      await refresh()
      return value
    } catch (error) {
      const e = error as ApiError
      const title =
        e.code === 'EXERCISE_INCOMPLETE'
          ? 'This exercise is ready for your implementation'
          : e.code === 'STALE_INVENTORY'
            ? 'Inventory changed. Reload before saving again.'
            : e.message
      setNotice({
        kind: 'error',
        title,
        detail: `${e.status ? `HTTP ${e.status} · ` : ''}${e.code ?? 'ERROR'}`,
      })
      record(title, e.code ?? 'ERROR', false)
      return undefined
    } finally {
      setBusy('')
    }
  }
  const filtered = items.filter(
    (item) =>
      `${item.name} ${item.category} ${sku(item.productId)}`
        .toLowerCase()
        .includes(query.toLowerCase()) &&
      (!onlyLow || item.available <= item.reorderPoint),
  )
  const filteredOrders = orders.filter((order) =>
    `${order.productName} ${order.id}`.toLowerCase().includes(query.toLowerCase()),
  )
  const navigate = (next: Page) => {
    setPage(next)
    setQuery('')
    setOnlyLow(false)
  }
  function inventoryTable(rows: Item[]) {
    return (
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th>Category</th>
              <th>Supplier</th>
              <th>Available</th>
              <th>Status</th>
              <th>
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((item) => (
              <tr key={item.productId}>
                <td>
                  <Product item={item} />
                </td>
                <td className="muted">{item.category}</td>
                <td><strong>{item.supplierName}</strong><br /><span className="muted">{item.supplierEmail}</span></td>
                <td>
                  <strong>{item.available}</strong>
                  <span className="unit"> units</span>
                </td>
                <td>
                  <Badge
                    tone={
                      item.available === 0
                        ? 'red'
                        : item.available <= item.reorderPoint
                          ? 'amber'
                          : 'green'
                    }
                  >
                    {item.available === 0
                      ? 'Out of stock'
                      : item.available <= item.reorderPoint
                        ? 'Low stock'
                        : 'In stock'}
                  </Badge>
                </td>
                <td className="align-right">
                  <button
                    className="text-button"
                    onClick={() => setModal({ type: 'adjust', item })}
                  >
                    Adjust <ArrowUpRight size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && (
          <div className="empty">
            {loading ? 'Loading inventory…' : 'No products match this view.'}
          </div>
        )}
      </div>
    )
  }
  function orderTable(rows: Order[]) {
    return (
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>Product</th>
              <th>Quantity</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((order) => (
              <tr key={order.id}>
                <td>
                  <strong className="mono">#{shortId(order.id)}</strong>
                  <small>
                    {new Date(order.createdAt).toLocaleString([], {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    })}
                  </small>
                </td>
                <td>{order.productName}</td>
                <td>{order.quantity}</td>
                <td>
                  <Badge>Placed</Badge>
                </td>
                <td>
                  <div className="row-actions">
                    <button
                      className="text-button"
                      disabled={busy === order.id}
                      onClick={() =>
                        void act(order.id, 'Simulated shipping label created', () =>
                          request(replica, `/orders/${order.id}/shipping-labels`, 'POST'),
                        )
                      }
                    >
                      <Truck size={15} /> Label
                    </button>
                    <button
                      className="text-button"
                      disabled={busy === order.id}
                      onClick={() =>
                        void act(order.id, 'Invoice ready', () =>
                          request(replica, `/orders/${order.id}/invoice`, 'POST'),
                        )
                      }
                    >
                      <FileText size={15} /> {order.hasInvoice ? 'Invoice' : 'Create invoice'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && (
          <div className="empty">
            <ShoppingBag size={28} />
            <h3>Your first order starts here</h3>
            <p>Reserve an item from your inventory to begin the workflow.</p>
            <button className="button primary" onClick={() => setModal({ type: 'order' })}>
              <Plus size={16} /> Create an order
            </button>
          </div>
        )}
      </div>
    )
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault()
            navigate('Overview')
          }}
        >
          <span className="brand-mark">
            <Package size={25} strokeWidth={1.6} />
          </span>
          <span>
            order<span className="brand-light">lab</span>
            <small>LEARN BY BUILDING</small>
          </span>
        </a>
        <div className="workspace-switch">
          <span className="workspace-avatar">OL</span>
          <div>
            <strong>Learning workspace</strong>
            <small>Local development</small>
          </div>
          <ChevronDown size={14} />
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav aria-label="Main navigation">
          {pages.map(({ title, icon: Icon }) => (
            <button
              key={title}
              className={`nav-item ${page === title ? 'active' : ''}`}
              onClick={() => navigate(title)}
              aria-current={page === title ? 'page' : undefined}
            >
              <Icon size={19} />
              {title}
              {title === 'Orders' && <span className="nav-count">{summary.orders}</span>}
            </button>
          ))}
        </nav>
        <p className="nav-label resources">RESOURCES</p>
        <button
          className={`nav-item ${page === 'Learning guide' ? 'active' : ''}`}
          onClick={() => navigate('Learning guide')}
        >
          <CircleHelp size={19} />
          Learning guide
          <ArrowUpRight size={14} />
        </button>
        <div className="sidebar-bottom">
          <div className="learning-card">
            <span className="tiny-icon">
              <Sparkles size={16} />
            </span>
            <strong>Small steps. Real systems.</strong>
            <p>Explore concurrency through a working application.</p>
            <button onClick={() => navigate('Learning guide')}>
              Continue learning <ArrowRight size={15} />
            </button>
          </div>
          <div className="profile">
            <span className="profile-avatar">DS</span>
            <div>
              <strong>Your workspace</strong>
              <small>Practice environment</small>
            </div>
            <Settings2 size={17} />
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <span>/</span> <strong>{page}</strong>
          </div>
          <div className="topbar-right">
            <span className={`connection ${online ? '' : 'offline'}`}>
              <i />
              {online ? 'Connected' : 'Connecting'}
            </span>
            <label className="instance-label">
              Instance
              <select
                aria-label="API instance"
                value={replica}
                onChange={(e) => setReplica(e.target.value as Replica)}
              >
                <option value="a">A · 8080</option>
                <option value="b">B · 8081</option>
              </select>
            </label>
            <span className="top-avatar">DS</span>
          </div>
        </header>
        <main>
          <div className="page-heading">
            <div>
              <p className="eyebrow">YOUR OPERATIONS, CONNECTED</p>
              <h1>{page === 'Overview' ? 'Workspace overview' : page}</h1>
              <p>{descriptions[page]}</p>
            </div>
            <div className="heading-actions">
              <button
                className="button secondary"
                onClick={() => void refresh()}
                aria-label="Refresh workspace"
              >
                <RefreshCw size={16} />
                <span>Refresh</span>
              </button>
              <button
                className="button primary"
                onClick={() => setModal({ type: 'order' })}
                disabled={!online}
              >
                <Plus size={18} /> New order
              </button>
            </div>
          </div>
          {loadError && (
            <div className="error-banner" role="alert">
              <strong>Workspace unavailable</strong>
              <span>{loadError}</span>
              <button onClick={() => void refresh()}>Try again</button>
            </div>
          )}
          {page === 'Overview' && (
            <>
              <div className="stats-grid">
                {[
                  {
                    label: 'Total orders',
                    value: summary.orders,
                    icon: ShoppingBag,
                    foot: 'Orders placed in this workspace',
                    tone: 'green',
                  },
                  {
                    label: 'Available units',
                    value: summary.units,
                    icon: Package,
                    foot: `Across ${items.length} catalog products`,
                    tone: 'blue',
                  },
                  {
                    label: 'Low-stock products',
                    value: summary.lowStock,
                    icon: Layers3,
                    foot: summary.lowStock
                      ? 'A little attention goes a long way'
                      : 'Your inventory is in good shape',
                    tone: 'amber',
                  },
                  {
                    label: 'Invoices created',
                    value: summary.invoices,
                    icon: FileText,
                    foot: 'Durable records, ready to revisit',
                    tone: 'purple',
                  },
                ].map((stat) => (
                  <section className="stat-card" key={stat.label}>
                    <div className="stat-label">
                      {stat.label}
                      <span className={`stat-icon ${stat.tone}`}>
                        <stat.icon size={18} />
                      </span>
                    </div>
                    <strong className="stat-number">{loading ? '—' : stat.value}</strong>
                    <p>{stat.foot}</p>
                  </section>
                ))}
              </div>
              <div className="overview-grid">
                <section className="panel inventory-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>
                        Inventory at a glance{' '}
                        <span className="count-pill">{items.length} products</span>
                      </h2>
                      <p>Your current stock, straight from the shared database.</p>
                    </div>
                    <button className="text-button" onClick={() => navigate('Inventory')}>
                      View inventory <ArrowRight size={15} />
                    </button>
                  </div>
                  {inventoryTable(items.slice(0, 4))}
                  <div className="panel-footer">
                    <span>
                      <span className="dot green" /> Stock is checked when every order is placed
                    </span>
                    <span>Live data</span>
                  </div>
                </section>
                <section className="panel workflow-panel">
                  <div className="panel-heading">
                    <div>
                      <p className="eyebrow">BUILT TO BE EXPLORED</p>
                      <h2>From stock to invoice</h2>
                    </div>
                    <span className="workflow-symbol">
                      <ArrowUpRight size={22} />
                    </span>
                  </div>
                  <p className="workflow-intro">A small workflow with real decisions underneath.</p>
                  <div className="workflow-steps">
                    {[
                      {
                        n: '01',
                        title: 'Reserve inventory',
                        detail: 'Place an order without overselling.',
                        icon: Package,
                      },
                      {
                        n: '02',
                        title: 'Prepare a shipment',
                        detail: 'Keep carrier calls within capacity.',
                        icon: Truck,
                      },
                      {
                        n: '03',
                        title: 'Create an invoice',
                        detail: 'Create once. Return it on retries.',
                        icon: FileText,
                      },
                    ].map((step) => (
                      <div className="workflow-step" key={step.n}>
                        <span className="step-icon">
                          <step.icon size={19} />
                        </span>
                        <div>
                          <small>STEP {step.n}</small>
                          <strong>{step.title}</strong>
                          <p>{step.detail}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                  <button className="workflow-link" onClick={() => navigate('Learning guide')}>
                    Explore the learning path <ArrowRight size={17} />
                  </button>
                </section>
              </div>
              <div className="bottom-grid">
                <section className="panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Recent orders</h2>
                      <p>The latest activity across both instances.</p>
                    </div>
                    <button className="text-button" onClick={() => navigate('Orders')}>
                      View all <ArrowRight size={15} />
                    </button>
                  </div>
                  {orderTable(orders.slice(0, 4))}
                </section>
                <section className="panel activity-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Activity</h2>
                      <p>Actions from this browser session.</p>
                    </div>
                    <span className="count-pill">Live</span>
                  </div>
                  {events.length === 0 ? (
                    <div className="activity-empty">
                      <span>
                        <ArrowDownLeft size={23} />
                      </span>
                      <strong>A fresh start</strong>
                      <p>Your workspace actions will appear here as you go.</p>
                    </div>
                  ) : (
                    <div className="activity-list">
                      {events.map((event, i) => (
                        <div className="activity" key={`${event.time}-${i}`}>
                          <span className={event.ok ? 'event-check' : 'event-error'}>
                            {event.ok ? <Check size={13} /> : <CircleHelp size={13} />}
                          </span>
                          <div>
                            <strong>{event.title}</strong>
                            <small>{event.detail}</small>
                            <time>{event.time}</time>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              </div>
            </>
          )}
          {page === 'Inventory' && (
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>
                    Product catalog <span className="count-pill">{items.length}</span>
                  </h2>
                  <p>Adjustments are recorded with before and after quantities.</p>
                </div>
              </div>
              <div className="table-tools">
                <label className="search-box">
                  <Search size={17} />
                  <input
                    placeholder="Search products or SKU…"
                    aria-label="Search inventory"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
                <button
                  className={`button secondary ${onlyLow ? 'selected' : ''}`}
                  onClick={() => setOnlyLow(!onlyLow)}
                  aria-pressed={onlyLow}
                >
                  <SlidersHorizontal size={16} />
                  {onlyLow ? 'Low stock only' : 'All stock levels'}
                </button>
              </div>
              {inventoryTable(filtered)}
            </section>
          )}
          {page === 'Orders' && (
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>
                    Order workspace <span className="count-pill">{summary.orders}</span>
                  </h2>
                  <p>Showing the latest 100 orders. Shipping labels are simulated.</p>
                </div>
              </div>
              <div className="table-tools">
                <label className="search-box">
                  <Search size={17} />
                  <input
                    placeholder="Search product or order ID…"
                    aria-label="Search orders"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
              </div>
              {orderTable(filteredOrders)}
            </section>
          )}
          {page === 'Invoices' && (
            <>
              <div className="info-strip">
                <FileText size={21} />
                <div>
                  <strong>An invoice is a durable result, not a temporary lock.</strong>
                  <p>Start from an order. Repeated creation should return the same invoice.</p>
                </div>
                <button className="text-button" onClick={() => navigate('Orders')}>
                  Choose an order <ArrowRight size={16} />
                </button>
              </div>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Invoice register</h2>
                    <p>Documents use a shared rendering lease. Demo content only.</p>
                  </div>
                </div>
                {invoices.length ? (
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Invoice</th>
                          <th>Product</th>
                          <th>Total</th>
                          <th>Document</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {invoices.map((invoice) => (
                          <tr key={invoice.id}>
                            <td>
                              <strong className="mono">INV-{shortId(invoice.id)}</strong>
                              <small>Order #{shortId(invoice.orderId)}</small>
                            </td>
                            <td>{invoice.productName}</td>
                            <td>
                              {new Intl.NumberFormat('en-MY', {
                                style: 'currency',
                                currency: invoice.currency,
                              }).format(invoice.totalMinor / 100)}
                            </td>
                            <td>
                              <Badge tone={invoice.hasDocument ? 'green' : 'neutral'}>
                                {invoice.hasDocument ? 'Available' : 'Not generated'}
                              </Badge>
                            </td>
                            <td>
                              <button
                                className="text-button"
                                disabled={busy === invoice.id}
                                onClick={async () => {
                                  const result = await act<{ documentId: string; content: string }>(
                                    invoice.id,
                                    'Invoice document ready',
                                    () =>
                                      request(
                                        replica,
                                        `/orders/${invoice.orderId}/invoice/document`,
                                        'POST',
                                      ),
                                  )
                                  if (result) setDocument(result)
                                }}
                              >
                                <Download size={15} />
                                {busy === invoice.id ? 'Preparing…' : 'Open document'}
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="empty">
                    <FileText size={30} />
                    <h3>No invoices yet</h3>
                    <p>
                      Create an invoice from an existing order. Exercise 005 contains the pending
                      implementation.
                    </p>
                    <button className="button secondary" onClick={() => navigate('Orders')}>
                      Go to orders <ArrowRight size={16} />
                    </button>
                  </div>
                )}
              </section>
            </>
          )}
          {page === 'Concurrency lab' && (
            <ConcurrencyLab items={items} onChange={() => void refresh()} />
          )}
          {page === 'Learning guide' && <LearningGuide />}
          <footer className="page-footer">
            <span>
              <Package size={14} /> Order Lab <span className="footer-separator">/</span> A working
              space for learning
            </span>
            <span>
              Local environment <span className="dot green" />
            </span>
          </footer>
        </main>
      </div>
      {notice && (
        <div className={`toast ${notice.kind}`} role={notice.kind === 'error' ? 'alert' : 'status'}>
          <span className="toast-icon">
            {notice.kind === 'success' ? <Check size={18} /> : <CircleHelp size={18} />}
          </span>
          <div>
            <strong>{notice.title}</strong>
            {notice.detail && <small>{notice.detail}</small>}
          </div>
          <button
            className="icon-button"
            onClick={() => setNotice(null)}
            aria-label="Dismiss notification"
          >
            <X size={16} />
          </button>
        </div>
      )}
      {modal && (
        <Dialog
          title={modal.type === 'order' ? 'Create a new order' : 'Adjust inventory'}
          onClose={() => setModal(null)}
        >
          <form
            onSubmit={async (e: FormEvent<HTMLFormElement>) => {
              e.preventDefault()
              const data = new FormData(e.currentTarget)
              const result =
                modal.type === 'order'
                  ? await act('form', 'Order placed successfully', () =>
                      request(replica, '/orders', 'POST', {
                        productId: Number(data.get('product')),
                        quantity: Number(data.get('quantity')),
                      }),
                    )
                  : await act('form', 'Inventory adjustment recorded', () =>
                      request(replica, `/inventory/${modal.item.productId}/adjustments`, 'POST', {
                        delta: Number(data.get('delta')),
                        reason: String(data.get('reason')),
                      }),
                    )
              if (result) setModal(null)
            }}
          >
            {modal.type === 'order' ? (
              <>
                <p className="form-intro">Stock is reserved when your order is placed.</p>
                <label className="field">
                  Product
                  <select name="product" required>
                    {items.map((item) => (
                      <option key={item.productId} value={item.productId}>
                        {item.name} · {item.available} available
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  Quantity
                  <input name="quantity" type="number" min="1" step="1" defaultValue="1" required />
                </label>
              </>
            ) : (
              <>
                <div className="form-product">
                  <Product item={modal.item} />
                  <Badge>{modal.item.available} available</Badge>
                </div>
                <label className="field">
                  Quantity change
                  <input name="delta" type="number" step="1" placeholder="e.g. 5 or -2" required />
                  <small>
                    Use a positive number to add stock or a negative number to remove it.
                  </small>
                </label>
                <label className="field">
                  Reason
                  <input
                    name="reason"
                    maxLength={200}
                    placeholder="e.g. New delivery received"
                    required
                  />
                </label>
              </>
            )}
            <div className="form-footer">
              <button type="button" className="button secondary" onClick={() => setModal(null)}>
                Cancel
              </button>
              <button className="button primary" disabled={busy === 'form'}>
                {busy === 'form'
                  ? 'Saving…'
                  : modal.type === 'order'
                    ? 'Place order'
                    : 'Save adjustment'}
                <ArrowRight size={16} />
              </button>
            </div>
          </form>
        </Dialog>
      )}
      {document && (
        <Dialog title="Invoice document" onClose={() => setDocument(null)}>
          <p className="form-intro">Demonstration text document · {shortId(document.documentId)}</p>
          <pre className="document-preview">{document.content}</pre>
          <button
            className="button secondary"
            onClick={() => {
              const blob = new Blob([document.content], { type: 'text/plain' })
              const url = URL.createObjectURL(blob)
              const link = window.document.createElement('a')
              link.href = url
              link.download = `invoice-${document.documentId}.txt`
              link.click()
              URL.revokeObjectURL(url)
            }}
          >
            <Download size={16} />
            Download text
          </button>
        </Dialog>
      )}
    </div>
  )
}

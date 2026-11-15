import { Sparkles } from 'lucide-react'
import { Badge } from '../components/ui'

export default function LearningGuide() {
  const lessons = [
    [
      '001',
      'Place orders without overselling',
      'Conditional stock deduction and a transaction around the complete order.',
      'Implemented',
    ],
    [
      '002',
      'Protect inventory adjustments',
      'Read, decide, write and audit while holding the database row lock.',
      'Implemented',
    ],
    [
      '003',
      'Bound local carrier capacity',
      'A CAS counter admits work, and a claim releases capacity on every exit.',
      'Implemented',
    ],
    [
      '004',
      'Reject stale edits across instances',
      'A shared database version check detects competing writers.',
      'Reviewed',
    ],
    [
      '005',
      'Idempotency and Redis ownership',
      'Persist one invoice, acquire an expiring lease, and release only your own token.',
      'Your next exercise',
    ],
    [
      '006',
      'Normalise suppliers and preserve order reads',
      'Keep Inventory supplier details consistent and preserve existing reads.',
      'Ready',
    ],
    ['007', 'Read and explain production query plans', 'Capture real plans and locate scan, sort and repeated lookup work.', 'Ready'],
    ['008', 'Tune filtering, search and indexes', 'Preserve filter results while measuring useful access paths and write costs.', 'Ready'],
    ['009', 'Paginate a changing order history', 'Implement cursor continuation, timestamp ties and bounded retrieval.', 'Ready'],
    ['010', 'Retrieve related data efficiently', 'Compare indexed probes, joins and bounded batches under an explicit consistency contract.', 'Ready'],
    ['011', 'Optimise product reports', 'Preserve zero rows and correct sums while reducing repeated work.', 'Ready'],
    ['012', 'Materialize product reports', 'Measure refresh cost, reader concurrency and data freshness.', 'Ready'],
    ['013', 'Cache product reports in Redis', 'Implement versioned cache-aside, expiry, invalidation and failure recovery.', 'Ready'],
  ]
  return (
    <>
      <div className="info-strip">
        <Sparkles size={22} />
        <div>
          <strong>Learn the rule. Implement the mechanism. Review the behavior.</strong>
          <p>
            Exercises leave focused TODOs in the backend. This console uses those actual endpoints.
          </p>
        </div>
      </div>
      <div className="lesson-list">
        {lessons.map(([id, title, detail, state]) => (
          <section className="panel lesson" key={id}>
            <span className="lesson-number">{id}</span>
            <div>
              <h2>{title}</h2>
              <p>{detail}</p>
            </div>
            <Badge tone={['005', '006', '007', '008', '009', '010', '011', '012', '013'].includes(id) ? 'amber' : 'green'}>{state}</Badge>
          </section>
        ))}
      </div>
      <section className="panel guide-note">
        <h2>Where to work</h2>
        <p>
          Open <code>practice/order-service/requirements/</code> for the acceptance criteria and
          target files. Exercise 005 has three inline TODOs in <code>InvoiceRepository</code> and{' '}
          <code>RedisDocumentLease</code>. Exercise 006 starts in <code>WorkspaceController</code> and the supplier migration. Exercises 007–013 use <code>OrderHistoryRepository</code> and the Orders history/report panels. Keep V1 for EXPLAIN comments and implement improvements in V2; <code>OrderReportReadService</code> contains the materialized-view and cache gaps. Save your implementation and ask for a code review.
        </p>
        <p>
          This is a local learning environment. Shipping labels and invoice documents are simulated;
          no paid services are called.
        </p>
      </section>
    </>
  )
}

-- Optional local development data for the REAL Orders screen, not a separate lab.
-- Adds 20,000 orders and 4,000 invoices; no stock reservation or real billing occurs.
-- Repeatable: deterministic IDs and conflict handling preserve existing rows.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO purchase_order (id, product_id, quantity, status, created_at)
SELECT md5('query-workload-order-' || n)::uuid,
       (SELECT min(product_id) FROM inventory), 1, 'PLACED',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + n * INTERVAL '1 minute'
FROM generate_series(1, 20000) n
WHERE EXISTS (SELECT 1 FROM inventory)
ON CONFLICT (id) DO NOTHING;
INSERT INTO order_invoice (id, order_id, total_minor, currency)
SELECT md5('query-workload-invoice-' || n)::uuid, md5('query-workload-order-' || n)::uuid, 1000, 'MYR'
FROM generate_series(1, 20000) n
WHERE n % 5 = 0 AND EXISTS (
    SELECT 1 FROM purchase_order WHERE id = md5('query-workload-order-' || n)::uuid)
ON CONFLICT (order_id) DO NOTHING;
COMMIT;
ANALYZE purchase_order;
ANALYZE order_invoice;

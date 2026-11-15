-- Optional synthetic data in the SAME application tables used by Orders history/reports.
-- Adds 30,000 orders and 6,000 invoices; does not reserve stock or call billing services.
-- Existing rows are preserved; repeat runs reuse deterministic IDs.
-- Up to three existing products are used, with a deliberately skewed distribution.
\set ON_ERROR_STOP on
BEGIN;
WITH catalog AS (
    SELECT array_agg(product_id ORDER BY product_id) AS ids FROM inventory
)
INSERT INTO purchase_order (id, product_id, quantity, status, created_at)
SELECT md5('production-query-order-' || n)::uuid,
       ids[CASE WHEN n % 10 = 0 THEN 1 + (n / 10) % least(cardinality(ids), 3) ELSE 1 END],
       1 + n % 3, 'PLACED',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + (n / 4) * INTERVAL '5 minutes'
FROM catalog CROSS JOIN generate_series(1, 30000) n
WHERE cardinality(ids) > 0
ON CONFLICT (id) DO NOTHING;
INSERT INTO order_invoice (id, order_id, total_minor, currency)
SELECT md5('production-query-invoice-' || n)::uuid, o.id, 1000 * o.quantity, 'MYR'
FROM generate_series(1, 30000) n
JOIN purchase_order o ON o.id = md5('production-query-order-' || n)::uuid
WHERE n % 5 = 0
ON CONFLICT (order_id) DO NOTHING;
COMMIT;
ANALYZE purchase_order;
ANALYZE order_invoice;

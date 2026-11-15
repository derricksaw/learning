-- V1 baseline: preserve this statement and record its EXPLAIN interpretation.
-- Run against the development database. This executes a read-only baseline SELECT.
-- psql -v page_offset=20000 -f scripts/explain-order-history.sql compares a deep page.
\set ON_ERROR_STOP on
\if :{?page_offset}
\else
\set page_offset 0
\endif
\if :{?page_size}
\else
\set page_size 25
\endif
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.product_id, i.product_name, o.quantity, o.status, o.created_at,
       (SELECT count(*) FROM order_invoice v
        WHERE v.order_id::text = o.id::text) > 0 AS has_invoice
FROM purchase_order o JOIN inventory i ON i.product_id = o.product_id
WHERE 1 = 1
ORDER BY o.created_at DESC, o.id ASC
LIMIT (:page_size + 1) OFFSET :page_offset;

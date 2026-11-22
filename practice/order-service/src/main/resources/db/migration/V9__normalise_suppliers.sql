-- Fail fast before mutating schema if any supplier_code has conflicting contacts.
DO $$
DECLARE
    conflicting_codes TEXT;
BEGIN
    SELECT string_agg(supplier_code, ', ' ORDER BY supplier_code)
    INTO conflicting_codes
    FROM (
        SELECT supplier_code
        FROM inventory
        GROUP BY supplier_code
        HAVING COUNT(DISTINCT (supplier_name, supplier_email)) > 1
    ) conflicts;

    IF conflicting_codes IS NOT NULL THEN
        RAISE EXCEPTION 'Conflicting supplier contact details found for supplier_code(s): %. Resolve conflicts before applying this migration.', conflicting_codes;
    END IF;
END $$;

-- Authoritative supplier table: a supplier can exist independently of inventory rows.
CREATE TABLE supplier (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL
);

-- Backfill unique suppliers from existing inventory rows.
INSERT INTO supplier (code, name, email)
SELECT supplier_code, MIN(supplier_name), MIN(supplier_email)
FROM inventory
GROUP BY supplier_code;

-- Ensure the default 'LOCAL' supplier exists for fresh databases and inserts using DEFAULT 'LOCAL'.
INSERT INTO supplier (code, name, email)
VALUES ('LOCAL', 'Local Supply', 'orders@local.example')
ON CONFLICT (code) DO NOTHING;

-- Drop redundant contact columns, enforce FK (default NO ACTION prevents deleting referenced suppliers),
-- and index the referencing FK column (PostgreSQL does not auto-index referencing FKs).
ALTER TABLE inventory
    DROP COLUMN supplier_name,
    DROP COLUMN supplier_email,
    ADD CONSTRAINT fk_inventory_supplier
        FOREIGN KEY (supplier_code) REFERENCES supplier(code);

CREATE INDEX inventory_supplier_code_idx ON inventory (supplier_code);

-- TODO 006: replace repeated supplier facts through a NEW forward migration.
-- Business rule: supplier_code identifies exactly one current name and email.
-- This intentionally unnormalised baseline powers the Inventory screen.
ALTER TABLE inventory
    ADD COLUMN supplier_code VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN supplier_name VARCHAR(120) NOT NULL DEFAULT 'Local Supply',
    ADD COLUMN supplier_email VARCHAR(160) NOT NULL DEFAULT 'orders@local.example';

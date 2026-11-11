CREATE TABLE inventory_adjustment (
    id UUID PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES inventory(product_id),
    delta INTEGER NOT NULL CHECK (delta <> 0),
    before_available INTEGER NOT NULL CHECK (before_available >= 0),
    after_available INTEGER NOT NULL CHECK (after_available >= 0),
    reason VARCHAR(200) NOT NULL CHECK (length(trim(reason)) > 0),
    CHECK (after_available::BIGINT = before_available::BIGINT + delta::BIGINT)
);

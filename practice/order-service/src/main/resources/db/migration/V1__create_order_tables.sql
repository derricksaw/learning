CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY CHECK (product_id > 0),
    available INTEGER NOT NULL CHECK (available >= 0)
);

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status = 'PLACED')
);

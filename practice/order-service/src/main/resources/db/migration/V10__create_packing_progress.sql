CREATE TABLE packing_progress (
    order_id UUID PRIMARY KEY REFERENCES purchase_order(id),
    packed_quantity INTEGER NOT NULL CHECK (packed_quantity >= 0)
);

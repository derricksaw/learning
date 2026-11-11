CREATE TABLE order_invoice (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES purchase_order(id),
    total_minor BIGINT NOT NULL CHECK (total_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'MYR')
);

-- Documents are immutable in this exercise: a late renderer cannot replace a result.
CREATE TABLE invoice_document (
    invoice_id UUID PRIMARY KEY REFERENCES order_invoice(id),
    document_id UUID NOT NULL UNIQUE,
    content TEXT NOT NULL
);

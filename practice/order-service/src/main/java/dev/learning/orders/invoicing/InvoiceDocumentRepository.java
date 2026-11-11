package dev.learning.orders.invoicing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceDocumentRepository {
    private final JdbcTemplate jdbc;

    public InvoiceDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InvoiceDocumentView> find(UUID invoiceId) {
        return jdbc.query("SELECT invoice_id, document_id, content FROM invoice_document WHERE invoice_id = ?",
                (row, index) -> new InvoiceDocumentView(row.getObject("invoice_id", UUID.class),
                        row.getObject("document_id", UUID.class), row.getString("content")), invoiceId)
                .stream().findFirst();
    }

    public InvoiceDocumentView publishIfAbsent(InvoiceDocumentView candidate) {
        jdbc.update("""
                INSERT INTO invoice_document (invoice_id, document_id, content) VALUES (?, ?, ?)
                ON CONFLICT (invoice_id) DO NOTHING
                """, candidate.invoiceId(), candidate.documentId(), candidate.content());
        return find(candidate.invoiceId()).orElseThrow();
    }
}

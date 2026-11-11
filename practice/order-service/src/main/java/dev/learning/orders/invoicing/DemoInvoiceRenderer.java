package dev.learning.orders.invoicing;

import dev.learning.orders.service.OrderProblem;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DemoInvoiceRenderer {
    private final long delayMillis;

    public DemoInvoiceRenderer(@Value("${invoice.document.demo-delay-millis:5000}") long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("render delay must be nonnegative");
        }
        this.delayMillis = delayMillis;
    }

    public InvoiceDocumentView render(InvoiceView invoice) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new OrderProblem(HttpStatus.SERVICE_UNAVAILABLE, "RENDER_INTERRUPTED", "Rendering interrupted");
        }
        return new InvoiceDocumentView(invoice.id(), UUID.randomUUID(),
                "DEMO ONLY | Invoice " + invoice.id() + " | " + invoice.currency() + " minor units " + invoice.totalMinor());
    }
}

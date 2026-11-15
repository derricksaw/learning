package dev.learning.orders.workspace;

import dev.learning.orders.service.OrderProblem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
public class OrderHistoryController {
    private final OrderHistoryRepository repository;

    public OrderHistoryController(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    public record Cursor(OffsetDateTime createdAt, UUID id) {}
    public record Page(List<OrderHistoryRepository.Row> items, boolean hasMore,
            Cursor nextCursor, Integer nextOffset) {}

    @GetMapping("/order-history")
    public Page history(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "offset") String mode,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime afterCreatedAt,
            @RequestParam(required = false) UUID afterId) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 1_000_000
                || productId != null && productId <= 0 || q.length() > 120
                || from != null && to != null && from.isAfter(to)
                || !List.of("offset", "cursor").contains(mode)
                || (afterCreatedAt == null) != (afterId == null)
                || mode.equals("offset") && afterId != null
                || mode.equals("cursor") && offset != 0) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Use limit 1..100, offset 0..1000000, a positive product ID, ordered dates, and a complete cursor");
        }
        var rows = mode.equals("offset")
                ? repository.offsetPage(limit + 1, offset, productId, from, to, q.trim())
                : repository.cursorPage(limit + 1, afterCreatedAt, afterId, productId, from, to, q.trim());
        boolean more = rows.size() > limit;
        var items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        var last = items.isEmpty() ? null : items.get(items.size() - 1);
        var cursor = more && last != null ? new Cursor(last.createdAt(), last.id()) : null;
        return new Page(items, more, cursor, more && mode.equals("offset") ? offset + limit : null);
    }

    @GetMapping("/order-report")
    public List<OrderHistoryRepository.ProductReport> report() {
        return repository.productReport();
    }
}

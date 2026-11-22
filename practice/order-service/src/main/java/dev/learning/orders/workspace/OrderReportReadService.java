package dev.learning.orders.workspace;

import dev.learning.orders.service.OrderProblem;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Explicit report sources for live-query, materialization and cache comparisons. */
@Service
public class OrderReportReadService {
    private final OrderHistoryRepository repository;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public OrderReportReadService(OrderHistoryRepository repository, JdbcTemplate jdbc,
            StringRedisTemplate redis) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public record Result(List<OrderHistoryRepository.ProductReport> items, String source,
            String version, OffsetDateTime generatedAt, String cacheStatus) {}

    public static void validateVersion(String version) {
        if (!List.of("v1", "v2").contains(version)) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Use version v1 or v2");
        }
    }

    public Result read(String source, String version) {
        validateVersion(version);
        return switch (source) {
            case "live" -> new Result(version.equals("v1")
                    ? repository.productReportV1() : repository.productReportV2(),
                    source, version, OffsetDateTime.now(ZoneOffset.UTC), "bypass");
            case "materialized" -> materialized(version);
            case "redis" -> cached(version);
            default -> throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Use source live, materialized or redis");
        };
    }

    private Result materialized(String version) {
        // TODO 012: read the learner-created materialized report and its actual refresh timestamp.
        // Use the supplied JdbcTemplate; preserve totals and distinguish freshness from request time.
        throw incomplete("012", "implement the materialized report read");
    }

    public void refreshMaterialized(String version) {
        validateVersion(version);
        // TODO 012: refresh the materialized report with a documented snapshot/reader-blocking policy.
        // The SQL TODOs are in docs/query-evidence/templates/materialized-report-migration.sql.
        throw incomplete("012", "implement materialized report refresh");
    }

    private Result cached(String version) {
        // TODO 013: implement a bounded cache-aside read using the supplied StringRedisTemplate.
        // Define versioned keys, payload/freshness metadata, TTL, miss races and Redis-failure behaviour.
        throw incomplete("013", "implement the Redis report cache");
    }

    public void invalidateCache(String version) {
        validateVersion(version);
        // TODO 013: invalidate only the selected report cache and account for racing fills.
        throw incomplete("013", "implement report cache invalidation");
    }

    private static OrderProblem incomplete(String exercise, String task) {
        return new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "EXERCISE_INCOMPLETE",
                "Exercise " + exercise + ": " + task + " in OrderReportReadService");
    }
}

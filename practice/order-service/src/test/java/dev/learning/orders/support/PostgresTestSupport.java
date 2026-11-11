package dev.learning.orders.support;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresTestSupport {
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired
    protected JdbcTemplate jdbc;

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE invoice_document, order_invoice, inventory_adjustment, purchase_order, inventory");
        jdbc.update("INSERT INTO inventory (product_id, available) VALUES (1, 3)");
    }

    protected HttpResponse<String> request(String method, String path, String body) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    protected int stock() {
        return jdbc.queryForObject("SELECT available FROM inventory WHERE product_id = 1", Integer.class);
    }

    protected int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order", Integer.class);
    }

    protected static <T> T field(HttpResponse<String> response, String path) {
        return JsonPath.read(response.body(), path);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}

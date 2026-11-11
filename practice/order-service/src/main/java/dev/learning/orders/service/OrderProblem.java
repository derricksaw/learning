package dev.learning.orders.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OrderProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrderProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static OrderProblem missing(String resource) {
        return new OrderProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " does not exist");
    }
}

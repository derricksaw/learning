package dev.learning.orders.controller;

import dev.learning.orders.service.OrderProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public record ErrorBody(String code, String message) {}

    @ExceptionHandler(OrderProblem.class)
    ResponseEntity<ErrorBody> domain(OrderProblem error) {
        return ResponseEntity.status(error.getStatus()).body(new ErrorBody(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> invalid(Exception error) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", "Check the request fields and types"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> internal(Exception error) {
        log.error("Order API operation failed", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("INTERNAL_ERROR", "The operation could not be completed"));
    }
}

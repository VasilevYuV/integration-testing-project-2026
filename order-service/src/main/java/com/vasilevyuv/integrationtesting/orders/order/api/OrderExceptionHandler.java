package com.vasilevyuv.integrationtesting.orders.order.api;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderNotFoundException;
import com.vasilevyuv.integrationtesting.orders.order.application.UnknownUserException;
import com.vasilevyuv.integrationtesting.orders.order.application.UserServiceUnavailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail notFound(OrderNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(UnknownUserException.class)
    public ProblemDetail unknownUser(UnknownUserException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "USER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ProblemDetail unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                "User service is temporarily unavailable.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidFields(MethodArgumentNotValidException exception) {
        var problem = invalidRequest();
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Check the request fields, JSON types and UUID format.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}

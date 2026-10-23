package com.vasilevyuv.integrationtesting.users.user.api;

import com.vasilevyuv.integrationtesting.users.user.application.UserNotFoundException;
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
public class UserExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail notFound(UserNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setProperty("code", "USER_NOT_FOUND");
        return problem;
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
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Check the request fields, JSON types and UUID format.");
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }
}

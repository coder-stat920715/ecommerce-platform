package com.shop.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 7807 problem+json. Bean-validation errors are handled by Spring's built-in ProblemDetail support. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ApiExceptions.IdempotencyConflict.class, ApiExceptions.UnprocessableOrder.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ApiExceptions.OrderNotFound.class)
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}

package com.briefwise.shared.infrastructure.web;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that maps domain exceptions to RFC 9457 problem responses. Spring MVC's own errors (missing
 * parameter, malformed UUID, ...) are already mapped to 400 by the base class.
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** A feature's daily LLM budget is used up (ADR 0008). No Retry-After: the budget resets at 00:00 UTC. */
    @ExceptionHandler(UsageLimitExceededException.class)
    ProblemDetail usageLimitExceeded(UsageLimitExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("feature", e.feature());
        return problem;
    }

    /** Invalid input rejected by a domain value object (e.g. a malformed ticker) or a service precondition. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}

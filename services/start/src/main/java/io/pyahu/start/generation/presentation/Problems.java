package io.pyahu.start.generation.presentation;

import io.pyahu.start.generation.domain.InvalidProjectRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every error is a problem detail (REST APIs guide, step 4). Spring MVC's own errors, such as a field that is too
 * long, come from Spring Boot's handler; this one adds the engine's and a fixed text for anything else (security
 * guide, step 6).
 */
@RestControllerAdvice
class Problems {

    private static final Logger log = LoggerFactory.getLogger(Problems.class);
    private static final URI INVALID_REQUEST = URI.create("https://start.pyahu.io/problems/invalid-request");

    @ExceptionHandler
    ProblemDetail invalidRequest(InvalidProjectRequest e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request names unknown fields or values the rules reject.");
        problem.setType(INVALID_REQUEST);
        problem.setTitle("Invalid project request");
        List<Map<String, String>> errors = e.errors().stream()
                .map(f -> Map.of("field", f.field(), "message", f.message()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> busy(GenerationLimit.Busy e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Too many projects are being generated right now. Try again.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    @ExceptionHandler
    ProblemDetail unexpected(Exception e) {
        log.error("Generation failed", e);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The project could not be generated.");
    }
}

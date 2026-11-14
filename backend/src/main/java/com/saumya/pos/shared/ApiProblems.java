package com.saumya.pos.shared;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiProblems {
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(org.springframework.web.bind.MethodArgumentNotValidException error){var body=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Request validation failed");body.setProperty("code","VALIDATION_FAILED");return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> state(ResponseStatusException error) {
        String code = error instanceof DomainProblem problem ? problem.code() : "REQUEST_REJECTED";
        var body = ProblemDetail.forStatusAndDetail(error.getStatusCode(),
            error.getReason() == null ? "Request rejected" : error.getReason());
        body.setType(URI.create("urn:solar:problem:" + code));
        body.setProperty("code", code);
        body.setProperty("correlationId", UUID.randomUUID().toString());
        return ResponseEntity.status(error.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}

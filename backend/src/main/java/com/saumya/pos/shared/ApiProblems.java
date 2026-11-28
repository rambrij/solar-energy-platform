package com.saumya.pos.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiProblems {
    private final ApiProblemWriter writer;

    public ApiProblems(ApiProblemWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<Map<String, String>> errors = error.getBindingResult().getFieldErrors().stream()
            .map(field -> Map.of("field", field.getField(), "message",
                field.getDefaultMessage() == null ? "Invalid value" : field.getDefaultMessage()))
            .distinct().toList();
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request",
            "One or more request fields failed validation.", Map.of("errors", errors));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class
    })
    ResponseEntity<ProblemDetail> malformed(Exception error, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Invalid request",
            "The request could not be parsed or contains an invalid parameter.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "DATA_CONFLICT", "Conflict",
            "The change conflicts with an existing record or data constraint.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException error, HttpServletRequest request) {
        return response(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed",
            "The HTTP method is not supported for this resource.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMedia(HttpMediaTypeNotSupportedException error,
            HttpServletRequest request) {
        return response(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
            "Unsupported media type", "The request media type is not supported.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> notAcceptable(HttpMediaTypeNotAcceptableException error,
            HttpServletRequest request) {
        return response(request, HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
            "Not acceptable", "No representation is available for the requested response media type.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException error, HttpServletRequest request) {
        return response(request, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
            "Payload too large", "The uploaded content exceeds the configured size limit.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException error, HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
            "Resource not found", "The requested resource does not exist.", null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> state(ResponseStatusException error, HttpServletRequest request) {
        if (error.getStatusCode().is5xxServerError()) {
            return response(request, error.getStatusCode(), "INTERNAL_ERROR", "Request could not be completed",
                "The server could not complete the request.", null);
        }
        String code = error instanceof DomainProblem problem ? problem.code() : "REQUEST_REJECTED";
        String detail = error.getReason() == null ? "The request was rejected." : error.getReason();
        return response(request, error.getStatusCode(), code, "Request rejected", detail, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Internal server error", "The server could not complete the request.", null);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request, HttpStatusCode status,
            String code, String title, String detail, Map<String, Object> properties) {
        ProblemDetail body = writer.problem(request, status, code, title, detail, properties);
        return ResponseEntity.status(status)
            .header("X-Correlation-Id", correlationId(body))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
    }

    private static String correlationId(ProblemDetail body) {
        if (body == null || body.getProperties() == null) return "";
        Object value = body.getProperties().get("correlationId");
        return value == null ? "" : value.toString();
    }
}

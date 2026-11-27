package com.saumya.pos.shared;

import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiProblems {
    private final ApiProblemWriter writer;
    public ApiProblems(ApiProblemWriter writer){this.writer=writer;}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException error,HttpServletRequest request){
        List<Map<String,String>> errors=error.getBindingResult().getFieldErrors().stream()
            .map(field->Map.of("field",field.getField(),"message",
                field.getDefaultMessage()==null?"Invalid value":field.getDefaultMessage()))
            .distinct().toList();
        return response(request,HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Invalid request",
            "One or more request fields failed validation.",Map.of("errors",errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> malformed(Exception error,HttpServletRequest request){
        return response(request,HttpStatus.BAD_REQUEST,"MALFORMED_REQUEST","Invalid request",
            "The request could not be parsed or contains an invalid parameter.",null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error,HttpServletRequest request){
        return response(request,HttpStatus.CONFLICT,"DATA_CONFLICT","Conflict",
            "The change conflicts with an existing record or data constraint.",null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException error,HttpServletRequest request){
        return response(request,HttpStatus.METHOD_NOT_ALLOWED,"METHOD_NOT_ALLOWED","Method not allowed",
            "The HTTP method is not supported for this resource.",null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> state(ResponseStatusException error,HttpServletRequest request) {
        String code=error instanceof DomainProblem problem?problem.code():"REQUEST_REJECTED";
        String detail=error.getReason()==null?"The request was rejected.":error.getReason();
        return response(request,error.getStatusCode(),code,"Request rejected",detail,null);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request,HttpStatusCode status,
            String code,String title,String detail,Map<String,Object> properties){
        ProblemDetail body=writer.problem(request,status,code,title,detail,properties);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}

package com.saumya.pos.platform;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Builds the standard ResponseStatusException that the shared ApiProblems advice renders as RFC 7807 problem details. */
final class Problems {
    private Problems(){}
    static ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    static ResponseStatusException missing(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
    static ResponseStatusException forbidden(String message){return new ResponseStatusException(HttpStatus.FORBIDDEN,message);}
}

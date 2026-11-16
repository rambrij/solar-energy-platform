package com.saumya.pos.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DomainProblem extends ResponseStatusException {
    private final String code;
    public DomainProblem(String code, String detail) {
        super(HttpStatus.CONFLICT, detail);
        this.code = code;
    }
    public String code() { return code; }
}

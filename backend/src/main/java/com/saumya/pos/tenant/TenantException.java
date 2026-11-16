package com.saumya.pos.tenant;

public class TenantException extends RuntimeException {
    public enum Kind { NOT_FOUND, INVALID, CONFLICT }
    private final Kind kind;
    public TenantException(Kind kind,String message){super(message);this.kind=kind;}
    public Kind kind(){return kind;}
}

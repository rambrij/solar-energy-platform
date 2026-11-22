package com.saumya.pos.returns.internal;
import java.time.Instant;
public record ReturnAuditRow(String id,String actor,String action,Instant createdAt){}

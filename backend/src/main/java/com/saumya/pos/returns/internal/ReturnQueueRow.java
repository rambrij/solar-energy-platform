package com.saumya.pos.returns.internal;
import java.time.Instant;
public record ReturnQueueRow(String id,String orderId,String state,Instant createdAt) {}

package com.saumya.pos.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SubscriptionPlanService {
    record PlanInput(String planKey,String displayName,BigDecimal price,String currency,String billingInterval,
        int maxUsers,long maxStorageMb,int maxBranches,int maxWarehouses,String entitlements,int graceDays,
        String taxMetadata,String invoiceDescription) {}
    record PlanVersionView(int version,BigDecimal price,String currency,String billingInterval,int maxUsers,
        long maxStorageMb,int maxBranches,int maxWarehouses,String entitlements,int graceDays,String taxMetadata,
        String invoiceDescription,String createdBy,Instant createdAt) {}
    record PlanView(long id,String planKey,String displayName,boolean active,int currentVersion,
        Instant createdAt,Instant updatedAt,List<PlanVersionView> versions) {}
    PlanView create(PlanInput input,String actor,String correlationId);
    PlanView revise(long id,PlanInput input,String actor,String correlationId);
    PlanView setActive(long id,boolean active,String actor,String correlationId);
    List<PlanView> list();
}

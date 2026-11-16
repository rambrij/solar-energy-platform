package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.PlanRepository;
import com.saumya.pos.platform.internal.SubscriptionInvoiceRepository;
import com.saumya.pos.platform.internal.SubscriptionRepository;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.tenant.TenantStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform dashboard analytics and system health. Aggregated platform-scope data only; no tenant-private content (RPT-003). */
@Service @Transactional(readOnly=true)
public class PlatformInsightService {
    public record OpenInvoices(String currency,long count,BigDecimal total) {}
    public record Overview(Map<TenantStatus,Long> tenantsByStatus,Map<String,Long> subscriptionsByStatus,Map<String,Long> subscriptionsByPlan,List<OpenInvoices> openInvoices,Instant generatedAt) {}
    public record Health(String status,String environment,boolean databaseReachable,boolean paymentProviderConfigured,boolean merchantConfigured,boolean mockVendorPaymentsEnabled,boolean mockSubscriptionProvider,List<String> issues,Instant checkedAt) {}
    private final TenantLifecycleService tenants;private final SubscriptionRepository subscriptions;private final SubscriptionInvoiceRepository invoices;private final PlanRepository plans;private final ConfigurationService config;private final Clock clock;private final boolean mockVendorPayments;
    public PlatformInsightService(TenantLifecycleService tenants,SubscriptionRepository subscriptions,SubscriptionInvoiceRepository invoices,PlanRepository plans,ConfigurationService config,Clock clock,@Value("${payments.mock-enabled:false}") boolean mockVendorPayments){
        this.tenants=tenants;this.subscriptions=subscriptions;this.invoices=invoices;this.plans=plans;this.config=config;this.clock=clock;this.mockVendorPayments=mockVendorPayments;
    }
    public Overview overview(){
        var byStatus=new LinkedHashMap<String,Long>();for(Object[] r:subscriptions.countEffectiveByStatus())byStatus.put((String)r[0],((Number)r[1]).longValue());
        var byPlan=new LinkedHashMap<String,Long>();for(Object[] r:subscriptions.countEffectiveByPlan())byPlan.put((String)r[0],((Number)r[1]).longValue());
        var open=new ArrayList<OpenInvoices>();for(Object[] r:invoices.openTotalsByCurrency())open.add(new OpenInvoices((String)r[0],((Number)r[1]).longValue(),(BigDecimal)r[2]));
        return new Overview(tenants.countByStatus(),byStatus,byPlan,open,clock.instant());
    }
    public Health health(){
        boolean db;try{plans.count();db=true;}catch(Exception e){db=false;}
        var provider=config.raw(ConfigurationPolicy.Key.SUBSCRIPTION_PAYMENT_PROVIDER);var merchant=config.raw(ConfigurationPolicy.Key.SUBSCRIPTION_MERCHANT_ID);
        boolean mockProvider=provider.map(v->v.equals("MOCK")||v.startsWith("MOCK_")).orElse(false);
        var issues=new ArrayList<String>();
        if(!db)issues.add("Database is not reachable");
        if(provider.isEmpty())issues.add("Subscription payment provider is not configured");
        if(merchant.isEmpty())issues.add("Platform merchant account is not configured");
        if(config.production()&&mockVendorPayments)issues.add("CRITICAL: mock vendor payments are enabled in production");
        if(config.production()&&mockProvider)issues.add("CRITICAL: a mock subscription provider is configured in production");
        String status=!db||issues.stream().anyMatch(i->i.startsWith("CRITICAL"))?"DOWN":issues.isEmpty()?"UP":"DEGRADED";
        return new Health(status,config.production()?"production":"non-production",db,provider.isPresent(),merchant.isPresent(),mockVendorPayments,mockProvider,issues,clock.instant());
    }
}

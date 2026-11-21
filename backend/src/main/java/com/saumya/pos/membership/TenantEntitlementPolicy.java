package com.saumya.pos.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves tenant lifecycle/subscription/feature entitlement on every membership revalidation. */
@Component
public class TenantEntitlementPolicy {
    private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;
    private static final Set<String> READ_ONLY=Set.of("POS_VIEW","POS_RECEIPT_VIEW","PRODUCT_VIEW","PRICE_VIEW","INVENTORY_VIEW",
        "ORDER_VIEW","INVOICE_VIEW","VENDOR_PAYMENT_VIEW","REPORT_VIEW","REPORT_EXPORT","VENDOR_AUDIT_VIEW",
        "ROLE_VIEW","USER_VIEW","TENANT_PROFILE_VIEW","CUSTOMER_RELATION_VIEW","CRM_NOTE_VIEW",
        "LEAD_VIEW","QUOTATION_VIEW","RETURN_VIEW","SHIPMENT_VIEW","SERVICE_VIEW","INSTALLATION_VIEW",
        "CART_SUPPORT_VIEW","POS_REPORT_VIEW","POS_CASHIER_REPORT_VIEW","POS_REFUND_VIEW","POS_RETURN_VIEW","POS_TENDER_VIEW");
    public TenantEntitlementPolicy(JdbcTemplate db,ObjectMapper json,Clock clock){
        this.db=Objects.requireNonNull(db);
        this.json=Objects.requireNonNull(json);
        this.clock=Objects.requireNonNull(clock);
    }
    /** Enforces a public commerce feature using the same authoritative subscription resolver as RBAC. */
    public void requireFeature(long tenant,String feature,boolean write){
        String probe=switch(feature){
            case "POS" -> write?"POS_SALE_CREATE":"POS_VIEW";
            case "CATALOG" -> write?"PRODUCT_UPDATE":"PRODUCT_VIEW";
            case "INVENTORY" -> write?"INVENTORY_RECEIVE":"INVENTORY_VIEW";
            case "ADMINISTRATION" -> write?"USER_UPDATE":"USER_VIEW";
            case "CRM" -> write?"CRM_NOTE_UPDATE":"CRM_NOTE_VIEW";
            case "LEADS" -> write?"LEAD_UPDATE":"LEAD_VIEW";
            case "QUOTATIONS" -> write?"QUOTATION_UPDATE":"QUOTATION_VIEW";
            case "ORDERS" -> write?"ORDER_CREATE":"ORDER_VIEW";
            case "RETURNS" -> write?"RETURN_APPROVE":"RETURN_VIEW";
            case "FINANCE" -> write?"INVOICE_CREATE":"INVOICE_VIEW";
            case "SHIPMENTS" -> write?"SHIPMENT_UPDATE":"SHIPMENT_VIEW";
            case "SERVICE" -> write?"SERVICE_UPDATE":"SERVICE_VIEW";
            case "REPORTING" -> write?"REPORT_EXPORT":"REPORT_VIEW";
            default -> throw new IllegalArgumentException("Unknown subscription feature: "+feature);
        };
        if(!permissions(tenant,List.of(probe)).contains(probe))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Tenant subscription does not entitle "+(write?"write":"read")+" access to "+feature);
    }
    public List<String> permissions(long tenant,List<String> grants){
        var rows=db.queryForList("""
            SELECT s.status,s.billing_period_end,s.grace_until,v.entitlements
            FROM tenant_subscriptions s
            JOIN subscription_plan_versions v ON v.plan_id=s.plan_id AND v.version=s.plan_version
            WHERE s.tenant_id=?
            ORDER BY s.created_at DESC,s.id DESC
            """,tenant);
        if(rows.isEmpty())return List.of();
        var row=rows.getFirst();String status=((String)row.get("status")).toUpperCase(Locale.ROOT);
        LocalDate end=((Date)row.get("billing_period_end")).toLocalDate();
        Object graceValue=row.get("grace_until");Instant grace=graceValue==null?null:((Timestamp)graceValue).toInstant();
        boolean graceActive=grace!=null&&grace.isAfter(clock.instant());
        boolean writable=switch(status){
            case "ACTIVE","CANCEL_AT_PERIOD_END"->!end.isBefore(LocalDate.now(clock));
            case "GRACE_PERIOD","PAST_DUE"->graceActive;
            default->false;
        };
        boolean readable=writable||Set.of("ACTIVE","CANCEL_AT_PERIOD_END","SUSPENDED","EXPIRED","CANCELLED","PAST_DUE","GRACE_PERIOD").contains(status);
        if(!readable)return List.of();
        JsonNode entitlements;
        try{entitlements=json.readTree(String.valueOf(row.get("entitlements")));}catch(Exception e){return List.of();}
        JsonNode features=entitlements.path("features");
        List<String> result=new ArrayList<>();
        for(String permission:grants){
            if(!writable&&!READ_ONLY.contains(permission))continue;
            String feature=feature(permission);
            if(feature!=null&&(!features.isObject()||!features.path(feature).asBoolean(false)))continue;
            result.add(permission);
        }
        return List.copyOf(result);
    }
    private String feature(String permission){
        if(permission.startsWith("POS_"))return "POS";
        if(permission.startsWith("PRODUCT_")||permission.startsWith("CATALOG_")||permission.startsWith("PRICE_"))return "CATALOG";
        if(permission.startsWith("INVENTORY_"))return "INVENTORY";
        if(permission.startsWith("USER_")||permission.startsWith("ROLE_")||permission.startsWith("TENANT_PROFILE_"))return "ADMINISTRATION";
        if(permission.startsWith("CRM_")||permission.startsWith("CRM_NOTE_")||permission.startsWith("CUSTOMER_RELATION_"))return "CRM";
        if(permission.startsWith("LEAD_"))return "LEADS";
        if(permission.startsWith("QUOTATION_"))return "QUOTATIONS";
        if(permission.startsWith("ORDER_")||permission.equals("CART_SUPPORT_VIEW"))return "ORDERS";
        if(permission.startsWith("RETURN_"))return "RETURNS";
        if(permission.startsWith("INVOICE_")||permission.startsWith("VENDOR_PAYMENT_"))return "FINANCE";
        if(permission.startsWith("SHIPMENT_"))return "SHIPMENTS";
        if(permission.startsWith("SERVICE_")||permission.startsWith("INSTALLATION_"))return "SERVICE";
        if(permission.startsWith("REPORT_")||permission.equals("VENDOR_AUDIT_VIEW"))return "REPORTING";
        return null;
    }
}

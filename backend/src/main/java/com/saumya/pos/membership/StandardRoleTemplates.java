package com.saumya.pos.membership;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Versioned definitions. Authorization always reads persisted tenant role assignments. */
public final class StandardRoleTemplates {
    public record Definition(String key, int version, List<String> permissions, String deferredReason) {
        String roleKey() { return "standard:" + key; }
    }

    private static Definition supported(String key, String grants) {
        var permissions = Arrays.stream(grants.strip().split("\\s+")).sorted().toList();
        if (!VendorPermissionRegistry.all().containsAll(permissions) ||
                new HashSet<>(permissions).size() != permissions.size())
            throw new IllegalStateException("Invalid grants for " + key);
        return new Definition(key, 1, permissions, null);
    }

    public static final List<Definition> ALL = List.of(
        supported("VENDOR_ADMIN", """
            CATALOG_PROMOTION_MANAGE INVENTORY_RECEIVE INVENTORY_VIEW POS_CASHIER_REPORT_VIEW POS_CASH_IN
            POS_CASH_OUT POS_DISCOUNT_APPLY POS_DISCOUNT_APPROVE POS_DISCOUNT_OVERRIDE POS_RECEIPT_REPRINT
            POS_RECEIPT_VIEW POS_REFUND_APPROVE POS_REFUND_CASH_RECORD POS_REFUND_PRINT POS_REFUND_VIEW POS_REPORT_EXPORT POS_REPORT_VIEW
            POS_RETURN_AUDIT_VIEW POS_RETURN_APPROVE POS_RETURN_CREATE POS_RETURN_INSPECT POS_RETURN_VIEW POS_SAFE_DROP
            POS_SALE_CANCEL POS_SALE_CREATE POS_SESSION_CLOSE POS_SESSION_OPEN POS_SESSION_REOPEN
            POS_SETTINGS_MANAGE POS_SHIFT_VARIANCE_APPROVE POS_TENDER_CASH_RECORD POS_TENDER_EXTERNAL_RECORD
            POS_TENDER_MANUAL_VERIFY POS_VIEW PRICE_UPDATE PRICE_VIEW PRODUCT_ARCHIVE PRODUCT_CREATE
            PRODUCT_UPDATE PRODUCT_VIEW ROLE_ASSIGN ROLE_CREATE ROLE_UPDATE ROLE_VIEW USER_CREATE
            USER_DISABLE USER_UPDATE USER_VIEW
            """),
        supported("POS_CASHIER", """
            POS_VIEW POS_SESSION_OPEN POS_SESSION_CLOSE POS_SALE_CREATE POS_TENDER_CASH_RECORD
            POS_TENDER_EXTERNAL_RECORD POS_RECEIPT_VIEW
            """),
        supported("SALES_MANAGER", """
            POS_VIEW POS_REPORT_VIEW
            """),
        supported("INVENTORY_MANAGER", """
            PRODUCT_VIEW INVENTORY_VIEW INVENTORY_RECEIVE
            """),
        supported("ACCOUNTANT", """
            POS_VIEW POS_REPORT_VIEW POS_REFUND_VIEW
            """),
        new Definition("SALES_EXECUTIVE", 1, List.of(), "Assigned leads, relationships, quotations and vendor orders have no assignment-scoped API."),
        new Definition("OPERATIONS_MANAGER", 1, List.of(), "Shipment and installation fulfilment APIs are unavailable."),
        new Definition("SERVICE_MANAGER", 1, List.of(), "Service request and engineer assignment APIs are unavailable."),
        new Definition("SERVICE_ENGINEER", 1, List.of(), "Assigned service work APIs are unavailable."),
        new Definition("CONTENT_MANAGER", 1, List.of(), "Vendor storefront editorial APIs are unavailable."));

    static Set<String> implemented() { return Set.copyOf(ALL.getFirst().permissions()); }

    static void lockTenant(JdbcTemplate db, long tenant) {
        db.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE", Long.class, tenant);
    }

    static long initialize(JdbcTemplate db, long tenant, String actor) {
        long admin = 0;
        for (var template : ALL) {
            if (template.deferredReason() != null) continue;
            var existing = db.queryForList(
                "SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=? FOR UPDATE", Long.class, tenant, template.roleKey());
            long id;
            if (existing.isEmpty()) {
                id = insert(db, tenant, actor, template, template.roleKey(), template.key(),
                    template.permissions(), "Initial standard tenant role");
            } else {
                id = existing.getFirst();
            }
            if (template.key().equals("VENDOR_ADMIN")) admin = id;
        }
        return admin;
    }

    static long insert(JdbcTemplate db, long tenant, String actor, Definition template, String key,
                       String name, List<String> permissions, String reason) {
        db.update("""
            INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by,template_key,template_version)
            VALUES(?,?,?,TRUE,?,?,?,?)
            """, tenant,key,name,actor,actor,template.key(),template.version());
        long id = db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",
            Long.class, tenant,key);
        db.batchUpdate("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",
            permissions.stream().map(p -> new Object[]{tenant,id,p,actor}).toList());
        db.update("""
            INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason)
            VALUES(?,?,'ROLE',?,'ROLE_TEMPLATE_APPLIED',?)
            """,tenant,actor,Long.toString(id),reason);
        return id;
    }

    private StandardRoleTemplates() {}
}

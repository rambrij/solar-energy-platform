package com.saumya.pos.isolation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Reusable two-vendor/two-customer fixture for P0 isolation integration tests.
 * Every tenant-owned row is deliberately assigned a distinct owner; Customer A
 * is related to both vendors while Customer B is related only to Vendor B.
 */
public final class TenantIsolationFixture {
    public static final long VENDOR_A_TENANT = 9_871_001L;
    public static final long VENDOR_B_TENANT = 9_871_002L;
    public static final long VENDOR_A_BRANCH = 9_871_101L;
    public static final long VENDOR_B_BRANCH = 9_871_102L;
    public static final long VENDOR_A_USER_ID = 9_871_201L;
    public static final long VENDOR_B_USER_ID = 9_871_202L;
    public static final long CUSTOMER_A_ID = 9_871_301L;
    public static final long CUSTOMER_B_ID = 9_871_302L;
    public static final long VENDOR_A_PRODUCT_ID = 9_871_401L;
    public static final long VENDOR_B_PRODUCT_ID = 9_871_402L;

    public static final String VENDOR_A = "p004-vendor-a@example.test";
    public static final String VENDOR_B = "p004-vendor-b@example.test";
    public static final String CUSTOMER_A = "p004-customer-a@example.test";
    public static final String CUSTOMER_B = "p004-customer-b@example.test";

    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    public TenantIsolationFixture(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }

    public Seed seed() {
        db.update("""
            INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status)
            VALUES(?,?,?,?, 'ACTIVE'),(?,?,?,?, 'ACTIVE')
            """,
            VENDOR_A_TENANT, "P004 Vendor A", "p004-vendor-a", "P004 Vendor A",
            VENDOR_B_TENANT, "P004 Vendor B", "p004-vendor-b", "P004 Vendor B");

        db.update("INSERT INTO branches(id,tenant_id,name) VALUES(?,?,?),(?,?,?)",
            VENDOR_A_BRANCH, VENDOR_A_TENANT, "P004 Branch A",
            VENDOR_B_BRANCH, VENDOR_B_TENANT, "P004 Branch B");
        db.update("""
            INSERT INTO pos_stock_locations(tenant_id,branch_id,stock_location_id,location_code,display_name,active)
            VALUES(?,?,?,'POS_DEFAULT',?,TRUE),(?,?,?,'POS_DEFAULT',?,TRUE)
            """,
            VENDOR_A_TENANT,VENDOR_A_BRANCH,VENDOR_A_BRANCH,"P004 Branch A POS stock room",
            VENDOR_B_TENANT,VENDOR_B_BRANCH,VENDOR_B_BRANCH,"P004 Branch B POS stock room");

        db.update("""
            INSERT INTO users(id,username,password_hash,enabled)
            VALUES(?,?,?,TRUE),(?,?,?,TRUE),(?,?,?,TRUE),(?,?,?,TRUE)
            """,
            VENDOR_A_USER_ID, VENDOR_A, passwords.encode("p004-vendor-a-test"),
            VENDOR_B_USER_ID, VENDOR_B, passwords.encode("p004-vendor-b-test"),
            CUSTOMER_A_ID, CUSTOMER_A, passwords.encode("p004-customer-a-test"),
            CUSTOMER_B_ID, CUSTOMER_B, passwords.encode("p004-customer-b-test"));

        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,?,?,TRUE),(?,?,?,TRUE)",
            VENDOR_A, VENDOR_A_TENANT, VENDOR_A_BRANCH,
            VENDOR_B, VENDOR_B_TENANT, VENDOR_B_BRANCH);
        seedEntitlements(VENDOR_A_TENANT,VENDOR_A);
        seedEntitlements(VENDOR_B_TENANT,VENDOR_B);

        db.update("INSERT INTO customer_profiles(id,user_id) VALUES(?,?),(?,?)",
            CUSTOMER_A_ID, CUSTOMER_A_ID, CUSTOMER_B_ID, CUSTOMER_B_ID);

        db.update("""
            INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
            VALUES(?,?,?,TRUE,?,?),(?,?,?,TRUE,?,?)
            """,
            VENDOR_A_TENANT, "p004-vendor-admin", "P004 Vendor A Admin", VENDOR_A, VENDOR_A,
            VENDOR_B_TENANT, "p004-vendor-admin", "P004 Vendor B Admin", VENDOR_B, VENDOR_B);

        long roleA = db.queryForObject(
            "SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key='p004-vendor-admin'",
            Long.class, VENDOR_A_TENANT);
        long roleB = db.queryForObject(
            "SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key='p004-vendor-admin'",
            Long.class, VENDOR_B_TENANT);

        for (Object[] grant : new Object[][] {
            {VENDOR_A_TENANT, roleA, "ROLE_VIEW", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "ROLE_UPDATE", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "USER_VIEW", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "ROLE_ASSIGN", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "USER_UPDATE", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "USER_DISABLE", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "INVENTORY_VIEW", VENDOR_A},
            {VENDOR_A_TENANT, roleA, "INVENTORY_ADJUST", VENDOR_A},
            {VENDOR_B_TENANT, roleB, "ROLE_VIEW", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "ROLE_UPDATE", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "USER_VIEW", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "ROLE_ASSIGN", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "USER_UPDATE", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "USER_DISABLE", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "INVENTORY_VIEW", VENDOR_B},
            {VENDOR_B_TENANT, roleB, "INVENTORY_ADJUST", VENDOR_B}
        }) {
            db.update(
                "INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",
                grant);
        }

        db.update("""
            INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
            VALUES(?,?,?, ?,TRUE,?),(?,?,?, ?,TRUE,?)
            """,
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A, roleA, VENDOR_A,
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B, roleB, VENDOR_B);

        db.update("""
            INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published)
            VALUES(?,?,?,NULL,?,100,0,TRUE),(?,?,?,NULL,?,200,0,TRUE)
            """,
            VENDOR_A_PRODUCT_ID, VENDOR_A_TENANT, "P004-A-ITEM", "Private Vendor A Item",
            VENDOR_B_PRODUCT_ID, VENDOR_B_TENANT, "P004-B-ITEM", "Private Vendor B Item");

        db.update("""
            INSERT INTO inventory(tenant_id,branch_id,product_id,quantity)
            VALUES(?,?,?,5),(?,?,?,7)
            """,
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_PRODUCT_ID,
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_PRODUCT_ID);

        // A single global customer may have independent relationships with
        // multiple vendors. Vendor B also has a separate Customer B relationship.
        db.update("""
            INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance)
            VALUES(?,?,?,CURRENT_TIMESTAMP,'P004_TEST_CONSENT'),
                  (?,?,?,CURRENT_TIMESTAMP,'P004_TEST_CONSENT'),
                  (?,?,?,CURRENT_TIMESTAMP,'P004_TEST_CONSENT')
            """,
            "p004-a-customer-a", VENDOR_A_TENANT, CUSTOMER_A_ID,
            "p004-b-customer-a", VENDOR_B_TENANT, CUSTOMER_A_ID,
            "p004-b-customer-b", VENDOR_B_TENANT, CUSTOMER_B_ID);

        return new Seed(roleA, roleB);
    }


    /** Creates a real active subscription so local/test actor resolution uses production entitlement filtering. */
    public void seedEntitlements(long tenantId,String actorUsername){
        db.update("""
            INSERT INTO subscription_plans(plan_key,display_name,active,current_version)
            SELECT 'p004-p101-test-plan','P004/P1-01 Test Plan',TRUE,1
            WHERE NOT EXISTS(SELECT 1 FROM subscription_plans WHERE plan_key='p004-p101-test-plan')
            """);
        Long planId=db.queryForObject("SELECT id FROM subscription_plans WHERE plan_key=?",
            Long.class,"p004-p101-test-plan");
        db.update("""
            INSERT INTO subscription_plan_versions(plan_id,version,price,currency,billing_interval,
                max_users,max_storage_mb,max_branches,max_warehouses,entitlements,grace_days,
                tax_metadata,invoice_description,created_by)
            SELECT ?,1,0,'INR','MONTHLY',1000,100000,100,100,
                '{"features":{"POS":true,"CATALOG":true,"INVENTORY":true,"ADMINISTRATION":true,"CRM":true,"LEADS":true,"QUOTATIONS":true,"ORDERS":true,"RETURNS":true,"FINANCE":true,"SHIPMENTS":true,"SERVICE":true,"REPORTING":true}}',
                30,'{}','P004/P1-01 test subscription',?
            WHERE NOT EXISTS(SELECT 1 FROM subscription_plan_versions WHERE plan_id=? AND version=1)
            """,planId,actorUsername,planId);
        db.update("""
            INSERT INTO tenant_subscriptions(tenant_id,plan_id,plan_version,status,
                billing_period_start,billing_period_end,terms_snapshot)
            SELECT ?,?,1,'ACTIVE',DATE '2026-01-01',DATE '2099-12-31','{"test":true}'
            WHERE NOT EXISTS(SELECT 1 FROM tenant_subscriptions WHERE tenant_id=?)
            """,tenantId,planId,tenantId);
    }

    public record Seed(long vendorARoleId, long vendorBRoleId) {}
}

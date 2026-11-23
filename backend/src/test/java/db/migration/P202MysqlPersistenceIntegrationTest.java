package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P2-02 MySQL 8.4 migration contract tests. These tests deliberately use the
 * same MySQL service as the backend CI matrix; H2 is not accepted as evidence.
 */
class P202MysqlPersistenceIntegrationTest {

    @Test
    void cleanInstallAppliesExactlyV1AndRepeatStartupIsANoOp() throws Exception {
        withSchema((url, user, password, schema) -> {
            Flyway flyway = flyway(url, user, password, schema);
            flyway.migrate();
            flyway.validate();

            var applied = Arrays.stream(flyway.info().applied()).toList();
            assertThat(applied).hasSize(1);
            assertThat(applied.getFirst().getScript()).isEqualTo("V1__base_schema.sql");
            assertThat(applied.getFirst().getVersion().getVersion()).isEqualTo("1");

            // A second application startup must be a no-op and validation must
            // still pass with the exact same applied migration set.
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();
            assertThat(Arrays.stream(flyway.info().applied())
                    .map(migration -> migration.getVersion().getVersion()).toList())
                    .containsExactly("1");
        });
    }


    @Test
    void mysqlSchemaEnforcesCoreTenantOwnershipChecksGeneratedColumnsAndFinancialPrecision() throws Exception {
        withSchema((url, user, password, schema) -> {
            Flyway flyway = flyway(url, user, password, schema);
            flyway.migrate();
            JdbcTemplate db = jdbc(url, user, password);

            for (String table : Set.of("branches", "employees", "registers", "pos_sessions",
                    "products", "inventory", "orders", "order_items", "inventory_ledger",
                    "pos_audit", "vendor_roles", "vendor_role_permissions",
                    "employee_role_assignments", "employee_register_assignments",
                    "tenant_subscriptions", "subscription_invoices", "subscription_payments",
                    "subscription_events", "pos_stock_locations")) {
                assertThat(db.queryForObject("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_schema=? AND table_name=? AND column_name='tenant_id'
                        """, String.class, schema, table))
                        .as("%s.tenant_id must be NOT NULL on MySQL 8.4", table)
                        .isEqualTo("NO");
            }

            assertThat(constraintCount(db, schema, "orders", "chk_pos_order_uses_its_branch_stock")).isEqualTo(1);
            assertThat(constraintCount(db, schema, "pos_stock_locations", "chk_pos_stock_location_branch_key")).isEqualTo(1);
            assertThat(constraintCount(db, schema, "pos_stock_locations", "chk_pos_stock_location_code")).isEqualTo(1);
            assertThat(constraintCount(db, schema, "inventory_ledger", "fk_inventory_ledger_order_stock_branch")).isEqualTo(1);
            assertThat(constraintCount(db, schema, "stock_reservations", "fk_stock_reservations_order_stock_branch")).isEqualTo(1);

            assertThat(db.queryForObject("""
                    SELECT extra FROM information_schema.columns
                    WHERE table_schema=? AND table_name='pos_sessions' AND column_name='active_register'
                    """, String.class, schema).toUpperCase()).contains("GENERATED");
            assertThat(db.queryForObject("""
                    SELECT CONCAT(numeric_precision, ':', numeric_scale)
                    FROM information_schema.columns
                    WHERE table_schema=? AND table_name='orders' AND column_name='total'
                    """, String.class, schema)).isEqualTo("19:2");

            assertThat(indexHasLeadingTenant(db, schema, "orders", "tenant_id")).isTrue();
            assertThat(indexHasLeadingTenant(db, schema, "inventory", "tenant_id")).isTrue();
            assertThat(indexHasLeadingTenant(db, schema, "subscription_invoices", "tenant_id")).isTrue();
        });
    }


    @Test
    void tenantOwnedTableInventoryAndCompositeParentKeysAreAssertedOnMysql() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            // Explicit ownership classification for every table containing tenant_id
            // in the V1-V55 schema. Platform audit and refresh-token rows are shared
            // actor-context tables; support-access rows are tenant-owned records.
            Set<String> expectedTenantTables = Set.of(
                    "branches", "employees", "registers", "pos_sessions", "products",
                    "inventory", "orders", "order_items", "inventory_ledger", "pos_audit",
                    "vendor_invoices", "pos_cash_movements", "carts", "cart_items",
                    "catalog_categories", "product_categories", "catalog_promotions",
                    "catalog_promotion_audit", "pos_returns", "pos_return_items",
                    "pos_return_events", "inventory_return_dispositions", "pos_cash_refunds",
                    "pos_cash_refund_items", "report_export_audit", "receipt_document_audit",
                    "vendor_payment_attempts", "tenant_customers", "pos_customer_challenges",
                    "branch_settings_events", "catalog_stock", "catalog_change_events",
                    "pos_drawer_tenders", "inventory_receipts", "order_buyer_snapshots",
                    "pos_active_drafts", "tenant_subscriptions", "subscription_invoices",
                    "subscription_payments", "subscription_events", "vendor_roles",
                    "vendor_role_permissions", "employee_role_assignments",
                    "employee_register_assignments", "vendor_access_audit_events",
                    "pos_stock_locations", "catalog_discount_policy", "pos_shift_reviews",
                    "platform_support_access", "stock_reservations");

            Set<String> discovered = new HashSet<>(db.queryForList("""
                    SELECT DISTINCT table_name FROM information_schema.columns
                    WHERE table_schema=? AND column_name='tenant_id'
                    """, String.class, schema));
            // tenant_id is also an optional actor-context column on platform-owned
            // audit and refresh-token rows. Keep those explicitly classified rather
            // than incorrectly requiring NOT NULL on them.
            Set<String> optionalScopeTables = Set.of("platform_audit_events", "identity_refresh_tokens");
            Set<String> classifiedTables = new HashSet<>(expectedTenantTables);
            classifiedTables.addAll(optionalScopeTables);
            assertThat(discovered).containsExactlyInAnyOrderElementsOf(classifiedTables);

            for (String table : expectedTenantTables) {
                assertThat(db.queryForObject("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_schema=? AND table_name=? AND column_name='tenant_id'
                        """, String.class, schema, table))
                        .as("%s.tenant_id must be NOT NULL", table)
                        .isEqualTo("NO");
            }

            // Verify every schema-discovered FK edge between tenant-owned tables
            // carries tenant_id on both sides. This catches omissions as migrations
            // add new commercial, financial or operational tables.
            Set<String> nonCompositeTenantEdges = new HashSet<>(db.queryForList("""
                    SELECT constraint_name FROM (
                      SELECT k.constraint_name,k.table_name,k.referenced_table_name,
                             SUM(k.column_name='tenant_id') AS child_tenant_columns,
                             SUM(k.referenced_column_name='tenant_id') AS parent_tenant_columns
                        FROM information_schema.key_column_usage k
                        JOIN information_schema.columns child
                          ON child.table_schema=k.table_schema AND child.table_name=k.table_name
                         AND child.column_name='tenant_id'
                        JOIN information_schema.columns parent
                          ON parent.table_schema=k.referenced_table_schema
                         AND parent.table_name=k.referenced_table_name
                         AND parent.column_name='tenant_id'
                       WHERE k.table_schema=? AND k.referenced_table_name IS NOT NULL
                       GROUP BY k.constraint_name,k.table_name,k.referenced_table_name
                      HAVING child_tenant_columns=0 OR parent_tenant_columns=0
                    ) incomplete_tenant_edges
                    """, String.class, schema));
            // V40's three single-column subscription FKs are retained for
            // backward compatibility and strengthened by V41 composite owner
            // FKs. The composite equivalents are asserted below; no other
            // tenant-to-tenant relationship may omit tenant_id.
            assertThat(nonCompositeTenantEdges).containsExactlyInAnyOrder(
                    "fk_sub_invoice_subscription", "fk_sub_payment_invoice",
                    "fk_sub_event_subscription");

            // Every discovered tenant-owned table must be represented by exactly
            // one ownership classification; all of its tenant-scoped foreign keys
            // are checked above against MySQL's actual ordered key metadata.
            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.key_column_usage
                    WHERE table_schema=? AND referenced_table_name IS NOT NULL
                      AND column_name='tenant_id'
                    """, Integer.class, schema)).isGreaterThan(0);

            // Every parent key used by tenant-scoped relationships must include
            // tenant_id in the unique key, not merely in an unrelated index.
            assertUniqueKey(db, schema, "branches", "tenant_id,id");
            assertUniqueKey(db, schema, "employees", "tenant_id,branch_id,username");
            assertUniqueKey(db, schema, "registers", "tenant_id,branch_id,id");
            assertUniqueKey(db, schema, "pos_sessions", "tenant_id,branch_id,id");
            assertUniqueKey(db, schema, "products", "tenant_id,id");
            assertUniqueKey(db, schema, "orders", "tenant_id,id");
            assertUniqueKey(db, schema, "orders", "tenant_id,branch_id,session_id,id");
            assertUniqueKey(db, schema, "orders", "tenant_id,branch_id,cashier,session_id,id");
            assertUniqueKey(db, schema, "carts", "tenant_id,id");
            assertUniqueKey(db, schema, "tenant_customers", "tenant_id,id");
            assertUniqueKey(db, schema, "pos_returns", "tenant_id,id");
            assertUniqueKey(db, schema, "pos_cash_refunds", "tenant_id,id");
            assertUniqueKey(db, schema, "tenant_subscriptions", "tenant_id,id");
            assertUniqueKey(db, schema, "subscription_invoices", "tenant_id,id");
            assertUniqueKey(db, schema, "vendor_roles", "tenant_id,id");

            // Derive every tenant-to-tenant FK edge from MySQL metadata and
            // assert that its referenced ordered column tuple is itself unique.
            // This covers parent keys beyond the hand-curated high-value list.
            for (String edge : db.queryForList("""
                    SELECT CONCAT(referenced_table_name,'|',
                                  GROUP_CONCAT(referenced_column_name
                                    ORDER BY position_in_unique_constraint SEPARATOR ','))
                    FROM information_schema.key_column_usage
                    WHERE table_schema=? AND referenced_table_name IS NOT NULL
                    GROUP BY constraint_name,table_name,referenced_table_name
                    HAVING SUM(column_name='tenant_id') > 0
                       AND SUM(referenced_column_name='tenant_id') > 0
                    """, String.class, schema)) {
                int separator=edge.indexOf('|');
                assertThat(separator).isGreaterThan(0);
                assertUniqueKey(db,schema,edge.substring(0,separator),edge.substring(separator+1));
            }
        });
    }

    @Test
    void v40ThroughV47ChecksCompositeRelationshipsAndGeneratedColumnsArePresent() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            assertConstraint(db, schema, "tenants", "chk_tenant_lifecycle", "CHECK");
            assertConstraint(db, schema, "platform_audit_events", "chk_platform_audit_outcome", "CHECK");
            assertConstraint(db, schema, "platform_support_access", "chk_support_expiry", "CHECK");
            assertConstraint(db, schema, "platform_configuration", "chk_platform_config_secret", "CHECK");
            assertConstraint(db, schema, "subscription_plan_versions", "chk_plan_interval", "CHECK");
            assertConstraint(db, schema, "subscription_plan_versions", "chk_plan_limits", "CHECK");
            assertConstraint(db, schema, "tenant_subscriptions", "chk_subscription_status", "CHECK");
            assertConstraint(db, schema, "subscription_invoices", "chk_sub_invoice_status", "CHECK");
            assertConstraint(db, schema, "subscription_payments", "chk_sub_payment_status", "CHECK");
            assertConstraint(db, schema, "vendor_role_permissions",
                    "chk_vendor_role_permission_no_platform", "CHECK");
            assertConstraint(db, schema, "platform_user_permissions", "chk_platform_permissions_v47", "CHECK");

            // Complete named-constraint inventory introduced or replaced by V40-V47.
            // V44's permission CHECK is deliberately replaced by V47's expanded
            // allow-list; both migrations are also verified by the final constraint.
            Set<String> v40ToV47 = Set.of(
                    "chk_tenant_lifecycle", "fk_platform_audit_actor", "fk_platform_audit_tenant",
                    "chk_platform_audit_outcome", "fk_support_tenant", "fk_support_actor",
                    "fk_support_revoker", "chk_support_expiry", "fk_platform_config_actor",
                    "chk_platform_config_secret", "fk_plan_version_plan", "fk_plan_version_actor",
                    "chk_plan_interval", "chk_plan_limits", "fk_subscription_tenant",
                    "fk_subscription_plan_version", "chk_subscription_status", "fk_sub_invoice_tenant",
                    "fk_sub_invoice_subscription", "chk_sub_invoice_status", "fk_sub_payment_tenant",
                    "fk_sub_payment_invoice", "chk_sub_payment_status", "fk_sub_event_tenant",
                    "fk_sub_event_subscription", "fk_sub_event_actor", "fk_invitation_user",
                    "uk_tenant_subscription_tenant_id", "uk_tenant_effective_subscription",
                    "uk_sub_invoice_tenant_id", "fk_sub_invoice_subscription_owner",
                    "fk_sub_payment_invoice_owner", "fk_platform_mfa_token_user",
                    "uk_vendor_role_tenant_key", "uk_vendor_role_tenant_id", "fk_vendor_role_tenant",
                    "fk_vendor_role_created_by", "fk_vendor_role_updated_by",
                    "fk_vendor_role_permission_role", "fk_vendor_role_permission_actor",
                    "chk_vendor_role_permission_no_platform", "fk_employee_role_employee",
                    "fk_employee_role_role", "fk_employee_role_assigned_by",
                    "fk_employee_role_revoked_by", "fk_employee_register_employee",
                    "fk_employee_register_register", "fk_employee_register_assigned_by",
                    "fk_employee_register_revoked_by", "fk_vendor_access_audit_tenant",
                    "fk_vendor_access_audit_actor", "fk_vendor_access_audit_subject",
                    "chk_platform_permissions_v47");
            Set<String> installedConstraintNames = new HashSet<>(db.queryForList("""
                    SELECT constraint_name FROM information_schema.table_constraints
                    WHERE table_schema=?
                    """, String.class, schema));
            assertThat(installedConstraintNames).containsAll(v40ToV47);
            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.triggers
                    WHERE trigger_schema=? AND event_object_table='platform_audit_events'
                      AND trigger_name IN ('trg_platform_audit_no_update','trg_platform_audit_no_delete')
                    """, Integer.class, schema)).isEqualTo(2);

            assertConstraint(db, schema, "tenant_subscriptions", "uk_tenant_subscription_tenant_id", "UNIQUE");
            assertConstraint(db, schema, "subscription_invoices", "uk_sub_invoice_tenant_id", "UNIQUE");
            assertConstraint(db, schema, "vendor_roles", "uk_vendor_role_tenant_id", "UNIQUE");
            assertConstraint(db, schema, "subscription_invoices",
                    "fk_sub_invoice_subscription_owner", "FOREIGN KEY");
            assertConstraint(db, schema, "subscription_payments",
                    "fk_sub_payment_invoice_owner", "FOREIGN KEY");
            assertConstraint(db, schema, "subscription_events",
                    "fk_sub_event_subscription_owner", "FOREIGN KEY");
            assertConstraint(db, schema, "vendor_role_permissions",
                    "fk_vendor_role_permission_role", "FOREIGN KEY");
            assertConstraint(db, schema, "employee_role_assignments",
                    "fk_employee_role_employee", "FOREIGN KEY");
            assertConstraint(db, schema, "employee_register_assignments",
                    "fk_employee_register_register", "FOREIGN KEY");

            assertForeignKey(db, schema, "subscription_invoices", "tenant_id,subscription_id",
                    "tenant_subscriptions", "tenant_id,id");
            assertForeignKey(db, schema, "subscription_payments", "tenant_id,subscription_invoice_id",
                    "subscription_invoices", "tenant_id,id");
            assertForeignKey(db, schema, "subscription_events", "tenant_id,subscription_id",
                    "tenant_subscriptions", "tenant_id,id");
            assertForeignKey(db, schema, "pos_drawer_tenders", "tenant_id,branch_id,session_id",
                    "pos_sessions", "tenant_id,branch_id,id");
            assertForeignKey(db, schema, "pos_drawer_tenders", "tenant_id,branch_id,session_id,order_reference",
                    "orders", "tenant_id,branch_id,session_id,id");
            assertForeignKey(db, schema, "pos_active_drafts", "tenant_id,branch_id,cashier,session_id,order_reference",
                    "orders", "tenant_id,branch_id,cashier,session_id,id");

            assertGeneratedColumn(db, schema, "pos_sessions", "active_register");
            assertGeneratedColumn(db, schema, "carts", "active_customer_id");
            assertGeneratedColumn(db, schema, "tenant_subscriptions", "active_tenant_id");
        });
    }

    @Test
    void generatedPosSessionColumnAndMysqlStockLocationCheckBehaveAsDeclared() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);
            db.update("INSERT INTO tenants(id,name) VALUES(991001,'P2 generated-column tenant')");
            db.update("INSERT INTO branches(id,tenant_id,name) VALUES(991011,991001,'P2 generated-column branch')");
            db.update("""
                    INSERT INTO users(id,username,password_hash,enabled)
                    VALUES(991012,'p2-cashier','not-a-login-secret',TRUE)
                    """);
            db.update("""
                    INSERT INTO employees(username,tenant_id,branch_id,enabled)
                    VALUES('p2-cashier',991001,991011,TRUE)
                    """);
            db.update("""
                    INSERT INTO pos_stock_locations
                    (tenant_id,branch_id,stock_location_id,location_code,display_name,active)
                    VALUES(991001,991011,991011,'POS_DEFAULT','P2 POS stock',TRUE)
                    """);
            db.update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(991011,991001,991011)");
            db.update("""
                    INSERT INTO pos_sessions
                    (id,tenant_id,branch_id,cashier,register_id,state,opening_float)
                    VALUES('p2-open-session',991001,991011,'p2-cashier',991011,'OPEN',0.00)
                    """);
            assertThat(db.queryForObject(
                    "SELECT active_register FROM pos_sessions WHERE id='p2-open-session'", Long.class))
                    .isEqualTo(991011L);
            db.update("UPDATE pos_sessions SET state='CLOSED' WHERE id='p2-open-session'");
            assertThat(db.queryForObject(
                    "SELECT active_register FROM pos_sessions WHERE id='p2-open-session'", Long.class))
                    .isNull();

            assertThatThrownBy(() -> db.update("""
                    INSERT INTO pos_stock_locations
                    (tenant_id,branch_id,stock_location_id,location_code,display_name,active)
                    VALUES(991001,991011,991012,'POS_DEFAULT','invalid branch stock',TRUE)
                    """)).hasMessageContaining("chk_pos_stock_location_branch_key");
        });
    }

    @Test
    void platformAuditIsAppendOnlyAndFinancialHistoryRejectsParentDeletion() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            db.update("""
                    INSERT INTO users(id,username,password_hash,enabled)
                    VALUES(993001,'p2-audit-actor','not-a-login-secret',TRUE)
                    """);
            db.update("INSERT INTO tenants(id,name) VALUES(993011,'P2 financial tenant'),(993012,'P2 second financial tenant')");
            db.update("""
                    INSERT INTO platform_audit_events(actor_username,action,resource_type,tenant_id,outcome)
                    VALUES('p2-audit-actor','P2_IMMUTABILITY_TEST','TEST',993011,'SUCCESS')
                    """);
            Long auditId = db.queryForObject("""
                    SELECT MAX(id) FROM platform_audit_events
                    WHERE action='P2_IMMUTABILITY_TEST'
                    """, Long.class);
            assertThat(auditId).isNotNull();
            assertThatThrownBy(() -> db.update("""
                    UPDATE platform_audit_events SET reason='tampered' WHERE id=?
                    """, auditId)).hasMessageContaining("immutable");
            assertThatThrownBy(() -> db.update("DELETE FROM platform_audit_events WHERE id=?", auditId))
                    .hasMessageContaining("cannot be deleted");
            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM platform_audit_events WHERE id=?
                    """, Integer.class, auditId)).isEqualTo(1);

            db.update("""
                    INSERT INTO subscription_plans(id,plan_key,display_name,active,current_version)
                    VALUES(993021,'p2-test-plan','P2 Test Plan',TRUE,1)
                    """);
            db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(993021,1,100.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','P2 test plan','p2-audit-actor')
                    """);
            db.update("""
                    INSERT INTO tenant_subscriptions
                    (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,terms_snapshot)
                    VALUES(993031,993011,993021,1,'ACTIVE','2026-01-01','2026-02-01','{}')
                    """);
            db.update("""
                    INSERT INTO subscription_invoices
                    (tenant_id,subscription_id,invoice_number,billing_period_start,billing_period_end,currency,
                     plan_snapshot,subtotal,tax_amount,total,status,due_at)
                    VALUES(993011,993031,'P2-INV-993031','2026-01-01','2026-02-01','INR','{}',
                           100.0000,0.0000,100.0000,'OPEN','2026-02-15 00:00:00')
                    """);
            Long invoiceId = db.queryForObject("""
                    SELECT id FROM subscription_invoices WHERE invoice_number='P2-INV-993031'
                    """, Long.class);
            db.update("""
                    INSERT INTO subscription_payments
                    (tenant_id,subscription_invoice_id,provider,platform_merchant_reference,idempotency_key,
                     amount,currency,status)
                    VALUES(993011,?,'TEST','P2-MERCHANT-993031','P2-IDEMPOTENCY-993031',
                           100.0000,'INR','SUCCEEDED')
                    """, invoiceId);
            db.update("""
                    INSERT INTO subscription_events(tenant_id,subscription_id,new_status,actor_username,reason)
                    VALUES(993011,993031,'ACTIVE','p2-audit-actor','P2 delete restriction test')
                    """);

            // Positive same-tenant ownership is represented by the rows above.
            // Each corresponding tenant mismatch must fail on INSERT and UPDATE.
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_invoices
                    (tenant_id,subscription_id,invoice_number,billing_period_start,billing_period_end,currency,
                     plan_snapshot,subtotal,tax_amount,total,status,due_at)
                    VALUES(993012,993031,'P2-CROSS-TENANT-INVOICE','2026-01-01','2026-02-01','INR','{}',
                           100.0000,0.0000,100.0000,'OPEN','2026-02-15 00:00:00')
                    """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("""
                    UPDATE subscription_invoices SET tenant_id=993012 WHERE id=?
                    """, invoiceId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_payments
                    (tenant_id,subscription_invoice_id,provider,platform_merchant_reference,idempotency_key,
                     amount,currency,status)
                    VALUES(993012,?,'TEST','P2-CROSS-TENANT-MERCHANT','P2-CROSS-TENANT-IDEMPOTENCY',
                           100.0000,'INR','SUCCEEDED')
                    """, invoiceId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("""
                    UPDATE subscription_payments SET tenant_id=993012
                    WHERE tenant_id=993011 AND subscription_invoice_id=?
                    """, invoiceId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_events(tenant_id,subscription_id,new_status,actor_username,reason)
                    VALUES(993012,993031,'ACTIVE','p2-audit-actor','cross-tenant must fail')
                    """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("""
                    UPDATE subscription_events SET tenant_id=993012
                    WHERE tenant_id=993011 AND subscription_id=993031
                    """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            assertThatThrownBy(() -> db.update("DELETE FROM subscription_invoices WHERE id=?", invoiceId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("DELETE FROM tenant_subscriptions WHERE id=993031"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update(
                    "DELETE FROM subscription_plan_versions WHERE plan_id=993021 AND version=1"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("DELETE FROM subscription_plans WHERE id=993021"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_invoices WHERE id=?",
                    Integer.class, invoiceId)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_payments WHERE tenant_id=993011 AND subscription_invoice_id=?",
                    Integer.class, invoiceId)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_events WHERE tenant_id=993011 AND subscription_id=993031",
                    Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_subscriptions WHERE id=993031",
                    Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_plan_versions WHERE plan_id=993021 AND version=1",
                    Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_plans WHERE id=993021",
                    Integer.class)).isEqualTo(1);

            for (String constraint : Set.of("fk_sub_payment_invoice_owner",
                    "fk_sub_invoice_subscription_owner", "fk_sub_event_subscription_owner")) {
                assertThat(db.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.referential_constraints
                        WHERE constraint_schema=? AND constraint_name=? AND delete_rule IN ('RESTRICT','NO ACTION')
                        """, Integer.class, schema, constraint))
                        .as("%s must prevent financial-history parent deletion", constraint)
                        .isEqualTo(1);
            }

            // No FK may cascade-delete or null out referenced financial history.
            // This is a schema-wide guard over the implemented commercial and
            // financial parent tables, not just the subscription invoice fixture.
            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.referential_constraints r
                    JOIN information_schema.key_column_usage k
                      ON k.constraint_schema=r.constraint_schema
                     AND k.constraint_name=r.constraint_name
                     AND k.table_name=r.table_name
                    WHERE r.constraint_schema=?
                      AND k.referenced_table_name IN (
                        'orders','vendor_invoices','subscription_invoices','subscription_payments',
                        'pos_returns','pos_cash_refunds','inventory_receipts','inventory_ledger',
                        'pos_drawer_tenders','pos_cash_movements','platform_audit_events',
                        'vendor_access_audit_events','receipt_document_audit','pos_audit'
                      )
                      AND r.delete_rule NOT IN ('RESTRICT','NO ACTION')
                    """, Integer.class, schema)).isZero();
        });
    }

    @Test
    void mysqlUtcTimestampRoundTripPreservesTheExactInstant() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("SET time_zone = '+00:00'");
                statement.execute("INSERT INTO tenants(id,name) VALUES(995001,'P2 UTC timestamp tenant')");
                java.time.Instant expected = java.time.Instant.parse("2026-11-22T07:08:09Z");
                try (var update = connection.prepareStatement(
                        "UPDATE tenants SET created_at=?,updated_at=? WHERE id=995001")) {
                    var utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                    update.setTimestamp(1, java.sql.Timestamp.from(expected), utc);
                    update.setTimestamp(2, java.sql.Timestamp.from(expected), utc);
                    update.executeUpdate();
                }
                try (var select = connection.prepareStatement(
                        "SELECT created_at FROM tenants WHERE id=995001");
                     ResultSet rows = select.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getTimestamp(1,
                            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))).toInstant())
                            .isEqualTo(expected);
                    assertThat(rows.next()).isFalse();
                }
                try (ResultSet zone = statement.executeQuery("SELECT @@session.time_zone")) {
                    assertThat(zone.next()).isTrue();
                    assertThat(zone.getString(1)).isEqualTo("+00:00");
                }
            }
        });
    }

    @Test
    void versionColumnsSupportCompareAndSwapWithoutStaleOverwrite() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);
            db.update("INSERT INTO tenants(id,name) VALUES(992001,'P2 optimistic tenant')");
            assertThat(db.update("""
                    UPDATE tenants SET name='winner',version=version+1 WHERE id=992001 AND version=0
                    """)).isEqualTo(1);
            assertThat(db.update("""
                    UPDATE tenants SET name='stale loser',version=version+1 WHERE id=992001 AND version=0
                    """)).isZero();
            assertThat(db.queryForObject("SELECT name FROM tenants WHERE id=992001", String.class))
                    .isEqualTo("winner");
            assertThat(db.queryForObject("SELECT version FROM tenants WHERE id=992001", Long.class))
                    .isEqualTo(1L);
        });
    }

    @Test
    void v40ToV47CheckConstraintsRejectInvalidTenantPlatformBillingAndRoleRowsOnMysql() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);
            db.update("""
                    INSERT INTO users(id,username,password_hash,enabled)
                    VALUES(994001,'p2-check-actor','not-a-login-secret',TRUE)
                    """);
            db.update("INSERT INTO tenants(id,name) VALUES(994011,'P2 check tenant')");
            db.update("INSERT INTO customer_profiles(id,user_id) VALUES(994001,994001)");
            db.update("""
                    INSERT INTO carts(id,tenant_id,customer_id,state)
                    VALUES('p2-active-cart',994011,994001,'ACTIVE')
                    """);
            assertThat(db.queryForObject("SELECT active_customer_id FROM carts WHERE id='p2-active-cart'",Long.class))
                    .isEqualTo(994001L);
            db.update("UPDATE carts SET state='ARCHIVED' WHERE id='p2-active-cart'");
            assertThat(db.queryForObject("SELECT active_customer_id FROM carts WHERE id='p2-active-cart'",Long.class))
                    .isNull();
            db.update("INSERT INTO branches(id,tenant_id,name) VALUES(994021,994011,'P2 check branch')");
            db.update("""
                    INSERT INTO employees(username,tenant_id,branch_id,enabled)
                    VALUES('p2-check-actor',994011,994021,TRUE)
                    """);

            assertThatThrownBy(() -> db.update(
                    "UPDATE tenants SET lifecycle_status='NOT_A_LIFECYCLE' WHERE id=994011"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO platform_audit_events(actor_username,action,resource_type,tenant_id,outcome)
                    VALUES('p2-check-actor','P2_BAD_OUTCOME','TEST',994011,'NOT_AN_OUTCOME')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO platform_support_access(tenant_id,actor_username,purpose,ticket_reference,expires_at)
                    VALUES(994011,'p2-check-actor','P2 invalid expiry','P2-EXPIRY','2000-01-01 00:00:00')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO platform_support_access(tenant_id,actor_username,purpose,ticket_reference,granted_at,expires_at)
                    VALUES(994011,'p2-check-actor','P2 valid expiry','P2-VALID-EXPIRY','2026-01-01 00:00:00','2026-01-02 00:00:00')
                    """);
            Long supportId=db.queryForObject("SELECT MAX(id) FROM platform_support_access WHERE ticket_reference='P2-VALID-EXPIRY'",Long.class);
            assertThatThrownBy(() -> db.update("UPDATE platform_support_access SET expires_at='2025-12-31 00:00:00' WHERE id=?",supportId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO platform_configuration(config_key,category,plain_value,secret_reference,is_sensitive,updated_by)
                    VALUES('p2-invalid-secret','SECURITY','plain-secret',NULL,TRUE,'p2-check-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO platform_configuration(config_key,category,secret_reference,is_sensitive,updated_by)
                    VALUES('p2-valid-secret','SECURITY','vault://p2/test',TRUE,'p2-check-actor')
                    """);
            assertThatThrownBy(() -> db.update("UPDATE platform_configuration SET plain_value='leaked' WHERE config_key='p2-valid-secret'"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO platform_configuration(config_key,category,plain_value,is_sensitive,updated_by)
                    VALUES('p2-valid-plain','GENERAL','safe value',FALSE,'p2-check-actor')
                    """);
            assertThatThrownBy(() -> db.update("UPDATE platform_configuration SET secret_reference='vault://unexpected' WHERE config_key='p2-valid-plain'"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);

            db.update("""
                    INSERT INTO subscription_plans(id,plan_key,display_name,active,current_version)
                    VALUES(994031,'p2-check-plan','P2 Check Plan',TRUE,1)
                    """);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(994099,1,100.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','missing parent plan','p2-check-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(994031,1,100.0000,'INR','WEEKLY',5,1000,2,0,'{}',7,'{}','P2 invalid interval','p2-check-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(994031,2,-1.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','P2 invalid price','p2-check-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(994031,3,100.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','P2 valid plan version','p2-check-actor')
                    """);
            assertThatThrownBy(() -> db.update("UPDATE subscription_plan_versions SET billing_interval='WEEKLY' WHERE plan_id=994031 AND version=3"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("UPDATE subscription_plan_versions SET max_users=0 WHERE plan_id=994031 AND version=3"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO tenant_subscriptions
                    (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,terms_snapshot)
                    VALUES(994040,994011,994031,99,'PENDING','2026-01-01','2026-02-01','{}')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO tenant_subscriptions
                    (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,terms_snapshot)
                    VALUES(994041,994011,994031,3,'NOT_A_STATUS','2026-01-01','2026-02-01','{}')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO tenant_subscriptions
                    (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,terms_snapshot)
                    VALUES(994042,994011,994031,3,'ACTIVE','2026-01-01','2026-02-01','{}')
                    """);
            assertThat(db.queryForObject("SELECT active_tenant_id FROM tenant_subscriptions WHERE id=994042",Long.class))
                    .isEqualTo(994011L);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO tenant_subscriptions
                    (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,terms_snapshot)
                    VALUES(994043,994011,994031,3,'ACTIVE','2026-02-01','2026-03-01','{}')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("UPDATE tenant_subscriptions SET plan_version=99 WHERE id=994042"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("UPDATE tenant_subscriptions SET status='NOT_A_STATUS' WHERE id=994042"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("UPDATE tenant_subscriptions SET status='CANCELLED' WHERE id=994042");
            assertThat(db.queryForObject("SELECT active_tenant_id FROM tenant_subscriptions WHERE id=994042",Long.class))
                    .isNull();
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_invoices
                    (tenant_id,subscription_id,invoice_number,billing_period_start,billing_period_end,currency,
                     plan_snapshot,subtotal,tax_amount,total,status,due_at)
                    VALUES(994011,994042,'P2-BAD-INVOICE-STATUS','2026-01-01','2026-02-01','INR','{}',
                           100.0000,0.0000,100.0000,'NOT_A_STATUS','2026-02-15 00:00:00')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO subscription_invoices
                    (tenant_id,subscription_id,invoice_number,billing_period_start,billing_period_end,currency,
                     plan_snapshot,subtotal,tax_amount,total,status,due_at)
                    VALUES(994011,994042,'P2-GOOD-INVOICE','2026-01-01','2026-02-01','INR','{}',
                           100.0000,0.0000,100.0000,'OPEN','2026-02-15 00:00:00')
                    """);
            Long invoiceId=db.queryForObject("SELECT id FROM subscription_invoices WHERE invoice_number='P2-GOOD-INVOICE'",Long.class);
            assertThatThrownBy(() -> db.update("UPDATE subscription_invoices SET status='NOT_A_STATUS' WHERE id=?",invoiceId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_payments
                    (tenant_id,subscription_invoice_id,provider,platform_merchant_reference,idempotency_key,
                     amount,currency,status)
                    VALUES(994011,?,'TEST','P2-BAD-PAYMENT','P2-BAD-PAYMENT','100.0000','INR','NOT_A_STATUS')
                    """,invoiceId)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("""
                    INSERT INTO subscription_payments
                    (tenant_id,subscription_invoice_id,provider,platform_merchant_reference,idempotency_key,
                     amount,currency,status)
                    VALUES(994011,?,'TEST','P2-GOOD-PAYMENT','P2-GOOD-PAYMENT','100.0000','INR','PENDING')
                    """,invoiceId);
            assertThatThrownBy(() -> db.update("""
                    UPDATE subscription_payments SET status='NOT_A_STATUS'
                    WHERE tenant_id=994011 AND platform_merchant_reference='P2-GOOD-PAYMENT'
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);

            db.update("""
                    INSERT INTO vendor_roles(id,tenant_id,role_key,display_name,created_by,updated_by)
                    VALUES(994051,994011,'p2-check-role','P2 Check Role','p2-check-actor','p2-check-actor')
                    """);
            db.update("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
                    VALUES(994011,994051,'INVENTORY_VIEW','p2-check-actor')
                    """);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
                    VALUES(994011,994051,'PLATFORM_CONFIG_MANAGE','p2-check-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    UPDATE vendor_role_permissions SET permission='PLATFORM_CONFIG_MANAGE'
                    WHERE tenant_id=994011 AND role_id=994051 AND permission='INVENTORY_VIEW'
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            db.update("INSERT INTO platform_user_permissions(username,permission) VALUES('p2-check-actor','PLATFORM_TENANT_VIEW')");
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO platform_user_permissions(username,permission)
                    VALUES('p2-check-actor','NOT_A_PLATFORM_PERMISSION')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    UPDATE platform_user_permissions SET permission='NOT_A_PLATFORM_PERMISSION'
                    WHERE username='p2-check-actor' AND permission='PLATFORM_TENANT_VIEW'
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        });
    }


    @Test
    void v45TenantRoleAndResourceCompositeForeignKeysRejectCrossTenantInsertAndUpdateOnMysql() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            db.update("INSERT INTO tenants(id,name) VALUES(996001,'P2 composite tenant A'),(996002,'P2 composite tenant B')");
            db.update("""
                    INSERT INTO branches(id,tenant_id,name)
                    VALUES(996011,996001,'P2 composite branch A'),(996012,996002,'P2 composite branch B')
                    """);
            db.update("""
                    INSERT INTO pos_stock_locations
                    (tenant_id,branch_id,stock_location_id,location_code,display_name,active)
                    VALUES(996001,996011,996011,'POS_DEFAULT','P2 composite stock A',TRUE),
                          (996002,996012,996012,'POS_DEFAULT','P2 composite stock B',TRUE)
                    """);
            // V28 binds employee usernames to global user identities. Seed
            // those parents first so this fixture reaches the V45 tenant/branch
            // composite relationships it is intended to exercise.
            db.update("""
                    INSERT INTO users(id,username,password_hash,enabled)
                    VALUES(996001,'p2-composite-a','not-a-login-secret',TRUE),
                          (996002,'p2-composite-b','not-a-login-secret',TRUE)
                    """);
            db.update("""
                    INSERT INTO employees(username,tenant_id,branch_id,enabled)
                    VALUES('p2-composite-a',996001,996011,TRUE),
                          ('p2-composite-b',996002,996012,TRUE)
                    """);
            db.update("""
                    INSERT INTO registers(id,tenant_id,branch_id)
                    VALUES(996021,996001,996011),(996022,996002,996012),
                          (996023,996001,996011),(996024,996001,996011)
                    """);
            db.update("""
                    INSERT INTO vendor_roles(id,tenant_id,role_key,display_name,created_by,updated_by)
                    VALUES(996031,996001,'p2-role-a','P2 Role A','p2-composite-a','p2-composite-a'),
                          (996032,996002,'p2-role-b','P2 Role B','p2-composite-b','p2-composite-b'),
                          (996033,996001,'p2-role-a2','P2 Role A2','p2-composite-a','p2-composite-a')
                    """);
            db.update("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
                    VALUES(996001,996031,'INVENTORY_VIEW','p2-composite-a')
                    """);
            db.update("""
                    INSERT INTO employee_role_assignments
                    (tenant_id,branch_id,username,role_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996031,TRUE,'p2-composite-a')
                    """);
            db.update("""
                    INSERT INTO employee_register_assignments
                    (tenant_id,branch_id,username,register_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996021,TRUE,'p2-composite-a')
                    """);
            db.update("""
                    INSERT INTO vendor_access_audit_events
                    (tenant_id,actor_username,subject_username,resource_type,action,reason)
                    VALUES(996001,'p2-composite-a','p2-composite-a','ROLE','P2_COMPOSITE_FIXTURE','same-tenant positive control')
                    """);

            // Positive controls above prove each child relationship accepts a
            // complete, same-tenant tuple before the negative probes are run.
            assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=996001", Integer.class))
                    .isEqualTo(2);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions WHERE tenant_id=996001", Integer.class))
                    .isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments WHERE tenant_id=996001", Integer.class))
                    .isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_register_assignments WHERE tenant_id=996001", Integer.class))
                    .isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=996001", Integer.class))
                    .isEqualTo(1);

            // vendor_roles -> employees(tenant_id, username)
            assertForeignKeyRejected("vendor_roles.created_by INSERT", () -> db.update("""
                    INSERT INTO vendor_roles(id,tenant_id,role_key,display_name,created_by,updated_by)
                    VALUES(996041,996001,'p2-bad-created','Bad creator','p2-composite-b','p2-composite-a')
                    """));
            assertForeignKeyRejected("vendor_roles.updated_by INSERT", () -> db.update("""
                    INSERT INTO vendor_roles(id,tenant_id,role_key,display_name,created_by,updated_by)
                    VALUES(996042,996001,'p2-bad-updated','Bad updater','p2-composite-a','p2-composite-b')
                    """));
            assertForeignKeyRejected("vendor_roles.created_by UPDATE",
                    () -> db.update("UPDATE vendor_roles SET created_by='p2-composite-b' WHERE id=996031"));
            assertForeignKeyRejected("vendor_roles.updated_by UPDATE",
                    () -> db.update("UPDATE vendor_roles SET updated_by='p2-composite-b' WHERE id=996031"));

            // vendor_role_permissions -> tenant-owned role and tenant-owned actor
            assertForeignKeyRejected("vendor_role_permissions.role INSERT", () -> db.update("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
                    VALUES(996001,996032,'POS_VIEW','p2-composite-a')
                    """));
            assertForeignKeyRejected("vendor_role_permissions.actor INSERT", () -> db.update("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
                    VALUES(996001,996031,'CATALOG_PROMOTION_MANAGE','p2-composite-b')
                    """));
            assertForeignKeyRejected("vendor_role_permissions.role UPDATE",
                    () -> db.update("UPDATE vendor_role_permissions SET role_id=996032 WHERE tenant_id=996001 AND permission='INVENTORY_VIEW'"));
            assertForeignKeyRejected("vendor_role_permissions.actor UPDATE",
                    () -> db.update("UPDATE vendor_role_permissions SET created_by='p2-composite-b' WHERE tenant_id=996001 AND permission='INVENTORY_VIEW'"));

            // Employee-role assignment covers employee, role, assigned_by and
            // nullable revoked_by as independent composite ownership edges.
            assertForeignKeyRejected("employee_role_assignments.employee INSERT", () -> db.update("""
                    INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-b',996031,TRUE,'p2-composite-a')
                    """));
            assertForeignKeyRejected("employee_role_assignments.role INSERT", () -> db.update("""
                    INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996032,TRUE,'p2-composite-a')
                    """));
            assertForeignKeyRejected("employee_role_assignments.assigned_by INSERT", () -> db.update("""
                    INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996033,TRUE,'p2-composite-b')
                    """));
            assertForeignKeyRejected("employee_role_assignments.revoked_by INSERT", () -> db.update("""
                    INSERT INTO employee_role_assignments
                    (tenant_id,branch_id,username,role_id,active,assigned_by,revoked_by,revoked_at)
                    VALUES(996001,996011,'p2-composite-a',996033,FALSE,'p2-composite-a','p2-composite-b',CURRENT_TIMESTAMP)
                    """));
            assertForeignKeyRejected("employee_role_assignments.employee UPDATE",
                    () -> db.update("UPDATE employee_role_assignments SET username='p2-composite-b' WHERE tenant_id=996001 AND role_id=996031"));
            assertForeignKeyRejected("employee_role_assignments.role UPDATE",
                    () -> db.update("UPDATE employee_role_assignments SET role_id=996032 WHERE tenant_id=996001 AND username='p2-composite-a'"));
            assertForeignKeyRejected("employee_role_assignments.assigned_by UPDATE",
                    () -> db.update("UPDATE employee_role_assignments SET assigned_by='p2-composite-b' WHERE tenant_id=996001 AND role_id=996031"));
            assertForeignKeyRejected("employee_role_assignments.revoked_by UPDATE",
                    () -> db.update("UPDATE employee_role_assignments SET revoked_by='p2-composite-b',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=996001 AND role_id=996031"));

            // Employee-register assignment covers employee, register,
            // assigned_by and revoked_by ownership independently.
            assertForeignKeyRejected("employee_register_assignments.employee INSERT", () -> db.update("""
                    INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-b',996021,TRUE,'p2-composite-a')
                    """));
            assertForeignKeyRejected("employee_register_assignments.register INSERT", () -> db.update("""
                    INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996022,TRUE,'p2-composite-a')
                    """));
            assertForeignKeyRejected("employee_register_assignments.assigned_by INSERT", () -> db.update("""
                    INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
                    VALUES(996001,996011,'p2-composite-a',996023,TRUE,'p2-composite-b')
                    """));
            assertForeignKeyRejected("employee_register_assignments.revoked_by INSERT", () -> db.update("""
                    INSERT INTO employee_register_assignments
                    (tenant_id,branch_id,username,register_id,active,assigned_by,revoked_by,revoked_at)
                    VALUES(996001,996011,'p2-composite-a',996024,FALSE,'p2-composite-a','p2-composite-b',CURRENT_TIMESTAMP)
                    """));
            assertForeignKeyRejected("employee_register_assignments.employee UPDATE",
                    () -> db.update("UPDATE employee_register_assignments SET username='p2-composite-b' WHERE tenant_id=996001 AND register_id=996021"));
            assertForeignKeyRejected("employee_register_assignments.register UPDATE",
                    () -> db.update("UPDATE employee_register_assignments SET register_id=996022 WHERE tenant_id=996001 AND username='p2-composite-a'"));
            assertForeignKeyRejected("employee_register_assignments.assigned_by UPDATE",
                    () -> db.update("UPDATE employee_register_assignments SET assigned_by='p2-composite-b' WHERE tenant_id=996001 AND register_id=996021"));
            assertForeignKeyRejected("employee_register_assignments.revoked_by UPDATE",
                    () -> db.update("UPDATE employee_register_assignments SET revoked_by='p2-composite-b',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=996001 AND register_id=996021"));

            // Vendor-access audit actors and subjects cannot be borrowed from
            // another tenant, on either INSERT or UPDATE.
            assertForeignKeyRejected("vendor_access_audit_events.actor INSERT", () -> db.update("""
                    INSERT INTO vendor_access_audit_events
                    (tenant_id,actor_username,subject_username,resource_type,action,reason)
                    VALUES(996001,'p2-composite-b','p2-composite-a','ROLE','P2_BAD_ACTOR','must reject')
                    """));
            assertForeignKeyRejected("vendor_access_audit_events.subject INSERT", () -> db.update("""
                    INSERT INTO vendor_access_audit_events
                    (tenant_id,actor_username,subject_username,resource_type,action,reason)
                    VALUES(996001,'p2-composite-a','p2-composite-b','ROLE','P2_BAD_SUBJECT','must reject')
                    """));
            Long auditId = db.queryForObject("""
                    SELECT MAX(id) FROM vendor_access_audit_events WHERE action='P2_COMPOSITE_FIXTURE'
                    """, Long.class);
            assertForeignKeyRejected("vendor_access_audit_events.actor UPDATE",
                    () -> db.update("UPDATE vendor_access_audit_events SET actor_username='p2-composite-b' WHERE id=?", auditId));
            assertForeignKeyRejected("vendor_access_audit_events.subject UPDATE",
                    () -> db.update("UPDATE vendor_access_audit_events SET subject_username='p2-composite-b' WHERE id=?", auditId));

            // Every failed UPDATE is statement-atomic; verify the original
            // same-tenant rows are still intact after all negative probes.
            assertThat(db.queryForObject("SELECT created_by FROM vendor_roles WHERE id=996031", String.class))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT updated_by FROM vendor_roles WHERE id=996031", String.class))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT role_id FROM vendor_role_permissions WHERE tenant_id=996001 AND permission='INVENTORY_VIEW'", Long.class))
                    .isEqualTo(996031L);
            assertThat(db.queryForObject("SELECT created_by FROM vendor_role_permissions WHERE tenant_id=996001 AND permission='INVENTORY_VIEW'", String.class))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT username FROM employee_role_assignments WHERE tenant_id=996001 AND role_id=996031", String.class))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT assigned_by FROM employee_role_assignments WHERE tenant_id=996001 AND role_id=996031", String.class))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT register_id FROM employee_register_assignments WHERE tenant_id=996001 AND username='p2-composite-a'", Long.class))
                    .isEqualTo(996021L);
            assertThat(db.queryForObject("SELECT actor_username FROM vendor_access_audit_events WHERE id=?", String.class, auditId))
                    .isEqualTo("p2-composite-a");
            assertThat(db.queryForObject("SELECT subject_username FROM vendor_access_audit_events WHERE id=?", String.class, auditId))
                    .isEqualTo("p2-composite-a");
        });
    }

    @Test
    void v40PlanLimitsCheckRejectsEveryInvalidLimitOnMysql() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema).migrate();
            JdbcTemplate db = jdbc(url, user, password);
            db.update("""
                    INSERT INTO users(id,username,password_hash,enabled)
                    VALUES(997001,'p2-plan-limits-actor','not-a-login-secret',TRUE)
                    """);
            db.update("""
                    INSERT INTO subscription_plans(id,plan_key,display_name,active,current_version)
                    VALUES(997011,'p2-plan-limits','P2 Plan Limits',TRUE,1)
                    """);

            String validPrefix = """
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,?,100.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}',?, 'p2-plan-limits-actor')
                    """;
            assertThat(db.update(validPrefix, 1, "P2 positive plan limit control")).isEqualTo(1);

            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,2,-1.0000,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','negative price','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,3,100.0000,'INR','MONTHLY',0,1000,2,0,'{}',7,'{}','zero users','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,4,100.0000,'INR','MONTHLY',5,-1,2,0,'{}',7,'{}','negative storage','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,5,100.0000,'INR','MONTHLY',5,1000,0,0,'{}',7,'{}','zero branches','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,6,100.0000,'INR','MONTHLY',5,1000,2,-1,'{}',7,'{}','negative warehouses','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO subscription_plan_versions
                    (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
                     max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
                    VALUES(997011,7,100.0000,'INR','MONTHLY',5,1000,2,0,'{}',-1,'{}','negative grace','p2-plan-limits-actor')
                    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);

            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_plan_versions WHERE plan_id=997011", Integer.class))
                    .isEqualTo(1);
        });
    }

    private static void assertForeignKeyRejected(String relationship, Runnable operation) {
        assertThatThrownBy(operation::run)
                .as("%s must reject a cross-tenant INSERT or UPDATE", relationship)
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static void assertConstraint(JdbcTemplate db, String schema, String table,
                                         String name, String type) {
        assertThat(constraintCount(db, schema, table, name))
                .as("%s.%s must exist as %s", table, name, type)
                .isEqualTo(1);
    }

    private static void assertGeneratedColumn(JdbcTemplate db, String schema, String table, String column) {
        assertThat(db.queryForObject("""
                SELECT extra FROM information_schema.columns
                WHERE table_schema=? AND table_name=? AND column_name=?
                """, String.class, schema, table, column).toUpperCase())
                .as("%s.%s must be generated", table, column)
                .contains("GENERATED");
    }

    private static void assertUniqueKey(JdbcTemplate db, String schema, String table, String columns) {
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS key_columns
                    FROM information_schema.statistics
                    WHERE table_schema=? AND table_name=? AND non_unique=0
                    GROUP BY index_name
                ) keys_found WHERE key_columns=?
                """, Integer.class, schema, table, columns))
                .as("%s must have a UNIQUE key (%s)", table, columns)
                .isGreaterThan(0);
    }

    private static void assertForeignKey(JdbcTemplate db, String schema, String childTable,
                                         String childColumns, String parentTable, String parentColumns) {
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT constraint_name,
                           GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS child_columns,
                           MIN(referenced_table_name) AS parent_table,
                           GROUP_CONCAT(referenced_column_name ORDER BY ordinal_position SEPARATOR ',') AS parent_columns
                    FROM information_schema.key_column_usage
                    WHERE table_schema=? AND table_name=? AND referenced_table_name IS NOT NULL
                    GROUP BY constraint_name
                ) fk
                WHERE child_columns=? AND parent_table=? AND parent_columns=?
                """, Integer.class, schema, childTable, childColumns, parentTable, parentColumns))
                .as("%s(%s) must reference %s(%s)", childTable, childColumns, parentTable, parentColumns)
                .isGreaterThan(0);
    }


    private static long constraintCount(JdbcTemplate db, String schema, String table, String name) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema=? AND table_name=? AND constraint_name=?
                """, Long.class, schema, table, name);
    }

    private static boolean indexHasLeadingTenant(JdbcTemplate db, String schema, String table, String column) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=? AND table_name=? AND seq_in_index=1 AND column_name=?
                """, Integer.class, schema, table, column) > 0;
    }

    private static Flyway flyway(String url, String user, String password, String schema) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(false)
                .cleanDisabled(true);
        return configuration.load();
    }

    private static JdbcTemplate jdbc(String url, String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    private static void withSchema(SchemaWork work) throws Exception {
        String baseUrl = System.getenv("TEST_DB_URL");
        String user = System.getenv("TEST_DB_USER");
        String password = System.getenv("TEST_DB_PASSWORD");
        assumeTrue(baseUrl != null && baseUrl.startsWith("jdbc:mysql://"),
                "P2-02 is a MySQL 8.4-only integration suite; run it in the MySQL CI lane");
        String schema = "solar_p202_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
        try (Connection admin = DriverManager.getConnection(baseUrl, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        try {
            work.run(serverUrl + schema, user, password, schema);
        } finally {
            try (Connection admin = DriverManager.getConnection(baseUrl, user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    @FunctionalInterface
    private interface SchemaWork {
        void run(String url, String user, String password, String schema) throws Exception;
    }
}

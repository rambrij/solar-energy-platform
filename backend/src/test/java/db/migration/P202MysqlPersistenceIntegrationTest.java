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
import org.flywaydb.core.api.MigrationVersion;
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
    private static final String BASELINE = "47";
    private static final String LATEST = "55";

    @Test
    void cleanInstallHasOrderedUniqueValidatedMigrationsAndRepeatableStartup() throws Exception {
        withSchema((url, user, password, schema) -> {
            Flyway flyway = flyway(url, user, password, schema, null);
            flyway.migrate();
            flyway.validate();

            var applied = Arrays.stream(flyway.info().applied()).toList();
            assertThat(applied).isNotEmpty();
            assertThat(applied).allMatch(migration -> migration.getState().isApplied());
            var versions = applied.stream()
                    .map(migration -> migration.getVersion().getVersion())
                    .toList();
            assertThat(new HashSet<>(versions)).hasSameSizeAs(versions);
            assertThat(applied.stream().map(m -> m.getVersion()).toList())
                    .isSortedAccordingTo(MigrationVersion::compareTo);
            assertThat(versions).contains(LATEST);

            // A second application startup must be a no-op and validation must
            // still pass with the exact same applied migration set.
            flyway.migrate();
            flyway.validate();
            assertThat(Arrays.stream(flyway.info().applied())
                    .map(migration -> migration.getVersion().getVersion()).toList())
                    .containsExactlyElementsOf(versions);
        });
    }

    @Test
    void supportedV47BaselineUpgradesToLatestAndRetainsTenantAndBranchData() throws Exception {
        withSchema((url, user, password, schema) -> {
            Flyway baseline = flyway(url, user, password, schema, BASELINE);
            baseline.migrate();
            JdbcTemplate db = jdbc(url, user, password);

            // V47 is the explicitly supported pre-ownership-hardening schema.
            // The records are deliberately present before V48/V50 are applied.
            db.update("INSERT INTO tenants(id,name) VALUES(990001,'P2-02 retained tenant')");
            db.update("INSERT INTO branches(id,tenant_id,name) VALUES(990011,990001,'P2-02 retained branch')");

            Flyway upgraded = flyway(url, user, password, schema, null);
            upgraded.migrate();
            upgraded.validate();

            assertThat(db.queryForObject("SELECT name FROM tenants WHERE id=990001", String.class))
                    .isEqualTo("P2-02 retained tenant");
            assertThat(db.queryForObject("SELECT name FROM branches WHERE tenant_id=990001 AND id=990011",
                    String.class)).isEqualTo("P2-02 retained branch");
            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM pos_stock_locations
                    WHERE tenant_id=990001 AND branch_id=990011
                      AND stock_location_id=990011 AND location_code='POS_DEFAULT'
                    """, Integer.class)).isEqualTo(1);
            assertThat(Arrays.stream(upgraded.info().applied())
                    .map(migration -> migration.getVersion().getVersion()).toList())
                    .contains(LATEST);
        });
    }

    @Test
    void mysqlSchemaEnforcesCoreTenantOwnershipChecksGeneratedColumnsAndFinancialPrecision() throws Exception {
        withSchema((url, user, password, schema) -> {
            Flyway flyway = flyway(url, user, password, schema, null);
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
            flyway(url, user, password, schema, null).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            // This is the explicit tenant-owned table inventory derived from the
            // V1-V55 schema and the SRS ownership classification. Platform audit
            // events are intentionally excluded: their tenant_id is nullable.
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
                    "pos_stock_locations");

            Set<String> discovered = new HashSet<>(db.queryForList("""
                    SELECT DISTINCT table_name FROM information_schema.columns
                    WHERE table_schema=? AND column_name='tenant_id'
                    """, String.class, schema));
            discovered.remove("platform_audit_events"); // platform-owned, optional tenant scope
            assertThat(discovered).containsAll(expectedTenantTables);

            for (String table : discovered) {
                assertThat(db.queryForObject("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_schema=? AND table_name=? AND column_name='tenant_id'
                        """, String.class, schema, table))
                        .as("%s.tenant_id must be NOT NULL", table)
                        .isEqualTo("NO");
            }

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
        });
    }

    @Test
    void v40ThroughV47ChecksCompositeRelationshipsAndGeneratedColumnsArePresent() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema, null).migrate();
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
            flyway(url, user, password, schema, null).migrate();
            JdbcTemplate db = jdbc(url, user, password);
            db.update("INSERT INTO tenants(id,name) VALUES(991001,'P2 generated-column tenant')");
            db.update("INSERT INTO branches(id,tenant_id,name) VALUES(991011,991001,'P2 generated-column branch')");
            db.update("""
                    INSERT INTO employees(username,password_hash,tenant_id,branch_id,enabled)
                    VALUES('p2-cashier','not-a-login-secret',991001,991011,TRUE)
                    """);
            db.update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(991011,991001,991011)");
            db.update("""
                    INSERT INTO pos_stock_locations
                    (tenant_id,branch_id,stock_location_id,location_code,display_name,active)
                    VALUES(991001,991011,991011,'POS_DEFAULT','P2 POS stock',TRUE)
                    """);
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
    void platformAuditIsAppendOnlyAndFinancialForeignKeysRestrictDeletion() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema, null).migrate();
            JdbcTemplate db = jdbc(url, user, password);

            assertThat(db.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.triggers
                    WHERE trigger_schema=? AND event_object_table='platform_audit_events'
                      AND trigger_name IN ('trg_platform_audit_no_update','trg_platform_audit_no_delete')
                    """, Integer.class, schema)).isEqualTo(2);

            db.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                db.update("""
                        INSERT INTO platform_audit_events(actor_username,action,resource_type,outcome)
                        VALUES('p2-missing-actor','P2_IMMUTABILITY_TEST','TEST','SUCCESS')
                        """);
            } finally {
                db.execute("SET FOREIGN_KEY_CHECKS=1");
            }
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

            var deleteRules = db.queryForList("""
                    SELECT CONCAT(table_name, ':', delete_rule)
                    FROM information_schema.referential_constraints
                    WHERE constraint_schema=?
                      AND referenced_table_name IN (
                        'orders','vendor_invoices','subscription_invoices','subscription_payments',
                        'tenant_subscriptions','pos_returns','pos_cash_refunds','inventory_ledger'
                      )
                    """, String.class, schema);
            assertThat(deleteRules).isNotEmpty();
            assertThat(deleteRules).allMatch(rule ->
                    rule.endsWith(":RESTRICT") || rule.endsWith(":NO ACTION"));
        });
    }

    @Test
    void mysqlUtcTimestampRoundTripPreservesTheExactInstant() throws Exception {
        withSchema((url, user, password, schema) -> {
            flyway(url, user, password, schema, null).migrate();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("SET time_zone = '+00:00'");
                statement.execute("CREATE TABLE p2_utc_timestamp_probe (id INT PRIMARY KEY, occurred_at TIMESTAMP(6) NOT NULL)");
                java.time.Instant expected = java.time.Instant.parse("2026-11-22T07:08:09.123456Z");
                try (var insert = connection.prepareStatement(
                        "INSERT INTO p2_utc_timestamp_probe(id,occurred_at) VALUES(1,?)")) {
                    insert.setTimestamp(1, java.sql.Timestamp.from(expected),
                            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
                    insert.executeUpdate();
                }
                try (var select = connection.prepareStatement(
                        "SELECT occurred_at FROM p2_utc_timestamp_probe WHERE id=1");
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
            flyway(url, user, password, schema, null).migrate();
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

    @Test
    void v48PreflightRejectsInvalidCrossTenantDataWithoutDeletingItOnMysql() throws Exception {
        withSchema((url, user, password, schema) -> {
            JdbcTemplate db = jdbc(url, user, password);
            db.execute("CREATE TABLE branches(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,PRIMARY KEY(tenant_id,id))");
            db.execute("CREATE TABLE pos_sessions(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,id VARCHAR(36) NOT NULL,PRIMARY KEY(tenant_id,branch_id,id))");
            db.execute("CREATE TABLE orders(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,cashier VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,id VARCHAR(36) NOT NULL,sales_channel VARCHAR(16) NOT NULL,status VARCHAR(24) NOT NULL,PRIMARY KEY(tenant_id,id))");
            db.execute("CREATE TABLE pos_drawer_tenders(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,session_id VARCHAR(36) NOT NULL,order_reference VARCHAR(36) NOT NULL)");
            db.execute("CREATE TABLE pos_active_drafts(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,cashier VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,order_reference VARCHAR(36) NOT NULL)");
            db.execute("CREATE TABLE tenant_subscriptions(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,PRIMARY KEY(tenant_id,id))");
            db.execute("CREATE TABLE subscription_events(tenant_id BIGINT NOT NULL,subscription_id BIGINT NOT NULL)");
            db.update("INSERT INTO branches VALUES(1,10),(2,20)");
            db.update("INSERT INTO pos_sessions VALUES(1,10,'session-a'),(2,20,'session-b')");
            db.update("INSERT INTO orders VALUES(1,10,'alice','session-a','order-a','IN_STORE','DRAFT'),(2,20,'bob','session-b','order-b','IN_STORE','DRAFT')");
            db.update("INSERT INTO tenant_subscriptions VALUES(1,100),(2,200)");
            db.update("INSERT INTO pos_drawer_tenders VALUES(1,10,'session-a','order-b')");
            db.update("INSERT INTO pos_active_drafts VALUES(1,10,'alice','session-a','order-b')");
            db.update("INSERT INTO subscription_events VALUES(1,200)");

            assertThatThrownBy(() -> {
                try (Connection connection = db.getDataSource().getConnection()) {
                    V48__tenant_child_ownership_constraints.validateExistingData(connection);
                }
            }).isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("pos_drawer_tenders=1")
                    .hasMessageContaining("pos_active_drafts=1")
                    .hasMessageContaining("subscription_events=1");

            assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders", Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts", Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_events", Integer.class)).isEqualTo(1);
        });
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

    private static Flyway flyway(String url, String user, String password, String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(false)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
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

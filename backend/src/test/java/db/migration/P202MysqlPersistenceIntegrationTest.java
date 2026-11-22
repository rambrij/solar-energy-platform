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
            db.update("INSERT INTO tenants(id,name,active) VALUES(990001,'P2-02 retained tenant',TRUE)");
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

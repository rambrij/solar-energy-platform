package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Adds database-enforced same-tenant ownership for POS projections and
 * platform subscription events. Existing data is validated before the first
 * DDL statement; invalid rows are never repaired or deleted automatically.
 */
public class V48__tenant_child_ownership_constraints extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        // All preconditions run before DDL. On MySQL, ALTER TABLE implicitly
        // commits, so discovering invalid data after the first ALTER is too late.
        validateExistingData(connection);

        ensureUnique(connection, "pos_sessions", "uk_pos_session_tenant_branch_id",
                "tenant_id,branch_id,id");
        ensureUnique(connection, "orders", "uk_orders_tenant_branch_session_id",
                "tenant_id,branch_id,session_id,id");
        ensureUnique(connection, "orders", "uk_orders_tenant_branch_cashier_session_id",
                "tenant_id,branch_id,cashier,session_id,id");
        ensureUnique(connection, "tenant_subscriptions", "uk_tenant_subscription_tenant_id",
                "tenant_id,id");

        ensureForeignKey(connection, "pos_drawer_tenders", "fk_pos_drawer_tender_branch",
                "FOREIGN KEY (tenant_id,branch_id) REFERENCES branches(tenant_id,id)");
        ensureForeignKey(connection, "pos_drawer_tenders", "fk_pos_drawer_tender_session",
                "FOREIGN KEY (tenant_id,branch_id,session_id) REFERENCES pos_sessions(tenant_id,branch_id,id)");
        ensureForeignKey(connection, "pos_drawer_tenders", "fk_pos_drawer_tender_order",
                "FOREIGN KEY (tenant_id,branch_id,session_id,order_reference) "
                        + "REFERENCES orders(tenant_id,branch_id,session_id,id)");
        ensureForeignKey(connection, "pos_active_drafts", "fk_pos_active_draft_order",
                "FOREIGN KEY (tenant_id,branch_id,cashier,session_id,order_reference) "
                        + "REFERENCES orders(tenant_id,branch_id,cashier,session_id,id)");
        ensureForeignKey(connection, "subscription_events", "fk_sub_event_subscription_owner",
                "FOREIGN KEY (tenant_id,subscription_id) REFERENCES tenant_subscriptions(tenant_id,id)");
    }

    static void validateExistingData(Connection connection) throws SQLException {
        long invalidDrawerRows = count(connection, """
                SELECT COUNT(*)
                FROM pos_drawer_tenders t
                WHERE NOT EXISTS (
                    SELECT 1 FROM branches b
                    WHERE b.tenant_id=t.tenant_id AND b.id=t.branch_id
                )
                OR NOT EXISTS (
                    SELECT 1 FROM pos_sessions s
                    WHERE s.tenant_id=t.tenant_id
                      AND s.branch_id=t.branch_id
                      AND s.id=t.session_id
                )
                OR NOT EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.tenant_id=t.tenant_id
                      AND o.branch_id=t.branch_id
                      AND o.session_id=t.session_id
                      AND o.id=t.order_reference
                      AND o.sales_channel='IN_STORE'
                )
                """);
        long invalidDraftRows = count(connection, """
                SELECT COUNT(*)
                FROM pos_active_drafts d
                WHERE NOT EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.tenant_id=d.tenant_id
                      AND o.branch_id=d.branch_id
                      AND o.cashier=d.cashier
                      AND o.session_id=d.session_id
                      AND o.id=d.order_reference
                      AND o.sales_channel='IN_STORE'
                      AND o.status='DRAFT'
                )
                """);
        long invalidSubscriptionEvents = count(connection, """
                SELECT COUNT(*)
                FROM subscription_events e
                WHERE NOT EXISTS (
                    SELECT 1 FROM tenant_subscriptions s
                    WHERE s.tenant_id=e.tenant_id
                      AND s.id=e.subscription_id
                )
                """);

        if (invalidDrawerRows != 0 || invalidDraftRows != 0 || invalidSubscriptionEvents != 0) {
            throw new SQLException(
                    "V48 tenant ownership migration aborted before making schema changes. "
                            + "Invalid existing rows: pos_drawer_tenders=" + invalidDrawerRows
                            + ", pos_active_drafts=" + invalidDraftRows
                            + ", subscription_events=" + invalidSubscriptionEvents
                            + ". Reconcile these rows from authoritative parent records, then retry. "
                            + "No rows were deleted or reassigned by this migration.");
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("V48 preflight query returned no count: " + sql);
            }
            return rows.getLong(1);
        }
    }

    private static void ensureUnique(Connection connection, String table, String name, String columns)
            throws SQLException {
        if (indexExists(connection, table, name)) {
            return;
        }
        execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " UNIQUE (" + columns + ")");
    }

    private static void ensureForeignKey(Connection connection, String table, String name, String definition)
            throws SQLException {
        if (foreignKeyExists(connection, table, name)) {
            return;
        }
        execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " " + definition);
    }

    private static boolean indexExists(Connection connection, String table, String indexName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String tableName : tableNames(table)) {
            try (ResultSet indexes = metadata.getIndexInfo(
                    connection.getCatalog(), connection.getSchema(), tableName, true, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean foreignKeyExists(Connection connection, String table, String foreignKeyName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String tableName : tableNames(table)) {
            try (ResultSet keys = metadata.getImportedKeys(
                    connection.getCatalog(), connection.getSchema(), tableName)) {
                while (keys.next()) {
                    if (foreignKeyName.equalsIgnoreCase(keys.getString("FK_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String[] tableNames(String table) {
        return new String[] {
                table,
                table.toUpperCase(Locale.ROOT),
                table.toLowerCase(Locale.ROOT)
        };
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Enforces append-only platform audit records in the production MySQL database.
 * H2 is used only as a fast compatibility test database; application code exposes
 * no audit update/delete operation and the MySQL integration run verifies the
 * database-level enforcement.
 */
public class V43__platform_audit_immutability extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (database.contains("mysql")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TRIGGER trg_platform_audit_no_update
                    BEFORE UPDATE ON platform_audit_events
                    FOR EACH ROW
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events are immutable'
                    """);
                statement.execute("""
                    CREATE TRIGGER trg_platform_audit_no_delete
                    BEFORE DELETE ON platform_audit_events
                    FOR EACH ROW
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events cannot be deleted'
                    """);
            }
            return;
        }
        if (database.contains("h2")) {
            return;
        }
        throw new SQLException("Unsupported database for platform audit immutability migration: " + database);
    }
}

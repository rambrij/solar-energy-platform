package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Adds PLATFORM_CONFIG_MANAGE without changing the already-applied V37 migration.
 *
 * V37 created an unnamed CHECK constraint. Resolve its database-generated name
 * from metadata so the migration works with both MySQL and the H2 MySQL-mode
 * test database.
 */
public class V44__platform_config_manage_permission extends BaseJavaMigration {

    private static final String TABLE_NAME = "platform_user_permissions";
    private static final String PERMISSION_CHECK = """
        permission IN (
            'PLATFORM_TENANT_CREATE',
            'PLATFORM_TENANT_UPDATE',
            'PLATFORM_TENANT_SUSPEND',
            'PLATFORM_TENANT_CLOSE',
            'PLATFORM_PLAN_MANAGE',
            'PLATFORM_SUBSCRIPTION_VIEW',
            'PLATFORM_SUBSCRIPTION_ADJUST',
            'PLATFORM_ANALYTICS_VIEW',
            'PLATFORM_AUDIT_VIEW',
            'PLATFORM_SUPPORT_ACCESS',
            'PLATFORM_CONFIG_MANAGE'
        )
        """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String database = connection.getMetaData()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean mysql = database.contains("mysql");
        boolean h2 = database.contains("h2");

        if (!mysql && !h2) {
            throw new SQLException("Unsupported database for platform permission migration: " + database);
        }

        List<String> checkConstraints = findCheckConstraints(connection, mysql);
        if (checkConstraints.size() != 1) {
            throw new SQLException(
                    "Expected exactly one CHECK constraint on " + TABLE_NAME
                            + " before expanding platform permissions, found " + checkConstraints.size());
        }

        String constraintName = quoteIdentifier(checkConstraints.get(0), mysql);
        String dropSql = mysql
                ? "ALTER TABLE " + TABLE_NAME + " DROP CHECK " + constraintName
                : "ALTER TABLE " + TABLE_NAME + " DROP CONSTRAINT " + constraintName;

        try (Statement statement = connection.createStatement()) {
            statement.execute(dropSql);
            statement.execute(
                    "ALTER TABLE " + TABLE_NAME
                            + " ADD CONSTRAINT chk_platform_user_permission CHECK ("
                            + PERMISSION_CHECK + ")");
        }
    }

    private static List<String> findCheckConstraints(Connection connection, boolean mysql) throws SQLException {
        String schemaExpression = mysql ? "DATABASE()" : "CURRENT_SCHEMA()";
        String tablePredicate = mysql
                ? "TABLE_NAME = '" + TABLE_NAME + "'"
                : "UPPER(TABLE_NAME) = '" + TABLE_NAME.toUpperCase(Locale.ROOT) + "'";
        String sql = """
            SELECT CONSTRAINT_NAME
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = %s
              AND %s
              AND CONSTRAINT_TYPE = 'CHECK'
            """.formatted(schemaExpression, tablePredicate);

        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                names.add(resultSet.getString("CONSTRAINT_NAME"));
            }
        }
        return names;
    }

    private static String quoteIdentifier(String identifier, boolean mysql) {
        if (mysql) {
            return "`" + identifier.replace("`", "``") + "`";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}

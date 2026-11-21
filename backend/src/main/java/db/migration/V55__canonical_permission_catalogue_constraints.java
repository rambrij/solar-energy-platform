package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Builds database grant constraints from the canonical permission resource. */
public class V55__canonical_permission_catalogue_constraints extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        JsonNode root;
        try (InputStream input = getClass().getResourceAsStream("/authorization-policy-registry.json")) {
            if (input == null) throw new IllegalStateException("Canonical permission catalogue resource is missing");
            root = new ObjectMapper().readTree(input);
        }
        Connection connection = context.getConnection();
        createCatalogue(connection, "vendor_permission_catalogue", root.path("vendorPermissions"));
        createCatalogue(connection, "platform_permission_catalogue", root.path("platformPermissions"));
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM vendor_role_permissions WHERE NOT EXISTS (SELECT 1 FROM vendor_permission_catalogue c WHERE c.permission=vendor_role_permissions.permission)");
            statement.executeUpdate("DELETE FROM platform_user_permissions WHERE NOT EXISTS (SELECT 1 FROM platform_permission_catalogue c WHERE c.permission=platform_user_permissions.permission)");
            statement.execute("ALTER TABLE vendor_role_permissions ADD CONSTRAINT fk_vrp_permission_catalog FOREIGN KEY(permission) REFERENCES vendor_permission_catalogue(permission)");
            statement.execute("ALTER TABLE platform_user_permissions ADD CONSTRAINT fk_pup_permission_catalog FOREIGN KEY(permission) REFERENCES platform_permission_catalogue(permission)");
        }
    }

    private static void createCatalogue(Connection connection, String table, JsonNode permissions) throws Exception {
        if (!permissions.isArray() || permissions.isEmpty()) throw new IllegalStateException("Missing active permission list for " + table);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + table + " (permission VARCHAR(80) NOT NULL PRIMARY KEY)");
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table + "(permission) VALUES(?)")) {
            for (JsonNode permission : permissions) {
                String value = permission.asText("");
                if (!value.matches("[A-Z][A-Z0-9_]{1,79}")) throw new IllegalStateException("Invalid permission identifier: " + value);
                insert.setString(1, value);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}

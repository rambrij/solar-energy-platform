package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Removes the pre-RBAC employee_permissions store and only the synthetic
 * per-employee roles created by V45 to copy those grants. Explicit vendor
 * roles and assignments (including vendor-admin-initial) are preserved.
 *
 * Employees whose only assignment was a V45 synthetic role become role-less
 * and are intentionally denied until an administrator assigns a real role.
 */
public class V49__remove_legacy_employee_permissions extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection=context.getConnection();
        removeLegacyData(connection);
    }


    static void removeLegacyData(Connection connection)throws SQLException{
        if(tableExists(connection,"employee_role_assignments")
                &&tableExists(connection,"vendor_roles")
                &&tableExists(connection,"vendor_role_permissions")){
            execute(connection,"""
                DELETE FROM employee_role_assignments a
                 WHERE EXISTS (
                   SELECT 1 FROM vendor_roles r
                    WHERE r.tenant_id=a.tenant_id AND r.id=a.role_id
                      AND r.role_key=CONCAT('legacy-',r.created_by)
                      AND r.display_name LIKE 'Migrated grants:%'
                 )
                """);
            execute(connection,"""
                DELETE FROM vendor_role_permissions p
                 WHERE EXISTS (
                   SELECT 1 FROM vendor_roles r
                    WHERE r.tenant_id=p.tenant_id AND r.id=p.role_id
                      AND r.role_key=CONCAT('legacy-',r.created_by)
                      AND r.display_name LIKE 'Migrated grants:%'
                 )
                """);
            execute(connection,"""
                DELETE FROM vendor_roles
                 WHERE role_key=CONCAT('legacy-',created_by)
                   AND display_name LIKE 'Migrated grants:%'
                """);
        }
        if(tableExists(connection,"employee_permissions"))execute(connection,"DROP TABLE employee_permissions");
    }

    private static boolean tableExists(Connection connection,String table)throws SQLException{
        DatabaseMetaData metadata=connection.getMetaData();
        try(ResultSet rows=metadata.getTables(connection.getCatalog(),null,table,new String[]{"TABLE"})){
            if(rows.next())return true;
        }
        try(ResultSet rows=metadata.getTables(connection.getCatalog(),null,table.toUpperCase(java.util.Locale.ROOT),new String[]{"TABLE"})){
            return rows.next();
        }
    }

    private static void execute(Connection connection,String sql)throws SQLException{
        try(var statement=connection.createStatement()){statement.executeUpdate(sql);}
    }
}

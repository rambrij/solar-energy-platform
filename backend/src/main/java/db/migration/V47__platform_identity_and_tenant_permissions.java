package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Expands the platform-only permission allow-list without editing released migrations. */
public class V47__platform_identity_and_tenant_permissions extends BaseJavaMigration {
    private static final String CHECK = """
        permission IN (
          'PLATFORM_TENANT_VIEW',
          'PLATFORM_TENANT_CREATE',
          'PLATFORM_TENANT_UPDATE',
          'PLATFORM_TENANT_REACTIVATE',
          'PLATFORM_TENANT_SUSPEND',
          'PLATFORM_TENANT_CLOSE',
          'PLATFORM_PLAN_MANAGE',
          'PLATFORM_SUBSCRIPTION_VIEW',
          'PLATFORM_SUBSCRIPTION_ADJUST',
          'PLATFORM_ANALYTICS_VIEW',
          'PLATFORM_AUDIT_VIEW',
          'PLATFORM_SUPPORT_ACCESS',
          'PLATFORM_CONFIG_MANAGE',
          'PLATFORM_IDENTITY_MANAGE'
        )
        """;
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        boolean mysql=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
        boolean h2=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        if(!mysql&&!h2)throw new SQLException("Unsupported database for platform identity grants");
        List<String> checks=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("""
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA=%s AND %s AND CONSTRAINT_TYPE='CHECK'
            """.formatted(mysql?"DATABASE()":"CURRENT_SCHEMA()",mysql?"TABLE_NAME='platform_user_permissions'":"UPPER(TABLE_NAME)='PLATFORM_USER_PERMISSIONS'"))){
            while(rs.next())checks.add(rs.getString(1));
        }
        if(checks.size()!=1)throw new SQLException("Expected one platform grants permission CHECK, found "+checks.size());
        String name=mysql?"`"+checks.getFirst().replace("`","``")+"`":"\""+checks.getFirst().replace("\"","\\"")+"\"";
        try(Statement s=c.createStatement()){
            s.execute("ALTER TABLE platform_user_permissions "+(mysql?"DROP CHECK ":"DROP CONSTRAINT ")+name);
            s.execute("ALTER TABLE platform_user_permissions ADD CONSTRAINT chk_platform_permissions_v47 CHECK ("+CHECK+")");
            s.execute("""
                INSERT INTO platform_user_permissions(username,permission)
                SELECT DISTINCT p.username,'PLATFORM_TENANT_VIEW'
                  FROM platform_user_permissions p
                 WHERE p.permission LIKE 'PLATFORM_%'
                   AND NOT EXISTS(SELECT 1 FROM platform_user_permissions x
                                   WHERE x.username=p.username AND x.permission='PLATFORM_TENANT_VIEW')
                """);
            s.execute("""
                INSERT INTO platform_user_permissions(username,permission)
                SELECT DISTINCT p.username,'PLATFORM_TENANT_REACTIVATE'
                  FROM platform_user_permissions p
                 WHERE p.permission='PLATFORM_TENANT_UPDATE'
                   AND NOT EXISTS(SELECT 1 FROM platform_user_permissions x
                                   WHERE x.username=p.username AND x.permission='PLATFORM_TENANT_REACTIVATE')
                """);
            s.execute("""
                INSERT INTO platform_user_permissions(username,permission)
                SELECT p.username,'PLATFORM_IDENTITY_MANAGE'
                  FROM platform_user_permissions p
                 WHERE p.permission='PLATFORM_TENANT_UPDATE'
                   AND EXISTS(SELECT 1 FROM platform_user_permissions a
                               WHERE a.username=p.username AND a.permission='PLATFORM_AUDIT_VIEW')
                   AND NOT EXISTS(SELECT 1 FROM platform_user_permissions x
                                   WHERE x.username=p.username AND x.permission='PLATFORM_IDENTITY_MANAGE')
                """);
        }
    }
}

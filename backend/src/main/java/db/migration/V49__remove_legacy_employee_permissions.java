package db.migration;

import com.saumya.pos.membership.StandardRoleTemplates;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Consolidates V45's synthetic per-employee roles into shared, tenant-owned
 * roles before removing the obsolete direct employee permission store.
 * Runtime authorization remains role-only; unsupported grants are excluded.
 */
public class V49__remove_legacy_employee_permissions extends BaseJavaMigration {
    private record Legacy(long tenant, long id, String creator) {}
    private record Assignment(long branch, String username, boolean active, String assignedBy,
                              Timestamp assignedAt, String revokedBy, Timestamp revokedAt) {}

    @Override public void migrate(Context context) throws Exception {
        removeLegacyData(context.getConnection());
    }

    static void removeLegacyData(Connection c) throws SQLException {
        if (tableExists(c,"employee_role_assignments") && tableExists(c,"vendor_roles")
                && tableExists(c,"vendor_role_permissions")) consolidate(c);
        if (tableExists(c,"employee_permissions")) execute(c,"DROP TABLE employee_permissions");
    }

    private static void consolidate(Connection c) throws SQLException {
        List<Legacy> legacy = new ArrayList<>();
        try (PreparedStatement p=c.prepareStatement("""
                SELECT tenant_id,id,created_by FROM vendor_roles
                WHERE role_key=CONCAT('legacy-',created_by)
                  AND display_name=CONCAT('Migrated grants: ',created_by)
                ORDER BY tenant_id,id
                """); ResultSet r=p.executeQuery()) {
            while(r.next()) legacy.add(new Legacy(r.getLong(1),r.getLong(2),r.getString(3)));
        }
        Map<String,Long> targets = new HashMap<>();
        for (Legacy old : legacy) {
            List<String> permissions = new ArrayList<>();
            try (PreparedStatement p=c.prepareStatement("""
                    SELECT permission FROM vendor_role_permissions
                    WHERE tenant_id=? AND role_id=? ORDER BY permission
                    """)) {
                p.setLong(1,old.tenant()); p.setLong(2,old.id());
                try(ResultSet r=p.executeQuery()) {
                    while(r.next()) if(StandardRoleTemplates.implemented().contains(r.getString(1)))
                        permissions.add(r.getString(1));
                }
            }
            List<Assignment> assignments = new ArrayList<>();
            try (PreparedStatement p=c.prepareStatement("""
                    SELECT branch_id,username,active,assigned_by,assigned_at,revoked_by,revoked_at
                    FROM employee_role_assignments WHERE tenant_id=? AND role_id=? ORDER BY username
                    """)) {
                p.setLong(1,old.tenant()); p.setLong(2,old.id());
                try(ResultSet r=p.executeQuery()) {
                    while(r.next()) assignments.add(new Assignment(r.getLong(1),r.getString(2),r.getBoolean(3),
                        r.getString(4),r.getTimestamp(5),r.getString(6),r.getTimestamp(7)));
                }
            }
            if(permissions.isEmpty() || assignments.isEmpty()) {
                deleteLegacy(c,old); continue;
            }
            String signature=String.join("\n",permissions);
            String key="migrated-"+sha256(signature).substring(0,20);
            Long target=targets.get(old.tenant()+":"+key);
            if(target==null) {
                target=ensureTarget(c,old,key,permissions);
                targets.put(old.tenant()+":"+key,target);
            }
            for(Assignment a:assignments) {
                boolean exists=false,active=false;
                try(PreparedStatement p=c.prepareStatement("""
                        SELECT active FROM employee_role_assignments
                        WHERE tenant_id=? AND username=? AND role_id=?
                        """)) {
                    p.setLong(1,old.tenant());p.setString(2,a.username());p.setLong(3,target);
                    try(ResultSet r=p.executeQuery()){if(r.next()){exists=true;active=r.getBoolean(1);}}
                }
                if(!exists) {
                    try(PreparedStatement p=c.prepareStatement("""
                            INSERT INTO employee_role_assignments
                            (tenant_id,branch_id,username,role_id,active,assigned_by,assigned_at,revoked_by,revoked_at)
                            VALUES(?,?,?,?,?,?,?,?,?)
                            """)) {
                        p.setLong(1,old.tenant());p.setLong(2,a.branch());p.setString(3,a.username());
                        p.setLong(4,target);p.setBoolean(5,a.active());p.setString(6,a.assignedBy());
                        p.setTimestamp(7,a.assignedAt());p.setString(8,a.revokedBy());p.setTimestamp(9,a.revokedAt());
                        p.executeUpdate();
                    }
                } else if(!active && a.active()) {
                    try(PreparedStatement p=c.prepareStatement("""
                            UPDATE employee_role_assignments SET active=TRUE,assigned_by=?,assigned_at=?,
                                revoked_by=NULL,revoked_at=NULL WHERE tenant_id=? AND username=? AND role_id=?
                            """)) {
                        p.setString(1,a.assignedBy());p.setTimestamp(2,a.assignedAt());
                        p.setLong(3,old.tenant());p.setString(4,a.username());p.setLong(5,target);p.executeUpdate();
                    }
                }
                if(!auditExists(c,old.tenant(),a.username(),target)) {
                    try(PreparedStatement p=c.prepareStatement("""
                            INSERT INTO vendor_access_audit_events
                            (tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason)
                            VALUES(?,?,?,'ROLE',?,'LEGACY_ROLE_CONSOLIDATED',
                            'Supported V45 vendor grants consolidated into a shared tenant-owned role; unsupported grants excluded')
                            """)) {
                        p.setLong(1,old.tenant());p.setString(2,a.assignedBy());p.setString(3,a.username());
                        p.setString(4,Long.toString(target));p.executeUpdate();
                    }
                }
            }
            deleteLegacy(c,old);
        }
    }

    private static long ensureTarget(Connection c,Legacy source,String key,List<String> permissions) throws SQLException {
        String name="Migrated vendor role "+key.substring("migrated-".length(), "migrated-".length()+10);
        Long id=null;
        try(PreparedStatement p=c.prepareStatement("SELECT id,display_name FROM vendor_roles WHERE tenant_id=? AND role_key=?")) {
            p.setLong(1,source.tenant());p.setString(2,key);
            try(ResultSet r=p.executeQuery()) {
                if(r.next()) {
                    id=r.getLong(1);
                    if(!name.equals(r.getString(2))) throw new SQLException("V49 migrated role key collides with an existing tenant role: "+key);
                }
            }
        }
        if(id==null) {
            try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
                    VALUES(?,?,?,TRUE,?,?)
                    """,Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1,source.tenant());p.setString(2,key);p.setString(3,name);
                p.setString(4,source.creator());p.setString(5,source.creator());p.executeUpdate();
                try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("V49 failed to create migrated tenant role");id=r.getLong(1);}
            }
        }
        List<String> persisted=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT permission FROM vendor_role_permissions WHERE tenant_id=? AND role_id=?")) {
            p.setLong(1,source.tenant());p.setLong(2,id);
            try(ResultSet r=p.executeQuery()){while(r.next())persisted.add(r.getString(1));}
        }
        if(!permissions.containsAll(persisted)) throw new SQLException("V49 target role contains unexpected grants: "+key);
        for(String permission:permissions) if(!persisted.contains(permission)) {
            try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)
                    """)) {
                p.setLong(1,source.tenant());p.setLong(2,id);p.setString(3,permission);p.setString(4,source.creator());p.executeUpdate();
            }
        }
        return id;
    }

    private static boolean auditExists(Connection c,long tenant,String username,long role) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=? AND subject_username=?
                AND resource_type='ROLE' AND resource_id=? AND action='LEGACY_ROLE_CONSOLIDATED'
                """)) {
            p.setLong(1,tenant);p.setString(2,username);p.setString(3,Long.toString(role));
            try(ResultSet r=p.executeQuery()){return r.next()&&r.getLong(1)>0;}
        }
    }

    private static void deleteLegacy(Connection c,Legacy old) throws SQLException {
        for(String table:List.of("employee_role_assignments","vendor_role_permissions","vendor_roles")) {
            try(PreparedStatement p=c.prepareStatement("DELETE FROM "+table+" WHERE tenant_id=? AND "+(table.equals("vendor_roles")?"id":"role_id")+"=?")) {
                p.setLong(1,old.tenant());p.setLong(2,old.id());p.executeUpdate();
            }
        }
    }
    private static String sha256(String value) throws SQLException {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new SQLException("SHA-256 unavailable",e);}
    }
    private static boolean tableExists(Connection c,String table) throws SQLException {
        DatabaseMetaData m=c.getMetaData();
        try(ResultSet r=m.getTables(c.getCatalog(),null,table,new String[]{"TABLE"})){if(r.next())return true;}
        try(ResultSet r=m.getTables(c.getCatalog(),null,table.toUpperCase(Locale.ROOT),new String[]{"TABLE"})){return r.next();}
    }
    private static void execute(Connection c,String sql) throws SQLException {
        try(Statement s=c.createStatement()){s.executeUpdate(sql);}
    }
}

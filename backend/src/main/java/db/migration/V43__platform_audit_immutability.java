package db.migration;

import java.sql.*;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** MySQL audit triggers are checked before creation so a partial DDL attempt can be retried safely. */
public class V43__platform_audit_immutability extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        String db=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if(db.contains("mysql")) {
            ensure(c,"trg_platform_audit_no_update","UPDATE","Platform audit events are immutable");
            ensure(c,"trg_platform_audit_no_delete","DELETE","Platform audit events cannot be deleted");
            return;
        }
        if(db.contains("h2")) return;
        throw new SQLException("Unsupported database for platform audit immutability migration: "+db);
    }
    private static void ensure(Connection c,String name,String event,String message) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                SELECT EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_STATEMENT
                FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?
                """)) {
            p.setString(1,name);
            try(ResultSet r=p.executeQuery()) {
                if(r.next()) {
                    if(!event.equalsIgnoreCase(r.getString(1))
                            || !"platform_audit_events".equalsIgnoreCase(r.getString(2))
                            || r.getString(3)==null || !r.getString(3).contains(message))
                        throw new SQLException("Existing trigger "+name+" does not match the required immutable-audit policy");
                    return;
                }
            }
        }
        try(Statement s=c.createStatement()) {
            s.execute("CREATE TRIGGER "+name+" BEFORE "+event+" ON platform_audit_events FOR EACH ROW "
                    +"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '"+message.replace("'","''")+"'");
        }
    }
}

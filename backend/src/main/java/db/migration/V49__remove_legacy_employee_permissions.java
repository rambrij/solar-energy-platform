package db.migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
/** Drops the retired direct employee-permission store; no grants are copied into roles. */
public class V49__remove_legacy_employee_permissions extends BaseJavaMigration {
 @Override public void migrate(Context context) throws Exception {
  Connection connection=context.getConnection();
  if(tableExists(connection,"employee_permissions"))try(var s=connection.createStatement()){s.executeUpdate("DROP TABLE employee_permissions");}
 }
 private static boolean tableExists(Connection c,String table)throws SQLException{
  DatabaseMetaData m=c.getMetaData();
  try(ResultSet r=m.getTables(c.getCatalog(),null,table,new String[]{"TABLE"})){if(r.next())return true;}
  try(ResultSet r=m.getTables(c.getCatalog(),null,table.toUpperCase(java.util.Locale.ROOT),new String[]{"TABLE"})){return r.next();}
 }
}

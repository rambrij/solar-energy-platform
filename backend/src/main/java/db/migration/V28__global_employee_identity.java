package db.migration;
import java.sql.*;
import org.flywaydb.core.api.migration.*;
public class V28__global_employee_identity extends BaseJavaMigration {
 public void migrate(Context context)throws Exception{
  var c=context.getConnection();long next;
  try(var s=c.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM users")){r.next();next=r.getLong(1);}
  try(var s=c.createStatement();var rows=s.executeQuery("SELECT username,password_hash,enabled FROM employees ORDER BY username")){
   while(rows.next()){
    String username=rows.getString(1);
    try(var find=c.prepareStatement("SELECT password_hash FROM users WHERE username=?")){find.setString(1,username);try(var found=find.executeQuery()){if(found.next())throw new SQLException("Resolve duplicated global employee/customer username before identity migration");}}
    try(var insert=c.prepareStatement("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,?)")){insert.setLong(1,next++);insert.setString(2,username);insert.setString(3,rows.getString(2));insert.setBoolean(4,rows.getBoolean(3));insert.executeUpdate();}
   }
  }
  run(c,"ALTER TABLE employees DROP COLUMN password_hash");
  run(c,"ALTER TABLE employees ADD CONSTRAINT fk_employee_global_identity FOREIGN KEY(username) REFERENCES users(username)");
  run(c,"ALTER TABLE employee_permissions ADD tenant_id BIGINT NULL");
  run(c,"UPDATE employee_permissions SET tenant_id=(SELECT tenant_id FROM employees e WHERE e.username=employee_permissions.username)");
  boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
  run(c,"ALTER TABLE employee_permissions "+(mysql?"MODIFY COLUMN tenant_id BIGINT NOT NULL":"ALTER COLUMN tenant_id SET NOT NULL"));
  run(c,"ALTER TABLE employees ADD CONSTRAINT uk_employee_tenant_username UNIQUE(tenant_id,username)");
  run(c,"ALTER TABLE employee_permissions ADD CONSTRAINT fk_permission_membership FOREIGN KEY(tenant_id,username) REFERENCES employees(tenant_id,username)");
  run(c,"ALTER TABLE employee_permissions ADD CONSTRAINT chk_vendor_permission CHECK(permission NOT LIKE 'PLATFORM_%')");
  run(c,"CREATE TABLE identity_sequence (id INT PRIMARY KEY,next_id BIGINT NOT NULL)");
  run(c,"INSERT INTO identity_sequence VALUES(1,"+next+")");
  run(c,"CREATE TABLE identity_refresh_tokens (token_hash VARCHAR(64) PRIMARY KEY,username VARCHAR(100) NOT NULL,family VARCHAR(36) NOT NULL,expires_at TIMESTAMP NOT NULL,used_at TIMESTAMP NULL,revoked BOOLEAN NOT NULL DEFAULT FALSE,FOREIGN KEY(username) REFERENCES users(username))");
  run(c,"CREATE INDEX ix_refresh_family ON identity_refresh_tokens(family,revoked)");
 }
 private void run(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}

package db.migration;
import java.sql.*;
import org.flywaydb.core.api.migration.*;
public class V25__sale_discount_policy extends BaseJavaMigration {
 public void migrate(Context context)throws Exception {
  var c=context.getConnection();boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
  run(c,"CREATE TABLE catalog_discount_policy (  tenant_id BIGINT PRIMARY KEY,  maximum_percent DECIMAL(5,2) NOT NULL CHECK(maximum_percent>=0 AND maximum_percent<=100),  FOREIGN KEY(tenant_id) REFERENCES tenants(id) )");
  run(c,"ALTER TABLE orders ADD discount_reason VARCHAR(250) NULL");
  run(c,"ALTER TABLE order_items ADD manual_discount DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK(manual_discount>=0)");
  run(c,"ALTER TABLE order_items DROP "+(mysql?"CHECK ":"CONSTRAINT ")+"chk_order_discount");
  run(c,"ALTER TABLE order_items ADD CONSTRAINT chk_order_discount CHECK(list_price>=price AND discount>=0 AND discount=(list_price-price)*quantity+manual_discount AND manual_discount<=price*quantity AND discount_percent>=0 AND discount_percent<=100)");
 }
 private void run(Connection c,String sql)throws SQLException{try(var statement=c.createStatement()){statement.execute(sql);}}
}

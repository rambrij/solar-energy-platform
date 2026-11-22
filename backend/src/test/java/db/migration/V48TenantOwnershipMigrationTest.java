package db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V48TenantOwnershipMigrationTest {

    private JdbcTemplate database() {
        String url = "jdbc:h2:mem:v48-preflight-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var source = new DriverManagerDataSource(url, "sa", "");
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE branches(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,PRIMARY KEY(tenant_id,id))");
        db.execute("CREATE TABLE pos_sessions(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,id VARCHAR(36) NOT NULL,PRIMARY KEY(tenant_id,branch_id,id))");
        db.execute("CREATE TABLE orders(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,cashier VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,id VARCHAR(36) NOT NULL,sales_channel VARCHAR(16) NOT NULL,status VARCHAR(24) NOT NULL,PRIMARY KEY(tenant_id,id))");
        db.execute("CREATE TABLE pos_drawer_tenders(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,session_id VARCHAR(36) NOT NULL,order_reference VARCHAR(36) NOT NULL)");
        db.execute("CREATE TABLE pos_active_drafts(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,cashier VARCHAR(100) NOT NULL,session_id VARCHAR(36) NOT NULL,order_reference VARCHAR(36) NOT NULL)");
        db.execute("CREATE TABLE tenant_subscriptions(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,PRIMARY KEY(tenant_id,id))");
        db.execute("CREATE TABLE subscription_events(tenant_id BIGINT NOT NULL,subscription_id BIGINT NOT NULL)");
        return db;
    }

    @Test
    void migrationPreflightRejectsOrphanAndCrossTenantRowsWithoutDeletingThem() {
        JdbcTemplate db = database();
        db.update("INSERT INTO branches VALUES(1,10),(2,20)");
        db.update("INSERT INTO pos_sessions VALUES(1,10,'session-a'),(2,20,'session-b')");
        db.update("INSERT INTO orders VALUES(1,10,'alice','session-a','order-a','IN_STORE','DRAFT'),(2,20,'bob','session-b','order-b','IN_STORE','DRAFT')");
        db.update("INSERT INTO tenant_subscriptions VALUES(1,100),(2,200)");
        db.update("INSERT INTO pos_drawer_tenders VALUES(1,10,'session-a','order-b')");
        db.update("INSERT INTO pos_active_drafts VALUES(1,10,'alice','session-a','order-b')");
        db.update("INSERT INTO subscription_events VALUES(1,200)");

        assertThatThrownBy(() -> {
            try (var connection = db.getDataSource().getConnection()) {
                V48__tenant_child_ownership_constraints.validateExistingData(connection);
            }
        })
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("pos_drawer_tenders=1")
                .hasMessageContaining("pos_active_drafts=1")
                .hasMessageContaining("subscription_events=1");

        assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void migrationPreflightAcceptsValidSameTenantOwnership() throws Exception {
        JdbcTemplate db = database();
        db.update("INSERT INTO branches VALUES(1,10)");
        db.update("INSERT INTO pos_sessions VALUES(1,10,'session-a')");
        db.update("INSERT INTO orders VALUES(1,10,'alice','session-a','order-a','IN_STORE','DRAFT')");
        db.update("INSERT INTO tenant_subscriptions VALUES(1,100)");
        db.update("INSERT INTO pos_drawer_tenders VALUES(1,10,'session-a','order-a')");
        db.update("INSERT INTO pos_active_drafts VALUES(1,10,'alice','session-a','order-a')");
        db.update("INSERT INTO subscription_events VALUES(1,100)");

        try (var connection = db.getDataSource().getConnection()) {
            V48__tenant_child_ownership_constraints.validateExistingData(connection);
        }

        assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_events", Integer.class)).isEqualTo(1);
    }
}

package com.saumya.pos;

import com.saumya.pos.inventory.WarehouseInventoryService;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the warehouse transfer state machine against a real relational schema. */
class WarehouseInventoryServiceLifecycleTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private WarehouseInventoryService service;

    private final AtomicReference<AuthorizationContext.Context> currentActor = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        String database = "warehouse_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        schema();
        seed();

        AuthorizationContext authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenAnswer(invocation -> currentActor.get());
        currentActor.set(actor("alice"));
        service = new WarehouseInventoryService(jdbc, authorization, Clock.systemUTC(),
            mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test
    void createsApprovesDispatchesAndPartiallyThenFullyReceivesWithoutDuplicatingAReceipt() {
        var warehouse = tx.execute(status ->
            service.createWarehouse("main", "Main Warehouse", "Approved storage location"));
        assertThat(warehouse).isNotNull();
        assertThat(warehouse.code()).isEqualTo("MAIN");
        assertThat(warehouse.branchId()).isEqualTo(10L);

        var draft = tx.execute(status -> service.createTransfer("transfer-key-0001",
            "BRANCH", null, "WAREHOUSE", warehouse.id(), 10, 7, 4, "Replenishment"));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
            "SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=10 AND product_id=7",
            Integer.class)).isEqualTo(10);

        var duplicateDraft = tx.execute(status -> service.createTransfer("transfer-key-0001",
            "BRANCH", null, "WAREHOUSE", warehouse.id(), 10, 7, 4, "Replenishment"));
        assertThat(duplicateDraft.id()).isEqualTo(draft.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfers", Integer.class)).isEqualTo(1);

        // Maker/checker: the creator cannot approve their own draft.
        assertThatThrownBy(() -> tx.execute(status ->
            service.approve(draft.id(), draft.version(), "Independent approval")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        currentActor.set(actor("bob"));
        var approved = tx.execute(status ->
            service.approve(draft.id(), draft.version(), "Stock and destination checked"));
        assertThat(approved.status()).isEqualTo("APPROVED");

        var dispatched = tx.execute(status ->
            service.dispatch(draft.id(), approved.version(), "Sent to warehouse"));
        assertThat(dispatched.status()).isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject(
            "SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=10 AND product_id=7",
            Integer.class)).isEqualTo(6);

        var partial = tx.execute(status -> service.receive(draft.id(), dispatched.version(),
            2, false, "receipt-key-0001", "First delivery received"));
        assertThat(partial.status()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(partial.receivedQuantity()).isEqualTo(2);

        // The same receipt command is safe to retry even with its original optimistic version.
        var retried = tx.execute(status -> service.receive(draft.id(), dispatched.version(),
            2, false, "receipt-key-0001", "First delivery received"));
        assertThat(retried.receivedQuantity()).isEqualTo(2);
        assertThat(retried.version()).isEqualTo(partial.version());

        var completed = tx.execute(status -> service.receive(draft.id(), partial.version(),
            2, true, "receipt-key-0002", "Remaining delivery received"));
        assertThat(completed.status()).isEqualTo("RECEIVED");
        assertThat(completed.receivedQuantity()).isEqualTo(4);

        assertThat(jdbc.queryForObject(
            "SELECT on_hand FROM warehouse_inventory WHERE tenant_id=1 AND warehouse_id=? AND product_id=7",
            Integer.class, warehouse.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory_transfer_events WHERE tenant_id=1 AND transfer_id=?",
            Integer.class, draft.id())).isEqualTo(5);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory_transfer_movements WHERE tenant_id=1 AND transfer_id=?",
            Integer.class, draft.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM warehouse_audit_events WHERE tenant_id=1 AND warehouse_id=?",
            Integer.class, warehouse.id())).isEqualTo(1);

        // A warehouse with stock must remain active until the stock is transferred or reconciled.
        assertThatThrownBy(() -> tx.execute(status ->
            service.updateWarehouse(warehouse.id(), warehouse.version(), "Main Warehouse",
                false, "Attempted closure with stock")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(error -> assertThat(
                ((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(409));
        assertThat(jdbc.queryForObject(
            "SELECT active FROM warehouses WHERE tenant_id=1 AND id=?",
            Boolean.class, warehouse.id())).isTrue();
    }

    @Test
    void reservesDispatchesAndReceivesSerialsWithFinalDiscrepancy() {
        var warehouse=tx.execute(status->service.createWarehouse("serial","Serial Warehouse","Serial test"));
        jdbc.update("UPDATE products SET serial_tracking_required=TRUE WHERE tenant_id=1 AND id=7");
        jdbc.update("""
            INSERT INTO inventory_receipt_serial_assets(id,tenant_id,branch_id,receipt_id,product_id,serial_number,recorded_by,recorded_at)
            VALUES(101,1,10,'receipt-101',7,'SN-001','alice',CURRENT_TIMESTAMP),
                  (102,1,10,'receipt-101',7,'SN-002','alice',CURRENT_TIMESTAMP),
                  (103,1,10,'receipt-101',7,'SN-003','alice',CURRENT_TIMESTAMP)
            """);
        for(long id:new long[]{101,102,103})jdbc.update("""
            INSERT INTO inventory_serial_asset_positions(tenant_id,serial_asset_id,product_id,location_type,
              branch_id,warehouse_id,status,reserved_transfer_id,in_transit_transfer_id,version,updated_at)
            VALUES(?,?,7,'BRANCH',10,NULL,'AVAILABLE',NULL,NULL,0,CURRENT_TIMESTAMP)
            """,1,id);
        var draft=tx.execute(status->service.createTransfer("serial-transfer-01","BRANCH",null,
            "WAREHOUSE",warehouse.id(),10,7,3,"Serialized replenishment",List.of("sn-001","SN-002","SN-003")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfer_serial_assets WHERE transfer_id=? AND state='RESERVED'",
            Integer.class,draft.id())).isEqualTo(3);
        assertThatThrownBy(()->tx.execute(status->service.createTransfer("serial-transfer-02","BRANCH",null,
            "WAREHOUSE",warehouse.id(),10,7,1,"Competing reservation",List.of("SN-001"))))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        currentActor.set(actor("bob"));
        var approved=tx.execute(status->service.approve(draft.id(),draft.version(),"Independent approval"));
        var dispatched=tx.execute(status->service.dispatch(draft.id(),approved.version(),"Serials dispatched"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_serial_asset_positions WHERE status='IN_TRANSIT' AND in_transit_transfer_id=?",
            Integer.class,draft.id())).isEqualTo(3);
        var partial=tx.execute(status->service.receive(draft.id(),dispatched.version(),1,false,
            "serial-receipt-01","One unit arrived",List.of("SN-001")));
        assertThat(partial.status()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_serial_asset_positions WHERE serial_asset_id=101",
            String.class)).isEqualTo("AVAILABLE");
        var retried=tx.execute(status->service.receive(draft.id(),dispatched.version(),1,false,
            "serial-receipt-01","One unit arrived",List.of("sn-001")));
        assertThat(retried.version()).isEqualTo(partial.version());
        var finalReceipt=tx.execute(status->service.receive(draft.id(),partial.version(),1,true,
            "serial-receipt-02","Final receipt: one unit short",List.of("SN-002")));
        assertThat(finalReceipt.status()).isEqualTo("RECEIVED_WITH_DISCREPANCY");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_serial_asset_positions WHERE serial_asset_id=102",
            String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_serial_asset_positions WHERE serial_asset_id=103",
            String.class)).isEqualTo("DISCREPANCY");
    }

    @Test
    void rejectsCrossTenantDestinationBeforeCreatingATransfer() {
        jdbc.update("INSERT INTO tenants(id) VALUES(2)");
        jdbc.update("INSERT INTO branches(id,tenant_id) VALUES(90,2)");

        assertThatThrownBy(() -> tx.execute(status -> service.createTransfer("cross-tenant-001",
            "BRANCH", null, "BRANCH", null, 90, 7, 1, "Must not cross tenant")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(error -> assertThat(
                ((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfers", Integer.class)).isZero();
    }

    private static AuthorizationContext.Context actor(String username) {
        return new AuthorizationContext.Context(username, 1L, 10L, "1:" + username,
            Set.of("INVENTORY_VIEW", "INVENTORY_TRANSFER"), false, false, true);
    }

    private void schema() {
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE branches(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,UNIQUE(tenant_id,id))");
        jdbc.execute("CREATE TABLE pos_stock_locations(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,active BOOLEAN NOT NULL,PRIMARY KEY(tenant_id,branch_id))");
        jdbc.execute("CREATE TABLE products(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,sku VARCHAR(80),name VARCHAR(160),serial_tracking_required BOOLEAN NOT NULL DEFAULT FALSE,PRIMARY KEY(tenant_id,id))");
        jdbc.execute("CREATE TABLE subscription_plan_versions(plan_id BIGINT NOT NULL,version INT NOT NULL,max_warehouses INT NOT NULL,PRIMARY KEY(plan_id,version))");
        jdbc.execute("CREATE TABLE tenant_subscriptions(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,plan_id BIGINT NOT NULL,plan_version INT NOT NULL,created_at TIMESTAMP NOT NULL)");
        jdbc.execute("""
            CREATE TABLE warehouses(
              id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,
              code VARCHAR(40) NOT NULL,name VARCHAR(160) NOT NULL,active BOOLEAN NOT NULL,
              created_by VARCHAR(100) NOT NULL,updated_by VARCHAR(100) NOT NULL,
              created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
              UNIQUE(tenant_id,branch_id,code))
            """);
        jdbc.execute("CREATE TABLE warehouse_inventory(tenant_id BIGINT NOT NULL,warehouse_id BIGINT NOT NULL,product_id BIGINT NOT NULL,on_hand INT NOT NULL,reserved INT NOT NULL,damaged INT NOT NULL,version BIGINT NOT NULL,PRIMARY KEY(tenant_id,warehouse_id,product_id))");
        jdbc.execute("CREATE TABLE inventory(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,product_id BIGINT NOT NULL,quantity INT NOT NULL,reserved INT NOT NULL,PRIMARY KEY(tenant_id,branch_id,product_id))");
        jdbc.execute("""
            CREATE TABLE inventory_transfers(
              id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,command_key VARCHAR(80) NOT NULL,
              source_type VARCHAR(12) NOT NULL,source_warehouse_id BIGINT,source_branch_id BIGINT NOT NULL,
              destination_type VARCHAR(12) NOT NULL,destination_warehouse_id BIGINT,destination_branch_id BIGINT NOT NULL,
              product_id BIGINT NOT NULL,quantity INT NOT NULL,received_quantity INT NOT NULL,status VARCHAR(28) NOT NULL,
              reason VARCHAR(500) NOT NULL,created_by VARCHAR(100) NOT NULL,approved_by VARCHAR(100),
              dispatched_by VARCHAR(100),completed_by VARCHAR(100),created_at TIMESTAMP(6) NOT NULL,
              approved_at TIMESTAMP(6),dispatched_at TIMESTAMP(6),completed_at TIMESTAMP(6),version BIGINT NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE inventory_transfer_events(
              id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,transfer_id VARCHAR(36) NOT NULL,
              event_type VARCHAR(32) NOT NULL,actor_username VARCHAR(100) NOT NULL,quantity INT,is_final BOOLEAN,
              command_key VARCHAR(80),detail VARCHAR(500) NOT NULL,occurred_at TIMESTAMP(6) NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE inventory_transfer_movements(
              id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,transfer_id VARCHAR(36) NOT NULL,
              location_type VARCHAR(12) NOT NULL,warehouse_id BIGINT,branch_id BIGINT NOT NULL,
              product_id BIGINT NOT NULL,movement_type VARCHAR(16) NOT NULL,quantity_delta INT NOT NULL,
              movement_key VARCHAR(100) NOT NULL,actor_username VARCHAR(100) NOT NULL,occurred_at TIMESTAMP(6) NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE inventory_receipt_serial_assets(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,receipt_id VARCHAR(36) NOT NULL,product_id BIGINT NOT NULL,serial_number VARCHAR(120) NOT NULL,recorded_by VARCHAR(100) NOT NULL,recorded_at TIMESTAMP(6) NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE inventory_serial_asset_positions(tenant_id BIGINT NOT NULL,serial_asset_id BIGINT NOT NULL,product_id BIGINT NOT NULL,location_type VARCHAR(16) NOT NULL,branch_id BIGINT NOT NULL,warehouse_id BIGINT,status VARCHAR(16) NOT NULL,reserved_transfer_id VARCHAR(36),in_transit_transfer_id VARCHAR(36),version BIGINT NOT NULL,updated_at TIMESTAMP(6) NOT NULL,PRIMARY KEY(tenant_id,serial_asset_id))
            """);
        jdbc.execute("""
            CREATE TABLE inventory_transfer_serial_assets(tenant_id BIGINT NOT NULL,transfer_id VARCHAR(36) NOT NULL,receipt_serial_asset_id BIGINT NOT NULL,product_id BIGINT NOT NULL,serial_number VARCHAR(120) NOT NULL,state VARCHAR(16) NOT NULL,receipt_command_key VARCHAR(80),PRIMARY KEY(tenant_id,transfer_id,receipt_serial_asset_id))
            """);
        jdbc.execute("""
            CREATE TABLE inventory_serial_asset_movements(id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,serial_asset_id BIGINT NOT NULL,product_id BIGINT NOT NULL,transfer_id VARCHAR(36),from_location_type VARCHAR(16),from_branch_id BIGINT,from_warehouse_id BIGINT,to_location_type VARCHAR(16) NOT NULL,to_branch_id BIGINT NOT NULL,to_warehouse_id BIGINT,movement_type VARCHAR(32) NOT NULL,movement_key VARCHAR(140) NOT NULL,actor_username VARCHAR(100) NOT NULL,detail VARCHAR(500) NOT NULL,occurred_at TIMESTAMP(6) NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE warehouse_audit_events(
              id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,warehouse_id BIGINT NOT NULL,
              actor_username VARCHAR(100) NOT NULL,action VARCHAR(24) NOT NULL,reason VARCHAR(500) NOT NULL,
              before_state VARCHAR(1000),after_state VARCHAR(1000) NOT NULL,occurred_at TIMESTAMP(6) NOT NULL)
            """);
    }

    private void seed() {
        jdbc.update("INSERT INTO tenants(id) VALUES(1)");
        jdbc.update("INSERT INTO branches(id,tenant_id) VALUES(10,1)");
        jdbc.update("INSERT INTO pos_stock_locations(tenant_id,branch_id,active) VALUES(1,10,TRUE)");
        jdbc.update("INSERT INTO products(tenant_id,id,sku,name,serial_tracking_required) VALUES(1,7,'PANEL-7','Solar Panel',FALSE)");
        jdbc.update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved) VALUES(1,10,7,10,0)");
        jdbc.update("INSERT INTO subscription_plan_versions(plan_id,version,max_warehouses) VALUES(1,1,3)");
        jdbc.update("INSERT INTO tenant_subscriptions(id,tenant_id,plan_id,plan_version,created_at) VALUES(1,1,1,1,CURRENT_TIMESTAMP)");
    }
}

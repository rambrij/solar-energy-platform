package com.saumya.pos.inventory;

import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Tenant-scoped warehouse and stock-transfer application boundary.
 * Branch stock remains in the existing POS inventory table; warehouse stock is separate.
 */
@Service
public class WarehouseInventoryService {
    private final JdbcTemplate jdbc;
    private final AuthorizationContext authorization;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WarehouseInventoryService(JdbcTemplate jdbc, AuthorizationContext authorization,
            Clock clock, ApplicationEventPublisher events) {
        this.jdbc=jdbc; this.authorization=authorization; this.clock=clock; this.events=events;
    }

    public record WarehouseView(long id,long branchId,String code,String name,boolean active,long version) {}
    public record TransferView(String id,String sourceType,Long sourceWarehouseId,long sourceBranchId,
        String destinationType,Long destinationWarehouseId,long destinationBranchId,long productId,
        int quantity,int receivedQuantity,String status,String reason,String createdBy,String approvedBy,
        String dispatchedBy,String completedBy,Instant createdAt,Instant dispatchedAt,Instant completedAt,long version) {}
    public record TransferEventView(String id,String eventType,String actorUsername,Integer quantity,
        Boolean finalReceipt,String detail,Instant occurredAt) {}
    public record TransferDetail(TransferView transfer,List<TransferEventView> events) {}

    private AuthorizationContext.Context actor(String permission) {
        var context=authorization.current();
        context.requireVendorMembership();
        context.requirePermission(permission);
        if(context.tenantId()==null||context.branchId()==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active tenant and branch assignment required");
        return context;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<WarehouseView> warehouses() {
        var a=actor("INVENTORY_VIEW");
        return jdbc.query("""
            SELECT id,branch_id,code,name,active,version FROM warehouses
            WHERE tenant_id=? AND branch_id=? ORDER BY code,id
            """,(rs,n)->new WarehouseView(rs.getLong("id"),rs.getLong("branch_id"),
            rs.getString("code"),rs.getString("name"),rs.getBoolean("active"),rs.getLong("version")),
            a.tenantId(),a.branchId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseView createWarehouse(String code,String name,String reason) {
        var a=actor("INVENTORY_TRANSFER");
        String normalizedCode=required(code,40,"Warehouse code").toUpperCase(Locale.ROOT);
        if(!normalizedCode.matches("[A-Z0-9][A-Z0-9_-]{1,39}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse code must use 2-40 uppercase letters, digits, _ or -");
        String normalizedName=required(name,160,"Warehouse name");
        String why=required(reason,500,"Reason");
        lockTenant(a.tenantId());
        jdbc.update("""
            INSERT INTO warehouses(tenant_id,branch_id,code,name,active,created_by,updated_by,created_at,updated_at,version)
            VALUES(?,?,?,?,TRUE,?,?,?,?,0)
            """,a.tenantId(),a.branchId(),normalizedCode,normalizedName,a.username(),a.username(),
            clock.instant(),clock.instant());
        long id=jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND branch_id=? AND code=?",
            Long.class,a.tenantId(),a.branchId(),normalizedCode);
        auditWarehouse(a,id,"CREATED",why,null,normalizedName);
        return warehouse(a,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseView updateWarehouse(long id,long expectedVersion,String name,Boolean active,String reason) {
        var a=actor("INVENTORY_TRANSFER");
        if(id<1||expectedVersion<0||active==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse, version and active state are required");
        String normalizedName=required(name,160,"Warehouse name");
        String why=required(reason,500,"Reason");
        lockTenant(a.tenantId());
        Map<String,Object> before=warehouseRow(a,id);
        if(((Number)before.get("version")).longValue()!=expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Warehouse changed; reload before saving");
        if(!active) {
            Integer openTransfers=jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_transfers
                WHERE tenant_id=? AND status IN ('DRAFT','APPROVED','IN_TRANSIT','PARTIALLY_RECEIVED')
                  AND (source_warehouse_id=? OR destination_warehouse_id=?)
                """,Integer.class,a.tenantId(),id,id);
            if(openTransfers!=null&&openTransfers>0)
                conflict("Warehouse has an open transfer and cannot be deactivated");
        }
        jdbc.update("""
            UPDATE warehouses SET name=?,active=?,updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND branch_id=? AND id=? AND version=?
            """,normalizedName,active,a.username(),clock.instant(),a.tenantId(),a.branchId(),id,expectedVersion);
        String action=Boolean.TRUE.equals(before.get("active"))&&!active?"DEACTIVATED":
            !Boolean.TRUE.equals(before.get("active"))&&active?"REACTIVATED":"UPDATED";
        auditWarehouse(a,id,action,why,before.get("name")+":"+before.get("active"),normalizedName+":"+active);
        return warehouse(a,id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<TransferView> transfers() {
        var a=actor("INVENTORY_VIEW");
        return jdbc.query("""
            SELECT * FROM inventory_transfers
            WHERE tenant_id=? AND (source_branch_id=? OR destination_branch_id=?)
            ORDER BY created_at DESC,id DESC LIMIT 200
            """,(rs,n)->transfer(rs),a.tenantId(),a.branchId(),a.branchId());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public TransferDetail transfer(String id) {
        var a=actor("INVENTORY_VIEW");
        Map<String,Object> row=transferRow(a,id);
        requireVisibleBranch(a,row);
        TransferView view=jdbc.queryForObject("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",
            (rs,n)->transfer(rs),a.tenantId(),id);
        List<TransferEventView> history=jdbc.query("""
            SELECT id,event_type,actor_username,quantity,is_final,detail,occurred_at
            FROM inventory_transfer_events WHERE tenant_id=? AND transfer_id=?
            ORDER BY occurred_at,id
            """,(rs,n)->new TransferEventView(rs.getString("id"),rs.getString("event_type"),
            rs.getString("actor_username"),(Integer)rs.getObject("quantity"),
            (Boolean)rs.getObject("is_final"),rs.getString("detail"),
            rs.getTimestamp("occurred_at").toInstant()),a.tenantId(),id);
        return new TransferDetail(view,history);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView createTransfer(String key,String sourceType,Long sourceWarehouseId,
            String destinationType,Long destinationWarehouseId,long destinationBranchId,
            long productId,int quantity,String reason) {
        var a=actor("INVENTORY_TRANSFER");
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}")||productId<1||quantity<1||quantity>1000000
            ||destinationBranchId<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid idempotency key, product, quantity and destination are required");
        String source=locationType(sourceType),destination=locationType(destinationType);
        if(("WAREHOUSE".equals(source)!=(sourceWarehouseId!=null))
            ||("WAREHOUSE".equals(destination)!=(destinationWarehouseId!=null)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse IDs must match location types");
        String why=required(reason,500,"Reason");
        lockTenant(a.tenantId());
        requireBranch(a.tenantId(),destinationBranchId);
        if("WAREHOUSE".equals(source))requireWarehouse(a,sourceWarehouseId,a.branchId(),true);
        if("WAREHOUSE".equals(destination))requireWarehouse(a,destinationWarehouseId,destinationBranchId,true);
        if("BRANCH".equals(destination))requirePosLocation(a.tenantId(),destinationBranchId);
        if("BRANCH".equals(source))requirePosLocation(a.tenantId(),a.branchId());
        if("WAREHOUSE".equals(source)&&"WAREHOUSE".equals(destination)
            &&sourceWarehouseId.equals(destinationWarehouseId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination must differ");
        if("BRANCH".equals(destination)&&destinationBranchId==a.branchId()&&"BRANCH".equals(source))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination must differ");
        Integer productCount=jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=? AND id=?",Integer.class,a.tenantId(),productId);
        if(productCount==null||productCount!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product unavailable");
        var previous=jdbc.query("""
            SELECT * FROM inventory_transfers WHERE tenant_id=? AND source_branch_id=? AND command_key=? FOR UPDATE
            """,(rs,n)->transfer(rs),a.tenantId(),a.branchId(),key);
        if(!previous.isEmpty()){
            TransferView old=previous.getFirst();
            if(old.productId()!=productId||old.quantity()!=quantity||!old.sourceType().equals(source)
                ||!java.util.Objects.equals(old.sourceWarehouseId(),sourceWarehouseId)
                ||!old.destinationType().equals(destination)
                ||!java.util.Objects.equals(old.destinationWarehouseId(),destinationWarehouseId)
                ||old.destinationBranchId()!=destinationBranchId||!old.reason().equals(why))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key identifies a different transfer");
            return old;
        }
        String id=UUID.randomUUID().toString(); Instant now=clock.instant();
        jdbc.update("""
            INSERT INTO inventory_transfers(id,tenant_id,command_key,source_type,source_warehouse_id,source_branch_id,
              destination_type,destination_warehouse_id,destination_branch_id,product_id,quantity,received_quantity,
              status,reason,created_by,created_at,version)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,0,'DRAFT',?,?,?,0)
            """,id,a.tenantId(),key,source,sourceWarehouseId,a.branchId(),destination,destinationWarehouseId,
            destinationBranchId,productId,quantity,why,a.username(),now);
        appendEvent(a,id,"CREATED",null,null,key,why);
        return transferById(a,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView approve(String id,long expectedVersion,String reason) {
        var a=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason"); lockTenant(a.tenantId());
        Map<String,Object> row=transferRow(a,id); requireSourceBranch(a,row);
        if(!"DRAFT".equals(row.get("status")))conflict("Only draft transfers can be approved");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before approval");
        if(a.username().equals(row.get("created_by")))conflict("A different authorized employee must approve the transfer");
        jdbc.update("UPDATE inventory_transfers SET status='APPROVED',approved_by=?,approved_at=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            a.username(),clock.instant(),a.tenantId(),id,expectedVersion);
        appendEvent(a,id,"APPROVED",null,null,null,why); return transferById(a,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView dispatch(String id,long expectedVersion,String reason) {
        var a=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason"); lockTenant(a.tenantId());
        Map<String,Object> row=transferRow(a,id); requireSourceBranch(a,row);
        if(!"APPROVED".equals(row.get("status")))conflict("Only approved transfers can be dispatched");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before dispatch");
        int qty=((Number)row.get("quantity")).intValue(); long product=((Number)row.get("product_id")).longValue();
        if("BRANCH".equals(row.get("source_type")))debitBranch(a.tenantId(),((Number)row.get("source_branch_id")).longValue(),product,qty);
        else debitWarehouse(a.tenantId(),((Number)row.get("source_warehouse_id")).longValue(),product,qty);
        Instant now=clock.instant();
        jdbc.update("UPDATE inventory_transfers SET status='IN_TRANSIT',dispatched_by=?,dispatched_at=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            a.username(),now,a.tenantId(),id,expectedVersion);
        appendEvent(a,id,"DISPATCHED",qty,null,null,why);
        return transferById(a,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView receive(String id,long expectedVersion,int quantity,boolean finalReceipt,String key,String reason) {
        var a=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason");
        if(quantity<1||quantity>1000000||key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid receipt quantity and idempotency key are required");
        lockTenant(a.tenantId());
        Map<String,Object> row=transferRow(a,id); requireDestinationBranch(a,row);
        var prior=jdbc.query("SELECT quantity,is_final,detail FROM inventory_transfer_events WHERE tenant_id=? AND transfer_id=? AND command_key=?",
            (rs,n)->new Object[]{rs.getInt(1),rs.getBoolean(2),rs.getString(3)},a.tenantId(),id,key);
        if(!prior.isEmpty()){
            Object[] saved=prior.getFirst();
            if(((Number)saved[0]).intValue()!=quantity||((Boolean)saved[1])!=finalReceipt||!why.equals(saved[2]))
                conflict("Receipt key identifies a different command");
            return transferById(a,id);
        }
        String status=String.valueOf(row.get("status"));
        if(!List.of("IN_TRANSIT","PARTIALLY_RECEIVED").contains(status))conflict("Transfer is not awaiting receipt");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before receipt");
        int total=((Number)row.get("quantity")).intValue(),received=((Number)row.get("received_quantity")).intValue();
        if(quantity>total-received)conflict("Receipt exceeds outstanding transfer quantity");
        long product=((Number)row.get("product_id")).longValue();
        if("BRANCH".equals(row.get("destination_type")))creditBranch(a.tenantId(),a.branchId(),product,quantity);
        else creditWarehouse(a.tenantId(),((Number)row.get("destination_warehouse_id")).longValue(),product,quantity);
        int newReceived=received+quantity;
        boolean complete=finalReceipt||newReceived==total;
        boolean discrepancy=complete&&newReceived<total;
        String newStatus=discrepancy?"RECEIVED_WITH_DISCREPANCY":
            newReceived==total?"RECEIVED":"PARTIALLY_RECEIVED";
        Instant now=clock.instant();
        jdbc.update("""
            UPDATE inventory_transfers SET received_quantity=?,status=?,
              completed_by=CASE WHEN ? THEN ? ELSE completed_by END,
              completed_at=CASE WHEN ? THEN ? ELSE completed_at END,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,newReceived,newStatus,complete,a.username(),complete,complete?now:null,
            a.tenantId(),id,expectedVersion);
        appendEvent(a,id,discrepancy?"RECEIVED_WITH_DISCREPANCY":"RECEIVED",quantity,complete,key,why);
        return transferById(a,id);
    }

    private void debitBranch(long tenant,long branch,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,tenant,branch);
        requirePosLocation(tenant,branch);
        var rows=jdbc.query("SELECT quantity,reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},tenant,branch,product);
        if(rows.isEmpty()||rows.getFirst()[0]-rows.getFirst()[1]<quantity)conflict("Insufficient available branch stock");
        jdbc.update("UPDATE inventory SET quantity=quantity-? WHERE tenant_id=? AND branch_id=? AND product_id=?",
            quantity,tenant,branch,product);
        events.publishEvent(new StockAvailabilityChanged(tenant,branch,product,rows.getFirst()[0]-rows.getFirst()[1]-quantity));
    }
    private void debitWarehouse(long tenant,long warehouse,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND id=? AND active=TRUE FOR UPDATE",Long.class,tenant,warehouse);
        var rows=jdbc.query("SELECT on_hand,reserved,damaged FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2),rs.getInt(3)},tenant,warehouse,product);
        if(rows.isEmpty()||rows.getFirst()[0]-rows.getFirst()[1]-rows.getFirst()[2]<quantity)conflict("Insufficient available warehouse stock");
        jdbc.update("UPDATE warehouse_inventory SET on_hand=on_hand-?,version=version+1 WHERE tenant_id=? AND warehouse_id=? AND product_id=?",
            quantity,tenant,warehouse,product);
    }
    private void creditBranch(long tenant,long branch,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,tenant,branch);
        requirePosLocation(tenant,branch);
        jdbc.update("""
            INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved)
            SELECT ?,?,?,0,0 WHERE NOT EXISTS(
              SELECT 1 FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?)
            """,tenant,branch,product,tenant,branch,product);
        var rows=jdbc.query("SELECT quantity,reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},tenant,branch,product);
        if(rows.isEmpty()||rows.getFirst()[0]>Integer.MAX_VALUE-quantity)conflict("Branch stock capacity exceeded");
        jdbc.update("UPDATE inventory SET quantity=quantity+? WHERE tenant_id=? AND branch_id=? AND product_id=?",
            quantity,tenant,branch,product);
        events.publishEvent(new StockAvailabilityChanged(tenant,branch,product,rows.getFirst()[0]-rows.getFirst()[1]+quantity));
    }
    private void creditWarehouse(long tenant,long warehouse,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND id=? AND active=TRUE FOR UPDATE",Long.class,tenant,warehouse);
        jdbc.update("""
            INSERT INTO warehouse_inventory(tenant_id,warehouse_id,product_id,on_hand,reserved,damaged,version)
            SELECT ?,?,?,0,0,0,0 WHERE NOT EXISTS(
              SELECT 1 FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=?)
            """,tenant,warehouse,product,tenant,warehouse,product);
        var rows=jdbc.query("SELECT on_hand FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=? FOR UPDATE",
            (rs,n)->rs.getInt(1),tenant,warehouse,product);
        if(rows.isEmpty()||rows.getFirst()>Integer.MAX_VALUE-quantity)conflict("Warehouse stock capacity exceeded");
        jdbc.update("UPDATE warehouse_inventory SET on_hand=on_hand+?,version=version+1 WHERE tenant_id=? AND warehouse_id=? AND product_id=?",
            quantity,tenant,warehouse,product);
    }

    private void lockTenant(long tenant) {
        jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",Long.class,tenant);
    }
    private void requireBranch(long tenant,long branch) {
        if(jdbc.query("SELECT id FROM branches WHERE tenant_id=? AND id=?",(rs,n)->rs.getLong(1),tenant,branch).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Destination branch unavailable");
    }
    private void requirePosLocation(long tenant,long branch) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM pos_stock_locations WHERE tenant_id=? AND branch_id=? AND active=TRUE",Integer.class,tenant,branch);
        if(count==null||count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch has no active designated POS stock location");
    }
    private void requireWarehouse(AuthorizationContext.Context a,Long id,long branch,boolean active) {
        if(id==null||jdbc.query("""
            SELECT id FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=? AND (?=FALSE OR active=TRUE)
            """,(rs,n)->rs.getLong(1),a.tenantId(),branch,id,active).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse unavailable");
    }
    private Map<String,Object> warehouseRow(AuthorizationContext.Context a,long id) {
        var rows=jdbc.queryForList("SELECT * FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",a.tenantId(),a.branchId(),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse unavailable");
        return rows.getFirst();
    }
    private WarehouseView warehouse(AuthorizationContext.Context a,long id) {
        return jdbc.queryForObject("SELECT id,branch_id,code,name,active,version FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=?",
            (rs,n)->new WarehouseView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getLong(6)),
            a.tenantId(),a.branchId(),id);
    }
    private void auditWarehouse(AuthorizationContext.Context a,long id,String action,String reason,Object before,Object after) {
        jdbc.update("""
            INSERT INTO warehouse_audit_events(id,tenant_id,warehouse_id,actor_username,action,reason,before_state,after_state,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),a.tenantId(),id,a.username(),action,reason,before==null?null:String.valueOf(before),
            String.valueOf(after),clock.instant());
    }
    private void appendEvent(AuthorizationContext.Context a,String id,String type,Integer quantity,Boolean finalReceipt,String key,String detail) {
        jdbc.update("""
            INSERT INTO inventory_transfer_events(id,tenant_id,transfer_id,event_type,actor_username,quantity,is_final,command_key,detail,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),a.tenantId(),id,type,a.username(),quantity,finalReceipt,key,detail,clock.instant());
    }
    private Map<String,Object> transferRow(AuthorizationContext.Context a,String id) {
        if(id==null||id.length()>36)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid transfer ID required");
        var rows=jdbc.queryForList("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",a.tenantId(),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
        return rows.getFirst();
    }
    private void requireVisibleBranch(AuthorizationContext.Context a,Map<String,Object> row) {
        if(!Long.valueOf(a.branchId()).equals(((Number)row.get("source_branch_id")).longValue())
            &&!Long.valueOf(a.branchId()).equals(((Number)row.get("destination_branch_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private void requireSourceBranch(AuthorizationContext.Context a,Map<String,Object> row) {
        if(((Number)row.get("source_branch_id")).longValue()!=a.branchId())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private void requireDestinationBranch(AuthorizationContext.Context a,Map<String,Object> row) {
        if(((Number)row.get("destination_branch_id")).longValue()!=a.branchId())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private TransferView transferById(AuthorizationContext.Context a,String id) {
        return jdbc.queryForObject("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",
            (rs,n)->transfer(rs),a.tenantId(),id);
    }
    private TransferView transfer(java.sql.ResultSet rs) throws java.sql.SQLException {
        Number sourceWarehouse=(Number)rs.getObject("source_warehouse_id");
        Number destinationWarehouse=(Number)rs.getObject("destination_warehouse_id");
        return new TransferView(rs.getString("id"),rs.getString("source_type"),
            sourceWarehouse==null?null:sourceWarehouse.longValue(),rs.getLong("source_branch_id"),
            rs.getString("destination_type"),destinationWarehouse==null?null:destinationWarehouse.longValue(),
            rs.getLong("destination_branch_id"),rs.getLong("product_id"),rs.getInt("quantity"),rs.getInt("received_quantity"),
            rs.getString("status"),rs.getString("reason"),rs.getString("created_by"),rs.getString("approved_by"),
            rs.getString("dispatched_by"),rs.getString("completed_by"),rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("dispatched_at")==null?null:rs.getTimestamp("dispatched_at").toInstant(),
            rs.getTimestamp("completed_at")==null?null:rs.getTimestamp("completed_at").toInstant(),rs.getLong("version"));
    }
    private String locationType(String value) {
        String type=value==null?"":value.strip().toUpperCase(Locale.ROOT);
        if(!List.of("BRANCH","WAREHOUSE").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Location type must be BRANCH or WAREHOUSE");
        return type;
    }
    private String required(String value,int max,String label) {
        String v=value==null?"":value.strip();
        if(v.isBlank()||v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" must contain 1 to "+max+" characters");
        return v;
    }
    private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}

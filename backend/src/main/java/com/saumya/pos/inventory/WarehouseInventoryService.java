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
    public record WarehouseStockView(long productId,String sku,String productName,int onHand,int reserved,int damaged,int available,long version) {}
    public record TransferView(String id,String sourceType,Long sourceWarehouseId,long sourceBranchId,
        String destinationType,Long destinationWarehouseId,long destinationBranchId,long productId,
        int quantity,int receivedQuantity,String status,String reason,String createdBy,String approvedBy,
        String dispatchedBy,String completedBy,Instant createdAt,Instant dispatchedAt,Instant completedAt,long version) {}
    public record TransferEventView(String id,String eventType,String actorUsername,Integer quantity,
        Boolean finalReceipt,String detail,Instant occurredAt) {}
    public record TransferMovementView(String id,String locationType,Long warehouseId,long branchId,long productId,
        String movementType,int quantityDelta,String movementKey,String actorUsername,Instant occurredAt) {}
    public record TransferSerialAssetView(long receiptSerialAssetId,String serialNumber,String status,String receiptCommandKey) {}
    public record TransferDetail(TransferView transfer,List<TransferEventView> events,
        List<TransferMovementView> movements,List<TransferSerialAssetView> serialAssets) {}

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
        var context=actor("INVENTORY_VIEW");
        return jdbc.query("""
            SELECT id,branch_id,code,name,active,version FROM warehouses
            WHERE tenant_id=? AND branch_id=? ORDER BY code,id
            """,(rs,n)->new WarehouseView(rs.getLong("id"),rs.getLong("branch_id"),
            rs.getString("code"),rs.getString("name"),rs.getBoolean("active"),rs.getLong("version")),
            context.tenantId(),context.branchId());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<WarehouseStockView> warehouseStock(long warehouseId) {
        var context=actor("INVENTORY_VIEW");
        if(warehouseId<1||jdbc.query("""
            SELECT id FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=?
            """,(rs,n)->rs.getLong(1),context.tenantId(),context.branchId(),warehouseId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse unavailable");
        return jdbc.query("""
            SELECT wi.product_id,p.sku,p.name,wi.on_hand,wi.reserved,wi.damaged,
              wi.on_hand-wi.reserved-wi.damaged AS available,wi.version
            FROM warehouse_inventory wi
            JOIN products p ON p.tenant_id=wi.tenant_id AND p.id=wi.product_id
            WHERE wi.tenant_id=? AND wi.warehouse_id=?
            ORDER BY p.name,wi.product_id
            """,(rs,n)->new WarehouseStockView(rs.getLong("product_id"),rs.getString("sku"),
            rs.getString("name"),rs.getInt("on_hand"),rs.getInt("reserved"),rs.getInt("damaged"),
            rs.getInt("available"),rs.getLong("version")),context.tenantId(),warehouseId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseView createWarehouse(String code,String name,String reason) {
        var context=actor("INVENTORY_TRANSFER");
        String normalizedCode=required(code,40,"Warehouse code").toUpperCase(Locale.ROOT);
        if(!normalizedCode.matches("[A-Z0-9][A-Z0-9_-]{1,39}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse code must use 2-40 uppercase letters, digits, _ or -");
        String normalizedName=required(name,160,"Warehouse name");
        String why=required(reason,500,"Reason");
        lockTenant(context);
        requireWarehouseCapacity(context);
        jdbc.update("""
            INSERT INTO warehouses(tenant_id,branch_id,code,name,active,created_by,updated_by,created_at,updated_at,version)
            VALUES(?,?,?,?,TRUE,?,?,?,?,0)
            """,context.tenantId(),context.branchId(),normalizedCode,normalizedName,context.username(),context.username(),
            java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));
        long id=jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND branch_id=? AND code=?",
            Long.class,context.tenantId(),context.branchId(),normalizedCode);
        auditWarehouse(context,id,"CREATED",why,null,normalizedName);
        return warehouse(context,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseView updateWarehouse(long id,long expectedVersion,String name,Boolean active,String reason) {
        var context=actor("INVENTORY_TRANSFER");
        if(id<1||expectedVersion<0||active==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse, version and active state are required");
        String normalizedName=required(name,160,"Warehouse name");
        String why=required(reason,500,"Reason");
        lockTenant(context);
        Map<String,Object> before=warehouseRow(context,id);
        if(((Number)before.get("version")).longValue()!=expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Warehouse changed; reload before saving");
        if(!active) {
            Integer openTransfers=jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_transfers
                WHERE tenant_id=? AND status IN ('DRAFT','APPROVED','IN_TRANSIT','PARTIALLY_RECEIVED')
                  AND (source_warehouse_id=? OR destination_warehouse_id=?)
                """,Integer.class,context.tenantId(),id,id);
            if(openTransfers!=null&&openTransfers>0)
                conflict("Warehouse has an open transfer and cannot be deactivated");
            Integer stockRows=jdbc.queryForObject("""
                SELECT COUNT(*) FROM warehouse_inventory
                WHERE tenant_id=? AND warehouse_id=? AND on_hand>0
                """,Integer.class,context.tenantId(),id);
            if(stockRows!=null&&stockRows>0)
                conflict("Warehouse with on-hand stock cannot be deactivated; transfer or reconcile stock first");
        }
        jdbc.update("""
            UPDATE warehouses SET name=?,active=?,updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND branch_id=? AND id=? AND version=?
            """,normalizedName,active,context.username(),java.sql.Timestamp.from(clock.instant()),context.tenantId(),context.branchId(),id,expectedVersion);
        String action=Boolean.TRUE.equals(before.get("active"))&&!active?"DEACTIVATED":
            !Boolean.TRUE.equals(before.get("active"))&&active?"REACTIVATED":"UPDATED";
        auditWarehouse(context,id,action,why,before.get("name")+":"+before.get("active"),normalizedName+":"+active);
        return warehouse(context,id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<TransferView> transfers() {
        var context=actor("INVENTORY_VIEW");
        return jdbc.query("""
            SELECT * FROM inventory_transfers
            WHERE tenant_id=? AND (source_branch_id=? OR destination_branch_id=?)
            ORDER BY created_at DESC,id DESC LIMIT 200
            """,(rs,n)->transfer(rs),context.tenantId(),context.branchId(),context.branchId());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public TransferDetail transfer(String id) {
        var context=actor("INVENTORY_VIEW");
        Map<String,Object> row=transferRow(context,id);
        requireVisibleBranch(context,row);
        TransferView view=jdbc.queryForObject("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",
            (rs,n)->transfer(rs),context.tenantId(),id);
        List<TransferEventView> history=jdbc.query("""
            SELECT id,event_type,actor_username,quantity,is_final,detail,occurred_at
            FROM inventory_transfer_events WHERE tenant_id=? AND transfer_id=?
            ORDER BY occurred_at,id
            """,(rs,n)->new TransferEventView(rs.getString("id"),rs.getString("event_type"),
            rs.getString("actor_username"),(Integer)rs.getObject("quantity"),
            (Boolean)rs.getObject("is_final"),rs.getString("detail"),
            rs.getTimestamp("occurred_at").toInstant()),context.tenantId(),id);
        List<TransferMovementView> movements=jdbc.query("""
            SELECT id,location_type,warehouse_id,branch_id,product_id,movement_type,quantity_delta,
              movement_key,actor_username,occurred_at
            FROM inventory_transfer_movements WHERE tenant_id=? AND transfer_id=?
            ORDER BY occurred_at,id
            """,(rs,n)->{
                Number warehouse=(Number)rs.getObject("warehouse_id");
                return new TransferMovementView(rs.getString("id"),rs.getString("location_type"),
                    warehouse==null?null:warehouse.longValue(),rs.getLong("branch_id"),rs.getLong("product_id"),
                    rs.getString("movement_type"),rs.getInt("quantity_delta"),rs.getString("movement_key"),
                    rs.getString("actor_username"),rs.getTimestamp("occurred_at").toInstant());
            },context.tenantId(),id);
        List<TransferSerialAssetView> serialAssets=jdbc.query("""
            SELECT receipt_serial_asset_id,serial_number,state,receipt_command_key
            FROM inventory_transfer_serial_assets WHERE tenant_id=? AND transfer_id=?
            ORDER BY serial_number
            """,(rs,n)->new TransferSerialAssetView(rs.getLong("receipt_serial_asset_id"),
            rs.getString("serial_number"),rs.getString("state"),rs.getString("receipt_command_key")),
            context.tenantId(),id);
        return new TransferDetail(view,history,movements,serialAssets);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView createTransfer(String key,String sourceType,Long sourceWarehouseId,
            String destinationType,Long destinationWarehouseId,long destinationBranchId,
            long productId,int quantity,String reason) {
        return createTransfer(key,sourceType,sourceWarehouseId,destinationType,destinationWarehouseId,
            destinationBranchId,productId,quantity,reason,List.of());
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView createTransfer(String key,String sourceType,Long sourceWarehouseId,
            String destinationType,Long destinationWarehouseId,long destinationBranchId,
            long productId,int quantity,String reason,List<String> serialNumbers) {
        var context=actor("INVENTORY_TRANSFER");
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}")||productId<1||quantity<1||quantity>1000000
            ||destinationBranchId<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid idempotency key, product, quantity and destination are required");
        String source=locationType(sourceType),destination=locationType(destinationType);
        if(("WAREHOUSE".equals(source)!=(sourceWarehouseId!=null))
            ||("WAREHOUSE".equals(destination)!=(destinationWarehouseId!=null)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouse IDs must match location types");
        String why=required(reason,500,"Reason");
        List<String> serials=normalizeSerialNumbers(serialNumbers);
        lockTenant(context);
        requireBranch(context,destinationBranchId);
        if("WAREHOUSE".equals(source))requireWarehouse(context,sourceWarehouseId,context.branchId(),true);
        if("WAREHOUSE".equals(destination))requireWarehouse(context,destinationWarehouseId,destinationBranchId,true);
        if("BRANCH".equals(destination))requirePosLocation(context,destinationBranchId);
        if("BRANCH".equals(source))requirePosLocation(context,context.branchId());
        if("WAREHOUSE".equals(source)&&"WAREHOUSE".equals(destination)
            &&sourceWarehouseId.equals(destinationWarehouseId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination must differ");
        if("BRANCH".equals(destination)&&destinationBranchId==context.branchId()&&"BRANCH".equals(source))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination must differ");
        Integer productCount=jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=? AND id=?",
            Integer.class,context.tenantId(),productId);
        if(productCount==null||productCount!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product unavailable");
        Integer tracking=jdbc.queryForObject(
            "SELECT CASE WHEN serial_tracking_required=TRUE THEN 1 ELSE 0 END FROM products WHERE tenant_id=? AND id=?",
            Integer.class,context.tenantId(),productId);
        boolean tracked=tracking!=null&&tracking==1;
        if(tracked&&(quantity>100||serials.size()!=quantity))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A serial-tracked transfer requires exactly one serial number per unit (maximum 100)");
        if(!tracked&&!serials.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Serial numbers are not enabled for this product");
        var previous=jdbc.query("""
            SELECT * FROM inventory_transfers WHERE tenant_id=? AND source_branch_id=? AND command_key=? FOR UPDATE
            """,(rs,n)->transfer(rs),context.tenantId(),context.branchId(),key);
        if(!previous.isEmpty()){
            TransferView old=previous.getFirst();
            if(old.productId()!=productId||old.quantity()!=quantity||!old.sourceType().equals(source)
                ||!java.util.Objects.equals(old.sourceWarehouseId(),sourceWarehouseId)
                ||!old.destinationType().equals(destination)
                ||!java.util.Objects.equals(old.destinationWarehouseId(),destinationWarehouseId)
                ||old.destinationBranchId()!=destinationBranchId||!old.reason().equals(why)
                ||!serialNumbersForTransfer(context.tenantId(),old.id()).equals(serials))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key identifies a different transfer");
            return old;
        }
        String id=UUID.randomUUID().toString(); java.sql.Timestamp now=java.sql.Timestamp.from(clock.instant());
        jdbc.update("""
            INSERT INTO inventory_transfers(id,tenant_id,command_key,source_type,source_warehouse_id,source_branch_id,
              destination_type,destination_warehouse_id,destination_branch_id,product_id,quantity,received_quantity,
              status,reason,created_by,created_at,version)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,0,'DRAFT',?,?,?,0)
            """,id,context.tenantId(),key,source,sourceWarehouseId,context.branchId(),destination,destinationWarehouseId,
            destinationBranchId,productId,quantity,why,context.username(),now);
        if(tracked)reserveSerialAssets(context,id,source,sourceWarehouseId,context.branchId(),productId,serials,why);
        appendEvent(context,id,"CREATED",null,null,null,why);
        return transferById(context,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView approve(String id,long expectedVersion,String reason) {
        var context=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason"); lockTenant(context);
        Map<String,Object> row=transferRow(context,id); requireSourceBranch(context,row);
        if(!"DRAFT".equals(row.get("status")))conflict("Only draft transfers can be approved");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before approval");
        if(context.username().equals(row.get("created_by")))conflict("A different authorized employee must approve the transfer");
        jdbc.update("UPDATE inventory_transfers SET status='APPROVED',approved_by=?,approved_at=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            context.username(),java.sql.Timestamp.from(clock.instant()),context.tenantId(),id,expectedVersion);
        appendEvent(context,id,"APPROVED",null,null,null,why); return transferById(context,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView cancel(String id,long expectedVersion,String reason) {
        var context=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason"); lockTenant(context);
        Map<String,Object> row=transferRow(context,id); requireSourceBranch(context,row);
        String status=String.valueOf(row.get("status"));
        if(!List.of("DRAFT","APPROVED").contains(status))
            conflict("Only undispatched transfers can be cancelled");
        if(((Number)row.get("version")).longValue()!=expectedVersion)
            conflict("Transfer changed; reload before cancellation");
        if("DRAFT".equals(status)&&!context.username().equals(row.get("created_by")))
            conflict("Only the creator may cancel a draft transfer");
        releaseReservedSerialAssets(context,id,why);
        jdbc.update("UPDATE inventory_transfers SET status='CANCELLED',version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            context.tenantId(),id,expectedVersion);
        appendEvent(context,id,"CANCELLED",null,null,null,why);
        return transferById(context,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView dispatch(String id,long expectedVersion,String reason) {
        var context=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason"); lockTenant(context);
        Map<String,Object> row=transferRow(context,id); requireSourceBranch(context,row);
        if(!"APPROVED".equals(row.get("status")))conflict("Only approved transfers can be dispatched");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before dispatch");
        int qty=((Number)row.get("quantity")).intValue(); long product=((Number)row.get("product_id")).longValue();
        long sourceBranch=((Number)row.get("source_branch_id")).longValue();
        String sourceType=String.valueOf(row.get("source_type"));
        Number sourceWarehouse=(Number)row.get("source_warehouse_id");
        if("BRANCH".equals(sourceType))debitBranch(context,sourceBranch,product,qty);
        else debitWarehouse(context,sourceWarehouse.longValue(),product,qty);
        recordMovement(context,id,sourceType,sourceWarehouse==null?null:sourceWarehouse.longValue(),
            sourceBranch,product,-qty,"DISPATCH_OUT","DISPATCH:"+id);
        dispatchSerialAssets(context,id);
        java.sql.Timestamp now=java.sql.Timestamp.from(clock.instant());
        jdbc.update("UPDATE inventory_transfers SET status='IN_TRANSIT',dispatched_by=?,dispatched_at=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            context.username(),now,context.tenantId(),id,expectedVersion);
        appendEvent(context,id,"DISPATCHED",qty,null,null,why);
        return transferById(context,id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView receive(String id,long expectedVersion,int quantity,boolean finalReceipt,String key,String reason) {
        return receive(id,expectedVersion,quantity,finalReceipt,key,reason,List.of());
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public TransferView receive(String id,long expectedVersion,int quantity,boolean finalReceipt,String key,
            String reason,List<String> serialNumbers) {
        var context=actor("INVENTORY_TRANSFER"); String why=required(reason,500,"Reason");
        if(quantity<1||quantity>1000000||key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid receipt quantity and idempotency key are required");
        List<String> serials=normalizeSerialNumbers(serialNumbers);
        lockTenant(context);
        Map<String,Object> row=transferRow(context,id); requireDestinationBranch(context,row);
        var prior=jdbc.query("SELECT quantity,is_final,detail FROM inventory_transfer_events WHERE tenant_id=? AND transfer_id=? AND command_key=?",
            (rs,n)->new Object[]{rs.getInt(1),rs.getBoolean(2),rs.getString(3)},context.tenantId(),id,key);
        if(!prior.isEmpty()){
            Object[] saved=prior.getFirst();
            if(((Number)saved[0]).intValue()!=quantity||((Boolean)saved[1])!=finalReceipt||!why.equals(saved[2])
                ||!serialNumbersForReceipt(context.tenantId(),id,key).equals(serials))
                conflict("Receipt key identifies a different command");
            return transferById(context,id);
        }
        String status=String.valueOf(row.get("status"));
        if(!List.of("IN_TRANSIT","PARTIALLY_RECEIVED").contains(status))conflict("Transfer is not awaiting receipt");
        if(((Number)row.get("version")).longValue()!=expectedVersion)conflict("Transfer changed; reload before receipt");
        int total=((Number)row.get("quantity")).intValue(),received=((Number)row.get("received_quantity")).intValue();
        if(quantity>total-received)conflict("Receipt exceeds outstanding transfer quantity");
        long product=((Number)row.get("product_id")).longValue();
        List<Map<String,Object>> planned=jdbc.queryForList("""
            SELECT receipt_serial_asset_id,serial_number,state FROM inventory_transfer_serial_assets
            WHERE tenant_id=? AND transfer_id=? ORDER BY serial_number FOR UPDATE
            """,context.tenantId(),id);
        boolean tracked=!planned.isEmpty();
        if(tracked&&serials.size()!=quantity)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A serial-tracked transfer receipt requires exactly one serial number per received unit");
        if(!tracked&&!serials.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Serial numbers are not enabled for this transfer");
        if(tracked){
            for(String serial:serials){
                var asset=planned.stream().filter(a->serial.equals(a.get("serial_number"))).findFirst();
                if(asset.isEmpty()||!"IN_TRANSIT".equals(asset.get().get("state")))
                    conflict("Every received serial must belong to this transfer and still be in transit");
            }
        }
        String destinationType=String.valueOf(row.get("destination_type"));
        Number destinationWarehouse=(Number)row.get("destination_warehouse_id");
        long destinationBranch=((Number)row.get("destination_branch_id")).longValue();
        if("BRANCH".equals(destinationType))creditBranch(context,destinationBranch,product,quantity);
        else creditWarehouse(context,destinationWarehouse.longValue(),product,quantity);
        recordMovement(context,id,destinationType,destinationWarehouse==null?null:destinationWarehouse.longValue(),
            destinationBranch,product,quantity,"RECEIPT_IN","RECEIPT:"+key);
        if(tracked)receiveSerialAssets(context,id,serials,key,why,destinationType,destinationWarehouse,destinationBranch);
        int newReceived=received+quantity;
        boolean complete=finalReceipt||newReceived==total;
        boolean discrepancy=complete&&newReceived<total;
        String newStatus=discrepancy?"RECEIVED_WITH_DISCREPANCY":
            newReceived==total?"RECEIVED":"PARTIALLY_RECEIVED";
        if(tracked&&discrepancy)markDiscrepantSerialAssets(context,id,why);
        java.sql.Timestamp now=java.sql.Timestamp.from(clock.instant());
        jdbc.update("""
            UPDATE inventory_transfers SET received_quantity=?,status=?,
              completed_by=CASE WHEN ? THEN ? ELSE completed_by END,
              completed_at=CASE WHEN ? THEN ? ELSE completed_at END,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,newReceived,newStatus,complete,context.username(),complete,complete?now:null,
            context.tenantId(),id,expectedVersion);
        appendEvent(context,id,discrepancy?"RECEIVED_WITH_DISCREPANCY":"RECEIVED",quantity,complete,key,why);
        return transferById(context,id);
    }

    private List<String> normalizeSerialNumbers(List<String> values) {
        if(values==null||values.isEmpty())return List.of();
        if(values.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At most 100 serial numbers may be transferred at once");
        var normalized=new java.util.ArrayList<String>();var unique=new java.util.HashSet<String>();
        for(String value:values){
            String serial=required(value,120,"Serial number").toUpperCase(Locale.ROOT);
            if(serial.codePoints().anyMatch(Character::isISOControl)||!unique.add(serial))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Serial numbers must be unique printable values");
            normalized.add(serial);
        }
        normalized.sort(String::compareTo);
        return List.copyOf(normalized);
    }

    private List<String> serialNumbersForTransfer(long tenant,String transferId) {
        return jdbc.query("SELECT serial_number FROM inventory_transfer_serial_assets WHERE tenant_id=? AND transfer_id=? ORDER BY serial_number",
            (rs,n)->rs.getString(1),tenant,transferId);
    }

    private List<String> serialNumbersForReceipt(long tenant,String transferId,String key) {
        return jdbc.query("SELECT serial_number FROM inventory_transfer_serial_assets WHERE tenant_id=? AND transfer_id=? AND receipt_command_key=? ORDER BY serial_number",
            (rs,n)->rs.getString(1),tenant,transferId,key);
    }

    private void reserveSerialAssets(AuthorizationContext.Context context,String transferId,String sourceType,
            Long sourceWarehouse,long sourceBranch,long product,List<String> serials,String reason) {
        for(String serial:serials){
            var rows=jdbc.query("""
                SELECT p.serial_asset_id,p.location_type,p.branch_id,p.warehouse_id
                FROM inventory_serial_asset_positions p
                JOIN inventory_receipt_serial_assets a
                  ON a.tenant_id=p.tenant_id AND a.id=p.serial_asset_id AND a.product_id=p.product_id
                WHERE p.tenant_id=? AND p.product_id=? AND a.serial_number=? AND p.status='AVAILABLE'
                  AND p.location_type=? AND p.branch_id=?
                  AND ((? IS NULL AND p.warehouse_id IS NULL) OR p.warehouse_id=?)
                FOR UPDATE
                """,(rs,n)->new Object[]{rs.getLong("serial_asset_id"),rs.getString("location_type"),
                    rs.getLong("branch_id"),(Number)rs.getObject("warehouse_id")},
                context.tenantId(),product,serial,sourceType,sourceBranch,sourceWarehouse,sourceWarehouse);
            if(rows.isEmpty())conflict("Serial "+serial+" is not available at the selected source location");
            Object[] p=rows.getFirst();long assetId=((Number)p[0]).longValue();
            jdbc.update("""
                INSERT INTO inventory_transfer_serial_assets
                  (tenant_id,transfer_id,receipt_serial_asset_id,product_id,serial_number,state,receipt_command_key)
                VALUES(?,?,?,?,?,'RESERVED',NULL)
                """,context.tenantId(),transferId,assetId,product,serial);
            int updated=jdbc.update("""
                UPDATE inventory_serial_asset_positions
                SET status='RESERVED',reserved_transfer_id=?,version=version+1,updated_at=?
                WHERE tenant_id=? AND serial_asset_id=? AND status='AVAILABLE'
                """,transferId,java.sql.Timestamp.from(clock.instant()),context.tenantId(),assetId);
            if(updated!=1)conflict("Serial "+serial+" is already reserved by another stock operation");
            recordSerialMovement(context,assetId,product,transferId,String.valueOf(p[1]),
                ((Number)p[2]).longValue(),(Number)p[3],"TRANSFER_RESERVED",
                "RESERVE:"+transferId,"Reserved for transfer");
        }
    }

    private void dispatchSerialAssets(AuthorizationContext.Context context,String transferId) {
        var lines=jdbc.query("""
            SELECT receipt_serial_asset_id,product_id,serial_number FROM inventory_transfer_serial_assets
            WHERE tenant_id=? AND transfer_id=? AND state='RESERVED' ORDER BY serial_number FOR UPDATE
            """,(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3)},context.tenantId(),transferId);
        for(Object[] line:lines){
            long assetId=((Number)line[0]).longValue(),product=((Number)line[1]).longValue();
            var positions=jdbc.query("""
                SELECT location_type,branch_id,warehouse_id FROM inventory_serial_asset_positions
                WHERE tenant_id=? AND serial_asset_id=? AND status='RESERVED' AND reserved_transfer_id=? FOR UPDATE
                """,(rs,n)->new Object[]{rs.getString(1),rs.getLong(2),(Number)rs.getObject(3)},
                context.tenantId(),assetId,transferId);
            if(positions.isEmpty())conflict("A reserved serial is no longer available for dispatch");
            Object[] position=positions.getFirst();
            jdbc.update("""
                UPDATE inventory_serial_asset_positions
                SET location_type='IN_TRANSIT',warehouse_id=NULL,status='IN_TRANSIT',
                    reserved_transfer_id=NULL,in_transit_transfer_id=?,version=version+1,updated_at=?
                WHERE tenant_id=? AND serial_asset_id=? AND reserved_transfer_id=?
                """,transferId,java.sql.Timestamp.from(clock.instant()),context.tenantId(),assetId,transferId);
            jdbc.update("UPDATE inventory_transfer_serial_assets SET state='IN_TRANSIT' WHERE tenant_id=? AND transfer_id=? AND receipt_serial_asset_id=?",
                context.tenantId(),transferId,assetId);
            recordSerialMovement(context,assetId,product,transferId,(String)position[0],
                ((Number)position[1]).longValue(),(Number)position[2],"TRANSFER_DISPATCHED",
                "DISPATCH:"+transferId,"Serial dispatched","IN_TRANSIT",
                ((Number)position[1]).longValue(),null);
        }
    }

    private void releaseReservedSerialAssets(AuthorizationContext.Context context,String transferId,String reason) {
        var lines=jdbc.query("""
            SELECT receipt_serial_asset_id,product_id,serial_number FROM inventory_transfer_serial_assets
            WHERE tenant_id=? AND transfer_id=? AND state='RESERVED' ORDER BY serial_number FOR UPDATE
            """,(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3)},context.tenantId(),transferId);
        for(Object[] line:lines){
            long assetId=((Number)line[0]).longValue(),product=((Number)line[1]).longValue();
            var positions=jdbc.query("""
                SELECT location_type,branch_id,warehouse_id FROM inventory_serial_asset_positions
                WHERE tenant_id=? AND serial_asset_id=? AND status='RESERVED' AND reserved_transfer_id=? FOR UPDATE
                """,(rs,n)->new Object[]{rs.getString(1),rs.getLong(2),(Number)rs.getObject(3)},
                context.tenantId(),assetId,transferId);
            if(positions.isEmpty())conflict("A reserved serial could not be released safely");
            Object[] p=positions.getFirst();
            jdbc.update("""
                UPDATE inventory_serial_asset_positions SET status='AVAILABLE',reserved_transfer_id=NULL,
                  version=version+1,updated_at=? WHERE tenant_id=? AND serial_asset_id=? AND reserved_transfer_id=?
                """,java.sql.Timestamp.from(clock.instant()),context.tenantId(),assetId,transferId);
            jdbc.update("""
                UPDATE inventory_transfer_serial_assets SET state='CANCELLED'
                WHERE tenant_id=? AND transfer_id=? AND receipt_serial_asset_id=?
                """,context.tenantId(),transferId,assetId);
            recordSerialMovement(context,assetId,product,transferId,(String)p[0],((Number)p[1]).longValue(),
                (Number)p[2],"TRANSFER_CANCELLED","CANCEL:"+transferId,reason);
        }
    }

    private void receiveSerialAssets(AuthorizationContext.Context context,String transferId,List<String> serials,
            String receiptKey,String reason,String destinationType,Number destinationWarehouse,long destinationBranch) {
        for(String serial:serials){
            var rows=jdbc.query("""
                SELECT a.receipt_serial_asset_id,a.product_id,p.location_type,p.branch_id,p.warehouse_id
                FROM inventory_transfer_serial_assets a
                JOIN inventory_serial_asset_positions p ON p.tenant_id=a.tenant_id
                  AND p.serial_asset_id=a.receipt_serial_asset_id AND p.product_id=a.product_id
                WHERE a.tenant_id=? AND a.transfer_id=? AND a.serial_number=? AND a.state='IN_TRANSIT'
                  AND p.status='IN_TRANSIT' AND p.in_transit_transfer_id=? FOR UPDATE
                """,(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),(Number)rs.getObject(5)},
                context.tenantId(),transferId,serial,transferId);
            if(rows.isEmpty())conflict("Serial "+serial+" is not awaiting receipt on this transfer");
            Object[] p=rows.getFirst();long assetId=((Number)p[0]).longValue(),product=((Number)p[1]).longValue();
            Long warehouseId=destinationWarehouse==null?null:destinationWarehouse.longValue();
            jdbc.update("""
                UPDATE inventory_serial_asset_positions SET location_type=?,branch_id=?,warehouse_id=?,status='AVAILABLE',
                  in_transit_transfer_id=NULL,version=version+1,updated_at=?
                WHERE tenant_id=? AND serial_asset_id=? AND in_transit_transfer_id=?
                """,destinationType,destinationBranch,warehouseId,java.sql.Timestamp.from(clock.instant()),
                context.tenantId(),assetId,transferId);
            jdbc.update("""
                UPDATE inventory_transfer_serial_assets SET state='RECEIVED',receipt_command_key=?
                WHERE tenant_id=? AND transfer_id=? AND receipt_serial_asset_id=? AND state='IN_TRANSIT'
                """,receiptKey,context.tenantId(),transferId,assetId);
            recordSerialMovement(context,assetId,product,transferId,(String)p[2],((Number)p[3]).longValue(),
                (Number)p[4],"TRANSFER_RECEIVED","RECEIPT:"+receiptKey+":"+assetId,reason,
                destinationType,destinationBranch,warehouseId);
        }
    }

    private void markDiscrepantSerialAssets(AuthorizationContext.Context context,String transferId,String reason) {
        var lines=jdbc.query("""
            SELECT a.receipt_serial_asset_id,a.product_id,p.location_type,p.branch_id,p.warehouse_id
            FROM inventory_transfer_serial_assets a
            JOIN inventory_serial_asset_positions p ON p.tenant_id=a.tenant_id
              AND p.serial_asset_id=a.receipt_serial_asset_id AND p.product_id=a.product_id
            WHERE a.tenant_id=? AND a.transfer_id=? AND a.state='IN_TRANSIT'
              AND p.status='IN_TRANSIT' AND p.in_transit_transfer_id=? FOR UPDATE
            """,(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),(Number)rs.getObject(5)},
            context.tenantId(),transferId,transferId);
        for(Object[] p:lines){
            long assetId=((Number)p[0]).longValue(),product=((Number)p[1]).longValue();
            jdbc.update("""
                UPDATE inventory_serial_asset_positions SET location_type='DISCREPANCY',status='DISCREPANCY',
                  in_transit_transfer_id=NULL,version=version+1,updated_at=?
                WHERE tenant_id=? AND serial_asset_id=? AND in_transit_transfer_id=?
                """,java.sql.Timestamp.from(clock.instant()),context.tenantId(),assetId,transferId);
            jdbc.update("""
                UPDATE inventory_transfer_serial_assets SET state='DISCREPANCY'
                WHERE tenant_id=? AND transfer_id=? AND receipt_serial_asset_id=? AND state='IN_TRANSIT'
                """,context.tenantId(),transferId,assetId);
            recordSerialMovement(context,assetId,product,transferId,(String)p[2],((Number)p[3]).longValue(),
                (Number)p[4],"TRANSFER_DISCREPANCY","DISCREPANCY:"+transferId+":"+assetId,reason,
                "DISCREPANCY",((Number)p[3]).longValue(),null);
        }
    }

    private void recordSerialMovement(AuthorizationContext.Context context,long assetId,long product,String transferId,
            String fromType,long fromBranch,Number fromWarehouse,String movementType,String movementKey,String detail) {
        recordSerialMovement(context,assetId,product,transferId,fromType,fromBranch,fromWarehouse,movementType,
            movementKey,detail,fromType,fromBranch,fromWarehouse==null?null:fromWarehouse.longValue());
    }

    private void recordSerialMovement(AuthorizationContext.Context context,long assetId,long product,String transferId,
            String fromType,long fromBranch,Number fromWarehouse,String movementType,String movementKey,String detail,
            String toType,long toBranch,Long toWarehouse) {
        jdbc.update("""
            INSERT INTO inventory_serial_asset_movements(id,tenant_id,serial_asset_id,product_id,transfer_id,
              from_location_type,from_branch_id,from_warehouse_id,to_location_type,to_branch_id,to_warehouse_id,
              movement_type,movement_key,actor_username,detail,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),context.tenantId(),assetId,product,transferId,fromType,fromBranch,
            fromWarehouse,toType,toBranch,toWarehouse,movementType,movementKey,context.username(),detail,
            java.sql.Timestamp.from(clock.instant()));
    }

    private void debitBranch(AuthorizationContext.Context context,long branch,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,context.tenantId(),branch);
        requirePosLocation(context,branch);
        var rows=jdbc.query("SELECT quantity,reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},context.tenantId(),branch,product);
        if(rows.isEmpty()||rows.getFirst()[0]-rows.getFirst()[1]<quantity)conflict("Insufficient available branch stock");
        jdbc.update("UPDATE inventory SET quantity=quantity-? WHERE tenant_id=? AND branch_id=? AND product_id=?",
            quantity,context.tenantId(),branch,product);
        events.publishEvent(new StockAvailabilityChanged(context.tenantId(),branch,product,rows.getFirst()[0]-rows.getFirst()[1]-quantity));
    }
    private void debitWarehouse(AuthorizationContext.Context context,long warehouse,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND id=? AND active=TRUE FOR UPDATE",Long.class,context.tenantId(),warehouse);
        var rows=jdbc.query("SELECT on_hand,reserved,damaged FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2),rs.getInt(3)},context.tenantId(),warehouse,product);
        if(rows.isEmpty()||rows.getFirst()[0]-rows.getFirst()[1]-rows.getFirst()[2]<quantity)conflict("Insufficient available warehouse stock");
        jdbc.update("UPDATE warehouse_inventory SET on_hand=on_hand-?,version=version+1 WHERE tenant_id=? AND warehouse_id=? AND product_id=?",
            quantity,context.tenantId(),warehouse,product);
    }
    private void creditBranch(AuthorizationContext.Context context,long branch,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,context.tenantId(),branch);
        requirePosLocation(context,branch);
        var rows=jdbc.query("SELECT quantity,reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=? FOR UPDATE",
            (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},context.tenantId(),branch,product);
        if(rows.isEmpty()) {
            jdbc.update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved) VALUES(?,?,?,0,0)",
                context.tenantId(),branch,product);
            rows=jdbc.query("SELECT quantity,reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=? FOR UPDATE",
                (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},context.tenantId(),branch,product);
        }
        if(rows.isEmpty()||rows.getFirst()[0]>Integer.MAX_VALUE-quantity)conflict("Branch stock capacity exceeded");
        jdbc.update("UPDATE inventory SET quantity=quantity+? WHERE tenant_id=? AND branch_id=? AND product_id=?",
            quantity,context.tenantId(),branch,product);
        events.publishEvent(new StockAvailabilityChanged(context.tenantId(),branch,product,rows.getFirst()[0]-rows.getFirst()[1]+quantity));
    }
    private void creditWarehouse(AuthorizationContext.Context context,long warehouse,long product,int quantity) {
        jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id=? AND id=? AND active=TRUE FOR UPDATE",Long.class,context.tenantId(),warehouse);
        var rows=jdbc.query("SELECT on_hand FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=? FOR UPDATE",
            (rs,n)->rs.getInt(1),context.tenantId(),warehouse,product);
        if(rows.isEmpty()) {
            jdbc.update("INSERT INTO warehouse_inventory(tenant_id,warehouse_id,product_id,on_hand,reserved,damaged,version) VALUES(?,?,?,0,0,0,0)",
                context.tenantId(),warehouse,product);
            rows=jdbc.query("SELECT on_hand FROM warehouse_inventory WHERE tenant_id=? AND warehouse_id=? AND product_id=? FOR UPDATE",
                (rs,n)->rs.getInt(1),context.tenantId(),warehouse,product);
        }
        if(rows.isEmpty()||rows.getFirst()>Integer.MAX_VALUE-quantity)conflict("Warehouse stock capacity exceeded");
        jdbc.update("UPDATE warehouse_inventory SET on_hand=on_hand+?,version=version+1 WHERE tenant_id=? AND warehouse_id=? AND product_id=?",
            quantity,context.tenantId(),warehouse,product);
    }

    private void requireWarehouseCapacity(AuthorizationContext.Context context) {
        var limits=jdbc.query("""
            SELECT v.max_warehouses FROM tenant_subscriptions s
            JOIN subscription_plan_versions v ON v.plan_id=s.plan_id AND v.version=s.plan_version
            WHERE s.tenant_id=? ORDER BY s.created_at DESC,s.id DESC LIMIT 1
            """,(rs,n)->rs.getInt(1),context.tenantId());
        if(limits.isEmpty()||limits.getFirst()<1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"The active tenant plan does not include warehouse capacity");
        Integer current=jdbc.queryForObject("SELECT COUNT(*) FROM warehouses WHERE tenant_id=?",Integer.class,context.tenantId());
        if(current==null||current>=limits.getFirst())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant warehouse limit has been reached");
    }

    private void lockTenant(AuthorizationContext.Context context) {
        jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",Long.class,context.tenantId());
    }
    private void requireBranch(AuthorizationContext.Context context,long branch) {
        if(jdbc.query("SELECT id FROM branches WHERE tenant_id=? AND id=?",(rs,n)->rs.getLong(1),context.tenantId(),branch).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Destination branch unavailable");
    }
    private void requirePosLocation(AuthorizationContext.Context context,long branch) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM pos_stock_locations WHERE tenant_id=? AND branch_id=? AND active=TRUE",Integer.class,context.tenantId(),branch);
        if(count==null||count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch has no active designated POS stock location");
    }
    private void requireWarehouse(AuthorizationContext.Context context,Long id,long branch,boolean active) {
        if(id==null||jdbc.query("""
            SELECT id FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=? AND (?=FALSE OR active=TRUE)
            """,(rs,n)->rs.getLong(1),context.tenantId(),branch,id,active).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse unavailable");
    }
    private Map<String,Object> warehouseRow(AuthorizationContext.Context context,long id) {
        var rows=jdbc.queryForList("SELECT * FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",context.tenantId(),context.branchId(),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Warehouse unavailable");
        return rows.getFirst();
    }
    private WarehouseView warehouse(AuthorizationContext.Context context,long id) {
        return jdbc.queryForObject("SELECT id,branch_id,code,name,active,version FROM warehouses WHERE tenant_id=? AND branch_id=? AND id=?",
            (rs,n)->new WarehouseView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getLong(6)),
            context.tenantId(),context.branchId(),id);
    }
    private void auditWarehouse(AuthorizationContext.Context context,long id,String action,String reason,Object before,Object after) {
        jdbc.update("""
            INSERT INTO warehouse_audit_events(id,tenant_id,warehouse_id,actor_username,action,reason,before_state,after_state,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),context.tenantId(),id,context.username(),action,reason,before==null?null:String.valueOf(before),
            String.valueOf(after),java.sql.Timestamp.from(clock.instant()));
    }
    private void recordMovement(AuthorizationContext.Context context,String transferId,String locationType,
            Long warehouseId,long branchId,long productId,int quantityDelta,String movementType,String movementKey) {
        jdbc.update("""
            INSERT INTO inventory_transfer_movements(id,tenant_id,transfer_id,location_type,warehouse_id,
              branch_id,product_id,movement_type,quantity_delta,movement_key,actor_username,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),context.tenantId(),transferId,locationType,warehouseId,branchId,
            productId,movementType,quantityDelta,movementKey,context.username(),
            java.sql.Timestamp.from(clock.instant()));
    }

    private void appendEvent(AuthorizationContext.Context context,String id,String type,Integer quantity,Boolean finalReceipt,String key,String detail) {
        jdbc.update("""
            INSERT INTO inventory_transfer_events(id,tenant_id,transfer_id,event_type,actor_username,quantity,is_final,command_key,detail,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID().toString(),context.tenantId(),id,type,context.username(),quantity,finalReceipt,key,detail,java.sql.Timestamp.from(clock.instant()));
    }
    private Map<String,Object> transferRow(AuthorizationContext.Context context,String id) {
        if(id==null||id.length()>36)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid transfer ID required");
        var rows=jdbc.queryForList("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",context.tenantId(),id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
        return rows.getFirst();
    }
    private void requireVisibleBranch(AuthorizationContext.Context context,Map<String,Object> row) {
        if(!Long.valueOf(context.branchId()).equals(((Number)row.get("source_branch_id")).longValue())
            &&!Long.valueOf(context.branchId()).equals(((Number)row.get("destination_branch_id")).longValue()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private void requireSourceBranch(AuthorizationContext.Context context,Map<String,Object> row) {
        if(((Number)row.get("source_branch_id")).longValue()!=context.branchId())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private void requireDestinationBranch(AuthorizationContext.Context context,Map<String,Object> row) {
        if(((Number)row.get("destination_branch_id")).longValue()!=context.branchId())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transfer unavailable");
    }
    private TransferView transferById(AuthorizationContext.Context context,String id) {
        return jdbc.queryForObject("SELECT * FROM inventory_transfers WHERE tenant_id=? AND id=?",
            (rs,n)->transfer(rs),context.tenantId(),id);
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

package com.saumya.pos.inventory;
import com.saumya.pos.inventory.internal.StockReceipt;
import com.saumya.pos.inventory.internal.StockReceiptRepository;
import com.saumya.pos.inventory.internal.InventoryReceiptSerialAsset;
import com.saumya.pos.inventory.internal.InventoryReceiptSerialAssetRepository;
import com.saumya.pos.inventory.ProductSerialTrackingPolicy;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import com.saumya.pos.shared.DomainProblem;
import java.time.*;
import java.util.List;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service
public class StockReceivingService {
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.security.AuthorizationContext authorization;
 private void guard(long tenant,long branch,String actor,String permission){
  if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Authorization context unavailable");
  var context=authorization.current();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  if(!context.username().equals(actor))throw new ResponseStatusException(FORBIDDEN,"Stock receipt actor must match authenticated identity");
  context.requirePermission(permission);
 }
 private final StockReceiptRepository receipts;private final InventoryService stock;private final Clock clock;
 private final ProductSerialTrackingPolicy trackingPolicy;private final InventoryReceiptSerialAssetRepository serialAssets;
 private final JdbcTemplate jdbc;
 @Autowired public StockReceivingService(StockReceiptRepository receipts,InventoryService stock,Clock clock,
     ProductSerialTrackingPolicy trackingPolicy,InventoryReceiptSerialAssetRepository serialAssets,JdbcTemplate jdbc){
  this.receipts=receipts;this.stock=stock;this.clock=clock;this.trackingPolicy=trackingPolicy;this.serialAssets=serialAssets;this.jdbc=jdbc;
 }
 public StockReceivingService(StockReceiptRepository receipts,InventoryService stock,Clock clock,
     ProductSerialTrackingPolicy trackingPolicy,InventoryReceiptSerialAssetRepository serialAssets){
  this(receipts,stock,clock,trackingPolicy,serialAssets,null);
 }
 public StockReceivingService(StockReceiptRepository receipts,InventoryService stock,Clock clock){
  this(receipts,stock,clock,null,null,null);
 }
 public record Input(@Positive long productId,@Min(1) @Max(1000000) int quantity,
     @NotBlank @Size(max=250) String reason,@Size(max=100) List<@NotBlank @Size(max=120) String> serialNumbers){
  public Input(long productId,int quantity,String reason){this(productId,quantity,reason,List.of());}
 }
 public record View(String id,long productId,int quantity,int availableAfter,String actor,String reason,Instant createdAt,List<String> serialNumbers){
  public View(String id,long productId,int quantity,int availableAfter,String actor,String reason,Instant createdAt){this(id,productId,quantity,availableAfter,actor,reason,createdAt,List.of());}
 }
 public record Page(List<View> items,int page,int size,long totalElements,int totalPages){}
 private List<String> serials(long tenant,long branch,StockReceipt row){
  return serialAssets==null?List.of():serialAssets.findByTenantAndBranchAndReceiptIdOrderByIdAsc(tenant,branch,row.id()).stream().map(InventoryReceiptSerialAsset::serialNumber).toList();
 }
 private View view(long tenant,long branch,StockReceipt row){return new View(row.id(),row.product(),row.quantity(),row.available(),row.actor(),row.reason(),row.created(),serials(tenant,branch,row));}
 private List<String> normalize(List<String> values){
  if(values==null||values.isEmpty())return List.of();
  if(values.size()>100)throw new ResponseStatusException(BAD_REQUEST,"At most 100 serial numbers may be captured per receipt");
  var normalized=new ArrayList<String>();var unique=new HashSet<String>();
  for(String value:values){
   if(value==null||value.isBlank()||value.length()>120||value.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(BAD_REQUEST,"Each serial number must contain 1-120 printable characters");
   String serial=value.strip().toUpperCase(Locale.ROOT);
   if(serial.isEmpty()||serial.length()>120||!unique.add(serial))throw new ResponseStatusException(BAD_REQUEST,"Serial numbers must be unique");
   normalized.add(serial);
  }
  return List.copyOf(normalized);
 }
 /** The application caller holds its assigned outlet lock in this transaction, serializing initial balance creation and retries. */
 @Transactional(propagation=Propagation.MANDATORY) @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
 public View receive(long tenant,long branch,String actor,String key,Input input){
  if(input==null||input.productId()<1||input.quantity()<1||input.quantity()>1000000||input.reason()==null||input.reason().isBlank()||input.reason().length()>250||key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))throw new ResponseStatusException(BAD_REQUEST,"Valid product, quantity, reason and idempotency key required");
  guard(tenant,branch,actor,"INVENTORY_RECEIVE");
  List<String> serialNumbers=normalize(input.serialNumbers());
  var previous=receipts.findByTenantAndBranchAndKey(tenant,branch,key);
  if(previous.isPresent()){var row=previous.get();if(row.product()!=input.productId()||row.quantity()!=input.quantity()||!row.actor().equals(actor)||!row.reason().equals(input.reason().strip())||!serials(tenant,branch,row).equals(serialNumbers))throw new DomainProblem("IDEMPOTENCY_KEY_CONFLICT","Stock receiving key already identifies a different command");return view(tenant,branch,row);}
  boolean tracked=trackingPolicy!=null&&trackingPolicy.serialTrackingRequired(tenant,branch,actor,input.productId());
  if(!tracked&&!serialNumbers.isEmpty())throw new ResponseStatusException(BAD_REQUEST,"Serial numbers are not enabled for this product");
  if(tracked&&(input.quantity()>100||serialNumbers.size()!=input.quantity()))throw new ResponseStatusException(BAD_REQUEST,"A serial-tracked receipt requires one serial per received unit (maximum 100)");
  int available=stock.receive(tenant,branch,input.productId(),input.quantity());
  var saved=receipts.saveAndFlush(new StockReceipt(tenant,branch,input.productId(),input.quantity(),actor,input.reason().strip(),key,available,clock.instant()));
  if(!serialNumbers.isEmpty()){
   if(serialAssets==null)throw new IllegalStateException("Serial receiving persistence is unavailable");
   try{
    var stored=serialAssets.saveAllAndFlush(serialNumbers.stream().map(serial->new InventoryReceiptSerialAsset(
        tenant,branch,saved.id(),input.productId(),serial,actor,clock.instant())).toList());
    if(jdbc==null)throw new IllegalStateException("Serial position persistence is unavailable");
    for(var asset:stored){
     jdbc.update("""
       INSERT INTO inventory_serial_asset_positions(tenant_id,serial_asset_id,product_id,location_type,
         branch_id,warehouse_id,status,reserved_transfer_id,in_transit_transfer_id,version,updated_at)
       VALUES(?,?,?,'BRANCH',?,NULL,'AVAILABLE',NULL,NULL,0,?)
       """,tenant,asset.id(),input.productId(),branch,java.sql.Timestamp.from(clock.instant()));
     jdbc.update("""
       INSERT INTO inventory_serial_asset_movements(id,tenant_id,serial_asset_id,product_id,transfer_id,
         from_location_type,from_branch_id,from_warehouse_id,to_location_type,to_branch_id,to_warehouse_id,
         movement_type,movement_key,actor_username,detail,occurred_at)
       VALUES(?,?,?,?,NULL,NULL,NULL,NULL,'BRANCH',?,NULL,'RECEIVED',?,?,?,?)
       """,java.util.UUID.randomUUID().toString(),tenant,asset.id(),input.productId(),branch,
       "RECEIPT:"+saved.id()+":"+asset.id(),actor,"Initial location established from stock receipt",
       java.sql.Timestamp.from(clock.instant()));
    }
   }catch(DataIntegrityViolationException duplicate){throw new ResponseStatusException(CONFLICT,"A serial number is already registered for this vendor",duplicate);}
  }
  return view(tenant,branch,saved);
 }
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
 public Page list(long tenant,long branch,int page,int size,Long productId){
  if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Authorization context unavailable");
  var context=authorization.current();
  context.requireVendorMembership();
  context.requireTenant(tenant);
  context.requireBranch(branch);
  context.requirePermission("INVENTORY_VIEW");
  if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(BAD_REQUEST,"Bounded receipt pagination required");
  if(productId!=null&&productId<1)throw new ResponseStatusException(BAD_REQUEST,"Product ID must be positive");
  var pageable=org.springframework.data.domain.PageRequest.of(page,size);
  var rows=productId==null?receipts.findByTenantAndBranchOrderByCreatedDescIdDesc(tenant,branch,pageable)
   :receipts.findByTenantAndBranchAndProductOrderByCreatedDescIdDesc(tenant,branch,productId,pageable);
  return new Page(rows.stream().map(row->view(tenant,branch,row)).toList(),page,size,rows.getTotalElements(),rows.getTotalPages());
 }
}

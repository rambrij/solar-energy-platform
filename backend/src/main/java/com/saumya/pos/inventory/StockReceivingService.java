package com.saumya.pos.inventory;
import com.saumya.pos.inventory.internal.StockReceipt;
import com.saumya.pos.inventory.internal.StockReceiptRepository;
import com.saumya.pos.shared.DomainProblem;
import java.time.*;
import java.util.List;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
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
 public StockReceivingService(StockReceiptRepository receipts,InventoryService stock,Clock clock){this.receipts=receipts;this.stock=stock;this.clock=clock;}
 public record Input(@Positive long productId,@Min(1) @Max(1000000) int quantity,@NotBlank @Size(max=250) String reason){}
 public record View(String id,long productId,int quantity,int availableAfter,String actor,String reason,Instant createdAt){}
 public record Page(List<View> items,int page,int size,long totalElements,int totalPages){}
 private View view(StockReceipt row){return new View(row.id(),row.product(),row.quantity(),row.available(),row.actor(),row.reason(),row.created());}
 /** The application caller holds its assigned outlet lock in this transaction, serializing initial balance creation and retries. */
 @Transactional(propagation=Propagation.MANDATORY) @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
 public View receive(long tenant,long branch,String actor,String key,Input input){
  if(input==null||input.productId()<1||input.quantity()<1||input.quantity()>1000000||input.reason()==null||input.reason().isBlank()||input.reason().length()>250||key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))throw new ResponseStatusException(BAD_REQUEST,"Valid product, quantity, reason and idempotency key required");
  guard(tenant,branch,actor,"INVENTORY_RECEIVE");
  var previous=receipts.findByTenantAndBranchAndKey(tenant,branch,key);
  if(previous.isPresent()){var row=previous.get();if(row.product()!=input.productId()||row.quantity()!=input.quantity()||!row.actor().equals(actor)||!row.reason().equals(input.reason().strip()))throw new DomainProblem("IDEMPOTENCY_KEY_CONFLICT","Stock receiving key already identifies a different command");return view(row);}
  int available=stock.receive(tenant,branch,input.productId(),input.quantity());
  return view(receipts.saveAndFlush(new StockReceipt(tenant,branch,input.productId(),input.quantity(),actor,input.reason().strip(),key,available,clock.instant())));
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
  return new Page(rows.stream().map(this::view).toList(),page,size,rows.getTotalElements(),rows.getTotalPages());
 }
}

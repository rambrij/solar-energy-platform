package com.saumya.pos.returns;

import com.saumya.pos.returns.internal.*;
import com.saumya.pos.checkout.ReturnSaleAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class ReturnService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final ReturnRepository cases;private final ReturnItemRepository items;private final ReturnEventRepository events;
 private final ReturnSaleAccess sales;private final PosService retail;private final InventoryService stock;private final ReturnRules rules;private final Clock clock;
 public ReturnService(ReturnRepository cases,ReturnItemRepository items,ReturnEventRepository events,ReturnSaleAccess sales,PosService retail,InventoryService stock,ReturnRules rules,Clock clock){this.cases=cases;this.items=items;this.events=events;this.sales=sales;this.retail=retail;this.stock=stock;this.rules=rules;this.clock=clock;}
 public record Item(@Positive long productId,@Min(1) @Max(10000) int quantity){}
 public record Create(@NotBlank @Size(max=36) String orderId,@NotBlank @Size(max=36) String sessionId,@NotEmpty @Size(max=100) List<@NotNull @Valid Item> items,@NotBlank @Size(max=250) String reason,@NotBlank String proof,@NotBlank @Size(max=250) String proofNote){}
 public record Review(@NotBlank String decision,@NotBlank @Size(max=250) String reason){}
 public record Inspection(@Positive long productId,@NotBlank String condition,@NotBlank String disposition,@NotBlank @Size(max=250) String note){}
 public record Inspect(@NotEmpty @Size(max=100) List<@NotNull @Valid Inspection> items){}
 public record Line(long productId,String name,int quantity,String condition,String disposition,String note){}
 public record View(String id,String orderId,String state,String requester,String reason,String proof,String proofNote,Instant createdAt,String reviewedBy,String reviewReason,String inspectedBy,String refundStatus,List<Line> items){}
 public record AvailableLine(long productId,String name,int sold,int remaining){}
 public record Eligibility(String orderId,Instant paidAt,int windowDays,Instant eligibleUntil,boolean eligible,String message,List<AvailableLine> items){}
 private void conflict(String reason){throw new ResponseStatusException(CONFLICT,reason);}
 private void bad(String reason){throw new ResponseStatusException(BAD_REQUEST,reason);}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Return unavailable");}
 private String text(String value){if(value==null||value.isBlank()||value.length()>250)bad("Nonblank reason/proof/inspection notes up to 250 characters required");return value.strip();}
 private String hash(List<String> values){try{var digest=MessageDigest.getInstance("SHA-256");for(String value:values){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void event(ReturnCase c,String actor,String action,String note){events.save(new ReturnEvent(c.tenant(),c.id(),actor,action,note,clock.instant()));}
 private View view(ReturnCase c){return new View(c.id(),c.order(),c.state(),c.requester(),c.reason(),c.proof(),c.proofNote(),c.created(),c.reviewer(),c.reviewReason(),c.inspector(),"NOT_INITIATED",items.items(c.tenant(),c.id()).stream().map(i->new Line(i.product(),i.name(),i.quantity(),i.condition(),i.disposition(),i.note())).toList());}
 private Map<Long,Long> used(long tenant,String order){var result=new HashMap<Long,Long>();for(var line:items.used(tenant,order))result.merge(line.product(),(long)line.quantity(),Long::sum);return result;}
 public Eligibility eligibility(String username,String order){var scope=retail.scope(username);var sale=sales.read(scope.tenant(),scope.branch(),order);var used=used(scope.tenant(),order);boolean eligible=rules.eligible(sale.paidAt(),sale.windowDays(),clock.instant());return new Eligibility(order,sale.paidAt(),sale.windowDays(),sale.windowDays()>0?sale.paidAt().plus(Duration.ofDays(sale.windowDays())):null,eligible,eligible?"Receipt/item proof and independent review are required. No refund will be initiated.":"Returns are disabled or this sale is outside the configured window.",sale.items().stream().map(i->new AvailableLine(i.productId(),i.name(),i.quantity(),Math.max(0,i.quantity()-used.getOrDefault(i.productId(),0L).intValue()))).toList());}
 private View replay(PosService.Scope scope,ReturnCase prior,String fingerprint){if(prior.branch()!=scope.branch()||!prior.requester().equals(scope.cashier())||!prior.hash().equals(fingerprint))conflict("Idempotency key already used for a different return");return view(prior);}
 public View create(String username,String clientKey,Create request){
  if(clientKey==null||!clientKey.matches("[A-Za-z0-9_-]{8,80}"))bad("Valid Idempotency-Key required");
  String reason=text(request.reason()),note=text(request.proofNote());
  if(request.orderId()==null||request.orderId().isBlank()||request.orderId().length()>36||request.sessionId()==null||request.sessionId().isBlank()||request.sessionId().length()>36||request.items()==null||request.items().isEmpty()||request.items().size()>100||request.items().stream().anyMatch(Objects::isNull)||request.proof()==null||!Set.of("ORIGINAL_RECEIPT","ORDER_AND_ITEM_VERIFIED").contains(request.proof()))bad("Valid original sale, session, proof and items required");
  var chosen=request.items().stream().sorted(Comparator.comparingLong(Item::productId)).toList();var seen=new HashSet<Long>();
  for(var i:chosen)if(i.productId()<1||i.quantity()<1||i.quantity()>10000||!seen.add(i.productId()))bad("Choose distinct positive products and quantities");
  var scope=retail.scope(username);String key="return-"+hash(List.of(scope.cashier(),clientKey));
  var parts=new ArrayList<>(List.of(scope.cashier(),request.orderId(),request.sessionId(),reason,request.proof(),note));for(var i:chosen){parts.add(Long.toString(i.productId()));parts.add(Integer.toString(i.quantity()));}String fingerprint=hash(parts);
  var prior=cases.findByTenantAndKey(scope.tenant(),key);if(prior.isPresent())return replay(scope,prior.get(),fingerprint);
  retail.lockOutlet(scope);retail.requireOpen(scope,request.sessionId());var sale=sales.lock(scope.tenant(),scope.branch(),request.orderId());
  prior=cases.currentKey(scope.tenant(),key);if(prior.isPresent()){em.refresh(prior.get(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return replay(scope,prior.get(),fingerprint);}
  rules.requireWindow(sale.paidAt(),sale.windowDays(),clock.instant());var used=used(scope.tenant(),sale.id());
  var original=new HashMap<Long,ReturnSaleAccess.Line>();for(var line:sale.items())original.put(line.productId(),line);
  for(var item:chosen){var line=original.get(item.productId());if(line==null)throw missing();rules.quantity(line.quantity(),used.getOrDefault(item.productId(),0L),item.quantity());}
  String id=UUID.randomUUID().toString();var c=cases.saveAndFlush(new ReturnCase(id,scope.tenant(),scope.branch(),sale.id(),scope.cashier(),request.sessionId(),key,fingerprint,reason,request.proof(),note,clock.instant()));
  items.saveAllAndFlush(chosen.stream().map(i->new ReturnItem(scope.tenant(),id,sale.id(),i.productId(),original.get(i.productId()).name(),i.quantity())).toList());event(c,scope.cashier(),"REQUESTED",reason);return view(c);
 }
 private ReturnCase locked(PosService.Scope scope,String id){var initial=cases.findByTenantAndBranchAndId(scope.tenant(),scope.branch(),id).orElseThrow(this::missing);retail.lockOutlet(scope);sales.lock(scope.tenant(),scope.branch(),initial.order());var c=cases.locked(scope.tenant(),scope.branch(),id).orElseThrow(this::missing);em.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return c;}
 public View review(String username,String id,Review request){
  String reason=text(request.reason());if(request.decision()==null||!Set.of("APPROVED","REJECTED").contains(request.decision()))bad("Approve or reject the intake");
  var scope=retail.scope(username);var c=locked(scope,id);rules.independent(c.requester(),scope.cashier());
  if(!c.state().equals("REQUESTED")){if((c.state().equals(request.decision())||(c.state().equals("INSPECTED")&&request.decision().equals("APPROVED")))&&scope.cashier().equals(c.reviewer())&&reason.equals(c.reviewReason()))return view(c);conflict("Return already reviewed with another decision");}
  c.review(request.decision(),scope.cashier(),reason,clock.instant());cases.flush();event(c,scope.cashier(),request.decision(),reason);return view(c);
 }
 public View inspect(String username,String id,Inspect request){
  if(request.items()==null||request.items().isEmpty()||request.items().size()>100||request.items().stream().anyMatch(Objects::isNull))bad("Inspect every approved return line");
  var commands=request.items().stream().sorted(Comparator.comparingLong(Inspection::productId)).toList();var selected=new HashMap<Long,Inspection>();var parts=new ArrayList<String>();
  var scope=retail.scope(username);parts.add(scope.cashier());
  for(var line:commands){rules.disposition(line.condition(),line.disposition());String note=text(line.note());if(line.productId()<1||selected.put(line.productId(),line)!=null)bad("Distinct inspection products required");parts.add(Long.toString(line.productId()));parts.add(line.condition());parts.add(line.disposition());parts.add(note);}
  String fingerprint=hash(parts);var c=locked(scope,id);rules.independent(c.requester(),scope.cashier());
  if(c.state().equals("INSPECTED")){if(fingerprint.equals(c.inspectionHash()))return view(c);conflict("Inspection already recorded with different details");}
  if(!c.state().equals("APPROVED"))conflict("Independent approval is required before inspection");
  var saved=items.items(scope.tenant(),id);if(saved.size()!=selected.size()||saved.stream().anyMatch(i->!selected.containsKey(i.product())))bad("Inspect exactly the approved return lines");
  for(var line:saved){var command=selected.get(line.product());line.inspect(command.condition(),command.disposition(),command.note().strip());stock.inspectReturn(scope.tenant(),scope.branch(),id,line.product(),line.quantity(),command.disposition(),command.condition(),scope.cashier(),clock.instant());}
  c.inspect(scope.cashier(),fingerprint,clock.instant());cases.flush();event(c,scope.cashier(),"INSPECTED","Goods inspected; refund not initiated");return view(c);
 }
 public View get(String username,String id){var scope=retail.scope(username);var c=cases.findByTenantAndBranchAndId(scope.tenant(),scope.branch(),id).orElseThrow(this::missing);return view(c);}
 public List<View> list(String username,String state){if(state==null||!Set.of("REQUESTED","APPROVED","REJECTED","INSPECTED").contains(state))bad("Valid return state required");var scope=retail.scope(username);return (Set.of("REQUESTED","APPROVED").contains(state)?cases.findTop20ByTenantAndBranchAndStateOrderByCreatedAscIdAsc(scope.tenant(),scope.branch(),state):cases.findTop20ByTenantAndBranchAndStateOrderByCreatedDescIdDesc(scope.tenant(),scope.branch(),state)).stream().map(this::view).toList();}
}

package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
@Service
public class BuyerDetailsService {
 private final BuyerSnapshotRepository snapshots;private final DraftRepository orders;private final PosService retail;private final BuyerDetailsPolicy policy;private final Clock clock;
 public BuyerDetailsService(BuyerSnapshotRepository snapshots,DraftRepository orders,PosService retail,BuyerDetailsPolicy policy,Clock clock){this.snapshots=snapshots;this.orders=orders;this.retail=retail;this.policy=policy;this.clock=clock;}
 BuyerDetailsPolicy.Input canonical(BuyerDetailsPolicy.Input input){return policy.normalize(input,false);}
 String fingerprint(BuyerDetailsPolicy.Input input){return policy.fingerprint(input);}
 @Transactional(propagation=Propagation.MANDATORY) void capture(long tenant,String order,BuyerDetailsPolicy.Input input,boolean required){var value=policy.normalize(input,required);if(value!=null)snapshots.saveAndFlush(new BuyerSnapshot(tenant,order,value.name(),value.contact(),value.taxId(),value.address(),clock.instant()));}
 BuyerDetailsPolicy.Input snapshot(long tenant,String id){return snapshots.findById(new BuyerSnapshot.Key(tenant,id)).map(row->new BuyerDetailsPolicy.Input(row.name(),row.contact(),row.taxId(),row.address())).orElse(null);}
 @Transactional(propagation=Propagation.MANDATORY) void discardUnpaid(long tenant,String id){snapshots.deleteById(new BuyerSnapshot.Key(tenant,id));}
 @Transactional public BuyerDetailsPolicy.Input forReceipt(String username,String id){
  if(id==null||!id.matches("[A-Za-z0-9-]{1,36}"))throw new ResponseStatusException(NOT_FOUND,"Receipt unavailable");
  var scope=retail.scope(username);var order=orders.findByTenantIdAndId(scope.tenant(),id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Receipt unavailable"));
  if(!"PAID".equals(order.status())||!"IN_STORE".equals(order.channel())||!java.util.Objects.equals(order.branch(),scope.branch()))throw new ResponseStatusException(NOT_FOUND,"Receipt unavailable");
  var value=snapshot(scope.tenant(),id);if(value!=null)retail.draftAudit(scope,"BUYER_SNAPSHOT_VIEW",id);return value;
 }
}

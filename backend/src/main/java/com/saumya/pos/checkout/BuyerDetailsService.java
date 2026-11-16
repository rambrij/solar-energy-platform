package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class BuyerDetailsService {
 private final BuyerSnapshotRepository snapshots;private final BuyerDetailsPolicy policy;private final Clock clock;
 public BuyerDetailsService(BuyerSnapshotRepository snapshots,BuyerDetailsPolicy policy,Clock clock){this.snapshots=snapshots;this.policy=policy;this.clock=clock;}
 BuyerDetailsPolicy.Input canonical(BuyerDetailsPolicy.Input input){return policy.normalize(input,false);}
 String fingerprint(BuyerDetailsPolicy.Input input){return policy.fingerprint(input);}
 @Transactional(propagation=Propagation.MANDATORY) void capture(long tenant,String order,BuyerDetailsPolicy.Input input,boolean required){var value=policy.normalize(input,required);if(value!=null)snapshots.saveAndFlush(new BuyerSnapshot(tenant,order,value.name(),value.contact(),value.taxId(),value.address(),clock.instant()));}
 BuyerDetailsPolicy.Input snapshot(long tenant,String id){return snapshots.findById(new BuyerSnapshot.Key(tenant,id)).map(row->new BuyerDetailsPolicy.Input(row.name(),row.contact(),row.taxId(),row.address())).orElse(null);}
 @Transactional(propagation=Propagation.MANDATORY) void discardUnpaid(long tenant,String id){snapshots.deleteById(new BuyerSnapshot.Key(tenant,id));}
}

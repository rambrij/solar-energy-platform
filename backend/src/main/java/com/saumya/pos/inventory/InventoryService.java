package com.saumya.pos.inventory;
import com.saumya.pos.inventory.internal.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class InventoryService {
 private final InventoryIssueRepository issues;private final InventoryRepository balances;private final ReservationRepository reservations;private final ReturnDispositionRepository returns;private final ApplicationEventPublisher events;
 public InventoryService(InventoryRepository balances,ReservationRepository reservations,ReturnDispositionRepository returns,InventoryIssueRepository issues,ApplicationEventPublisher events){this.balances=balances;this.reservations=reservations;this.returns=returns;this.issues=issues;this.events=events;}
 private InventoryBalance locked(long tenant,long branch,long product){return balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable"));}
 private void changed(long tenant,long branch,long product,InventoryBalance balance){balances.flush();events.publishEvent(new StockAvailabilityChanged(tenant,branch,product,balance.available()));}
 @Transactional(readOnly=true) public long available(long tenant,long product){return balances.available(tenant,product);}
 @Transactional(readOnly=true) public long atBranch(long tenant,long branch,long product){return balances.atBranch(tenant,branch,product);}
 @Transactional public void issue(long tenant,long branch,long product,int quantity){var balance=locked(tenant,branch,product);balance.issue(quantity);changed(tenant,branch,product,balance);}
 @Transactional public void reserve(long tenant,long branch,long product,int quantity,String order,Instant expires){var balance=locked(tenant,branch,product);balance.reserve(quantity);reservations.save(new StockReservation(tenant,order,product,branch,quantity,expires));changed(tenant,branch,product,balance);}
 /** Caller holds the canonical order lock. Product ordering is shared by reserve/release/issue. */
 @Transactional public void release(long tenant,String order){for(var reservation:reservations.held(tenant,order)){var balance=locked(tenant,reservation.branch(),reservation.product());balance.release(reservation.quantity());reservation.release();changed(tenant,reservation.branch(),reservation.product(),balance);}}
 @Transactional public Map<Long,Integer> consume(long tenant,String order){var consumed=new LinkedHashMap<Long,Integer>();for(var reservation:reservations.held(tenant,order)){var balance=locked(tenant,reservation.branch(),reservation.product());balance.consume(reservation.quantity());reservation.consume();issues.saveAndFlush(new InventoryIssue(tenant,order,reservation.product(),reservation.branch(),reservation.quantity()));consumed.put(reservation.product(),reservation.quantity());changed(tenant,reservation.branch(),reservation.product(),balance);}return consumed;}
 @Transactional public void inspectReturn(long tenant,long branch,String id,long product,int quantity,String disposition,String condition,String actor,Instant time){
  if(quantity<1||!Set.of("RESTOCK","QUARANTINE").contains(disposition)||!Set.of("SELLABLE","DAMAGED","INCOMPLETE").contains(condition)||("RESTOCK".equals(disposition)&&!"SELLABLE".equals(condition)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid return disposition");
  var key=new ReturnDisposition.Key(tenant,id,product);if(returns.existsById(key))throw new ResponseStatusException(HttpStatus.CONFLICT,"Return stock already recorded");var balance=locked(tenant,branch,product);if("RESTOCK".equals(disposition))balance.restock(quantity);returns.save(new ReturnDisposition(tenant,branch,id,product,quantity,disposition,condition,actor,time));changed(tenant,branch,product,balance);
 }
}

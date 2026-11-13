package com.saumya.pos.inventory;
import com.saumya.pos.inventory.internal.InventoryRepository;
import com.saumya.pos.inventory.internal.ReservationRepository;
import com.saumya.pos.inventory.internal.StockReservation;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class InventoryService {
    private final InventoryRepository balances;
    private final ReservationRepository reservations;
    private final com.saumya.pos.inventory.internal.ReturnDispositionRepository returns;
    public InventoryService(InventoryRepository balances,ReservationRepository reservations,com.saumya.pos.inventory.internal.ReturnDispositionRepository returns){this.balances=balances;this.reservations=reservations;this.returns=returns;}
    @Transactional(readOnly=true) public long available(long tenant,long product){return balances.available(tenant,product);}
    @Transactional(readOnly=true) public long atBranch(long tenant,long branch,long product){return balances.atBranch(tenant,branch,product);}
    @Transactional public void issue(long tenant,long branch,long product,int quantity){balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable")).issue(quantity);balances.flush();}
    @Transactional public void reserve(long tenant,long branch,long product,int quantity,String order,Instant expires){
        balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock unavailable")).reserve(quantity);
        reservations.save(new StockReservation(tenant,order,product,branch,quantity,expires));balances.flush();
    }
    /** Caller holds the canonical order lock; reservation rows never outlive that owner state. */
    @Transactional public void release(long tenant,String order){for(var r:reservations.held(tenant,order)){balances.lock(tenant,r.branch(),r.product()).orElseThrow().release(r.quantity());r.release();}balances.flush();}
    @Transactional public java.util.Map<Long,Integer> consume(long tenant,String order){var consumed=new java.util.LinkedHashMap<Long,Integer>();for(var r:reservations.held(tenant,order)){balances.lock(tenant,r.branch(),r.product()).orElseThrow().consume(r.quantity());r.consume();consumed.put(r.product(),r.quantity());}balances.flush();return consumed;}
    @Transactional public void inspectReturn(long tenant,long branch,String id,long product,int quantity,String disposition,String condition,String actor,Instant time){
        if(quantity<1||!java.util.Set.of("RESTOCK","QUARANTINE").contains(disposition)||!java.util.Set.of("SELLABLE","DAMAGED","INCOMPLETE").contains(condition)||("RESTOCK".equals(disposition)&&!"SELLABLE".equals(condition)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid return disposition");
        var key=new com.saumya.pos.inventory.internal.ReturnDisposition.Key(tenant,id,product);
        if(returns.existsById(key))throw new ResponseStatusException(HttpStatus.CONFLICT,"Return stock already recorded");
        var balance=balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable"));
        if("RESTOCK".equals(disposition))balance.restock(quantity);
        returns.save(new com.saumya.pos.inventory.internal.ReturnDisposition(tenant,branch,id,product,quantity,disposition,condition,actor,time));balances.flush();
    }
}

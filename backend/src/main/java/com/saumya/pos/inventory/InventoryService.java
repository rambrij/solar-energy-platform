package com.saumya.pos.inventory;
import com.saumya.pos.inventory.internal.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class InventoryService {
    private final InventoryRepository balances;
    public InventoryService(InventoryRepository balances){this.balances=balances;}
    @Transactional(readOnly=true) public long available(long tenant,long product){return balances.available(tenant,product);}
    @Transactional(readOnly=true) public long atBranch(long tenant,long branch,long product){return balances.atBranch(tenant,branch,product);}
    @Transactional public void issue(long tenant,long branch,long product,int quantity){balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable")).issue(quantity);balances.flush();}
}

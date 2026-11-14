package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.inventory.StockAvailabilityChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class CatalogAvailabilityService {
 private final CatalogStockRepository stock;
 public CatalogAvailabilityService(CatalogStockRepository stock){this.stock=stock;}
 @EventListener @Transactional(propagation=Propagation.MANDATORY)
 public void changed(StockAvailabilityChanged event){var key=new CatalogStock.Key(event.tenant(),event.branch(),event.product());var row=stock.findById(key).orElseGet(()->new CatalogStock(key,event.available()));row.availability(event.available());stock.saveAndFlush(row);}
}

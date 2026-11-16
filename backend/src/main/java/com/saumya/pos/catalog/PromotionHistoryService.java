package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PromotionHistoryService {
 private final PromotionAuditRepository audit;private final ProductRepository products;
 public PromotionHistoryService(PromotionAuditRepository audit,ProductRepository products){this.audit=audit;this.products=products;}
 public record Change(String id,BigDecimal previousPercent,BigDecimal newPercent,String actor,String reason,Instant createdAt){}
 public record History(long productId,String currentSku,String currentName,boolean published,int page,int size,long totalElements,int totalPages,List<Change> items){}
 public History history(long tenant,long product,int page,int size){
  if(product<1||page<0||page>10000||size<1||size>48)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a positive product ID, page 0-10000 and size 1-48");
  var record=products.findByTenantIdAndId(tenant,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalogue resource unavailable"));
  var result=audit.history(tenant,product,PageRequest.of(page,size));
  return new History(record.id(),record.sku(),record.name(),record.published(),page,size,result.getTotalElements(),result.getTotalPages(),result.getContent());
 }
}

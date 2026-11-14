package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.PromotionPricing;
import java.math.BigDecimal;
import java.time.Clock;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true)
public class PromotionService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final PromotionRepository promotions; private final PromotionAuditRepository audit; private final ProductRepository products; private final PromotionPricing pricing; private final Clock clock;
 public PromotionService(PromotionRepository promotions,PromotionAuditRepository audit,ProductRepository products,PromotionPricing pricing,Clock clock){this.promotions=promotions;this.audit=audit;this.products=products;this.pricing=pricing;this.clock=clock;}
 public record Change(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal percent,@NotBlank @Size(max=250) String reason){}
 public record Price(BigDecimal listPrice,BigDecimal price,BigDecimal percent){}
 public Price price(long tenant,long product,BigDecimal listPrice,boolean locked){
  var rule=locked?promotions.locked(tenant,product):promotions.read(tenant,product);
  if(locked&&rule.isPresent())em.refresh(rule.get(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  BigDecimal percent=rule.map(Promotion::percent).orElse(BigDecimal.ZERO);
  return new Price(listPrice,pricing.unitPrice(listPrice,percent),percent);
 }
 @Transactional public Price approve(long tenant,String actor,long product,Change request){
  if(actor==null||actor.isBlank()||actor.length()>100||request.percent()==null||request.percent().signum()<0||request.percent().compareTo(new BigDecimal("100"))>0||request.percent().scale()>2||request.reason()==null||request.reason().isBlank()||request.reason().length()>250)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid promotion percent and reason required");
  var p=products.lockPublished(tenant,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalogue resource unavailable"));
  var existing=promotions.locked(tenant,product);if(existing.isPresent())em.refresh(existing.get(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  BigDecimal old=existing.map(Promotion::percent).orElse(BigDecimal.ZERO);
  if(old.compareTo(request.percent())==0)return new Price(p.price(),pricing.unitPrice(p.price(),old),old);
  var time=clock.instant();var rule=existing.orElseGet(()->new Promotion(tenant,product,BigDecimal.ZERO,actor,request.reason().strip(),time));
  rule.change(request.percent(),actor,request.reason().strip(),time);promotions.saveAndFlush(rule);
  audit.save(new PromotionAudit(tenant,product,old,request.percent(),actor,request.reason().strip(),time));
  return new Price(p.price(),pricing.unitPrice(p.price(),request.percent()),request.percent());
 }
}

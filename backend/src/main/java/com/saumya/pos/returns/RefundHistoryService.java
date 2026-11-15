package com.saumya.pos.returns;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class RefundHistoryService {
 private final CashRefundRepository refunds;private final PosService retail;private final Clock clock;
 public RefundHistoryService(CashRefundRepository refunds,PosService retail,Clock clock){this.refunds=refunds;this.retail=retail;this.clock=clock;}
 public record Row(String id,String returnId,String orderId,String originalReceipt,String cashier,BigDecimal amount,Instant paidAt){}
 public record History(long branchId,LocalDate from,LocalDate to,String reference,int page,int size,long totalElements,int totalPages,List<Row> items){}
 public History history(String username,LocalDate requestedFrom,LocalDate requestedTo,String requestedReference,String requestedReturn,int page,int size){
  LocalDate to=requestedTo==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):requestedTo;if(to.getYear()<1970||to.getYear()>9998)throw invalid();LocalDate from=requestedFrom==null?to.minusDays(6):requestedFrom;
  String reference=requestedReference==null?"":requestedReference.strip(),returned=requestedReturn==null?"":requestedReturn.strip();
  if(from.getYear()<1970||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365||page<0||page>10000||size<1||size>48||!reference.matches("[A-Za-z0-9-]{0,36}")||returned.length()>36)throw invalid();
  Long receipt=reference.matches("[0-9]{1,18}")?Long.parseLong(reference):null;
  var scope=retail.scope(username);Instant start=from.atStartOfDay(ZoneOffset.UTC).toInstant(),until=to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();String prefix=reference.toLowerCase(Locale.ROOT)+"%";
  Specification<CashRefund> owned=(r,q,b)->{var rules=new ArrayList<jakarta.persistence.criteria.Predicate>();rules.add(b.equal(r.get("tenant"),scope.tenant()));rules.add(b.equal(r.get("branch"),scope.branch()));rules.add(b.greaterThanOrEqualTo(r.get("paid"),start));rules.add(b.lessThan(r.get("paid"),until));if(!returned.isEmpty())rules.add(b.equal(r.get("returnId"),returned));if(!reference.isEmpty()){var ids=b.or(b.like(b.lower(r.get("id")),prefix),b.like(b.lower(r.get("returnId")),prefix),b.like(b.lower(r.get("orderId")),prefix));rules.add(receipt==null?ids:b.or(ids,b.equal(r.get("receipt"),receipt)));}return b.and(rules.toArray(jakarta.persistence.criteria.Predicate[]::new));};
  var result=refunds.findAll(owned,PageRequest.of(page,size,Sort.by(Sort.Order.desc("paid"),Sort.Order.desc("id"))));
  return new History(scope.branch(),from,to,reference,page,size,result.getTotalElements(),result.getTotalPages(),result.stream().map(r->new Row(r.id(),r.returned(),r.order(),r.branch()+"/"+r.receipt(),r.cashier(),r.amount(),r.paid())).toList());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid reference, inclusive UTC range up to 366 days, page 0-10000 and size 1-48");}
}

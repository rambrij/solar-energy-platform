package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CashierSalesService {
 private final CashierSalesRepository reports;private final PosService retail;private final Clock clock;
 public CashierSalesService(CashierSalesRepository reports,PosService retail,Clock clock){this.reports=reports;this.retail=retail;this.clock=clock;}
 public record Totals(long paidOrders,BigDecimal sales,BigDecimal tax,BigDecimal beforeTax,BigDecimal cashReceived,BigDecimal changeGiven,BigDecimal cashApplied,long units,long invoices,BigDecimal discounts){}
 public record Cashier(String cashier,Totals totals){}
 public record Report(long branchId,LocalDate from,LocalDate to,Instant generatedAt,int page,int size,long totalElements,int totalPages,Totals pageTotals,List<Cashier> items){}
 private static class Bucket {
  long paid,units,invoices;BigDecimal sales=BigDecimal.ZERO,tax=BigDecimal.ZERO,cash=BigDecimal.ZERO,change=BigDecimal.ZERO,discounts=BigDecimal.ZERO;
  void header(CashierSalesHeader h){paid+=h.paidOrders();sales=sales.add(h.sales());cash=cash.add(h.cashReceived());change=change.add(h.changeGiven());invoices+=h.invoices();}
  void lines(CashierSalesLines l){tax=tax.add(l.tax());units+=l.units();discounts=discounts.add(l.discounts());}
  Totals view(){return new Totals(paid,sales,tax,sales.subtract(tax),cash,change,cash.subtract(change),units,invoices,discounts);}
 }
 public Report report(String username,LocalDate requestedFrom,LocalDate requestedTo,int page,int size){
  LocalDate to=requestedTo==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):requestedTo;
  if(to.getYear()<1970||to.getYear()>9998)throw invalid();LocalDate from=requestedFrom==null?to.minusDays(6):requestedFrom;
  if(from.getYear()<1970||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365||page<0||page>10000||size<1||size>48)throw invalid();
  var scope=retail.scope(username);Instant start=from.atStartOfDay(ZoneOffset.UTC).toInstant(),until=to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  var headers=reports.headers(scope.tenant(),scope.branch(),start,until,PageRequest.of(page,size));var buckets=new LinkedHashMap<String,Bucket>();var total=new Bucket();
  for(var row:headers){var bucket=new Bucket();bucket.header(row);buckets.put(row.cashier(),bucket);total.header(row);}
  if(!buckets.isEmpty())for(var row:reports.lines(scope.tenant(),scope.branch(),start,until,List.copyOf(buckets.keySet()))){var bucket=buckets.get(row.cashier());if(bucket==null)throw new IllegalStateException("Unrequested cashier aggregate");bucket.lines(row);total.lines(row);}
  return new Report(scope.branch(),from,to,clock.instant(),page,size,headers.getTotalElements(),headers.getTotalPages(),total.view(),buckets.entrySet().stream().map(e->new Cashier(e.getKey(),e.getValue().view())).toList());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an inclusive UTC range of 1-366 days, years 1970-9998, page 0-10000 and size 1-48");}
}

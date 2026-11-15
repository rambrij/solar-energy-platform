package com.saumya.pos.returns;
import com.saumya.pos.returns.internal.RefundReportRepository;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class RefundReportService {
 private final RefundReportRepository reports;private final PosService retail;private final Clock clock;
 public RefundReportService(RefundReportRepository reports,PosService retail,Clock clock){this.reports=reports;this.retail=retail;this.clock=clock;}
 public record Totals(long refunds,long units,BigDecimal cash,BigDecimal beforeTax,BigDecimal tax){}
 public record Day(LocalDate date,Totals totals){}
 public record Cashier(String cashier,Totals totals){}
 public record Report(long branchId,LocalDate from,LocalDate to,Instant generatedAt,Totals totals,List<Day> days,List<Cashier> cashiers){}
 private static class Bucket {
  long refunds,units;BigDecimal cash=BigDecimal.ZERO,net=BigDecimal.ZERO,tax=BigDecimal.ZERO;
  void header(long count,BigDecimal amount){refunds+=count;cash=cash.add(amount);}
  void lines(long count,BigDecimal before,BigDecimal taxAmount){units+=count;net=net.add(before);tax=tax.add(taxAmount);}
  Totals view(){return new Totals(refunds,units,cash,net,tax);}
 }
 public Report report(String username,LocalDate requestedFrom,LocalDate requestedTo){
  LocalDate to=requestedTo==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):requestedTo;
  if(to.getYear()<1970||to.getYear()>9998)throw invalid();
  LocalDate from=requestedFrom==null?to.minusDays(6):requestedFrom;
  if(from.getYear()<1970||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365)throw invalid();
  var scope=retail.scope(username);Instant start=from.atStartOfDay(ZoneOffset.UTC).toInstant(),until=to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  var total=new Bucket();var days=new LinkedHashMap<LocalDate,Bucket>();var cashiers=new TreeMap<String,Bucket>();
  for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1))days.put(day,new Bucket());
  // Header and line queries are separate so multi-line refunds never multiply cash/counts.
  for(var row:reports.headers(scope.tenant(),scope.branch(),start,until)){
   total.header(row.refunds(),row.cash());days.get(row.day()).header(row.refunds(),row.cash());cashiers.computeIfAbsent(row.cashier(),key->new Bucket()).header(row.refunds(),row.cash());
  }
  for(var row:reports.lines(scope.tenant(),scope.branch(),start,until)){
   total.lines(row.units(),row.beforeTax(),row.tax());days.get(row.day()).lines(row.units(),row.beforeTax(),row.tax());cashiers.computeIfAbsent(row.cashier(),key->new Bucket()).lines(row.units(),row.beforeTax(),row.tax());
  }
  return new Report(scope.branch(),from,to,clock.instant(),total.view(),days.entrySet().stream().map(e->new Day(e.getKey(),e.getValue().view())).toList(),cashiers.entrySet().stream().map(e->new Cashier(e.getKey(),e.getValue().view())).toList());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an inclusive UTC date range of 1-366 days, within years 1970-9998");}
}

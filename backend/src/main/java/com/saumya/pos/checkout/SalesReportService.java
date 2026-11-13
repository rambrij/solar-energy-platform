package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.SalesReportRepository;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SalesReportService {
    private final SalesReportRepository reports;
    private final PosService retail;
    private final Clock clock;
    public SalesReportService(SalesReportRepository reports,PosService retail,Clock clock){this.reports=reports;this.retail=retail;this.clock=clock;}
    public record Totals(long paidOrders,BigDecimal sales,BigDecimal tax,BigDecimal beforeTax,BigDecimal cashReceived,
                         BigDecimal changeGiven,BigDecimal cashApplied,long units,long invoices,long unpaidOrders,
                         BigDecimal unpaidAmount,long cancelledOrders,long expiredOrders){}
    public record Channel(String channel,Totals totals){}
    public record Day(LocalDate date,Totals totals){}
    public record Report(long branchId,LocalDate from,LocalDate to,String channel,Instant generatedAt,Totals totals,List<Channel> channels,List<Day> days){}
    private static class Bucket {
        long paid,units,invoices,unpaid,cancelled,expired;
        BigDecimal sales=BigDecimal.ZERO,tax=BigDecimal.ZERO,cash=BigDecimal.ZERO,change=BigDecimal.ZERO,unpaidAmount=BigDecimal.ZERO;
        void add(Bucket b){paid+=b.paid;units+=b.units;invoices+=b.invoices;unpaid+=b.unpaid;cancelled+=b.cancelled;expired+=b.expired;sales=sales.add(b.sales);tax=tax.add(b.tax);cash=cash.add(b.cash);change=change.add(b.change);unpaidAmount=unpaidAmount.add(b.unpaidAmount);}
        Totals view(){return new Totals(paid,sales,tax,sales.subtract(tax),cash,change,cash.subtract(change),units,invoices,unpaid,unpaidAmount,cancelled,expired);}
    }
    public Report report(String username,LocalDate requestedFrom,LocalDate requestedTo,String requestedChannel){
        LocalDate to=requestedTo==null?LocalDate.now(clock):requestedTo;
        if(to.getYear()<1970||to.getYear()>9998)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Date is out of range");
        LocalDate from=requestedFrom==null?to.minusDays(6):requestedFrom;
        String channel=requestedChannel==null?"ALL":requestedChannel.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","IN_STORE","ONLINE").contains(channel)||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365||from.getYear()<1970||to.getYear()>9998)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose ALL, IN_STORE or ONLINE and an inclusive UTC range of at most 366 days");
        var scope=retail.scope(username);
        Instant start=from.atStartOfDay(ZoneOffset.UTC).toInstant(),until=to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var names=channel.equals("ALL")?List.of("ONLINE","IN_STORE"):List.of(channel);
        var buckets=new LinkedHashMap<LocalDate,Map<String,Bucket>>();
        for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){var perChannel=new LinkedHashMap<String,Bucket>();for(String name:names)perChannel.put(name,new Bucket());buckets.put(day,perChannel);}
        for(var row:reports.orders(scope.tenant(),scope.branch(),channel,start,until)){
            var b=buckets.get(row.day()).get(row.channel());
            switch(row.status()){
                case "PAID" -> {b.paid+=row.orders();b.sales=b.sales.add(row.gross());b.cash=b.cash.add(row.cash());b.change=b.change.add(row.change());b.invoices+=row.invoices();}
                case "DRAFT" -> {b.unpaid+=row.orders();b.unpaidAmount=b.unpaidAmount.add(row.gross());}
                case "CANCELLED" -> b.cancelled+=row.orders();
                case "EXPIRED" -> b.expired+=row.orders();
                default -> throw new IllegalStateException("Unsupported recorded order state");
            }
        }
        for(var row:reports.lines(scope.tenant(),scope.branch(),channel,start,until)){var b=buckets.get(row.day()).get(row.channel());b.tax=b.tax.add(row.tax());b.units+=row.units();}
        var total=new Bucket();var byChannel=new LinkedHashMap<String,Bucket>();for(String name:names)byChannel.put(name,new Bucket());
        var days=new ArrayList<Day>();
        buckets.forEach((day,values)->{var daily=new Bucket();values.forEach((name,b)->{daily.add(b);byChannel.get(name).add(b);});total.add(daily);days.add(new Day(day,daily.view()));});
        return new Report(scope.branch(),from,to,channel,clock.instant(),total.view(),byChannel.entrySet().stream().map(e->new Channel(e.getKey(),e.getValue().view())).toList(),List.copyOf(days));
    }
}

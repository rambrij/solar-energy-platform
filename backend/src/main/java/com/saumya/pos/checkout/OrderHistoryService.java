package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only application API; financial and cancellation commands remain in their existing owners. */
@Service
@Transactional(readOnly=true)
public class OrderHistoryService {
    private final DraftRepository orders;
    private final DraftLineRepository lines;
    private final CustomerAccess customers;
    private final PosService retail;
    private final CatalogService catalog;
    public OrderHistoryService(DraftRepository orders,DraftLineRepository lines,CustomerAccess customers,PosService retail,CatalogService catalog){
        this.orders=orders;this.lines=lines;this.customers=customers;this.retail=retail;this.catalog=catalog;
    }
    public record Filter(String status,String reference,LocalDate from,LocalDate to,int page,int size){
        public Filter {
            status=status==null?"ALL":status.toUpperCase(Locale.ROOT);
            reference=reference==null?"":reference.strip();
            if(!Set.of("ALL","DRAFT","PAID","CANCELLED","EXPIRED").contains(status))bad("Unknown order status");
            if(page<0||page>10000||size<1||size>50)bad("Page must be 0..10000 and size 1..50");
            if(!reference.isEmpty()&&!reference.matches("[0-9]{1,18}|[0-9a-fA-F-]{4,36}"))bad("Use a receipt number or at least four characters of an order reference");
            if(from!=null&&to!=null&&from.isAfter(to))bad("From date must not follow to date");
            if(to!=null&&to.equals(LocalDate.MAX))bad("Date is out of range");
        }
    }
    public record Order(String id,String vendorName,String channel,String status,Instant createdAt,Instant expiresAt,
                        BigDecimal total,Long receiptNumber,boolean taxInvoice){}
    public record History(List<Order> items,int page,int size,long totalElements,int totalPages){}
    public record Line(long productId,String name,int quantity,BigDecimal unitPrice,BigDecimal taxRate,BigDecimal tax,BigDecimal total,BigDecimal listPrice,BigDecimal discount){}
    public record Detail(Order order,List<Line> items,BigDecimal cashReceived,BigDecimal changeGiven,String cancellationReason,Instant statusChangedAt){}
    private static void bad(String reason){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Order unavailable");}
    private Specification<DraftOrder> customerScope(long customer){return (r,q,b)->b.and(b.equal(r.get("customerId"),customer),b.equal(r.get("channel"),"ONLINE"));}
    private Specification<DraftOrder> branchScope(PosService.Scope scope){return (r,q,b)->b.and(b.equal(r.get("tenantId"),scope.tenant()),b.equal(r.get("branchId"),scope.branch()),b.equal(r.get("channel"),"IN_STORE"));}
    private Specification<DraftOrder> filtered(Specification<DraftOrder> scope,Filter filter){
        return scope.and((r,q,b)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(!filter.status().equals("ALL"))predicates.add(b.equal(r.get("status"),filter.status()));
            if(!filter.reference().isEmpty()){
                var reference=b.like(b.lower(r.get("id")),filter.reference().toLowerCase(Locale.ROOT)+"%");
                predicates.add(filter.reference().matches("[0-9]{1,18}")?b.or(reference,b.equal(r.get("receiptNumber"),Long.parseLong(filter.reference()))):reference);
            }
            if(filter.from()!=null)predicates.add(b.greaterThanOrEqualTo(r.get("createdAt"),filter.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
            if(filter.to()!=null)predicates.add(b.lessThan(r.get("createdAt"),filter.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        });
    }
    public History customer(String username,Filter filter){return search(customerScope(customers.resolve(username)),filter);}
    public History pos(String username,Filter filter){return search(branchScope(retail.scope(username)),filter);}
    private Order view(DraftOrder order,Map<Long,String> names){
        return new Order(order.id(),names.computeIfAbsent(order.tenant(),id->catalog.vendor(id).name()),order.channel(),order.status(),order.created(),order.expires(),order.total(),order.receiptNumber(),order.taxInvoice());
    }
    private History search(Specification<DraftOrder> scope,Filter filter){
        var page=orders.findAll(filtered(scope,filter),PageRequest.of(filter.page(),filter.size(),Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        var names=new HashMap<Long,String>();
        return new History(page.getContent().stream().map(o->view(o,names)).toList(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
    }
    public Detail customerDetail(String username,String id){return detail(customerScope(customers.resolve(username)),id);}
    public Detail posDetail(String username,String id){return detail(branchScope(retail.scope(username)),id);}
    private Detail detail(Specification<DraftOrder> scope,String id){
        var order=orders.findOne(scope.and((r,q,b)->b.equal(r.get("id"),id))).orElseThrow(this::missing);
        var snapshots=lines.lines(order.tenant(),order.id()).stream().map(l->new Line(l.product(),l.name(),l.quantity(),l.price(),l.rate(),l.tax(),l.total(),l.listPrice(),l.discount())).toList();
        return new Detail(view(order,new HashMap<>()),snapshots,order.cash(),order.change(),order.cancellationReason(),order.changed());
    }
}

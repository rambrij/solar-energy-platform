package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.checkout.CheckoutService;
import com.saumya.pos.customer.VendorAccessAuditService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.marketplace.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class QuotationService {
    private final QuotationRepository quotations;
    private final QuotationRevisionRepository revisions;
    private final QuotationLineRepository lines;
    private final QuotationAuditRepository audit;
    private final MarketplaceEnquiryRepository enquiries;
    private final CatalogService catalogue;
    private final CheckoutService checkout;
    private final CustomerAccess customers;
    private final AuthorizationContext authorization;
    private final VendorAccessAuditService vendorAudit;
    private final Clock clock;

    public QuotationService(QuotationRepository quotations, QuotationRevisionRepository revisions,
            QuotationLineRepository lines, QuotationAuditRepository audit,
            MarketplaceEnquiryRepository enquiries, CatalogService catalogue, CheckoutService checkout,
            CustomerAccess customers, AuthorizationContext authorization,
            VendorAccessAuditService vendorAudit, Clock clock) {
        this.quotations=quotations; this.revisions=revisions; this.lines=lines; this.audit=audit;
        this.enquiries=enquiries; this.catalogue=catalogue; this.checkout=checkout;
        this.customers=customers; this.authorization=authorization; this.vendorAudit=vendorAudit; this.clock=clock;
    }

    public record LineInput(long productId, int quantity) {}
    public record Create(long enquiryId, List<LineInput> items, int validityDays, String terms, String reason) {}
    public record Revise(List<LineInput> items, int validityDays, String terms, String reason) {}
    public record Decision(String reason) {}
    public record LineView(long productId, String name, int quantity, BigDecimal listPrice,
            BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal taxRate,
            BigDecimal tax, BigDecimal total) {}
    public record RevisionView(long id, int revisionNumber, String status, String currency,
            Instant validUntil, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
            String terms, Instant sentAt, List<LineView> items) {}
    public record View(long id, long enquiryId, String status, Integer acceptedRevisionNumber,
            String convertedOrderId, long version, List<RevisionView> revisions) {}
    public record CustomerDecision(View quotation, CheckoutService.Draft order) {}

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation unavailable");
    }
    private String required(String value, int max, String label) {
        String result=value==null?"":value.strip();
        if(result.isBlank()||result.length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" must contain 1 to "+max+" characters");
        return result;
    }
    private void validateValidity(int days) {
        if(days<1||days>90) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation validity must be 1 to 90 days");
    }
    private List<LineInput> validateItems(List<LineInput> items) {
        if(items==null||items.isEmpty()||items.size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation requires 1 to 100 product lines");
        Set<Long> ids=new HashSet<>();
        for(LineInput item:items) {
            if(item==null||item.productId()<=0||item.quantity()<1||item.quantity()>10000||!ids.add(item.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation product lines must be valid and unique");
        }
        return items.stream().sorted(Comparator.comparingLong(LineInput::productId)).toList();
    }
    private String vendorActor() {
        var actor=authorization.current();
        actor.requireVendorMembership(); actor.requirePermission("LEAD_UPDATE");
        return actor.username();
    }
    private void requireLeadScope(AuthorizationContext.Context actor, MarketplaceEnquiry lead) {
        if(!actor.hasPermission("LEAD_ASSIGN")&&!Objects.equals(lead.assignedTo(),actor.username())) throw missing();
    }

    public View create(Create request) {
        String actorName=vendorActor();
        var actor=authorization.current();
        if(request==null||request.enquiryId()<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lead is required");
        validateValidity(request.validityDays());
        String reason=required(request.reason(),500,"Quotation reason");
        String terms=request.terms()==null?null:request.terms().strip();
        if(terms!=null&&terms.length()>4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation terms are too long");
        MarketplaceEnquiry lead=enquiries.findByTenantIdAndIdForUpdate(actor.tenantId(),request.enquiryId()).orElseThrow(this::missing);
        requireLeadScope(actor,lead);
        if(lead.customerId()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "The enquiry must be linked to its verified global customer identity before a quotation can be sent");
        if("LOST".equals(lead.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"A lost lead cannot receive a quotation");

        Instant now=clock.instant();
        Quotation quote=quotations.saveAndFlush(new Quotation(actor.tenantId(),lead.id(),lead.customerId(),actorName,now));
        int revisionNumber=quote.addRevision(actorName,now);
        RevisionView revision=createSentRevision(quote,revisionNumber,validateItems(request.items()),request.validityDays(),terms,actorName,now);
        audit.save(new QuotationAuditEvent(actor.tenantId(),quote.id(),revision.id(),"VENDOR",actorName,null,
            "QUOTATION_SENT",reason,now));
        vendorAudit.recordQuotationMutation(quote.id(),"QUOTATION_SENT",reason);
        return view(quote);
    }

    public View revise(long quotationId, Revise request) {
        String actorName=vendorActor();
        var actor=authorization.current();
        if(request==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation revision is required");
        validateValidity(request.validityDays());
        String reason=required(request.reason(),500,"Revision reason");
        String terms=request.terms()==null?null:request.terms().strip();
        if(terms!=null&&terms.length()>4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quotation terms are too long");
        Quotation quote=quotations.lockTenant(actor.tenantId(),quotationId).orElseThrow(this::missing);
        MarketplaceEnquiry lead=enquiries.findByTenantIdAndIdForUpdate(actor.tenantId(),quote.enquiryId()).orElseThrow(this::missing);
        requireLeadScope(actor,lead);
        if(!"OPEN".equals(quote.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an open quotation can be revised");
        var previous=revisions.findTopByTenantIdAndQuotationIdOrderByRevisionNumberDesc(actor.tenantId(),quote.id())
            .orElseThrow(this::missing);
        if("SENT".equals(previous.status())) previous.supersede();
        Instant now=clock.instant();
        int number=quote.addRevision(actorName,now);
        RevisionView revision=createSentRevision(quote,number,validateItems(request.items()),request.validityDays(),terms,actorName,now);
        audit.save(new QuotationAuditEvent(actor.tenantId(),quote.id(),revision.id(),"VENDOR",actorName,null,
            "QUOTATION_REVISION_SENT",reason,now));
        vendorAudit.recordQuotationMutation(quote.id(),"QUOTATION_REVISION_SENT",reason);
        return view(quote);
    }

    private RevisionView createSentRevision(Quotation quote,int number,List<LineInput> requested,
            int validityDays,String terms,String actor,Instant now) {
        var productSnapshots=new ArrayList<QuotationLine>();
        BigDecimal subtotal=BigDecimal.ZERO, taxTotal=BigDecimal.ZERO, total=BigDecimal.ZERO;
        int lineNumber=0;
        for(LineInput item:requested) {
            var product=catalogue.lockForQuotation(quote.tenantId(),item.productId());
            var amount=new com.saumya.pos.shared.Pricing.Amounts(
                product.price().multiply(BigDecimal.valueOf(item.quantity())),
                product.price().multiply(BigDecimal.valueOf(item.quantity()))
                    .multiply(product.taxRate()).divide(new BigDecimal("100"),2,java.math.RoundingMode.HALF_UP),
                product.price().multiply(BigDecimal.valueOf(item.quantity()))
                    .multiply(BigDecimal.ONE.add(product.taxRate().divide(new BigDecimal("100"),6,java.math.RoundingMode.HALF_UP)))
                    .setScale(2,java.math.RoundingMode.HALF_UP));
            subtotal=subtotal.add(amount.net()); taxTotal=taxTotal.add(amount.tax()); total=total.add(amount.total());
            productSnapshots.add(new QuotationLine(quote.tenantId(),0,quote.id(),number,++lineNumber,
                product.id(),product.name(),item.quantity(),product.listPrice(),product.price(),
                product.discountPercent(),product.taxRate(),amount.tax(),amount.total()));
        }
        Instant validUntil=now.plus(Duration.ofDays(validityDays));
        QuotationRevision revision=revisions.saveAndFlush(new QuotationRevision(quote.tenantId(),
            quote.id(),number,validUntil,"INR",subtotal,taxTotal,total,terms,actor,now));
        // Revision identity is now generated; create immutable line snapshots against it.
        List<QuotationLine> persisted=productSnapshots.stream().map(line->new QuotationLine(
            quote.tenantId(),revision.id(),quote.id(),number,line.lineNumber(),line.productId(),
            line.productNameSnapshot(),line.quantity(),line.listPrice(),line.unitPrice(),
            line.discountPercent(),line.taxRate(),line.taxAmount(),line.lineTotal())).toList();
        lines.saveAllAndFlush(persisted);
        revision.send(now);
        return revisionView(revision,persisted);
    }

    @Transactional(readOnly=true)
    public List<View> vendorList() {
        var actor=authorization.current();
        actor.requireVendorMembership(); actor.requirePermission("LEAD_VIEW");
        return quotations.visibleToVendor(actor.tenantId(),actor.hasPermission("LEAD_ASSIGN"),
            actor.username(),PageRequest.of(0,100)).stream().map(this::view).toList();
    }

    @Transactional(readOnly=true)
    public List<View> customerList(String username) {
        long customer=customers.resolve(username);
        return quotations.findTop100ByCustomerIdOrderByCreatedAtDescIdDesc(customer).stream()
            .map(this::view).toList();
    }

    public CustomerDecision decide(String username,long quotationId,int revisionNumber,
            boolean accept,String requestedReason) {
        long customer=customers.resolve(username);
        customers.lock(customer);
        Quotation quote=quotations.lockByCustomerIdAndId(customer,quotationId).orElseThrow(this::missing);
        long tenant=quote.tenantId();
        QuotationRevision revision=revisions.lockRevision(tenant,quote.id(),revisionNumber).orElseThrow(this::missing);
        Instant now=clock.instant();
        if("CONVERTED".equals(quote.status())
                &&Objects.equals(quote.acceptedRevisionNumber(),revisionNumber)
                &&Objects.equals(quote.acceptedByCustomerId(),customer)
                &&"ACCEPTED".equals(revision.status())) {
            var existingOrder=checkout.fromAcceptedQuotation(tenant,customer,quote.id(),revision.total(),
                lines.findAllByTenantIdAndRevisionIdOrderByLineNumber(tenant,revision.id()).stream()
                .map(line->new CheckoutService.QuotationLineSnapshot(line.productId(),line.productNameSnapshot(),
                    line.quantity(),line.listPrice(),line.unitPrice(),line.discountPercent(),line.taxRate(),
                    line.taxAmount(),line.lineTotal())).toList());
            if(!Objects.equals(existingOrder.id(),quote.convertedOrderId()))
                throw new IllegalStateException("Quotation conversion idempotency key returned a different order");
            return new CustomerDecision(view(quote),existingOrder);
        }
        if(!"OPEN".equals(quote.status())||quote.latestRevisionNumber()!=revisionNumber
                ||!"SENT".equals(revision.status())||!revision.validUntil().isAfter(now))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This quotation revision is no longer valid");
        String reason=requestedReason==null?"":requestedReason.strip();
        if(reason.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision reason is too long");
        if(!accept&&reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A reason is required to reject a quotation");
        revision.decide(accept?"ACCEPTED":"REJECTED",customer,reason.isBlank()?null:reason,now);
        if(!accept) {
            audit.save(new QuotationAuditEvent(tenant,quote.id(),revision.id(),"CUSTOMER",username,
                customer,"QUOTATION_REJECTED",reason,now));
            return new CustomerDecision(view(quote),null);
        }
        quote.accept(revisionNumber,customer,now);
        var order=checkout.fromAcceptedQuotation(tenant,customer,quote.id(),revision.total(),
            lines.findAllByTenantIdAndRevisionIdOrderByLineNumber(tenant,revision.id()).stream()
                .map(line->new CheckoutService.QuotationLineSnapshot(line.productId(),line.productNameSnapshot(),
                    line.quantity(),line.listPrice(),line.unitPrice(),line.discountPercent(),line.taxRate(),
                    line.taxAmount(),line.lineTotal())).toList());
        quote.converted(order.id(),now);
        audit.save(new QuotationAuditEvent(tenant,quote.id(),revision.id(),"CUSTOMER",username,
            customer,"QUOTATION_ACCEPTED_AND_CONVERTED", "Customer accepted the current quotation revision",now));
        return new CustomerDecision(view(quote),order);
    }

    @Transactional(readOnly=true)
    public View customerGet(String username,long id) {
        long customer=customers.resolve(username);
        Quotation quote=quotations.findByCustomerIdAndId(customer,id).orElseThrow(this::missing);
        return view(quote);
    }

    private View view(Quotation quote) {
        long tenant=quote.tenantId();
        List<RevisionView> revisionViews=revisions.findAllByTenantIdAndQuotationIdOrderByRevisionNumberAsc(
            tenant,quote.id()).stream().map(this::revisionView).toList();
        return new View(quote.id(),quote.enquiryId(),quote.status(),quote.acceptedRevisionNumber(),
            quote.convertedOrderId(),quote.version(),revisionViews);
    }
    private RevisionView revisionView(QuotationRevision revision) {
        long tenant=revision.tenantId();
        return revisionView(revision,lines.findAllByTenantIdAndRevisionIdOrderByLineNumber(
            tenant,revision.id()));
    }
    private RevisionView revisionView(QuotationRevision revision,List<QuotationLine> items) {
        return new RevisionView(revision.id(),revision.revisionNumber(),revision.status(),revision.currency(),
            revision.validUntil(),revision.subtotal(),revision.taxTotal(),revision.total(),revision.terms(),
            revision.sentAt(),items.stream().map(line->new LineView(line.productId(),line.productNameSnapshot(),
                line.quantity(),line.listPrice(),line.unitPrice(),line.discountPercent(),line.taxRate(),
                line.taxAmount(),line.lineTotal())).toList());
    }
}

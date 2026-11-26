package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.customer.internal.VendorAccessAuditEvent;
import com.saumya.pos.customer.internal.VendorAccessAuditRepository;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiry;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiryRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class MarketplaceEnquiryService {
    private static final int MAX_REQUESTS_PER_WINDOW=5;
    private static final Duration RATE_WINDOW=Duration.ofMinutes(10);
    private final CatalogService catalogue;
    private final MarketplaceEnquiryRepository enquiries;
    private final CustomerAccess customers;
    private final VendorCustomerService relationships;
    private final AuthorizationContext authorization;
    private final VendorAccessAuditRepository audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MarketplaceEnquiryService(CatalogService catalogue,MarketplaceEnquiryRepository enquiries,
            CustomerAccess customers,VendorCustomerService relationships,AuthorizationContext authorization,
            VendorAccessAuditRepository audit,JdbcTemplate jdbc,Clock clock) {
        this.catalogue=catalogue;this.enquiries=enquiries;this.customers=customers;this.relationships=relationships;
        this.authorization=authorization;this.audit=audit;this.jdbc=jdbc;this.clock=clock;
    }

    public record Submission(String contactName,String contactEmail,String contactPhone,String message,
                             Long productId,boolean consent,boolean linkToAccount) {}
    public record Receipt(Long id,Instant createdAt,String status) {}
    public record Lead(Long id,Long productId,String productName,String contactName,String contactEmail,
                       String contactPhone,String message,Instant createdAt,String status) {}

    @Transactional
    public Receipt submit(String slug,Submission request,String remoteAddress,String username) {
        if(request==null||!request.consent())
            throw new ResponseStatusException(BAD_REQUEST,"Explicit consent to share enquiry details with this vendor is required");
        String name=required(request.contactName(),120,"Contact name");
        String email=required(request.contactEmail(),254,"Email").toLowerCase(Locale.ROOT);
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(BAD_REQUEST,"A valid email address is required");
        String phone=optional(request.contactPhone(),30);
        String message=required(request.message(),2000,"Enquiry message");
        var vendor=catalogue.activeVendor(slug);
        String requesterHash=hashAddress(remoteAddress);
        jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",Long.class,vendor.id());
        Instant now=clock.instant();
        long recent=enquiries.countByTenantIdAndRequesterHashAndCreatedAtAfter(vendor.id(),requesterHash,now.minus(RATE_WINDOW));
        if(recent>=MAX_REQUESTS_PER_WINDOW)
            throw new ResponseStatusException(TOO_MANY_REQUESTS,"Too many enquiries from this connection. Please try again later");

        Long productId=null;
        String productName=null;
        if(request.productId()!=null) {
            var product=catalogue.published(vendor.id(),request.productId());
            productId=product.id();
            productName=product.name();
        }

        Long customerId=null;
        if(request.linkToAccount()) {
            if(username==null||username.isBlank())
                throw new ResponseStatusException(UNAUTHORIZED,"Sign in with your global customer account before linking this enquiry");
            if(!email.equals(username.strip().toLowerCase(Locale.ROOT)))
                throw new ResponseStatusException(BAD_REQUEST,"Linked enquiry email must match the signed-in customer account");
            customerId=customers.resolve(username);
            customers.lock(customerId);
            associateWithVerifiedConsent(vendor.id(),customerId,now);
        }

        var saved=enquiries.saveAndFlush(new MarketplaceEnquiry(vendor.id(),customerId,productId,productName,
            name,email,phone,message,now,requesterHash,now));
        return new Receipt(saved.id(),saved.createdAt(),saved.status());
    }

    @Transactional
    public List<Lead> leads() {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_VIEW");
        var rows=enquiries.findAllByTenantIdOrderByCreatedAtDescIdDesc(actor.tenantId(),PageRequest.of(0,100));
        audit.save(new VendorAccessAuditEvent(actor.tenantId(),actor.username(),
            "LEAD_INBOX",null,"LEAD_INBOX_VIEWED","Vendor lead inbox viewed"));
        return rows.getContent().stream().map(row->new Lead(row.id(),row.productId(),row.productNameSnapshot(),
            row.contactName(),row.contactEmail(),row.contactPhone(),row.message(),row.createdAt(),row.status())).toList();
    }

    private void associateWithVerifiedConsent(long tenant,long customer,Instant consent) {
        var previous=SecurityContextHolder.getContext();
        var delegated=SecurityContextHolder.createEmptyContext();
        delegated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            VendorCustomerService.APPROVAL_PRINCIPAL,null,
            List.of(new SimpleGrantedAuthority(VendorCustomerService.APPROVAL_AUTHORITY))));
        SecurityContextHolder.setContext(delegated);
        try { relationships.associate(tenant,customer,consent,"CUSTOMER_AUTHENTICATED_MARKETPLACE_ENQUIRY"); }
        finally { SecurityContextHolder.setContext(previous); }
    }

    private String hashAddress(String address) {
        String value=address==null?"":address.strip();
        if(value.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Enquiry origin could not be verified");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String required(String value,int max,String label) {
        String normalized=value==null?"":value.strip();
        if(normalized.isBlank()||normalized.length()>max)
            throw new ResponseStatusException(BAD_REQUEST,label+" must contain 1 to "+max+" characters");
        return normalized;
    }

    private String optional(String value,int max) {
        String normalized=value==null?"":value.strip();
        if(normalized.length()>max||!normalized.matches("[+0-9() .-]*"))
            throw new ResponseStatusException(BAD_REQUEST,"Contact phone is invalid");
        return normalized.isBlank()?null:normalized;
    }
}

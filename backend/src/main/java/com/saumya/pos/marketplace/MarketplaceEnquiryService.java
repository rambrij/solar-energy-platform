package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.customer.VendorAccessAuditService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiry;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiryRepository;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.TenantLifecycleService;
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
import java.util.Objects;
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
    private final VendorAccessAuditService audit;
    private final TenantLifecycleService tenants;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MarketplaceEnquiryService(CatalogService catalogue,MarketplaceEnquiryRepository enquiries,
            CustomerAccess customers,VendorCustomerService relationships,AuthorizationContext authorization,
            VendorAccessAuditService audit,TenantLifecycleService tenants,JdbcTemplate jdbc,Clock clock) {
        this.catalogue=catalogue;this.enquiries=enquiries;this.customers=customers;this.relationships=relationships;
        this.authorization=authorization;this.audit=audit;this.tenants=tenants;this.jdbc=jdbc;this.clock=clock;
    }

    public record Submission(String contactName,String contactEmail,String contactPhone,String message,
                             Long productId,boolean consent,boolean linkToAccount) {}
    public record Receipt(Long id,Instant createdAt,String status) {}
    public record Lead(Long id,Long productId,String productName,String contactName,String contactEmail,String contactPhone,String message,Instant createdAt,String status,String assignedTo,Instant updatedAt,String updatedBy,long version) {}
    public record LeadChange(String status,String employeeUsername,long expectedVersion,String reason) {}

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
        if(!tenants.lockActive(vendor.id())) throw new ResponseStatusException(NOT_FOUND,"Vendor unavailable");
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

    @Transactional(readOnly=true)
    public List<Lead> leads() {
        var actor=authorization.current(); actor.requireVendorMembership(); actor.requirePermission("LEAD_VIEW");
        var rows=actor.hasPermission("LEAD_ASSIGN")
            ?enquiries.findAllByTenantIdOrderByCreatedAtDescIdDesc(actor.tenantId(),PageRequest.of(0,100))
            :enquiries.findAllByTenantIdAndAssignedToOrderByCreatedAtDescIdDesc(actor.tenantId(),actor.username(),PageRequest.of(0,100));
        audit.recordLeadInboxRead(); return rows.getContent().stream().map(this::leadView).toList();
    }
    @Transactional(readOnly=true)
    public List<String> leadAssignees(){
        var a=authorization.current();a.requireVendorMembership();a.requirePermission("LEAD_ASSIGN");
        var names=jdbc.queryForList("SELECT DISTINCT e.username FROM employees e JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE JOIN vendor_role_permissions p ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='LEAD_VIEW' WHERE e.tenant_id=? AND e.enabled=TRUE ORDER BY e.username",String.class,a.tenantId());
        audit.recordLeadAssigneesRead();return names;
    }
    @Transactional
    public Lead updateLead(long id,LeadChange change){
        var a=authorization.current();a.requireVendorMembership();
        if(change==null||change.reason()==null||change.reason().isBlank()||change.reason().strip().length()>500||change.expectedVersion()<0)throw new ResponseStatusException(BAD_REQUEST,"A valid version and reason are required");
        var row=enquiries.findByTenantIdAndIdForUpdate(a.tenantId(),id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Lead unavailable"));
        if(row.version()!=change.expectedVersion())throw new ResponseStatusException(CONFLICT,"Lead changed; reload before saving");
        if(change.employeeUsername()!=null||a.hasPermission("LEAD_ASSIGN")){
            a.requirePermission("LEAD_ASSIGN");String target=change.employeeUsername()==null||change.employeeUsername().isBlank()?null:change.employeeUsername().strip().toLowerCase(Locale.ROOT);
            if(target!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM employees e JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE JOIN vendor_role_permissions p ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='LEAD_VIEW' WHERE e.tenant_id=? AND e.username=? AND e.enabled=TRUE",Boolean.class,a.tenantId(),target)))throw new ResponseStatusException(NOT_FOUND,"Lead assignee unavailable");
            row.assignTo(target,a.username(),clock.instant());
            audit.recordLeadMutation(id,target,target==null?"LEAD_UNASSIGNED":"LEAD_ASSIGNED",change.reason());
        } else {
            a.requirePermission("LEAD_UPDATE");
            if(!Objects.equals(row.assignedTo(),a.username()))throw new ResponseStatusException(NOT_FOUND,"Lead unavailable");
            String next=change.status()==null?"":change.status().strip().toUpperCase(Locale.ROOT);
            boolean valid=switch(row.status()){case "NEW"->Set.of("CONTACTED","LOST").contains(next);case "CONTACTED"->Set.of("QUALIFIED","LOST").contains(next);case "QUALIFIED"->"LOST".equals(next);default->false;};
            if(!valid)throw new ResponseStatusException(CONFLICT,"Lead status transition is not allowed");
            row.changeLeadStatus(next,a.username(),clock.instant());
            audit.recordLeadMutation(id,row.assignedTo(),"LEAD_STATUS_UPDATED",change.reason());
        }
        return leadView(enquiries.saveAndFlush(row));
    }
    private Lead leadView(MarketplaceEnquiry row){return new Lead(row.id(),row.productId(),row.productNameSnapshot(),row.contactName(),row.contactEmail(),row.contactPhone(),row.message(),row.createdAt(),row.status(),row.assignedTo(),row.updatedAt(),row.updatedBy(),row.version());}

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

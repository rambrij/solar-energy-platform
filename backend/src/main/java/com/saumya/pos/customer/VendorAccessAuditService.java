package com.saumya.pos.customer;

import com.saumya.pos.customer.internal.VendorAccessAuditEvent;
import com.saumya.pos.customer.internal.VendorAccessAuditRepository;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorAccessAuditService {
    private final VendorAccessAuditRepository events;
    private final AuthorizationContext authorization;

    public VendorAccessAuditService(VendorAccessAuditRepository events,AuthorizationContext authorization) {
        this.events=events;
        this.authorization=authorization;
    }

    private static final java.util.Set<String> LEAD_MUTATIONS=java.util.Set.of(
        "LEAD_ASSIGNED","LEAD_UNASSIGNED","LEAD_STATUS_UPDATED");

    @Transactional
    public void recordLeadAssigneesRead() {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_ASSIGN");
        events.save(new VendorAccessAuditEvent(actor.tenantId(),actor.username(),
            "LEAD_ASSIGNMENT",null,"LEAD_ASSIGNEES_VIEWED","Assignable lead employees viewed"));
    }

    @Transactional
    public void recordLeadMutation(long leadId,String subjectUsername,String action,String reason) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        if(!LEAD_MUTATIONS.contains(action))
            throw new IllegalArgumentException("Unsupported lead audit action");
        if("LEAD_STATUS_UPDATED".equals(action))actor.requirePermission("LEAD_UPDATE");
        else actor.requirePermission("LEAD_ASSIGN");
        if(reason==null||reason.isBlank()||reason.strip().length()>500)
            throw new IllegalArgumentException("Lead mutation audit reason is required");
        events.save(new VendorAccessAuditEvent(actor.tenantId(),actor.username(),subjectUsername,
            "LEAD",Long.toString(leadId),action,reason.strip()));
    }

    @Transactional
    public void recordLeadInboxRead() {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_VIEW");
        events.save(new VendorAccessAuditEvent(actor.tenantId(),actor.username(),
            "LEAD_INBOX",null,"LEAD_INBOX_VIEWED","Vendor lead inbox viewed"));
    }
}

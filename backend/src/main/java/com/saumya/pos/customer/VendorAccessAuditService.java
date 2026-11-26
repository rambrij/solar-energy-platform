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

    @Transactional
    public void recordLeadInboxRead() {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_VIEW");
        events.save(new VendorAccessAuditEvent(actor.tenantId(),actor.username(),
            "LEAD_INBOX",null,"LEAD_INBOX_VIEWED","Vendor lead inbox viewed"));
    }
}

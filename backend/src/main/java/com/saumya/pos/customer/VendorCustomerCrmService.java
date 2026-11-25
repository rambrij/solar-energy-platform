package com.saumya.pos.customer;

import com.saumya.pos.customer.internal.VendorAccessAuditEvent;
import com.saumya.pos.customer.internal.VendorAccessAuditRepository;
import com.saumya.pos.customer.internal.VendorCustomerCrmNote;
import com.saumya.pos.customer.internal.VendorCustomerCrmNoteRepository;
import com.saumya.pos.customer.internal.VendorCustomerRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VendorCustomerCrmService {
    private static final int MAX_NOTE_LENGTH = 2000;
    private final VendorCustomerRepository relationships;
    private final VendorCustomerCrmNoteRepository crmNotes;
    private final VendorAccessAuditRepository auditEvents;
    private final AuthorizationContext authorization;

    public VendorCustomerCrmService(VendorCustomerRepository relationships,
            VendorCustomerCrmNoteRepository crmNotes, VendorAccessAuditRepository auditEvents,
            AuthorizationContext authorization) {
        this.relationships = relationships;
        this.crmNotes = crmNotes;
        this.auditEvents = auditEvents;
        this.authorization = authorization;
    }

    public record CustomerRelationship(String relationshipId, long customerId, String displayName, Instant consentAt, String provenance) {}
    public record CrmNote(long id, String relationshipId, String note, String createdBy, String updatedBy,
                          Instant createdAt, Instant updatedAt, long version) {}

    private AuthorizationContext.Context actor(String permission) {
        var context = authorization.current();
        context.requireVendorMembership();
        context.requirePermission(permission);
        return context;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer relationship or CRM note unavailable");
    }

    private void requireRelationship(long tenantId, String relationshipId) {
        if (!relationships.existsByTenantAndId(tenantId, relationshipId)) throw unavailable();
    }

    private String noteText(String value) {
        String note = value == null ? "" : value.strip();
        if (note.isBlank() || note.length() > MAX_NOTE_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRM note must contain 1 to 2000 characters");
        return note;
    }

    private String reasonText(String value) {
        String reason = value == null ? "" : value.strip();
        if (reason.isBlank() || reason.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason of 1 to 500 characters is required");
        return reason;
    }

    @Transactional(readOnly = true)
    public List<CustomerRelationship> customers() {
        var context = actor("CUSTOMER_RELATION_VIEW");
        return relationships.findCustomerRelationships(context.tenantId()).stream()
            .map(row -> new CustomerRelationship(row.relationshipId(), row.customerId(), row.displayName(), row.consentAt(), row.provenance()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CrmNote> notes(String relationshipId) {
        var context = actor("CRM_NOTE_VIEW");
        requireRelationship(context.tenantId(), relationshipId);
        return crmNotes.findAllByTenantIdAndTenantCustomerIdOrderByCreatedAtDescIdDesc(context.tenantId(), relationshipId)
            .stream().map(this::view).toList();
    }

    @Transactional
    public CrmNote createNote(String relationshipId, String requestedNote, String requestedReason) {
        var context = actor("CRM_NOTE_CREATE");
        String note = noteText(requestedNote);
        String reason = reasonText(requestedReason);
        requireRelationship(context.tenantId(), relationshipId);
        var saved = crmNotes.saveAndFlush(new VendorCustomerCrmNote(
            context.tenantId(), relationshipId, note, context.username()));
        audit(context, saved.id(), "CRM_NOTE_CREATED", reason);
        return view(saved);
    }

    @Transactional
    public CrmNote updateNote(String relationshipId, long noteId, long expectedVersion,
                              String requestedNote, String requestedReason) {
        var context = actor("CRM_NOTE_UPDATE");
        String note = noteText(requestedNote);
        String reason = reasonText(requestedReason);
        requireRelationship(context.tenantId(), relationshipId);
        var existing = crmNotes.findByTenantIdAndTenantCustomerIdAndId(context.tenantId(), relationshipId, noteId)
            .orElseThrow(this::unavailable);
        if (existing.version() != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CRM note changed; reload before updating");
        existing.update(note, context.username());
        try {
            var saved = crmNotes.saveAndFlush(existing);
            audit(context, saved.id(), "CRM_NOTE_UPDATED", reason);
            return view(saved);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CRM note changed; reload before updating");
        }
    }

    private CrmNote view(VendorCustomerCrmNote note) {
        return new CrmNote(note.id(), note.tenantCustomerId(), note.noteText(), note.createdBy(), note.updatedBy(),
            note.createdAt(), note.updatedAt(), note.version());
    }

    private void audit(AuthorizationContext.Context context, long noteId, String action, String reason) {
        auditEvents.save(new VendorAccessAuditEvent(context.tenantId(), context.username(),
            "CRM_NOTE", Long.toString(noteId), action, reason));
    }
}

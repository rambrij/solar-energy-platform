package com.saumya.pos.customer;

import com.saumya.pos.security.AuthorizationContext;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VendorCustomerCrmService {
    private static final int MAX_NOTE_LENGTH = 2000;
    private final JdbcTemplate jdbc;
    private final AuthorizationContext authorization;

    public VendorCustomerCrmService(JdbcTemplate jdbc, AuthorizationContext authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    public record CustomerRelationship(long customerId, String displayName, Instant consentAt, String provenance) {}
    public record CrmNote(long id, long customerId, String note, String createdBy, String updatedBy,
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

    private void requireRelationship(long tenantId, long customerId) {
        Boolean exists = jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM tenant_customers WHERE tenant_id=? AND customer_id=?",
            Boolean.class, tenantId, customerId);
        if (!Boolean.TRUE.equals(exists)) throw unavailable();
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
        return jdbc.query("""
            SELECT tc.customer_id,
                   COALESCE(NULLIF(cp.display_name, ''), CONCAT('Customer ', tc.customer_id)) AS display_name,
                   tc.consent_at, tc.provenance
            FROM tenant_customers tc
            JOIN customer_profiles cp ON cp.id=tc.customer_id
            WHERE tc.tenant_id=?
            ORDER BY tc.consent_at DESC, tc.customer_id DESC
            """, (rs, row) -> new CustomerRelationship(rs.getLong("customer_id"),
                rs.getString("display_name"), rs.getTimestamp("consent_at").toInstant(), rs.getString("provenance")),
            context.tenantId());
    }

    @Transactional(readOnly = true)
    public List<CrmNote> notes(long customerId) {
        var context = actor("CRM_NOTE_VIEW");
        requireRelationship(context.tenantId(), customerId);
        return jdbc.query("""
            SELECT id, customer_id, note_text, created_by, updated_by, created_at, updated_at, version
            FROM vendor_customer_crm_notes
            WHERE tenant_id=? AND customer_id=?
            ORDER BY created_at DESC, id DESC
            """, (rs, row) -> mapNote(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("note_text"),
                rs.getString("created_by"), rs.getString("updated_by"), rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at"), rs.getLong("version")), context.tenantId(), customerId);
    }

    @Transactional
    public CrmNote createNote(long customerId, String requestedNote, String requestedReason) {
        var context = actor("CRM_NOTE_CREATE");
        String note = noteText(requestedNote);
        String reason = reasonText(requestedReason);
        requireRelationship(context.tenantId(), customerId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO vendor_customer_crm_notes (tenant_id, customer_id, note_text, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, context.tenantId());
            statement.setLong(2, customerId);
            statement.setString(3, note);
            statement.setString(4, context.username());
            statement.setString(5, context.username());
            return statement;
        }, key);
        Number generated = key.getKey();
        if (generated == null) throw new IllegalStateException("CRM note identifier was not generated");
        long noteId = generated.longValue();
        audit(context, noteId, "CRM_NOTE_CREATED", reason);
        return loadNote(context.tenantId(), customerId, noteId);
    }

    @Transactional
    public CrmNote updateNote(long customerId, long noteId, long expectedVersion,
                              String requestedNote, String requestedReason) {
        var context = actor("CRM_NOTE_UPDATE");
        String note = noteText(requestedNote);
        String reason = reasonText(requestedReason);
        requireRelationship(context.tenantId(), customerId);
        Boolean exists = jdbc.queryForObject("""
            SELECT COUNT(*) > 0 FROM vendor_customer_crm_notes
            WHERE tenant_id=? AND customer_id=? AND id=?
            """, Boolean.class, context.tenantId(), customerId, noteId);
        if (!Boolean.TRUE.equals(exists)) throw unavailable();
        int changed = jdbc.update("""
            UPDATE vendor_customer_crm_notes
            SET note_text=?, updated_by=?, updated_at=CURRENT_TIMESTAMP, version=version+1
            WHERE tenant_id=? AND customer_id=? AND id=? AND version=?
            """, note, context.username(), context.tenantId(), customerId, noteId, expectedVersion);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "CRM note changed; reload before updating");
        audit(context, noteId, "CRM_NOTE_UPDATED", reason);
        return loadNote(context.tenantId(), customerId, noteId);
    }

    private CrmNote loadNote(long tenantId, long customerId, long noteId) {
        return jdbc.queryForObject("""
            SELECT id, customer_id, note_text, created_by, updated_by, created_at, updated_at, version
            FROM vendor_customer_crm_notes WHERE tenant_id=? AND customer_id=? AND id=?
            """, (rs, row) -> mapNote(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("note_text"),
                rs.getString("created_by"), rs.getString("updated_by"), rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at"), rs.getLong("version")), tenantId, customerId, noteId);
    }

    private CrmNote mapNote(long id, long customerId, String note, String createdBy, String updatedBy,
                            Timestamp createdAt, Timestamp updatedAt, long version) {
        return new CrmNote(id, customerId, note, createdBy, updatedBy,
            createdAt.toInstant(), updatedAt.toInstant(), version);
    }

    private void audit(AuthorizationContext.Context context, long noteId, String action, String reason) {
        jdbc.update("""
            INSERT INTO vendor_access_audit_events
              (tenant_id, actor_username, resource_type, resource_id, action, reason)
            VALUES (?, ?, 'CRM_NOTE', ?, ?, ?)
            """, context.tenantId(), context.username(), Long.toString(noteId), action, reason);
    }
}

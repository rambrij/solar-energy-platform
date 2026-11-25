package com.saumya.pos.customer.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorCustomerCrmNoteRepository extends JpaRepository<VendorCustomerCrmNote,Long> {
    @Query("""
        select note from VendorCustomerCrmNote note
        where note.tenantId = :tenantId and note.tenantCustomerId = :relationshipId
        order by note.createdAt desc, note.id desc
        """)
    List<VendorCustomerCrmNote> findNotesForRelationship(
        @Param("tenantId") long tenantId, @Param("relationshipId") String relationshipId);

    @Query("""
        select note from VendorCustomerCrmNote note
        where note.tenantId = :tenantId and note.tenantCustomerId = :relationshipId and note.id = :noteId
        """)
    Optional<VendorCustomerCrmNote> findNoteForRelationship(
        @Param("tenantId") long tenantId, @Param("relationshipId") String relationshipId,
        @Param("noteId") long noteId);
}

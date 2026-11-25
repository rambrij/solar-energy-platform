package com.saumya.pos.customer.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendorCustomerCrmNoteRepository extends JpaRepository<VendorCustomerCrmNote,Long> {
    @Query("""
        select note from VendorCustomerCrmNote note
        where note.tenantId = ?1 and note.tenantCustomerId = ?2
        order by note.createdAt desc, note.id desc
        """)
    List<VendorCustomerCrmNote> findNotesForRelationship(long tenantId,String relationshipId);

    @Query("""
        select note from VendorCustomerCrmNote note
        where note.tenantId = ?1 and note.tenantCustomerId = ?2 and note.id = ?3
        """)
    Optional<VendorCustomerCrmNote> findNoteForRelationship(long tenantId,String relationshipId,long noteId);
}

package com.saumya.pos.tenant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TenantLifecycleService {
    record Registration(String slug,String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address,String initialVendorAdminUsername) {}
    record ProfileUpdate(String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address) {}
    record TenantSummary(long id,String slug,String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address,String lifecycleStatus,Long onlineBranchId,String initialVendorAdminUsername,Instant createdAt,Instant updatedAt,long version) {}
    record StatusCount(String status,long count) {}
    record DirectoryPage(String query,int page,int size,long totalElements,int totalPages,List<TenantSummary> items) {}
    record VendorCard(long id,String slug,String name) {}
    record VendorDirectoryPage(String query,int page,int size,long totalElements,int totalPages,List<VendorCard> items) {}
    TenantSummary create(Registration request); TenantSummary updateProfile(long id,ProfileUpdate request); TenantSummary transition(long id,TenantStatus target); TenantSummary assignOnlineBranch(long id,long branchId); TenantSummary get(long id); Optional<TenantSummary> findBySlug(String slug); TenantSummary activeVendor(String slug); List<TenantSummary> activeVendors(); boolean isActive(long id); long onlineBranch(long id); List<StatusCount> statusCounts(); DirectoryPage search(String query,TenantStatus status,int page,int size); VendorDirectoryPage searchActiveVendors(String query,int page,int size);
}

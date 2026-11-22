package com.saumya.pos.retail;

public interface BranchProvisioningService {
    long createInitialBranch(long tenantId,String name);
}

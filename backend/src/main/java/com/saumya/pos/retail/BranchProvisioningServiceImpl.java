package com.saumya.pos.retail;

import com.saumya.pos.retail.internal.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class BranchProvisioningServiceImpl implements BranchProvisioningService {
    private final BranchConfigurationRepository branches;
    private final RetailSequenceRepository sequences;
    public BranchProvisioningServiceImpl(BranchConfigurationRepository branches,RetailSequenceRepository sequences){
        this.branches=branches;this.sequences=sequences;
    }
    @Override public long createInitialBranch(long tenantId,String name){
        long id=sequences.branch().orElseThrow(()->new IllegalStateException("Branch sequence unavailable")).allocate();
        branches.saveAndFlush(new BranchConfiguration(id,tenantId,name));
        return id;
    }
}

package com.saumya.pos.retail;

import com.saumya.pos.retail.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class BranchProvisioningServiceImpl implements BranchProvisioningService {
    private final BranchConfigurationRepository branches;
    private final RetailSequenceRepository sequences;
    private final AuthorizationContext authorization;
    private final JdbcTemplate db;
    public BranchProvisioningServiceImpl(BranchConfigurationRepository branches,RetailSequenceRepository sequences){this(branches,sequences,null,null);}
    public BranchProvisioningServiceImpl(BranchConfigurationRepository branches,RetailSequenceRepository sequences,AuthorizationContext authorization){this(branches,sequences,authorization,null);}
    @Autowired public BranchProvisioningServiceImpl(BranchConfigurationRepository branches,RetailSequenceRepository sequences,AuthorizationContext authorization,JdbcTemplate db){
        this.branches=branches;this.sequences=sequences;this.authorization=authorization;this.db=db;
    }
    @Override public long createInitialBranch(long tenantId,String name){
        if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");
        var actor=authorization.current();if(!actor.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        actor.requirePermission("PLATFORM_TENANT_CREATE");
        if(db==null)throw new ResponseStatusException(SERVICE_UNAVAILABLE,"POS stock-location provisioner unavailable");
        long id=sequences.branch().orElseThrow(()->new IllegalStateException("Branch sequence unavailable")).allocate();
        branches.saveAndFlush(new BranchConfiguration(id,tenantId,name));
        db.update("INSERT INTO pos_stock_locations(tenant_id,branch_id,stock_location_id,location_code,display_name,active) VALUES(?,?,?,'POS_DEFAULT',?,TRUE)",
            tenantId,id,id,name+" POS stock room");
        return id;
    }
}

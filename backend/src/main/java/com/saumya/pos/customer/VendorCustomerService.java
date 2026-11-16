package com.saumya.pos.customer;
import com.saumya.pos.customer.internal.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class VendorCustomerService {
 private final VendorCustomerRepository relationships;
 public VendorCustomerService(VendorCustomerRepository relationships){this.relationships=relationships;}
 /** Caller holds the global customer lock after customer-authenticated consent. */
 @Transactional(propagation=Propagation.MANDATORY) public void associate(long tenant,long customer,Instant consent){if(!relationships.existsByTenantAndCustomer(tenant,customer))relationships.saveAndFlush(new VendorCustomer(tenant,customer,consent));}
}

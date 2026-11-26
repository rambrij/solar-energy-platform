package com.saumya.pos.customer;
import com.saumya.pos.customer.internal.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class VendorCustomerService {
 private final VendorCustomerRepository relationships;
 public static final String APPROVAL_PRINCIPAL = "system:customer-association";
 public static final String APPROVAL_AUTHORITY = "SYSTEM_CUSTOMER_ASSOCIATION";
 public VendorCustomerService(VendorCustomerRepository relationships){this.relationships=relationships;}
 /** Caller holds the global customer lock after customer-authenticated consent. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void associate(long tenant,long customer,Instant consent){associate(tenant,customer,consent,"CUSTOMER_AUTHENTICATED_POS_CONSENT");}
 @Transactional(propagation=Propagation.MANDATORY)
 public void associate(long tenant,long customer,Instant consent,String provenance){
  Authentication actor=SecurityContextHolder.getContext().getAuthentication();
  if(actor==null||!actor.isAuthenticated()||!APPROVAL_PRINCIPAL.equals(actor.getName())
      ||actor.getAuthorities().stream().noneMatch(grant->APPROVAL_AUTHORITY.equals(grant.getAuthority())))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verified customer-association approval required");
  if(tenant<1||customer<1||consent==null||!java.util.Set.of("CUSTOMER_AUTHENTICATED_POS_CONSENT","CUSTOMER_AUTHENTICATED_MARKETPLACE_ENQUIRY").contains(provenance))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid approved customer association required");
  if(!relationships.existsByTenantAndCustomer(tenant,customer))
   relationships.saveAndFlush(new VendorCustomer(tenant,customer,consent,provenance));
 }
}

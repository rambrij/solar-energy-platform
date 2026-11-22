package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import java.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service @Transactional
public class CustomerAssociationService {
 private final CustomerChallengeRepository challenges;private final DraftRepository orders;private final CustomerAccess customers;private final VendorCustomerService relationships;private final CatalogService catalog;private final PosService retail;private final Clock clock;
 public CustomerAssociationService(CustomerChallengeRepository challenges,DraftRepository orders,CustomerAccess customers,VendorCustomerService relationships,CatalogService catalog,PosService retail,Clock clock){this.challenges=challenges;this.orders=orders;this.customers=customers;this.relationships=relationships;this.catalog=catalog;this.retail=retail;this.clock=clock;}
 public record Challenge(String token,Instant expiresAt){}
 public record Preview(String vendorName,BigDecimal total,String status){}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Customer association unavailable");}
 private String hash(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw missing();try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    
    @PreAuthorize("hasAuthority('POS_SALE_CREATE')")
    public Challenge create(String username, String id, boolean customerRequested) {
        if (!customerRequested)
            throw new ResponseStatusException(BAD_REQUEST, "Customer must request account association");
        var scope = retail.scope();
        retail.authorize(scope,username,"POS_SALE_CREATE");
        retail.lockOutlet(scope);
        var order = orders.lock(scope.tenant(), id).orElseThrow(this::missing);
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),scope.branch())||!username.equals(order.cashier())||!"DRAFT".equals(order.status())||order.customer()!=null||!order.expires().isAfter(clock.instant()))throw missing();
  retail.requireOpen(scope,order.session());byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Instant expires=clock.instant().plusSeconds(300);
  challenges.saveAndFlush(new CustomerChallenge(hash(token),order.tenant(),id,expires));retail.draftAudit(scope,"CUSTOMER_ASSOCIATION_REQUEST",id);return new Challenge(token,expires);
 }
 @PreAuthorize("hasAuthority('CUSTOMER')")
 public Preview preview(String username,String token){String tokenHash=hash(token);retail.authorizeCustomer(username);long customer=customers.resolve(username);var challenge=challenges.findById(tokenHash).orElseThrow(this::missing);if(challenge.customer()==null&&!challenge.expires().isAfter(clock.instant())||challenge.customer()!=null&&challenge.customer()!=customer)throw missing();var order=orders.findByTenantIdAndId(challenge.tenant(),challenge.order()).orElseThrow(this::missing);return new Preview(catalog.vendor(order.tenant()).name(),order.total(),challenge.customer()==null?"AWAITING_CUSTOMER_CONSENT":"ASSOCIATED");}
 @PreAuthorize("hasAuthority('CUSTOMER')")
 public Preview approve(String username,String token,boolean consent){
  if(!consent)throw new ResponseStatusException(BAD_REQUEST,"Explicit customer consent required");
  String tokenHash=hash(token);retail.authorizeCustomer(username);
  long customer=customers.resolve(username);customers.lock(customer);var challenge=challenges.locked(tokenHash).orElseThrow(this::missing);
  if(challenge.customer()!=null){if(challenge.customer()!=customer)throw missing();return preview(username,token);}
  if(!challenge.expires().isAfter(clock.instant()))throw missing();var order=orders.lock(challenge.tenant(),challenge.order()).orElseThrow(this::missing);
  if(!"IN_STORE".equals(order.channel())||!"DRAFT".equals(order.status())||order.customer()!=null||!order.expires().isAfter(clock.instant()))throw missing();
  if(!catalog.vendor(order.tenant()).active())throw missing();retail.requireOpenForCustomer(order.tenant(),order.branch(),customer,order.id(),order.cashier(),order.session());
  associateWithVerifiedConsent(order.tenant(),customer,clock.instant());
  order.associate(customer);challenge.approve(customer);orders.flush();challenges.flush();retail.customerDraftAudit(order.tenant(),order.branch(),customer,"CUSTOMER_ASSOCIATION_APPROVE",order.id());return new Preview(catalog.vendor(order.tenant()).name(),order.total(),"ASSOCIATED");
 }

 private void associateWithVerifiedConsent(long tenant,long customer,Instant consent){
  var previous=SecurityContextHolder.getContext();
  var delegated=SecurityContextHolder.createEmptyContext();
  delegated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
      VendorCustomerService.APPROVAL_PRINCIPAL,null,
      List.of(new SimpleGrantedAuthority(VendorCustomerService.APPROVAL_AUTHORITY))));
  SecurityContextHolder.setContext(delegated);
  try{
   relationships.associate(tenant,customer,consent);
  }finally{
   SecurityContextHolder.setContext(previous);
  }
 }

}
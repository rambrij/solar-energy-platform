package com.saumya.pos.catalog;

import com.saumya.pos.catalog.internal.ProductRepository;
import com.saumya.pos.inventory.ProductSerialTrackingPolicy;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Catalogue-owned adapter; inventory consumes only its public policy port. */
@Service
public class CatalogProductSerialTrackingPolicy implements ProductSerialTrackingPolicy {
 private final ProductRepository products;
 private final AuthorizationContext authorization;
 public CatalogProductSerialTrackingPolicy(ProductRepository products,AuthorizationContext authorization){
  this.products=products;this.authorization=authorization;
 }
 @Override
 @Transactional(propagation=Propagation.MANDATORY)
 @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
 public boolean serialTrackingRequired(long tenant,long branch,String actor,long productId){
  var context=authorization.current();
  context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  if(!context.username().equals(actor))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Stock receipt actor must match authenticated identity");
  context.requirePermission("INVENTORY_RECEIVE");
  return products.locked(tenant,productId)
      .map(product->product.serialTrackingRequired())
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product unavailable"));
 }
}

package com.saumya.pos.shared;
/** Transactional checkout fact used by retail to guard shift closure. */
public record PosDraftStateChanged(long tenant,long branch,String cashier,String session,String order,String state){
 public PosDraftStateChanged{if(tenant<1||branch<1||cashier==null||cashier.isBlank()||session==null||session.isBlank()||order==null||order.isBlank()||state==null||!java.util.Set.of("DRAFT","PAID","CANCELLED","EXPIRED").contains(state))throw new IllegalArgumentException("Invalid POS draft state fact");}
}

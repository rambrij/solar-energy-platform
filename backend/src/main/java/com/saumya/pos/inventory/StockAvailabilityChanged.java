package com.saumya.pos.inventory;
/** Transactional public event; no persistence model crosses the inventory boundary. */
public record StockAvailabilityChanged(long tenant,long branch,long product,int available){public StockAvailabilityChanged{if(tenant<1||branch<1||product<1||available<0)throw new IllegalArgumentException("Invalid stock availability event");}}

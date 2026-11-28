package com.saumya.pos.inventory;

/**
 * Inventory-owned read port for the catalogue's serial-tracking setting.
 * Implementations must resolve the setting from the authenticated tenant-owned product.
 */
public interface ProductSerialTrackingPolicy {
 boolean serialTrackingRequired(long tenant,long branch,String actor,long productId);
}

package com.saumya.pos.inventory.internal;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="inventory_receipt_serial_assets")
public class InventoryReceiptSerialAsset {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="tenant_id",nullable=false) private long tenant;
 @Column(name="branch_id",nullable=false) private long branch;
 @Column(name="receipt_id",nullable=false,length=36) private String receiptId;
 @Column(name="product_id",nullable=false) private long product;
 @Column(name="serial_number",nullable=false,length=120) private String serialNumber;
 @Column(name="recorded_by",nullable=false,length=100) private String recordedBy;
 @Column(name="recorded_at",nullable=false) private Instant recordedAt;
 protected InventoryReceiptSerialAsset(){}
 public InventoryReceiptSerialAsset(long tenant,long branch,String receiptId,long product,String serialNumber,String recordedBy,Instant recordedAt){
  this.tenant=tenant;this.branch=branch;this.receiptId=receiptId;this.product=product;this.serialNumber=serialNumber;this.recordedBy=recordedBy;this.recordedAt=recordedAt;
 }
 public long id(){return id;} public String serialNumber(){return serialNumber;} public long product(){return product;}
}

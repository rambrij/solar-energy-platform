package com.saumya.pos.documents;
import java.math.BigDecimal;
import java.util.*;
public record ReceiptPdfData(String orderId,long branch,long number,String seller,String outlet,String issued,BigDecimal total,BigDecimal cash,BigDecimal change,List<Line> lines,List<String> invoices){
 public record Line(String name,int quantity,BigDecimal unit,BigDecimal discount,BigDecimal tax,BigDecimal rate,BigDecimal total){}
 @SuppressWarnings("unchecked") public static ReceiptPdfData saved(Map<String,Object> receipt){
  var invoices=(List<Map<String,Object>>)receipt.get("invoices");String seller=(String)receipt.get("receipt_seller_name"),outlet=(String)receipt.get("receipt_outlet_name");
  if(!invoices.isEmpty()){if(seller==null)seller=(String)invoices.getFirst().get("seller_name");if(outlet==null)outlet=(String)invoices.getFirst().get("outlet_name");}
  var lines=((List<Map<String,Object>>)receipt.get("items")).stream().map(row->new Line((String)row.get("name"),((Number)row.get("quantity")).intValue(),decimal(row,"price"),row.get("discount")==null?null:decimal(row,"discount"),decimal(row,"tax"),decimal(row,"tax_rate"),decimal(row,"total"))).toList();
  Object at=receipt.get("issued_at");String issued=at instanceof java.util.Date date?date.toInstant().toString():at==null?"Not recorded":at.toString();
  return new ReceiptPdfData((String)receipt.get("id"),((Number)receipt.get("branch_id")).longValue(),((Number)receipt.get("receipt_number")).longValue(),seller==null?"Not recorded":seller,outlet==null?"Not recorded":outlet,issued,decimal(receipt,"total"),decimal(receipt,"cash_received"),decimal(receipt,"change_given"),lines,invoices.stream().map(row->(String)row.get("invoice_number")).toList());
 }
 private static BigDecimal decimal(Map<String,Object> row,String key){return new BigDecimal(row.get(key).toString());}
}

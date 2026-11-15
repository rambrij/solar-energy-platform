package com.saumya.pos.reporting;
import com.saumya.pos.reporting.internal.*;
import com.saumya.pos.checkout.SalesReportService;
import com.saumya.pos.returns.RefundReportService;
import com.saumya.pos.retail.PosService;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(isolation=Isolation.REPEATABLE_READ)
public class ReportExportService {
 private final SalesReportService sales;private final RefundReportService refunds;private final PosService retail;private final ReportExportAuditRepository audits;private final CsvWriter csv;private final Clock clock;
 public ReportExportService(SalesReportService sales,RefundReportService refunds,PosService retail,ReportExportAuditRepository audits,CsvWriter csv,Clock clock){this.sales=sales;this.refunds=refunds;this.retail=retail;this.audits=audits;this.csv=csv;this.clock=clock;}
 public record Export(String id,String filename,int rows,byte[] content){}
 private String reason(String reason){if(reason==null||reason.isBlank()||reason.length()>250)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Export reason required (1-250 characters)");return reason.strip();}
 private void scope(PosService.Scope scope,long reportBranch){if(scope.branch()!=reportBranch)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 private void limit(int rows){if(rows>5000)throw new ResponseStatusException(HttpStatus.CONFLICT,"Export exceeds 5000 rows; choose a shorter period");}
 private List<String> prefix(PosService.Scope s,LocalDate from,LocalDate to,Instant at,String section,String bucket){return new ArrayList<>(List.of(Long.toString(s.tenant()),Long.toString(s.branch()),from.toString(),to.toString(),at.toString(),section,bucket));}
 private List<String> headers(String... tail){var names=new ArrayList<>(List.of("tenant_id","branch_id","from_utc","to_utc","generated_at","section","bucket"));names.addAll(List.of(tail));return names;}
 private List<String> salesRow(List<String> prefix,SalesReportService.Totals t){prefix.addAll(List.of(Long.toString(t.paidOrders()),t.sales().toPlainString(),t.beforeTax().toPlainString(),t.tax().toPlainString(),t.cashReceived().toPlainString(),t.changeGiven().toPlainString(),t.cashApplied().toPlainString(),Long.toString(t.units()),Long.toString(t.invoices()),Long.toString(t.unpaidOrders()),t.unpaidAmount().toPlainString(),Long.toString(t.cancelledOrders()),Long.toString(t.expiredOrders()),t.discounts().toPlainString()));return prefix;}
 private List<String> refundRow(List<String> prefix,RefundReportService.Totals t){prefix.addAll(List.of(Long.toString(t.refunds()),Long.toString(t.units()),t.cash().toPlainString(),t.beforeTax().toPlainString(),t.tax().toPlainString()));return prefix;}
 public Export sales(String username,LocalDate from,LocalDate to,String channel,String requestedReason){
  String reason=reason(requestedReason);var s=retail.scope(username);var r=sales.report(username,from,to,channel);scope(s,r.branchId());limit(1+r.channels().size()+r.days().size());var rows=new ArrayList<List<String>>();
  rows.add(salesRow(prefix(s,r.from(),r.to(),r.generatedAt(),"TOTAL",r.channel()),r.totals()));
  for(var c:r.channels())rows.add(salesRow(prefix(s,r.from(),r.to(),r.generatedAt(),"CHANNEL",c.channel()),c.totals()));
  for(var d:r.days())rows.add(salesRow(prefix(s,r.from(),r.to(),r.generatedAt(),"DAY",d.date().toString()),d.totals()));
  return save(s,"SALES",r.channel(),r.from(),r.to(),reason,headers("paid_orders","sales_incl_tax","before_tax","tax","cash_received","change_given","cash_applied","units","invoices","unpaid_orders","unpaid_amount","cancelled_orders","expired_orders","promotion_discounts"),rows);
 }
 public Export refunds(String username,LocalDate from,LocalDate to,String requestedReason){
  String reason=reason(requestedReason);var s=retail.scope(username);var r=refunds.report(username,from,to);scope(s,r.branchId());limit(1+r.days().size()+r.cashiers().size());var rows=new ArrayList<List<String>>();
  rows.add(refundRow(prefix(s,r.from(),r.to(),r.generatedAt(),"TOTAL","CASH_ONLY"),r.totals()));
  for(var d:r.days())rows.add(refundRow(prefix(s,r.from(),r.to(),r.generatedAt(),"DAY",d.date().toString()),d.totals()));
  for(var c:r.cashiers())rows.add(refundRow(prefix(s,r.from(),r.to(),r.generatedAt(),"CASHIER",c.cashier()),c.totals()));
  return save(s,"CASH_REFUNDS","CASH_ONLY",r.from(),r.to(),reason,headers("paid_refunds","units","cash_refunded","before_tax","allocated_tax"),rows);
 }
 private Export save(PosService.Scope s,String kind,String channel,LocalDate from,LocalDate to,String reason,List<String> headers,List<List<String>> rows){
  byte[] bytes=csv.write(headers,rows).getBytes(StandardCharsets.UTF_8);String digest;try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  String id=UUID.randomUUID().toString();audits.saveAndFlush(new ReportExportAudit(id,s.tenant(),s.branch(),s.cashier(),kind,from,to,channel,rows.size(),digest,reason,clock.instant()));
  return new Export(id,kind.toLowerCase(Locale.ROOT)+"-branch-"+s.branch()+"-"+from+"-"+to+"-"+id+".csv",rows.size(),bytes);
 }
}

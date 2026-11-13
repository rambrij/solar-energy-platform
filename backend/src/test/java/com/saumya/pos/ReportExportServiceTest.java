package com.saumya.pos;
import com.saumya.pos.reporting.*;
import com.saumya.pos.reporting.internal.*;
import com.saumya.pos.checkout.SalesReportService;
import com.saumya.pos.returns.RefundReportService;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReportExportServiceTest {
 final LocalDate date=LocalDate.of(2026,11,13);final Clock clock=Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"),ZoneOffset.UTC);
 ReportExportAudit audit;int reads;long branch=9;boolean large;ReportExportService service;
 @BeforeEach void setup(){
  var audits=(ReportExportAuditRepository)Proxy.newProxyInstance(ReportExportAuditRepository.class.getClassLoader(),new Class<?>[]{ReportExportAuditRepository.class},(p,m,args)->{audit=(ReportExportAudit)args[0];return audit;});
  var retail=new PosService(null,null){@Override public Scope scope(String user){return new Scope(7,9,user);}};
  var sales=new SalesReportService(null,null,clock){@Override public Report report(String user,LocalDate from,LocalDate to,String channel){reads++;var zero=BigDecimal.ZERO;var totals=new Totals(1,new BigDecimal("110"),BigDecimal.TEN,new BigDecimal("100"),new BigDecimal("120"),BigDecimal.TEN,new BigDecimal("110"),2,1,0,zero,0,0,zero);return new Report(branch,date,date,"ALL",clock.instant(),totals,List.of(new Channel("IN_STORE",totals)),List.of(new Day(date,totals)));}};
  var refunds=new RefundReportService(null,null,clock){@Override public Report report(String user,LocalDate from,LocalDate to){reads++;var totals=new Totals(1,2,new BigDecimal("110"),new BigDecimal("100"),BigDecimal.TEN);var cashiers=large?Collections.nCopies(5000,new Cashier("manager",totals)):List.of(new Cashier("=unsafe",totals));return new Report(branch,date,date,clock.instant(),totals,List.of(new Day(date,totals)),cashiers);}};
  service=new ReportExportService(sales,refunds,retail,audits,new CsvWriter(),clock);
 }
 @Test void createsScopedSectionsAndAuditsExactBytes() throws Exception {var result=service.sales("manager",date,date,"ALL","Business review");String text=new String(result.content(),StandardCharsets.UTF_8);assertThat(result.rows()).isEqualTo(3);assertThat(text).contains("\"7\",\"9\"","\"TOTAL\"","\"CHANNEL\"","\"DAY\"");assertThat(audit.id()).isEqualTo(result.id());assertThat(audit.digest()).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(result.content())));}
 @Test void refundCashierCellsAreFormulaSafe(){var result=service.refunds("manager",date,date,"Review");assertThat(new String(result.content(),StandardCharsets.UTF_8)).contains("\"'=unsafe\"");assertThat(result.rows()).isEqualTo(3);}
 @Test void missingReasonStopsBeforeReportsOrAudit(){for(String reason:new String[]{null," ","x".repeat(251)})assertThatThrownBy(()->service.sales("manager",date,date,"ALL",reason)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();assertThat(audit).isNull();}
 @Test void scopeMismatchCannotGenerateOrAuditExport(){branch=10;assertThatThrownBy(()->service.sales("manager",date,date,"ALL","Review")).isInstanceOf(ResponseStatusException.class);assertThat(audit).isNull();}
 @Test void excessiveCashierRowsRequireShorterRange(){large=true;assertThatThrownBy(()->service.refunds("manager",date,date,"Review")).isInstanceOf(ResponseStatusException.class);assertThat(audit).isNull();}
}

package com.saumya.pos;

import com.saumya.pos.checkout.OnlineReceiptService;
import com.saumya.pos.documents.ReceiptPdfRenderer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import static org.assertj.core.api.Assertions.*;

class CustomerReceiptPdfRendererTest {
 @Test void rendersSavedCustomerReceiptFactsAndClearlyLabelsMockPaymentAndCustomerCopy() throws Exception {
  var receipt=new OnlineReceiptService.Receipt("order-customer-1","Saved vendor","Saved outlet",
   Instant.parse("2026-11-14T00:00:00Z"),new BigDecimal("110.00"),"MOCK_CARD",
   List.of(new OnlineReceiptService.Line(1,"Saved solar panel",2,new BigDecimal("50.00"),
    new BigDecimal("10.00"),BigDecimal.ZERO,new BigDecimal("10.00"),new BigDecimal("110.00"))),
   List.of(new OnlineReceiptService.Invoice("INV-1","Saved vendor","Saved outlet",new BigDecimal("110.00"))),true,null);
  byte[] pdf=new ReceiptPdfRenderer().renderCustomerCopy(receipt,"customer-copy-reference",Instant.parse("2026-11-15T00:00:00Z"));
  var reader=new PdfReader(pdf);
  String text;
  try{text=new PdfTextExtractor(reader).getTextFromPage(1);}finally{reader.close();}
  assertThat(text).contains("CUSTOMER COPY - Purchase receipt","Saved vendor","Saved outlet","order-customer-1",
   "Saved solar panel","INV-1","TEST PAYMENT - MOCK_CARD - no funds collected","Total paid: 110.00",
   "customer-copy-reference","not a new payment, tax invoice or credit note");
 }

 @Test void rendererRejectsNonCopyAndEmptySavedReceipt() {
  var renderer=new ReceiptPdfRenderer();
  var line=new OnlineReceiptService.Line(1,"Panel",1,BigDecimal.ONE,BigDecimal.ZERO,
   BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ONE);
  var notCopy=new OnlineReceiptService.Receipt("order-1","Seller","Outlet",Instant.EPOCH,
   BigDecimal.ONE,"CASH",List.of(line),List.of(),false,null);
  var empty=new OnlineReceiptService.Receipt("order-1","Seller","Outlet",Instant.EPOCH,
   BigDecimal.ONE,"CASH",List.of(),List.of(),true,null);
  assertThatThrownBy(()->renderer.renderCustomerCopy(notCopy,"ref",Instant.EPOCH)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->renderer.renderCustomerCopy(empty,"ref",Instant.EPOCH)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}

package com.saumya.pos.documents;
import java.io.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.awt.Color;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class ReceiptPdfRenderer {
 private final byte[] fontBytes;
 public ReceiptPdfRenderer(){try(var input=getClass().getClassLoader().getResourceAsStream("liberation/LiberationSans-Regular.ttf")){if(input==null)throw new IllegalStateException("Receipt font missing");fontBytes=input.readAllBytes();}catch(IOException e){throw new IllegalStateException(e);}}

 public byte[] renderCustomerCopy(com.saumya.pos.checkout.OnlineReceiptService.Receipt receipt,
                                  String reference, Instant generated) {
  if (receipt == null || !receipt.copy())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A customer-owned receipt copy is required");
  var lines=receipt.items()==null?java.util.List.<com.saumya.pos.checkout.OnlineReceiptService.Line>of():receipt.items();
  if(lines.isEmpty()||lines.size()>100)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt must have 1-100 saved lines");
  var invoices=receipt.invoices()==null?java.util.List.<com.saumya.pos.checkout.OnlineReceiptService.Invoice>of():receipt.invoices();
  String seller=receipt.seller()==null||receipt.seller().isBlank()?"Seller not recorded":receipt.seller();
  String outlet=receipt.outlet()==null||receipt.outlet().isBlank()?"Outlet not recorded":receipt.outlet();
  String order=receipt.orderId()==null?"Not recorded":receipt.orderId();
  String issued=receipt.issuedAt()==null?"Not recorded":receipt.issuedAt().toString();
  String payment=receipt.paymentMethod()==null?"Not recorded":receipt.paymentMethod();
  var textValues=new java.util.ArrayList<String>();
  textValues.addAll(java.util.List.of(seller,outlet,order,issued,payment));
  lines.forEach(line->{if(line.name()!=null)textValues.add(line.name());});
  invoices.forEach(invoice->{if(invoice.number()!=null)textValues.add(invoice.number());});
  if(receipt.buyer()!=null)for(String value:new String[]{receipt.buyer().name(),receipt.buyer().contact(),receipt.buyer().taxId(),receipt.buyer().address()})if(value!=null)textValues.add(value);
  try{
   BaseFont base=BaseFont.createFont("LiberationSans-Regular.ttf",BaseFont.IDENTITY_H,true,false,fontBytes,null);
   for(String value:textValues)for(int cp:value.codePoints().toArray())
    if(!Character.isWhitespace(cp)&&!base.charExists(cp))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt contains characters unsupported by the configured PDF font; use browser print");
   var normal=new Font(base,10);var small=new Font(base,8);
   var strong=new Font(base,11,Font.BOLD);var title=new Font(base,20,Font.BOLD,new Color(16,47,80));
   var output=new ByteArrayOutputStream();var document=new Document(PageSize.A4,36,36,36,48);
   var writer=PdfWriter.getInstance(document,output);
   writer.setPageEvent(new PdfPageEventHelper(){
    @Override public void onEndPage(PdfWriter pdf,Document doc){
     ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_LEFT,
       new Phrase("CUSTOMER COPY | "+reference,small),doc.left(),24,0);
     ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_RIGHT,
       new Phrase("Page "+pdf.getPageNumber(),small),doc.right(),24,0);
    }
   });
   document.addTitle("Customer receipt copy "+order);document.addCreator("Solar Energy Platform");document.open();
   document.add(new Paragraph("CUSTOMER COPY - Purchase receipt",title));
   document.add(new Paragraph(seller,strong));document.add(new Paragraph(outlet,normal));
   document.add(new Paragraph("Order: "+order+"\nIssued (UTC): "+issued+"\nGenerated (UTC): "+generated+
     "\nDocument reference: "+reference,small));
   if(receipt.buyer()!=null){
    var buyer=receipt.buyer();document.add(new Paragraph("Buyer details saved with this purchase",strong));
    if(buyer.name()!=null)document.add(new Paragraph("Buyer: "+buyer.name(),normal));
    if(buyer.contact()!=null)document.add(new Paragraph("Contact: "+buyer.contact(),normal));
    if(buyer.taxId()!=null)document.add(new Paragraph("Tax ID: "+buyer.taxId(),normal));
    if(buyer.address()!=null)document.add(new Paragraph("Address: "+buyer.address(),normal));
   }
   var table=new PdfPTable(new float[]{34,7,15,14,15,15});
   table.setWidthPercentage(100);table.setSpacingBefore(16);table.setSpacingAfter(12);table.setHeaderRows(1);table.setSplitLate(false);
   for(String heading:new String[]{"Item","Qty","Unit price","Discount","Tax","Line total"}){
    var cell=new PdfPCell(new Phrase(heading,new Font(base,9,Font.BOLD,Color.WHITE)));
    cell.setBackgroundColor(new Color(16,47,80));cell.setPadding(6);table.addCell(cell);
   }
   for(var line:lines){
    String[] row={line.name()==null?"Not recorded":line.name(),Integer.toString(line.quantity()),
      amount(line.unitPrice()),amount(line.discount()),amount(line.tax())+" ("+amount(line.taxRate())+"%)",amount(line.total())};
    for(int i=0;i<row.length;i++){
     var cell=new PdfPCell(new Phrase(row[i],normal));cell.setPadding(6);
     cell.setHorizontalAlignment(i==0?Element.ALIGN_LEFT:Element.ALIGN_RIGHT);
     cell.setBorderColor(new Color(220,229,237));table.addCell(cell);
    }
   }
   document.add(table);
   document.add(new Paragraph(payment.startsWith("MOCK_")
     ?"TEST PAYMENT - "+payment+" - no funds collected":"Payment method: "+payment,strong));
   document.add(new Paragraph("Total paid: "+amount(receipt.total()),strong));
   if(invoices.isEmpty())document.add(new Paragraph("No linked vendor invoice record is available.",small));
   else document.add(new Paragraph("Linked vendor invoice reference(s): "+
     String.join(", ",invoices.stream().map(com.saumya.pos.checkout.OnlineReceiptService.Invoice::number).toList()),small));
   document.add(new Paragraph("This is an audited customer copy of saved receipt facts. It is not a new payment, tax invoice or credit note. Linked invoice references are metadata only.",small));
   document.close();return output.toByteArray();
  }catch(ResponseStatusException e){throw e;}
   catch(Exception e){throw new IllegalStateException("Customer receipt PDF generation failed",e);}
 }

 private String amount(BigDecimal value){return value==null?"Not recorded":value.setScale(2).toPlainString();}
 public byte[] render(ReceiptPdfData receipt,String reference,Instant generated){
  if(receipt.lines().isEmpty()||receipt.lines().size()>100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt must have 1-100 saved lines");
  try{
   BaseFont base=BaseFont.createFont("LiberationSans-Regular.ttf",BaseFont.IDENTITY_H,true,false,fontBytes,null);
   for(String text:java.util.stream.Stream.concat(java.util.stream.Stream.of(receipt.seller(),receipt.outlet(),receipt.orderId(),receipt.issued()),java.util.stream.Stream.concat(receipt.lines().stream().map(ReceiptPdfData.Line::name),receipt.invoices().stream())).toList())for(int c:text.codePoints().toArray())if(!Character.isWhitespace(c)&&!base.charExists(c))throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt has characters unsupported by the configured PDF font; use browser print");
   if(receipt.buyer()!=null)for(String value:new String[]{receipt.buyer().name(),receipt.buyer().contact(),receipt.buyer().taxId(),receipt.buyer().address()})if(value!=null)for(int c:value.codePoints().toArray())if(!Character.isWhitespace(c)&&!base.charExists(c))throw new ResponseStatusException(HttpStatus.CONFLICT,"Buyer details contain characters unsupported by the PDF font; use browser print");
   var normal=new Font(base,10);var small=new Font(base,8);var strong=new Font(base,11,Font.BOLD);var title=new Font(base,20,Font.BOLD,new Color(16,47,80));
   var output=new ByteArrayOutputStream();var document=new Document(PageSize.A4,36,36,36,48);var writer=PdfWriter.getInstance(document,output);
   writer.setPageEvent(new PdfPageEventHelper(){@Override public void onEndPage(PdfWriter pdf,Document doc){ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_LEFT,new Phrase("COPY | "+reference,small),doc.left(),24,0);ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_RIGHT,new Phrase("Page "+pdf.getPageNumber(),small),doc.right(),24,0);}});
   document.addTitle("Receipt copy "+receipt.branch()+"/"+receipt.number());document.addCreator("Solar Energy Platform");document.open();
   document.add(new Paragraph("COPY - Receipt "+receipt.branch()+"/"+receipt.number(),title));document.add(new Paragraph(receipt.seller(),strong));document.add(new Paragraph(receipt.outlet(),normal));
   document.add(new Paragraph("Sale: "+receipt.orderId()+"\nIssued (UTC): "+receipt.issued()+"\nGenerated (UTC): "+generated+"\nDocument reference: "+reference,small));
   if(receipt.buyer()!=null){document.add(new Paragraph("Buyer details at sale",strong));var buyer=receipt.buyer();if(buyer.name()!=null)document.add(new Paragraph("Buyer: "+buyer.name(),normal));if(buyer.contact()!=null)document.add(new Paragraph("Contact: "+buyer.contact(),normal));if(buyer.taxId()!=null)document.add(new Paragraph("Tax ID: "+buyer.taxId(),normal));if(buyer.address()!=null)document.add(new Paragraph("Address: "+buyer.address(),normal));}
   var table=new PdfPTable(new float[]{34,7,15,14,15,15});table.setWidthPercentage(100);table.setSpacingBefore(16);table.setSpacingAfter(12);table.setHeaderRows(1);table.setSplitLate(false);
   for(String heading:new String[]{"Item","Qty","Unit price","Discount","Tax","Line total"}){var cell=new PdfPCell(new Phrase(heading,new Font(base,9,Font.BOLD,Color.WHITE)));cell.setBackgroundColor(new Color(16,47,80));cell.setPadding(6);table.addCell(cell);}
   for(var line:receipt.lines()){String[] values={line.name(),Integer.toString(line.quantity()),amount(line.unit()),amount(line.discount()),amount(line.tax())+" ("+amount(line.rate())+"%)",amount(line.total())};for(int i=0;i<values.length;i++){var cell=new PdfPCell(new Phrase(values[i],normal));cell.setPadding(6);cell.setHorizontalAlignment(i==0?Element.ALIGN_LEFT:Element.ALIGN_RIGHT);cell.setBorderColor(new Color(220,229,237));table.addCell(cell);}}
   document.add(table);if("SPLIT".equals(receipt.paymentMethod()))for(var tender:receipt.tenders())document.add(new Paragraph(tender.method()+": "+amount(tender.appliedAmount())+("CASH".equals(tender.method())?" (received "+amount(tender.cashReceived())+", change "+amount(tender.changeGiven())+")":" - reference "+tender.reference()),normal));document.add(new Paragraph(receipt.paymentMethod().startsWith("MOCK_")?"TEST PAYMENT - "+receipt.paymentMethod()+" - no funds collected":"Tender: "+receipt.paymentMethod(),strong));document.add(new Paragraph("Total paid: "+amount(receipt.total()),strong));document.add(new Paragraph("Cash received: "+amount(receipt.cash())+"    Change given: "+amount(receipt.change()),normal));
   if(receipt.paymentReference()!=null)document.add(new Paragraph("Terminal payment reference: "+receipt.paymentReference(),normal));
   document.add(new Paragraph(receipt.invoices().isEmpty()?"No linked tax invoice.":"Linked tax invoice: "+String.join(", ",receipt.invoices()),small));
   document.add(new Paragraph("Saved sale amounts; discounts are already included in prices and totals. This copy is not a new sale, invoice or credit note. Generation does not prove physical printing or delivery.",small));document.close();return output.toByteArray();
  }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new IllegalStateException("Receipt PDF generation failed",e);}
 }
}

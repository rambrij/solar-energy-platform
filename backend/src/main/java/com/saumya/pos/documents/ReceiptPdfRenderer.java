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
 private String amount(BigDecimal value){return value==null?"Not recorded":value.setScale(2).toPlainString();}
 public byte[] render(ReceiptPdfData receipt,String reference,Instant generated){
  if(receipt.lines().isEmpty()||receipt.lines().size()>100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt must have 1-100 saved lines");
  try{
   BaseFont base=BaseFont.createFont("LiberationSans-Regular.ttf",BaseFont.IDENTITY_H,true,false,fontBytes,null);
   for(String text:java.util.stream.Stream.concat(java.util.stream.Stream.of(receipt.seller(),receipt.outlet(),receipt.orderId(),receipt.issued()),java.util.stream.Stream.concat(receipt.lines().stream().map(ReceiptPdfData.Line::name),receipt.invoices().stream())).toList())for(int c:text.codePoints().toArray())if(!Character.isWhitespace(c)&&!base.charExists(c))throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt has characters unsupported by the configured PDF font; use browser print");
   var normal=new Font(base,10);var small=new Font(base,8);var strong=new Font(base,11,Font.BOLD);var title=new Font(base,20,Font.BOLD,new Color(16,47,80));
   var output=new ByteArrayOutputStream();var document=new Document(PageSize.A4,36,36,36,48);var writer=PdfWriter.getInstance(document,output);
   writer.setPageEvent(new PdfPageEventHelper(){@Override public void onEndPage(PdfWriter pdf,Document doc){ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_LEFT,new Phrase("COPY | "+reference,small),doc.left(),24,0);ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_RIGHT,new Phrase("Page "+pdf.getPageNumber(),small),doc.right(),24,0);}});
   document.addTitle("Receipt copy "+receipt.branch()+"/"+receipt.number());document.addCreator("Solar Energy Platform");document.open();
   document.add(new Paragraph("COPY - Receipt "+receipt.branch()+"/"+receipt.number(),title));document.add(new Paragraph(receipt.seller(),strong));document.add(new Paragraph(receipt.outlet(),normal));
   document.add(new Paragraph("Sale: "+receipt.orderId()+"\nIssued (UTC): "+receipt.issued()+"\nGenerated (UTC): "+generated+"\nDocument reference: "+reference,small));
   var table=new PdfPTable(new float[]{34,7,15,14,15,15});table.setWidthPercentage(100);table.setSpacingBefore(16);table.setSpacingAfter(12);table.setHeaderRows(1);table.setSplitLate(false);
   for(String heading:new String[]{"Item","Qty","Unit price","Discount","Tax","Line total"}){var cell=new PdfPCell(new Phrase(heading,new Font(base,9,Font.BOLD,Color.WHITE)));cell.setBackgroundColor(new Color(16,47,80));cell.setPadding(6);table.addCell(cell);}
   for(var line:receipt.lines()){String[] values={line.name(),Integer.toString(line.quantity()),amount(line.unit()),amount(line.discount()),amount(line.tax())+" ("+amount(line.rate())+"%)",amount(line.total())};for(int i=0;i<values.length;i++){var cell=new PdfPCell(new Phrase(values[i],normal));cell.setPadding(6);cell.setHorizontalAlignment(i==0?Element.ALIGN_LEFT:Element.ALIGN_RIGHT);cell.setBorderColor(new Color(220,229,237));table.addCell(cell);}}
   document.add(table);document.add(new Paragraph(receipt.paymentMethod().startsWith("MOCK_")?"TEST PAYMENT - "+receipt.paymentMethod()+" - no funds collected":"Tender: Cash",strong));document.add(new Paragraph("Total paid: "+amount(receipt.total()),strong));document.add(new Paragraph("Cash received: "+amount(receipt.cash())+"    Change given: "+amount(receipt.change()),normal));
   document.add(new Paragraph(receipt.invoices().isEmpty()?"No linked tax invoice.":"Linked tax invoice: "+String.join(", ",receipt.invoices()),small));
   document.add(new Paragraph("Saved sale amounts; discounts are already included in prices and totals. This copy is not a new sale, invoice or credit note. Generation does not prove physical printing or delivery.",small));document.close();return output.toByteArray();
  }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new IllegalStateException("Receipt PDF generation failed",e);}
 }
}

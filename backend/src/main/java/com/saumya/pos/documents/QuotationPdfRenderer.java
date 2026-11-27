package com.saumya.pos.documents;

import com.saumya.pos.documents.QuotationPdfData;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Produces a printable copy from immutable quotation revision snapshots only. */
@Component
public class QuotationPdfRenderer {
    private final byte[] fontBytes;

    public QuotationPdfRenderer() {
        try(var input=getClass().getClassLoader().getResourceAsStream("liberation/LiberationSans-Regular.ttf")) {
            if(input==null) throw new IllegalStateException("Quotation PDF font missing");
            fontBytes=input.readAllBytes();
        } catch(IOException failure) {
            throw new IllegalStateException("Quotation PDF font could not be loaded",failure);
        }
    }

    public byte[] render(QuotationPdfData quotation,String reference,Instant generatedAt) {
        if(quotation==null||quotation.quotationId()<=0||quotation.revisionNumber()<1
                ||quotation.items()==null||quotation.items().isEmpty()||quotation.items().size()>100
                ||reference==null||reference.isBlank()||generatedAt==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A saved quotation revision is required");
        try {
            BaseFont base=BaseFont.createFont("LiberationSans-Regular.ttf",BaseFont.IDENTITY_H,true,false,fontBytes,null);
            java.util.List<String> textValues=new java.util.ArrayList<>();
            textValues.add(quotation.status());
            textValues.add(quotation.currency());
            if(quotation.terms()!=null)textValues.add(quotation.terms());
            quotation.items().forEach(line->textValues.add(line.name()));
            for(String value:textValues) {
                if(value==null)continue;
                for(int cp:value.codePoints().toArray())
                    if(!Character.isWhitespace(cp)&&!base.charExists(cp))
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Quotation contains characters unsupported by the configured PDF font");
            }
            Font normal=new Font(base,9);
            Font small=new Font(base,8);
            Font strong=new Font(base,10,Font.BOLD);
            Font heading=new Font(base,20,Font.BOLD,new Color(16,47,80));
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            Document document=new Document(PageSize.A4,36,36,42,48);
            PdfWriter writer=PdfWriter.getInstance(document,output);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter pdf,Document doc) {
                    ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_LEFT,
                        new Phrase("QUOTATION COPY | "+reference,small),doc.left(),24,0);
                    ColumnText.showTextAligned(pdf.getDirectContent(),Element.ALIGN_RIGHT,
                        new Phrase("Page "+pdf.getPageNumber(),small),doc.right(),24,0);
                }
            });
            document.addTitle("Quotation "+quotation.quotationId()+" revision "+quotation.revisionNumber());
            document.addCreator("Solar Energy Platform");
            document.open();
            document.add(new Paragraph("Solar quotation",heading));
            document.add(new Paragraph("Quotation #"+quotation.quotationId()
                +" · Revision "+quotation.revisionNumber(),strong));
            document.add(new Paragraph("Status: "+quotation.status()
                +"\nIssued (UTC): "+(quotation.sentAt()==null?"Not recorded":quotation.sentAt())
                +"\nValid until (UTC): "+quotation.validUntil()
                +"\nGenerated (UTC): "+generatedAt
                +"\nDocument reference: "+reference,small));

            PdfPTable table=new PdfPTable(new float[]{33,7,14,12,14,20});
            table.setWidthPercentage(100);
            table.setSpacingBefore(16);
            table.setSpacingAfter(12);
            table.setHeaderRows(1);
            table.setSplitLate(false);
            for(String label:new String[]{"Item","Qty","List price","Unit price","Tax","Line total"}) {
                PdfPCell cell=new PdfPCell(new Phrase(label,new Font(base,8,Font.BOLD,Color.WHITE)));
                cell.setBackgroundColor(new Color(16,47,80));
                cell.setPadding(6);
                table.addCell(cell);
            }
            for(var line:quotation.items()) {
                String[] values={line.name(),Integer.toString(line.quantity()),amount(line.listPrice()),
                    amount(line.unitPrice()),amount(line.tax())+" ("+amount(line.taxRate())+"%)",
                    amount(line.total())};
                for(int index=0;index<values.length;index++) {
                    PdfPCell cell=new PdfPCell(new Phrase(values[index],normal));
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(index==0?Element.ALIGN_LEFT:Element.ALIGN_RIGHT);
                    cell.setBorderColor(new Color(220,229,237));
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.add(new Paragraph("Subtotal: "+quotation.currency()+" "+amount(quotation.subtotal()),normal));
            document.add(new Paragraph("Tax: "+quotation.currency()+" "+amount(quotation.taxTotal()),normal));
            document.add(new Paragraph("Total: "+quotation.currency()+" "+amount(quotation.total()),strong));
            if(quotation.terms()!=null&&!quotation.terms().isBlank()) {
                document.add(new Paragraph("Terms and conditions",strong));
                document.add(new Paragraph(quotation.terms(),normal));
            }
            document.add(new Paragraph(
                "This document is a copy of the saved quotation revision. It is not an invoice, receipt or payment confirmation. No payment is collected by issuing this document.",
                small));
            document.close();
            return output.toByteArray();
        } catch(ResponseStatusException failure) {
            throw failure;
        } catch(Exception failure) {
            throw new IllegalStateException("Quotation PDF generation failed",failure);
        }
    }

    private String amount(BigDecimal value) {
        return value==null?"Not recorded":value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

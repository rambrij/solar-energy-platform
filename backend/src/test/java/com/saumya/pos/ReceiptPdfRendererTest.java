package com.saumya.pos;
import com.saumya.pos.documents.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReceiptPdfRendererTest {
 private ReceiptPdfData data(List<ReceiptPdfData.Line> lines){return new ReceiptPdfData("order-1",1,9,"Saved seller","Saved outlet","2026-11-13T12:00:00Z",new BigDecimal("99"),new BigDecimal("100"),BigDecimal.ONE,lines,List.of("INV-1-9"));}
 private ReceiptPdfData.Line line(String name){return new ReceiptPdfData.Line(name,1,new BigDecimal("90"),BigDecimal.TEN,new BigDecimal("9"),BigDecimal.TEN,new BigDecimal("99"));}
 private String text(byte[] bytes) throws Exception {try(var reader=new PdfReader(bytes)){StringBuilder text=new StringBuilder();var parser=new PdfTextExtractor(reader);for(int page=1;page<=reader.getNumberOfPages();page++)text.append(parser.getTextFromPage(page));return text.toString();}}
 @Test void embedsSavedFactsAndCopyIdentityInAReadablePdf() throws Exception {byte[] bytes=new ReceiptPdfRenderer().render(data(List.of(line("Solar panel μ"))),"document-reference",Instant.parse("2026-11-14T00:00:00Z"));assertThat(new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");assertThat(text(bytes)).contains("COPY - Receipt 1/9","Saved seller","Saved outlet","Solar panel μ","99.00","100.00","INV-1-9","document-reference");}
 @Test void paginatesLongReceiptsWithEveryLineAndCopyFooter() throws Exception {var lines=new ArrayList<ReceiptPdfData.Line>();for(int i=0;i<100;i++)lines.add(line(String.format("Solar item %03d ",i)+"long mounting structure and panel description ".repeat(3)));byte[] bytes=new ReceiptPdfRenderer().render(data(lines),"many-pages",Instant.EPOCH);try(var reader=new PdfReader(bytes)){assertThat(reader.getNumberOfPages()).isGreaterThan(1);var parser=new PdfTextExtractor(reader);for(int page=1;page<=reader.getNumberOfPages();page++)assertThat(parser.getTextFromPage(page)).contains("COPY | many-pages","Page "+page);}assertThat(text(bytes)).contains("Solar item 000","Solar item 099");java.nio.file.Files.write(java.nio.file.Path.of("target/receipt-pdf-review.pdf"),bytes);}
 @Test void rejectsUnsupportedGlyphsAndUnboundedReceiptsRatherThanDroppingFacts(){var renderer=new ReceiptPdfRenderer();assertThatThrownBy(()->renderer.render(data(List.of(line("Solar 😀"))),"ref",Instant.EPOCH)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->renderer.render(data(List.of()),"ref",Instant.EPOCH)).isInstanceOf(ResponseStatusException.class);}
}

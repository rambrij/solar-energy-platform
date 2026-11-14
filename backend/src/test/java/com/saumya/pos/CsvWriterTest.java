package com.saumya.pos;
import com.saumya.pos.reporting.CsvWriter;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CsvWriterTest {
 final CsvWriter csv=new CsvWriter();
 @Test void quotesMultilineUnicodeAndEmbeddedQuotes(){assertThat(csv.cell("Solar, \"Panel\"\nऊर्जा")).isEqualTo("\"Solar, \"\"Panel\"\"\nऊर्जा\"");assertThat(csv.write(List.of("name"),List.of(List.of("Panel")))).isEqualTo("\uFEFF\"name\"\r\n\"Panel\"\r\n");}
 @Test void neutralizesFormulaPrefixesIncludingHiddenWhitespace(){for(String value:List.of("=1+1","+cmd","-1+cmd","@SUM(A1)","  =1","\t=1","\r=1","\n=1","\uFEFF=1","\u200B=1",String.valueOf((char)0)+"=1",String.valueOf((char)11)+"=1"))assertThat(csv.cell(value)).startsWith("\"'");}
 @Test void keepsNumericValuesAndOrdinaryTextIntact(){assertThat(csv.cell("-10.50")).isEqualTo("\"-10.50\"");assertThat(csv.cell("2026-11-13")).isEqualTo("\"2026-11-13\"");assertThat(csv.cell(null)).isEqualTo("\"\"");}
 @Test void rejectsMalformedColumnCounts(){assertThatThrownBy(()->csv.write(List.of("one","two"),List.of(List.of("one")))).isInstanceOf(IllegalArgumentException.class);}
}

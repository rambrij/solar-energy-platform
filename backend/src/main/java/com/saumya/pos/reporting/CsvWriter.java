package com.saumya.pos.reporting;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class CsvWriter {
 public String cell(String value){
  String text=value==null?"":value;
  int first=0;while(first<text.length()){int cp=text.codePointAt(first);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&!Character.isISOControl(cp)&&Character.getType(cp)!=Character.FORMAT)break;first+=Character.charCount(cp);}
  boolean control=!text.isEmpty()&&Character.isISOControl(text.codePointAt(0));
  boolean formula=first<text.length()&&"=+-@".indexOf(text.charAt(first))>=0&&!text.matches("-?[0-9]+(\\.[0-9]+)?");
  if(control||formula)text="'"+text;
  return "\""+text.replace("\"","\"\"")+"\"";
 }
 public String write(List<String> header,List<List<String>> rows){
  var out=new StringBuilder("\uFEFF");append(out,header);for(var row:rows){if(row.size()!=header.size())throw new IllegalArgumentException("CSV column count mismatch");append(out,row);}return out.toString();
 }
 private void append(StringBuilder out,List<String> row){out.append(row.stream().map(this::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");}
}

package com.saumya.pos.checkout;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
@Component
public class BuyerDetailsPolicy {
 public record Input(@Size(max=160) String name,@Size(max=160) String contact,@Size(max=80) String taxId,@Size(max=1000) String address){}
 private String clean(String value,int limit,boolean multiline){if(value==null||value.isBlank())return null;if(value.length()>limit||value.codePoints().anyMatch(c->Character.isISOControl(c)&&!(multiline&&(c=='\n'||c=='\r'))))throw new ResponseStatusException(BAD_REQUEST,"Buyer details exceed the allowed size or contain unsupported control characters");return value.strip();}
 public Input normalize(Input input,boolean required){
  Input value=input==null?null:new Input(clean(input.name(),160,false),clean(input.contact(),160,false),clean(input.taxId(),80,false),clean(input.address(),1000,true));
  if(value!=null&&value.name()==null&&value.contact()==null&&value.taxId()==null&&value.address()==null)value=null;
  if(required&&(value==null||value.name()==null||value.address()==null))throw new com.saumya.pos.shared.DomainProblem("BUYER_DETAILS_REQUIRED","Buyer name and address are required by this outlet's approved tax-invoice configuration");return value;
 }
 public String fingerprint(Input input){var value=normalize(input,false);if(value==null)return "";var result=new StringBuilder();for(String part:new String[]{value.name(),value.contact(),value.taxId(),value.address()})result.append(part==null?"-1:":part.length()+":"+part);return result.toString();}
}

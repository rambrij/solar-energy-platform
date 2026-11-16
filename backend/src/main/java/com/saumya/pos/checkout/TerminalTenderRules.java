package com.saumya.pos.checkout;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Component
public class TerminalTenderRules {
 public void independent(String cashier,String reviewer){if(cashier.equals(reviewer))throw new ResponseStatusException(FORBIDDEN,"A different authorized supervisor must verify the tender");}
 public String token(String value,int maximum){if(value==null||value.length()>maximum||!value.matches("[A-Za-z0-9._-]{4,"+maximum+"}")||sensitive(value))throw new ResponseStatusException(BAD_REQUEST,"Use an opaque reference without card or payment credentials");return value;}
 public String reason(String value){if(value==null||value.isBlank()||value.length()>250||value.codePoints().anyMatch(Character::isISOControl)||sensitive(value))throw new ResponseStatusException(BAD_REQUEST,"Plain verification reason without payment credentials required");return value.strip();}
 private boolean sensitive(String value){return java.util.regex.Pattern.compile("[0-9](?:[\\s./:_-]*[0-9]){12,18}|(?i)\\b(?:cvv|cvc|pin|pan|track)\\b").matcher(value).find();}
 public void decision(String decision){if(!"CONFIRM".equals(decision)&&!"FAIL".equals(decision))throw new ResponseStatusException(BAD_REQUEST,"Choose CONFIRM or FAIL after verifying external evidence");}
}

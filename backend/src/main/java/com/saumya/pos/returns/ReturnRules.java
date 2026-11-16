package com.saumya.pos.returns;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component
public class ReturnRules {
 public boolean eligible(Instant paid,int days,Instant now){return days>=1&&days<=365&&paid!=null&&!now.isBefore(paid)&&now.isBefore(paid.plus(Duration.ofDays(days)));}
 public void requireWindow(Instant paid,int days,Instant now){if(days<1||days>365)conflict("Returns are not configured for this branch");if(paid==null||now.isBefore(paid)||!now.isBefore(paid.plus(Duration.ofDays(days))))conflict("The configured return window has ended");}
 public void quantity(int sold,long already,int requested){if(requested<1||sold<0||already<0||already>(long)sold-requested)conflict("Return quantity exceeds the unreturned sold quantity");}
 public void independent(String requester,String actor){if(requester.equals(actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A different authorized employee must review/inspect this return");}
 public void disposition(String condition,String disposition){if(condition==null||disposition==null||!Set.of("SELLABLE","DAMAGED","INCOMPLETE").contains(condition)||!Set.of("RESTOCK","QUARANTINE").contains(disposition))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid condition and disposition required");if(disposition.equals("RESTOCK")&&!condition.equals("SELLABLE"))conflict("Damaged or incomplete goods must remain quarantined");}
 private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}

package com.saumya.pos;
import com.saumya.pos.returns.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReturnServiceTest {
 final ReturnService service=new ReturnService(null,null,null,null,null,null,new ReturnRules(),Clock.systemUTC());
 @Test void badCreateCommandsDoNotReachIdentityOrPersistence(){
  var item=new ReturnService.Item(1,1);
  for(var command:List.of(new ReturnService.Create("order","session",List.of(item)," ","ORIGINAL_RECEIPT","Checked"),new ReturnService.Create("order","session",List.of(item,item),"Reason","ORIGINAL_RECEIPT","Checked"),new ReturnService.Create("order","session",Arrays.asList((ReturnService.Item)null),"Reason","ORIGINAL_RECEIPT","Checked"),new ReturnService.Create("order","session",List.of(item),"Reason","UNKNOWN","Checked")))assertThatThrownBy(()->service.create("alice","return-key",command)).isInstanceOf(ResponseStatusException.class);
 }
 @Test void missingRetryIdentityAndInvalidInspectionStopBeforeDatabaseCalls(){
  assertThatThrownBy(()->service.create("alice","bad",new ReturnService.Create("order","session",List.of(new ReturnService.Item(1,1)),"Reason","ORIGINAL_RECEIPT","Checked"))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.inspect("alice","return",new ReturnService.Inspect(Arrays.asList((ReturnService.Inspection)null)))).isInstanceOf(ResponseStatusException.class);
 }
}

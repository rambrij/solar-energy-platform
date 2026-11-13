package com.saumya.pos.returns;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReturnQueueService {
 private final ReturnRepository returns;private final PosService retail;private final Clock clock;
 public ReturnQueueService(ReturnRepository returns,PosService retail,Clock clock){this.returns=returns;this.retail=retail;this.clock=clock;}
 public record Queue(long branchId,String state,LocalDate from,LocalDate to,String reference,int page,int size,long totalElements,int totalPages,List<ReturnQueueRow> items){}
 public Queue queue(String username,String state,LocalDate requestedFrom,LocalDate requestedTo,String requestedReference,int page,int size){
  LocalDate to=requestedTo==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):requestedTo;
  if(to.getYear()<1970||to.getYear()>9998)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose dates between 1970 and 9998");
  LocalDate from=requestedFrom==null?to.minusDays(29):requestedFrom;
  String reference=requestedReference==null?"":requestedReference.strip();
  if(!Set.of("ALL","REQUESTED","APPROVED","REJECTED","INSPECTED").contains(state==null?"":state)||from.getYear()<1970||to.getYear()>9998||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365||page<0||page>10000||size<1||size>48||!reference.matches("[A-Za-z0-9-]{0,36}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid state, reference, inclusive UTC range up to 366 days, page 0-10000 and size 1-48");
  var scope=retail.scope(username);
  var result=returns.queue(scope.tenant(),scope.branch(),state,from.atStartOfDay(ZoneOffset.UTC).toInstant(),to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),reference.toLowerCase(Locale.ROOT)+"%",PageRequest.of(page,size));
  return new Queue(scope.branch(),state,from,to,reference,page,size,result.getTotalElements(),result.getTotalPages(),result.getContent());
 }
}

package com.saumya.pos.retail;
import com.saumya.pos.retail.internal.ClosedShiftRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ShiftHistoryService {
 private final ClosedShiftRepository shifts;private final PosService retail;private final Clock clock;
 public ShiftHistoryService(ClosedShiftRepository shifts,PosService retail,Clock clock){this.shifts=shifts;this.retail=retail;this.clock=clock;}
 public record Row(String id,long registerId,BigDecimal openingFloat,BigDecimal expectedCash,BigDecimal countedCash,BigDecimal variance,Instant closedAt){}
 public record History(long branchId,LocalDate from,LocalDate to,String reference,int page,int size,long totalElements,int totalPages,List<Row> items){}
 public History history(String username,LocalDate requestedFrom,LocalDate requestedTo,String requestedReference,int page,int size){
  LocalDate to=requestedTo==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):requestedTo;
  if(to.getYear()<1970||to.getYear()>9998)throw invalid();
  LocalDate from=requestedFrom==null?to.minusDays(29):requestedFrom;String reference=requestedReference==null?"":requestedReference.strip();
  if(from.getYear()<1970||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365||page<0||page>10000||size<1||size>48||!reference.matches("[A-Za-z0-9-]{0,36}"))throw invalid();
  var scope=retail.scope(username);var result=shifts.history(scope.tenant(),scope.branch(),scope.cashier(),from.atStartOfDay(ZoneOffset.UTC).toInstant(),to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),reference.toLowerCase(Locale.ROOT)+"%",PageRequest.of(page,size));
  return new History(scope.branch(),from,to,reference,page,size,result.getTotalElements(),result.getTotalPages(),result.getContent());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid session reference, inclusive UTC range up to 366 days, page 0-10000 and size 1-48");}
}

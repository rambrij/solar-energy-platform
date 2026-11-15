package com.saumya.pos.returns;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReturnAuditService {
 private final ReturnRepository cases;private final ReturnEventRepository events;private final PosService retail;
 public ReturnAuditService(ReturnRepository cases,ReturnEventRepository events,PosService retail){this.cases=cases;this.events=events;this.retail=retail;}
 public record Event(String id,String actor,String action,Instant createdAt){}
 public record History(String returnId,String state,int page,int size,long totalElements,int totalPages,List<Event> items){}
 public History history(String username,String returned,int page,int size){
  if(returned==null||returned.isBlank()||returned.length()>36)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Return unavailable");
  if(page<0||page>10000||size<1||size>48)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose page 0-10000 and size 1-48");
  var s=retail.scope(username);var record=cases.findByTenantAndBranchAndId(s.tenant(),s.branch(),returned).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Return unavailable"));
  var result=events.history(s.tenant(),s.branch(),record.id(),PageRequest.of(page,size));
  return new History(record.id(),record.state(),page,size,result.getTotalElements(),result.getTotalPages(),result.stream().map(e->new Event(e.id(),e.actor(),e.action(),e.createdAt())).toList());
 }
}

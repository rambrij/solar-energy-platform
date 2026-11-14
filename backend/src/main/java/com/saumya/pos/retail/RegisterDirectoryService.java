package com.saumya.pos.retail;
import com.saumya.pos.retail.internal.RegisterDirectoryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class RegisterDirectoryService {
 private final RegisterDirectoryRepository registers;private final PosService retail;
 public RegisterDirectoryService(RegisterDirectoryRepository registers,PosService retail){this.registers=registers;this.retail=retail;}
 public record Register(long id,boolean available){}
 public record Directory(long branchId,int page,int size,long totalElements,int totalPages,List<Register> items){}
 public Directory directory(String username,int page,int size){
  if(page<0||page>10000||size<1||size>48)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose page 0-10000 and size 1-48");
  var scope=retail.scope(username);var result=registers.directory(scope.tenant(),scope.branch(),PageRequest.of(page,size));
  return new Directory(scope.branch(),page,size,result.getTotalElements(),result.getTotalPages(),result.getContent());
 }
}

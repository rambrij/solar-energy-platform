package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.VendorRepository;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class VendorDirectoryService {
 private final VendorRepository vendors;
 public VendorDirectoryService(VendorRepository vendors){this.vendors=vendors;}
 public record VendorCard(long id,String slug,String name){}
 public record Directory(String query,int page,int size,long totalElements,int totalPages,List<VendorCard> items){}
 public Directory search(String requested,int page,int size){
  String query=requested==null?"":requested.strip();
  if(query.length()>120||query.codePoints().anyMatch(Character::isISOControl)||page<0||page>10000||size<1||size>48)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a search up to 120 characters, page 0-10000 and size 1-48");
  String term="%"+query.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  var result=vendors.directory(term,PageRequest.of(page,size));
  return new Directory(query,page,size,result.getTotalElements(),result.getTotalPages(),result.getContent());
 }
}

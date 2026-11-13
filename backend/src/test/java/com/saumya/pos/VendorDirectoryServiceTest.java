package com.saumya.pos;
import com.saumya.pos.catalog.VendorDirectoryService;
import com.saumya.pos.catalog.internal.VendorRepository;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class VendorDirectoryServiceTest {
 int reads;Object[] arguments;VendorDirectoryService service;
 @BeforeEach void setup(){var repo=(VendorRepository)Proxy.newProxyInstance(VendorRepository.class.getClassLoader(),new Class<?>[]{VendorRepository.class},(p,m,args)->{reads++;arguments=args;return new PageImpl<>(List.of(new VendorDirectoryService.VendorCard(7,"solar","Solar Store")),(Pageable)args[1],1);});service=new VendorDirectoryService(repo);}
 @Test void normalizesLiteralSearchAndReturnsOnlyPublicCards(){var result=service.search("  SUN%_!  ",0,12);assertThat(arguments).containsExactly("%sun!%!_!!%",PageRequest.of(0,12));assertThat(result.query()).isEqualTo("SUN%_!");assertThat(result.items().getFirst().slug()).isEqualTo("solar");assertThat(result.totalElements()).isEqualTo(1);}
 @Test void defaultSearchIsBounded(){service.search(null,2,24);assertThat(arguments).containsExactly("%%",PageRequest.of(2,24));}
 @Test void rejectsInvalidInputsBeforeReading(){for(String query:List.of("x".repeat(121),"bad\nquery"))assertThatThrownBy(()->service.search(query,0,12)).isInstanceOf(ResponseStatusException.class);for(int page:List.of(-1,10001))assertThatThrownBy(()->service.search("",page,12)).isInstanceOf(ResponseStatusException.class);for(int size:List.of(0,49))assertThatThrownBy(()->service.search("",0,size)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}

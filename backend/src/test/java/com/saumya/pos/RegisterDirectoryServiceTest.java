package com.saumya.pos;
import com.saumya.pos.retail.*;
import com.saumya.pos.retail.internal.RegisterDirectoryRepository;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class RegisterDirectoryServiceTest {
 int reads;Object[] arguments;RegisterDirectoryService service;
 @BeforeEach void setup(){var repository=(RegisterDirectoryRepository)Proxy.newProxyInstance(RegisterDirectoryRepository.class.getClassLoader(),new Class<?>[]{RegisterDirectoryRepository.class},(p,m,args)->{reads++;arguments=args;return new PageImpl<>(List.of(new RegisterDirectoryService.Register(3,true)),(Pageable)args[2],1);});var retail=new PosService(null,null){@Override public Scope scope(String user){return new Scope(7,9,user);}};service=new RegisterDirectoryService(repository,retail);}
 @Test void usesCanonicalTenantBranchAndBoundedPage(){var r=service.directory("cashier",0,12);assertThat(arguments).containsExactly(7L,9L,PageRequest.of(0,12));assertThat(r.branchId()).isEqualTo(9);assertThat(r.items()).containsExactly(new RegisterDirectoryService.Register(3,true));}
 @Test void invalidBoundsStopBeforeRepositoryAccess(){for(int page:List.of(-1,10001))assertThatThrownBy(()->service.directory("cashier",page,12)).isInstanceOf(ResponseStatusException.class);for(int size:List.of(0,49))assertThatThrownBy(()->service.directory("cashier",0,size)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}

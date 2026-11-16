package com.saumya.pos;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.customer.internal.VendorCustomerRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class VendorCustomerServiceTest {
 @Test void repeatedConsentDoesNotDuplicateOrQueryAnotherVendorRelationship(){var saved=new AtomicInteger();var repository=(VendorCustomerRepository)Proxy.newProxyInstance(VendorCustomerRepository.class.getClassLoader(),new Class<?>[]{VendorCustomerRepository.class},(p,m,a)->{if(m.getName().equals("existsByTenantAndCustomer")){assertThat(a).containsExactly(7L,9L);return saved.get()>0;}if(m.getName().equals("saveAndFlush")){saved.incrementAndGet();return a[0];}throw new AssertionError(m.getName());});var service=new VendorCustomerService(repository);service.associate(7,9,Instant.now());service.associate(7,9,Instant.now());assertThat(saved.get()).isEqualTo(1);}
}

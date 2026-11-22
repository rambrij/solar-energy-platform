package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BuyerDetailsServiceTest {
 @Test void omittedGuestDetailsNeverWriteASnapshot(){new BuyerDetailsService(null,new BuyerDetailsPolicy(),Clock.systemUTC()).capture(1,"order",null,false);}
 @Test void requiredInvoiceDetailsAreValidatedBeforePersistence(){assertThatThrownBy(()->new BuyerDetailsService(null,new BuyerDetailsPolicy(),Clock.systemUTC()).capture(1,"order",null,true)).hasMessageContaining("Buyer name and address");}
 @Test void captureStoresOnlyTheNormalizedTransactionSnapshot(){var saved=new java.util.ArrayList<BuyerSnapshot>();var repo=(BuyerSnapshotRepository)java.lang.reflect.Proxy.newProxyInstance(BuyerSnapshotRepository.class.getClassLoader(),new Class<?>[]{BuyerSnapshotRepository.class},(p,m,a)->{assertThat(m.getName()).isEqualTo("saveAndFlush");saved.add((BuyerSnapshot)a[0]);return a[0];});new BuyerDetailsService(repo,new BuyerDetailsPolicy(),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC)).capture(1,"order",new BuyerDetailsPolicy.Input(" Buyer ",null," TAX "," Street "),true);assertThat(saved.getFirst().name()).isEqualTo("Buyer");assertThat(saved.getFirst().taxId()).isEqualTo("TAX");assertThat(saved.getFirst().contact()).isNull();}
}

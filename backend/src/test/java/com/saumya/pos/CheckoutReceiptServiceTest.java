package com.saumya.pos;
import com.saumya.pos.checkout.CheckoutReceiptService;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.retail.PosService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CheckoutReceiptServiceTest {
 @Test void malformedReferencesCannotReachPrivateReceiptData(){assertThatThrownBy(()->new CheckoutReceiptService(null,null,null,null,null,null).receipt(new PosService.Scope(1,1,"alice"),"../private")).hasMessageContaining("Receipt unavailable");}
    
    @Test
    void forgedScopeCannotSelectAnotherTenantOrBranch() {
        var retail = new PosService(null, null, null) {
            public Scope scope() {
                return new Scope(1, 1, "name");
            }
            @Override public void authorize(Scope scope,String actor,String... permissions){}
        };
        var reader = new CheckoutReceiptService(null, null, null, null, null, retail);
        assertThatThrownBy(() -> reader.receipt(new PosService.Scope(2, 2, "alice"), "order")).hasMessageContaining("Receipt unavailable");
        assertThatThrownBy(() -> reader.receipt(new PosService.Scope(1, 2, "alice"), "order")).hasMessageContaining("Receipt unavailable");
    }
    
    @Test
    void absentOrderCannotReachBuyerOrTenderPersistence() {
        var retail = new PosService(null, null, null) {
            public Scope scope() {
                return new Scope(1, 1, "name");
            }
            @Override public void authorize(Scope scope,String actor,String... permissions){}
        };
        var orders = (DraftRepository) java.lang.reflect.Proxy.newProxyInstance(
                DraftRepository.class.getClassLoader(), new Class<?>[] { DraftRepository.class }, (p, m, a) -> {
                    assertThat(m.getName()).isEqualTo("lock");
                    assertThat(a).containsExactly(1L, "private-order");
                    return java.util.Optional.empty();
                }
        );
        assertThatThrownBy(() -> new CheckoutReceiptService(orders, null, null, null, null, retail).receipt(new PosService.Scope(1, 1, "alice"), "private-order")).hasMessageContaining("Receipt unavailable");
    }
}

package com.saumya.pos.returns;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerReturnController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class CustomerReturnControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean CustomerReturnHistoryService history;

    @Test
    @WithMockUser(username="buyer@example.test", authorities={"CUSTOMER"})
    void customerActorCanReadTheReturnStatusRoute() throws Exception {
        when(history.history("buyer@example.test", "order-1"))
            .thenReturn(new CustomerReturnHistoryService.History("order-1", List.of()));
        mvc.perform(get("/api/v1/customer/orders/order-1/returns"))
            .andExpect(status().isOk());
        verify(history).history("buyer@example.test", "order-1");
    }

    @Test
    @WithMockUser(username="employee@example.test", authorities={"POS_VIEW", "POS_RETURN_VIEW"})
    void vendorEmployeeCannotUseTheCustomerReturnRoute() throws Exception {
        mvc.perform(get("/api/v1/customer/orders/order-1/returns"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/customer/orders/order-1/returns"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }
}

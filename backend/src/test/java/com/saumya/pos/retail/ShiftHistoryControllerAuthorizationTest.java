package com.saumya.pos.retail;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.time.LocalDate;
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

@WebMvcTest(ShiftHistoryController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class ShiftHistoryControllerAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean ShiftHistoryService service;

    @Test
    @WithMockUser(username="cashier@vendor.test",authorities={"POS_VIEW"})
    void cashierCanReadOwnShiftHistoryWithoutBranchReportGrant() throws Exception {
        when(service.history(eq("cashier@vendor.test"),any(),any(),any(),anyInt(),anyInt()))
            .thenReturn(new ShiftHistoryService.History(1L,LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-16"),"",0,12,0,0,List.of()));
        mvc.perform(get("/api/v1/vendor/pos/sessions/history"))
            .andExpect(status().isOk());
        verify(service).history(eq("cashier@vendor.test"),any(),any(),any(),anyInt(),anyInt());
    }

    @Test
    @WithMockUser(username="cashier@vendor.test",authorities={"POS_REPORT_VIEW"})
    void reportGrantWithoutPosViewDoesNotEnterCashierHistory() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/sessions/history"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCannotReadShiftHistory() throws Exception {
        mvc.perform(get("/api/v1/vendor/pos/sessions/history"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}

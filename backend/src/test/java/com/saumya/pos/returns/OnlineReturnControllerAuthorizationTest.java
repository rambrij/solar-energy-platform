package com.saumya.pos.returns;

import com.saumya.pos.AuthorizationTestSecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnlineReturnController.class)
@Import(AuthorizationTestSecurityConfiguration.class)
class OnlineReturnControllerAuthorizationTest {
 @Autowired MockMvc mvc;
 @MockBean OnlineReturnService service;

 @Test @WithMockUser(username="customer@example.test",authorities={"CUSTOMER"})
 void customerActorCanReadAndSubmitOwnReturnRoutes() throws Exception {
  when(service.customerWorkspace("customer@example.test")).thenReturn(new OnlineReturnService.Workspace(List.of(),List.of()));
  mvc.perform(get("/api/v1/customer/online-returns")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/customer/online-returns").with(csrf()).header("Idempotency-Key","return-key-123")
   .contentType(MediaType.APPLICATION_JSON)
   .content("{\"orderId\":\"order-1\",\"items\":[{\"productId\":1,\"quantity\":1}],\"reason\":\"Damaged packaging\"}"))
   .andExpect(status().isCreated());
  verify(service).customerWorkspace("customer@example.test");
  verify(service).createCustomer(eq("customer@example.test"),eq("return-key-123"),any());
 }

 @Test @WithMockUser(username="vendor@example.test",authorities={"RETURN_VIEW","RETURN_APPROVE"})
 void vendorCanReadPolicyAndReviewQueue() throws Exception {
  when(service.vendorList()).thenReturn(List.of());
  when(service.policy()).thenReturn(new OnlineReturnService.Policy(0,0));
  mvc.perform(get("/api/v1/vendor/online-returns")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/vendor/online-returns/policy")).andExpect(status().isOk());
  mvc.perform(put("/api/v1/vendor/online-returns/policy").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"expectedVersion\":0,\"returnWindowDays\":14,\"reason\":\"Enable returns\"}"))
   .andExpect(status().isOk());
  mvc.perform(post("/api/v1/vendor/online-returns/return-1/decision").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"expectedVersion\":0,\"decision\":\"APPROVED\",\"reason\":\"Evidence reviewed\",\"customerMessage\":\"Your return is approved.\"}"))
   .andExpect(status().isOk());
  verify(service).vendorList();verify(service).policy();verify(service).updatePolicy(any());verify(service).decide(eq("return-1"),any());
 }

 @Test @WithMockUser(username="viewer@example.test",authorities={"RETURN_VIEW"})
 void readOnlyVendorCannotChangePolicyOrDecide() throws Exception {
  when(service.vendorList()).thenReturn(List.of());
  mvc.perform(get("/api/v1/vendor/online-returns")).andExpect(status().isOk());
  mvc.perform(put("/api/v1/vendor/online-returns/policy").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"expectedVersion\":0,\"returnWindowDays\":14,\"reason\":\"test\"}"))
   .andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/online-returns/return-1/decision").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"expectedVersion\":0,\"decision\":\"APPROVED\",\"reason\":\"test\",\"customerMessage\":\"approved\"}"))
   .andExpect(status().isForbidden());
  verify(service).vendorList();verifyNoMoreInteractions(service);
 }

 @Test @WithMockUser(username="vendor@example.test",authorities={"RETURN_VIEW"})
 void vendorCannotUseCustomerRoutes() throws Exception {
  mvc.perform(get("/api/v1/customer/online-returns")).andExpect(status().isForbidden());
  verifyNoInteractions(service);
 }
}

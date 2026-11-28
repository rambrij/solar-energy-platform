package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:openapi-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "checkout.cleanup-enabled=false",
    "payments.mock-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void generatedOpenApiSpecDocumentsImplementedIdentityProfileCatalogueAndCrmRoutes()throws Exception{
        String body=mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn().getResponse().getContentAsString();
        JsonNode spec=mapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(spec.path("openapi").asText()).startsWith("3.");
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/auth/email-verification/verify")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/profile")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/invoices")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/reports/overview")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/reports/refunds")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/payment-accounts")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/leads")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/site-surveys")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/leads/{leadId}/site-surveys")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/site-surveys/{id}")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/quotation-catalogue")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/quotations")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/quotations/{id}/revisions")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/customer/quotations")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/customer/quotations/{id}/revisions/{revision}/decision")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/quotations/{id}/revisions/{revision}/document")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/customer/quotations/{id}/revisions/{revision}/document")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations/orders")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations/engineers")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations/{id}")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations/{id}/assignment")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/installations/{id}/status")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/warehouses")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/warehouses/{id}")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/warehouses/{id}/stock")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers/{id}")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers/{id}/approve")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers/{id}/cancel")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers/{id}/dispatch")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/inventory/transfers/{id}/receive")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/customer/installations")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/customer/installations/{id}")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("schemas").has("ApiProblem")).isTrue();
        JsonNode invoiceOperation = spec.path("paths").path("/api/v1/vendor/invoices").path("get");
        for (String status : List.of("400","401","403","404","406","409","413","415","429","500")) {
            org.assertj.core.api.Assertions.assertThat(invoiceOperation.path("responses").path(status)
                .path("content").has("application/problem+json")).as("RFC 9457 response %s",status).isTrue();
            org.assertj.core.api.Assertions.assertThat(invoiceOperation.path("responses").path(status)
                .path("content").path("application/problem+json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ApiProblem");
        }
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("securitySchemes").has("refreshCookie")).isTrue();
    }

    @Test void protectedApiAndValidationErrorsUseProblemJson()throws Exception{
        mvc.perform(get("/api/v1/vendor/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:solar:problem:UNAUTHENTICATED"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.instance").value("/api/v1/vendor/profile"))
            .andExpect(header().exists("X-Correlation-Id"));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                .content("{\"username\":\"\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:solar:problem:VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").exists())
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
            .andExpect(header().exists("X-Correlation-Id"));

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("text/plain").content("not-json"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:solar:problem:UNSUPPORTED_MEDIA_TYPE"))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(header().exists("X-Correlation-Id"));
    }
}

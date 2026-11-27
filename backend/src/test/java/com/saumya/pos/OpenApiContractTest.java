package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").has("/api/v1/vendor/leads")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
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
            .andExpect(jsonPath("$.errors[0].field").exists());
    }
}

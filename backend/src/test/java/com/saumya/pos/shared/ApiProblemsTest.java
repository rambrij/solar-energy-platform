package com.saumya.pos.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiProblemsTest {
    @Test void unexpectedExceptionsReturnGenericRfc9457ProblemWithoutLeakingExceptionText() {
        var problems = new ApiProblems(new ApiProblemWriter(new com.fasterxml.jackson.databind.ObjectMapper()));
        var request = new MockHttpServletRequest("GET", "/api/v1/vendor/invoices");
        var response = problems.unexpected(new IllegalStateException("database password leaked"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType().toString()).isEqualTo("urn:solar:problem:INTERNAL_ERROR");
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getDetail()).doesNotContain("database password leaked");
        assertThat(body.getProperties()).containsEntry("correlationId", response.getHeaders().getFirst("X-Correlation-Id"));
    }
}

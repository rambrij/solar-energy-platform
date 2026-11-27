package com.saumya.pos.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class ApiProblemWriterTest {
    @Test void emitsRfc9457ProblemWithStableTypeInstanceAndCorrelationHeader()throws Exception{
        var mapper=new ObjectMapper();
        var writer=new ApiProblemWriter(mapper);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/v1/vendor/profile");
        MockHttpServletResponse response=new MockHttpServletResponse();
        writer.write(request,response,HttpStatus.FORBIDDEN,"FORBIDDEN","Forbidden","Not permitted.");
        var body=mapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(body.path("type").asText()).isEqualTo("urn:solar:problem:FORBIDDEN");
        assertThat(body.path("title").asText()).isEqualTo("Forbidden");
        assertThat(body.path("status").asInt()).isEqualTo(403);
        assertThat(body.path("detail").asText()).isEqualTo("Not permitted.");
        assertThat(body.path("instance").asText()).isEqualTo("/api/v1/vendor/profile");
        assertThat(body.path("correlationId").asText()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeaderNames()).anyMatch(name -> name.equalsIgnoreCase("X-Correlation-Id"));
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(body.path("correlationId").asText());
    }

    @Test void ignoresUnsafeProblemTypeCodes(){
        var writer=new ApiProblemWriter(new ObjectMapper());
        var request=new MockHttpServletRequest("GET","/api/v1/example");
        var body=writer.problem(request,HttpStatus.BAD_REQUEST,"bad:urn","Bad Request","Invalid.",null);
        assertThat(body.getType().toString()).isEqualTo("urn:solar:problem:REQUEST_REJECTED");
    }
}

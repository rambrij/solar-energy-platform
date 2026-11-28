package com.saumya.pos.shared;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

@Configuration
@OpenAPIDefinition(info=@Info(
    title="Saumya Solar Marketplace SaaS API",
    version="1.0.0",
    description="REST API for a multi-tenant solar marketplace and vendor ERP. Vendor tenant context is always derived from the authenticated membership; client-supplied tenant identifiers are never authorization inputs.",
    contact=@Contact(name="Saumya Solar Platform Team")
))
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@SecurityScheme(name="refreshCookie",type=SecuritySchemeType.APIKEY,in=SecuritySchemeIn.COOKIE,paramName="solar-refresh")
public class OpenApiConfiguration {
    @Bean
    OperationCustomizer actorSecurityDocumentation() {
        return (Operation operation, HandlerMethod handler) -> {
            PreAuthorize method = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
            PreAuthorize type = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
            String expression = method != null ? method.value() : (type == null ? "" : type.value());
            String path = handler.getMethod().getName();
            if (expression.contains("permitAll()")) {
                if (path.equals("refresh") || path.equals("logout"))
                    operation.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("refreshCookie"));
            } else {
                operation.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer rfc9457ProblemResponses() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new io.swagger.v3.oas.models.Components());
            Schema<?> error = new ObjectSchema()
                .addProperties("field", new StringSchema().description("Request field with a validation failure."))
                .addProperties("message", new StringSchema().description("Safe validation message."));
            Schema<?> problem = new ObjectSchema()
                .addProperties("type", new StringSchema().format("uri").description("Problem type URI."))
                .addProperties("title", new StringSchema().description("Short, human-readable problem title."))
                .addProperties("status", new IntegerSchema().description("HTTP status code."))
                .addProperties("detail", new StringSchema().description("Safe, human-readable problem detail."))
                .addProperties("instance", new StringSchema().format("uri-reference").description("Request instance URI."))
                .addProperties("code", new StringSchema().description("Stable application problem code."))
                .addProperties("correlationId", new StringSchema().format("uuid").description("Request correlation identifier."))
                .addProperties("errors", new ArraySchema().items(error).description("Field-level validation failures."));
            api.getComponents().addSchemas("ApiProblem", problem);

            Map<String, String> responses = new LinkedHashMap<>();
            responses.put("400", "Invalid or malformed request (RFC 9457 problem detail).");
            responses.put("401", "Authentication required (RFC 9457 problem detail).");
            responses.put("403", "Actor not authorized (RFC 9457 problem detail).");
            responses.put("404", "Resource not found (RFC 9457 problem detail).");
            responses.put("406", "No acceptable response representation (RFC 9457 problem detail).");
            responses.put("409", "State or data conflict (RFC 9457 problem detail).");
            responses.put("413", "Request body exceeds the configured limit (RFC 9457 problem detail).");
            responses.put("415", "Unsupported request media type (RFC 9457 problem detail).");
            responses.put("429", "Rate limit exceeded (RFC 9457 problem detail).");
            responses.put("500", "Unexpected server error (RFC 9457 problem detail).");
            if (api.getPaths() == null) return;
            for (PathItem path : api.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
                    for (var entry : responses.entrySet()) {
                        operation.getResponses().putIfAbsent(entry.getKey(), new ApiResponse()
                            .description(entry.getValue())
                            .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new io.swagger.v3.oas.models.media.MediaType().schema(
                                    new Schema<>().$ref("#/components/schemas/ApiProblem")))));
                    }
                }
            }
        };
    }
}

package com.saumya.pos.shared;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
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
    OperationCustomizer actorSecurityDocumentation(){
        return (Operation operation,HandlerMethod handler)->{
            PreAuthorize method=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),PreAuthorize.class);
            PreAuthorize type=AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),PreAuthorize.class);
            String expression=method!=null?method.value():(type==null?"":type.value());
            String path=handler.getMethod().getName();
            if(expression.contains("permitAll()")){
                if(path.equals("refresh")||path.equals("logout"))
                    operation.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("refreshCookie"));
            }else{
                operation.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
            }
            return operation;
        };
    }
}

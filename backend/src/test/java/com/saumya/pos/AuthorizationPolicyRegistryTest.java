package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.membership.VendorPermissionRegistry;
import com.saumya.pos.platform.PlatformPermissionRegistry;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;

class AuthorizationPolicyRegistryTest {
    private JsonNode registry() throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/authorization-policy-registry.json")){
            assertThat(in).as("canonical authorization-policy-registry.json").isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    @Test void canonicalVendorAndPlatformPermissionAllowlistsMatchRuntime(){
        try {
            JsonNode json=registry();
            Set<String> vendor=new TreeSet<>();
            json.path("vendorPermissions").forEach(v->vendor.add(v.asText()));
            assertThat(vendor).containsExactlyInAnyOrderElementsOf(VendorPermissionRegistry.all());
            assertThat(vendor).noneMatch(p->p.startsWith("PLATFORM_"));
            Set<String> platform=new TreeSet<>();
            json.path("platformPermissions").forEach(v->platform.add(v.asText()));
            assertThat(platform).containsExactlyInAnyOrderElementsOf(PlatformPermissionRegistry.all());
            assertThat(platform).allMatch(p->p.startsWith("PLATFORM_"));
        } catch(Exception e){throw new AssertionError(e);}
    }

    @Test void everyMappedBackendControllerIsInCanonicalEndpointInventoryAndHasAClassifiedPolicy(){
        try {
            JsonNode policies=registry().path("controllerPolicies");
            var scanner=new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            Set<String> discovered=new TreeSet<>();
            for(var bean:scanner.findCandidateComponents("com.saumya.pos")){
                Class<?> type=Class.forName(bean.getBeanClassName());
                boolean hasMappings=Arrays.stream(type.getDeclaredMethods()).anyMatch(AuthorizationPolicyRegistryTest::isMapped);
                if(!hasMappings)continue;
                String name=type.getSimpleName();discovered.add(name);
                assertThat(policies.has(name)).as("controller policy for %s",name).isTrue();
                String policy=policies.path(name).asText("");
                assertThat(policy).as("non-empty policy for %s",name).isNotBlank();
                for(Method method:type.getDeclaredMethods()){
                    if(!isMapped(method))continue;
                    PreAuthorize methodPolicy=AnnotatedElementUtils.findMergedAnnotation(method,PreAuthorize.class);
                    PreAuthorize classPolicy=AnnotatedElementUtils.findMergedAnnotation(type,PreAuthorize.class);
                    if(methodPolicy!=null||classPolicy!=null)validateAuthorities(
                        methodPolicy!=null?methodPolicy.value():classPolicy.value(),name+"#"+method.getName());
                }
            }
            Set<String> recorded=new TreeSet<>();policies.fieldNames().forEachRemaining(recorded::add);
            assertThat(discovered).containsExactlyInAnyOrderElementsOf(recorded);
        } catch(Exception e){throw new AssertionError(e);}
    }

    private static boolean isMapped(Method method){
        return AnnotatedElementUtils.hasAnnotation(method,RequestMapping.class);
    }

    private static void validateAuthorities(String expression,String location){
        Matcher matcher=Pattern.compile("(?:hasAuthority|hasAnyAuthority)\\('([A-Z][A-Z0-9_]+)'").matcher(expression);
        while(matcher.find()){
            String permission=matcher.group(1);
            if(permission.endsWith("_"))continue; // runtime POS_{movement} permission family
            assertThat(permission.equals("CUSTOMER")||VendorPermissionRegistry.all().contains(permission)
                ||PlatformPermissionRegistry.contains(permission))
                .as("known permission %s in %s",permission,location).isTrue();
        }
    }

    @Test void vendorPermissionRegistryRejectsUnknownAndPlatformGrants(){
        assertThatThrownBy(()->VendorPermissionRegistry.requireVendorPermission("PLATFORM_IDENTITY_MANAGE"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->VendorPermissionRegistry.requireVendorPermission("NOT_A_PERMISSION"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(VendorPermissionRegistry.requireVendorPermission("PRODUCT_UPDATE")).isEqualTo("PRODUCT_UPDATE");
    }
}

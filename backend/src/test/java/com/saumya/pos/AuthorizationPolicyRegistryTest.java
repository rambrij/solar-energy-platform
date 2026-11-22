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
            JsonNode actorPolicies;
            try(InputStream matrix=getClass().getResourceAsStream("/runtime-authorization-matrix.json")){
                assertThat(matrix).isNotNull();
                actorPolicies=new ObjectMapper().readTree(matrix).path("controllerPolicies");
            }
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
                    assertThat(methodPolicy!=null||classPolicy!=null)
                        .as("every mapped endpoint must have a machine-enforced @PreAuthorize policy or explicit permitAll policy: %s#%s",name,method.getName())
                        .isTrue();
                    String expression=methodPolicy!=null?methodPolicy.value():classPolicy.value();
                    validateAuthorities(expression,name+"#"+method.getName());
                    JsonNode operationMetadata=actorPolicies.path(name);
                    String operation=name+"#"+method.getName();
                    assertThat(operationMetadata.path("actor").asText())
                        .as("actor metadata for protected operation %s",operation).isIn(
                            "PLATFORM","VENDOR","CUSTOMER","MIXED","PUBLIC","AUTHENTICATION","INVITATION_TOKEN");
                    assertThat(operationMetadata.path("scope").asText())
                        .as("tenant/customer/resource scope for protected operation %s",operation).isNotBlank();
                    assertThat(operationMetadata.path("assignment").asText())
                        .as("assignment/state metadata for protected operation %s",operation).isNotBlank();
                    rejectPlatformPermissionForVendor(operationMetadata.path("actor").asText(),
                        expression,operation);
                    assertMutationHasNonViewAuthority(method,expression,operation);
                }
            }
            Set<String> recorded=new TreeSet<>();policies.fieldNames().forEachRemaining(recorded::add);
            assertThat(discovered).containsExactlyInAnyOrderElementsOf(recorded);
        } catch(Exception e){throw new AssertionError(e);}
    }

    private static boolean isMapped(Method method){
        return AnnotatedElementUtils.hasAnnotation(method,RequestMapping.class);
    }

    private static void assertMutationHasNonViewAuthority(Method method,String expression,String location){
        RequestMapping mapping=AnnotatedElementUtils.findMergedAnnotation(method,RequestMapping.class);
        if(mapping==null)return;
        boolean mutation=Arrays.stream(mapping.method()).anyMatch(verb ->
            verb==RequestMethod.POST||verb==RequestMethod.PUT||verb==RequestMethod.PATCH||verb==RequestMethod.DELETE);
        if(!mutation)return;
        Matcher matcher=Pattern.compile("(?:hasAuthority|hasAnyAuthority)\\('([A-Z][A-Z0-9_]+)'").matcher(expression);
        Set<String> authorities=new HashSet<>();
        while(matcher.find())authorities.add(matcher.group(1));
        if(!authorities.isEmpty()){
            assertThat(authorities).as("mutation endpoint %s must not be protected only by VIEW permissions",location)
                .anyMatch(permission -> !permission.endsWith("_VIEW"));
        }
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

    @Test void negativeFixturesRejectUnknownPermissionAndViewOnlyMutation(){
        assertThatThrownBy(() -> validateAuthorities("hasAuthority('UNKNOWN_P204_PERMISSION')","fixture#unknown"))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertMutationHasNonViewAuthority(
            fixtureMethod("postFixture"),"hasAuthority('POS_VIEW')","fixture#postFixture"))
            .isInstanceOf(AssertionError.class);
    }

    private static Method fixtureMethod(String name){
        try{return AuthorizationPolicyRegistryTest.NegativeAuthorizationFixture.class.getDeclaredMethod(name);}
        catch(NoSuchMethodException exception){throw new AssertionError(exception);}
    }

    static class NegativeAuthorizationFixture {
        @PostMapping("/fixture")
        void postFixture(){}
    }


    @Test void everyProtectedControllerOperationHasCanonicalActorScopeAndAssignmentMetadata(){
        try(InputStream in=getClass().getResourceAsStream("/runtime-authorization-matrix.json")){
            assertThat(in).as("P0-06 runtime authorization matrix").isNotNull();
            JsonNode matrix=new ObjectMapper().readTree(in).path("controllerPolicies");
            JsonNode registryControllers=registry().path("controllerPolicies");
            Set<String> expected=new TreeSet<>();registryControllers.fieldNames().forEachRemaining(expected::add);
            Set<String> actual=new TreeSet<>();matrix.fieldNames().forEachRemaining(actual::add);
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
            for(String controller:actual){
                JsonNode metadata=matrix.path(controller);
                for(String field:List.of("actor","scope","assignment")){
                    assertThat(metadata.path(field).asText()).as("%s operation metadata: %s",controller,field).isNotBlank();
                }
            }
        }catch(Exception exception){throw new AssertionError(exception);}
    }

    private static void rejectPlatformPermissionForVendor(String actor,String expression,String location){
        if(!"VENDOR".equals(actor))return;
        Matcher matcher=Pattern.compile("(?:hasAuthority|hasAnyAuthority)\\('([A-Z][A-Z0-9_]+)'").matcher(expression);
        while(matcher.find()){
            assertThat(matcher.group(1)).as("PLATFORM permission cannot authorize vendor operation %s",location)
                .doesNotStartWith("PLATFORM_");
        }
    }

    @Test void negativeFixtureRejectsPlatformPermissionOnVendorOperation(){
        assertThatThrownBy(() -> rejectPlatformPermissionForVendor("VENDOR",
            "hasAuthority('PLATFORM_TENANT_SUSPEND')","fixture#vendorMutation"))
            .isInstanceOf(AssertionError.class);
    }

    @Test void vendorPermissionRegistryRejectsUnknownAndPlatformGrants(){
        assertThatThrownBy(()->VendorPermissionRegistry.requireVendorPermission("PLATFORM_IDENTITY_MANAGE"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->VendorPermissionRegistry.requireVendorPermission("NOT_A_PERMISSION"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(VendorPermissionRegistry.requireVendorPermission("PRODUCT_UPDATE")).isEqualTo("PRODUCT_UPDATE");
    }
}

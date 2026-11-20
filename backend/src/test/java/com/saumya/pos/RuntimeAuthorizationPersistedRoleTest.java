package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import com.saumya.pos.membership.VendorPermissionRegistry;
import com.saumya.pos.checkout.MockVendorPaymentAdapter;
import com.saumya.pos.identity.AuthenticationService;
import com.saumya.pos.identity.JwtService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import java.lang.reflect.*;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.security.Principal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real authentication + persisted vendor role fixture. Unlike expression-only
 * tests, this goes through login, JWT decoding, membership resolution, active
 * role assignment and MVC method security.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:runtime-authorization-persisted;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "payments.mock-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeAuthorizationPersistedRoleTest {
    private static final String USER = "runtime.matrix.viewer@example.test";
    private static final String PASSWORD = "RuntimeMatrixPassword!2026";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext applicationContext;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappings;
    @Autowired AuthenticationService identities;
    @Autowired JwtService jwt;
    @org.springframework.boot.test.mock.mockito.SpyBean MockVendorPaymentAdapter mockPaymentAdapter;

    @BeforeEach
    void seedPersistedViewOnlyActor() {
        db.update("DELETE FROM identity_refresh_tokens WHERE username=?", USER);
        db.update("DELETE FROM employee_register_assignments WHERE username=?", USER);
        db.update("DELETE FROM employee_role_assignments WHERE username=?", USER);
        db.update("DELETE FROM vendor_role_permissions WHERE role_id IN (SELECT id FROM vendor_roles WHERE role_key='runtime-matrix-viewer')");
        db.update("DELETE FROM vendor_roles WHERE role_key='runtime-matrix-viewer'");
        db.update("DELETE FROM employees WHERE username=?", USER);
        db.update("DELETE FROM users WHERE username=?", USER);

        // Remove tenant-owned rows before their creator employee to respect
        // the composite (tenant_id, created_by) foreign key on vendor_roles.
        db.update("DELETE FROM vendor_roles WHERE tenant_id=91002");
        db.update("DELETE FROM employees WHERE username='runtime.foreign@example.test'");
        db.update("DELETE FROM users WHERE username='runtime.foreign@example.test'");
        db.update("DELETE FROM branches WHERE id IN (91001,91002)");
        db.update("DELETE FROM tenants WHERE id IN (91001,91002)");
        db.update("INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status) VALUES(91001,'Runtime Matrix Vendor','runtime-matrix-vendor','Runtime Matrix Vendor','ACTIVE')");
        db.update("INSERT INTO branches(id,tenant_id,name) VALUES(91001,91001,'Runtime Matrix Branch')");
        db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(91001,?,?,TRUE)",
            USER, passwords.encode(PASSWORD));
        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,91001,91001,TRUE)", USER);
        db.update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(91001,'runtime-matrix-viewer','Runtime Matrix VIEW only',TRUE,?,?)", USER, USER);
        Long roleId = db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=91001 AND role_key='runtime-matrix-viewer'", Long.class);
        for (String permission : VendorPermissionRegistry.all()) {
            if (permission.endsWith("_VIEW")) {
                db.update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(91001,?,?,?)",
                    roleId, permission, USER);
            }
        }
        db.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(91001,91001,?,?,TRUE,?)", USER, roleId, USER);
    }

    private String bearerToken() throws Exception {
        String body = json.writeValueAsString(Map.of("username", USER, "password", PASSWORD));
        String response = mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode parsed = json.readTree(response);
        return parsed.path("accessToken").asText();
    }

    @Test
    void persistedRoleViewGrantAllowsRoleReadButNeverRoleCreation() throws Exception {
        String token = bearerToken();

        mvc.perform(get("/api/v1/vendor/roles").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        int before = db.queryForObject(
            "SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=91001", Integer.class);

        String create = json.writeValueAsString(Map.of(
            "roleKey", "should-not-exist",
            "displayName", "Must be denied",
            "permissions", java.util.List.of("PRODUCT_VIEW"),
            "reason", "Runtime matrix negative mutation test"
        ));
        mvc.perform(post("/api/v1/vendor/roles").with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(create))
            .andExpect(status().isForbidden());

        int after = db.queryForObject(
            "SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=91001", Integer.class);
        assertThat(after).as("denied CREATE must not persist a role").isEqualTo(before);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=91001 AND role_key='should-not-exist'",
            Integer.class)).isZero();
    }

    @Test
    void persistedProductViewPermissionAllowsReadButNeverProductCreate() throws Exception {
        String token = bearerToken();

        mvc.perform(get("/api/v1/vendor/products").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        int before = db.queryForObject(
            "SELECT COUNT(*) FROM products WHERE tenant_id=91001", Integer.class);
        String product = json.writeValueAsString(Map.of(
            "sku", "VIEW-CANNOT-CREATE-001",
            "name", "Must not be created",
            "price", 1000.00,
            "taxRate", 18.00,
            "published", false,
            "expectedVersion", 0,
            "reason", "Runtime VIEW-only mutation denial"
        ));
        mvc.perform(post("/api/v1/vendor/products").with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(product))
            .andExpect(status().isForbidden());

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM products WHERE tenant_id=91001", Integer.class))
            .as("VIEW-only product create must not persist a product").isEqualTo(before);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM products WHERE tenant_id=91001 AND sku='VIEW-CANNOT-CREATE-001'",
            Integer.class)).isZero();
    }

    @Test
    void forgedTenantHeaderCannotChangePersistedVendorMembershipScope() throws Exception {
        db.update("INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status) VALUES(91002,'Foreign Runtime Tenant','foreign-runtime-tenant','Foreign Runtime Tenant','ACTIVE')");
        db.update("INSERT INTO branches(id,tenant_id,name) VALUES(91002,91002,'Foreign Runtime Branch')");
        db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(91002,'runtime.foreign@example.test',?,TRUE)",
            passwords.encode("ForeignRuntimePassword!2026"));
        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES('runtime.foreign@example.test',91002,91002,TRUE)");
        db.update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(91002,'foreign-runtime-secret-role','Foreign Tenant Secret Role',TRUE,'runtime.foreign@example.test','runtime.foreign@example.test')");

        String token = bearerToken();
        String ownTenantResponse = mvc.perform(get("/api/v1/vendor/roles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(ownTenantResponse).contains("runtime-matrix-viewer");
        assertThat(ownTenantResponse).doesNotContain("foreign-runtime-secret-role");

        String forgedTenantResponse = mvc.perform(get("/api/v1/vendor/roles")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", "91002")
                .header("X-Tenant-Id", "91002"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(forgedTenantResponse).contains("runtime-matrix-viewer");
        assertThat(forgedTenantResponse).doesNotContain("foreign-runtime-secret-role");
    }

    @Test
    void removingPersistedRoleAssignmentImmediatelyRemovesEffectivePermission() throws Exception {
        String token = bearerToken();
        mvc.perform(get("/api/v1/vendor/roles").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        db.update("DELETE FROM employee_role_assignments WHERE username=?", USER);

        mvc.perform(get("/api/v1/vendor/roles").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void activeMembershipWithoutEffectivePermissionIsForbidden() throws Exception {
        String token = bearerToken();
        db.update("DELETE FROM vendor_role_permissions WHERE role_id IN (SELECT id FROM vendor_roles WHERE role_key='runtime-matrix-viewer')");
        mvc.perform(get("/api/v1/vendor/products").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void disabledPersistedIdentityCannotAuthenticate() throws Exception {
        db.update("UPDATE users SET enabled=FALSE WHERE username=?", USER);
        String body = json.writeValueAsString(Map.of("username", USER, "password", PASSWORD));
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousProtectedEndpointReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/vendor/roles"))
            .andExpect(status().isUnauthorized());
    }
    /**
     * Exercise every mapped mutating controller method through its live Spring
     * AOP proxy using a JWT-derived principal whose effective grants are read
     * from a persisted tenant role containing VIEW permissions only.
     *
     * This is intentionally method-level rather than mockMvc payload-level:
     * Spring method authorization runs before the controller body and avoids
     * fabricating unrelated business payloads for 100+ mutation endpoints.
     * The test verifies the denial type and whole-database side-effect counts.
     */
    @Test
    void everyMappedMutationRejectsPersistedViewOnlyActorBeforeAnySideEffect() throws Exception {
        String token = bearerToken();
        Authentication principal = identities.authenticate(jwt.decoder().decode(token));
        assertThat(principal.getAuthorities()).isNotEmpty();
        assertThat(principal.getAuthorities()).allMatch(grant -> grant.getAuthority().endsWith("_VIEW"));
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(principal);
        SecurityContextHolder.setContext(security);

        List<String> sideEffectTables = List.of(
            "products", "orders", "order_items", "inventory", "inventory_ledger",
            "inventory_receipts", "stock_reservations", "pos_sessions", "pos_drawer_tenders",
            "pos_cash_movements", "pos_cash_refunds", "pos_returns", "pos_return_events",
            "pos_audit", "pos_shift_reviews", "carts", "cart_items", "vendor_roles",
            "vendor_role_permissions", "employee_role_assignments", "employee_register_assignments",
            "platform_support_access", "platform_configuration", "platform_audit_events",
            "report_export_audit", "vendor_payment_attempts", "tenant_subscriptions",
            "subscription_invoices", "subscription_payments", "subscription_events",
            "catalog_promotions", "catalog_change_events", "receipt_document_audit"
        );
        Map<String, Long> before = new LinkedHashMap<>();
        for (String table : sideEffectTables) {
            before.put(table, db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }

        Set<String> executed = new TreeSet<>();
        Set<String> viewDeniedOperations = new TreeSet<>();
        int denied = 0;
        try {
            for (var entry : requestMappings.getHandlerMethods().entrySet()) {
                HandlerMethod handler = entry.getValue();
                Class<?> controllerType = handler.getBeanType();
                if (!controllerType.getName().startsWith("com.saumya.pos.")
                    || !AnnotatedElementUtils.hasAnnotation(controllerType, RestController.class)) {
                    continue;
                }
                Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
                List<RequestMethod> mutating = methods.isEmpty()
                    ? List.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE)
                    : methods.stream().filter(m -> m == RequestMethod.POST || m == RequestMethod.PUT
                        || m == RequestMethod.PATCH || m == RequestMethod.DELETE).toList();
                if (mutating.isEmpty()) continue;

                var methodPolicy = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                    org.springframework.security.access.prepost.PreAuthorize.class);
                var classPolicy = AnnotatedElementUtils.findMergedAnnotation(controllerType,
                    org.springframework.security.access.prepost.PreAuthorize.class);
                var policy = methodPolicy != null ? methodPolicy : classPolicy;
                assertThat(policy).as("mapped mutation has a policy: %s#%s",
                    controllerType.getSimpleName(), handler.getMethod().getName()).isNotNull();
                if (policy.value().contains("permitAll()")) continue;

                String endpoint = controllerType.getSimpleName() + "#" + handler.getMethod().getName();
                if (!executed.add(endpoint)) continue;

                Object bean = applicationContext.getBean(controllerType);
                Object[] args = Arrays.stream(handler.getMethod().getParameterTypes())
                    .map(this::defaultArgument).toArray();
                Throwable failure = null;
                try {
                    handler.getMethod().setAccessible(true);
                    handler.getMethod().invoke(bean, args);
                } catch (InvocationTargetException invocation) {
                    failure = invocation.getCause();
                } catch (Throwable invocation) {
                    failure = invocation;
                }

                assertThat(isAuthorizationDenial(failure))
                    .as("VIEW-only persisted actor must be denied on %s (%s)", endpoint, policy.value())
                    .isTrue();
                denied++;
                viewDeniedOperations.add(endpoint);
                System.out.println("RUNTIME_AUTHORIZATION_VIEW_MUTATION|"
                    + String.join(",", mutating.stream().map(Enum::name).toList())
                    + "|" + endpoint + "|" + policy.value().replace("|", "/") + "|403|PASS");
            }
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(denied).as("all protected mapped mutation operations executed").isGreaterThan(20);
        for (String table : sideEffectTables) {
            Long after = db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            assertThat(after).as("denied mutation side effects in %s", table).isEqualTo(before.get(table));
        }
        org.mockito.Mockito.verifyNoInteractions(mockPaymentAdapter);
        System.out.println("RUNTIME_AUTHORIZATION_EXTERNAL_SIDE_EFFECT|MockVendorPaymentAdapter|calls=0|PASS");
        System.out.println("RUNTIME_AUTHORIZATION_VIEW_MUTATION_SUMMARY|operations="
            + executed.size() + "|denied=" + denied + "|sideEffects=UNCHANGED|PASS");

        // Emit one endpoint-level CSV row per live HTTP method/route. Anonymous
        // results are observed through MockMvc; VIEW mutation results reference
        // the actual Spring-proxy invocation above. Other actor combinations are
        // explicitly marked NOT EXECUTED rather than inferred from annotations.
        List<String> report = new ArrayList<>();
        report.add("method,route,controller,operation,effective_permission,scope,anonymous_expected,anonymous_actual,anonymous_result,view_mutation_expected,view_mutation_actual,view_mutation_result,view_mutation_evidence,other_actor_matrix");
        JsonNode controllerPolicies;
        try (var matrixStream = getClass().getResourceAsStream("/runtime-authorization-matrix.json")) {
            assertThat(matrixStream).isNotNull();
            controllerPolicies = json.readTree(matrixStream).path("controllerPolicies");
        }
        for (var entry : requestMappings.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> controllerType = handler.getBeanType();
            if (!controllerType.getName().startsWith("com.saumya.pos.")
                || !AnnotatedElementUtils.hasAnnotation(controllerType, RestController.class)) continue;
            var methodPolicy = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                org.springframework.security.access.prepost.PreAuthorize.class);
            var classPolicy = AnnotatedElementUtils.findMergedAnnotation(controllerType,
                org.springframework.security.access.prepost.PreAuthorize.class);
            var policy = methodPolicy != null ? methodPolicy : classPolicy;
            if (policy == null) continue;
            String operation = controllerType.getSimpleName() + "#" + handler.getMethod().getName();
            boolean mutating = entry.getKey().getMethodsCondition().getMethods().stream()
                .anyMatch(m -> m == RequestMethod.POST || m == RequestMethod.PUT
                    || m == RequestMethod.PATCH || m == RequestMethod.DELETE);
            List<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods().isEmpty()
                ? List.of(RequestMethod.GET)
                : entry.getKey().getMethodsCondition().getMethods().stream().sorted().toList();
            for (String route : entry.getKey().getPatternValues()) {
                String concretePath = route.replaceAll("\\{[^/]+\\}", "1");
                for (RequestMethod httpMethod : methods) {
                    String anonymousExpected = policy.value().contains("permitAll()") ? "PUBLIC" : "401";
                    String anonymousActual;
                    try {
                        anonymousActual = Integer.toString(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            org.springframework.http.HttpMethod.valueOf(httpMethod.name()),
                            java.net.URI.create("http://localhost" + concretePath)).with(csrf()))
                            .andReturn().getResponse().getStatus());
                    } catch (Exception failure) {
                        anonymousActual = "EXECUTION_ERROR";
                    }
                    String anonymousResult = anonymousExpected.equals("PUBLIC")
                        ? "NOT_APPLICABLE" : ("401".equals(anonymousActual) ? "PASS" : "FAIL");
                    boolean publicEndpoint = policy.value().contains("permitAll()");
                    String viewExpected = mutating && !publicEndpoint ? "403" : "NOT_APPLICABLE";
                    String viewActual = mutating && !publicEndpoint && viewDeniedOperations.contains(operation) ? "403" : "NOT_APPLICABLE";
                    String viewResult = !mutating || publicEndpoint ? "NOT_APPLICABLE"
                        : (viewDeniedOperations.contains(operation) ? "PASS" : "NOT_EXECUTED");
                    String escapedRoute = route.replace("\"", "\"\"");
                    String escapedPermission = policy.value().replace("\"", "\"\"");
                    String scope = controllerPolicies.path(controllerType.getSimpleName()).path("scope").asText("UNSPECIFIED");
                    String escapedScope = scope.replace("\"", "\"\"");
                    report.add(String.join(",", List.of(
                        httpMethod.name(), "\"" + escapedRoute + "\"",
                        controllerType.getSimpleName(), handler.getMethod().getName(),
                        "\"" + escapedPermission + "\"", "\"" + escapedScope + "\"",
                        anonymousExpected, anonymousActual, anonymousResult,
                        viewExpected, viewActual, viewResult,
                        "403".equals(viewActual) ? "SPRING_PROXY_METHOD_SECURITY" : "NOT_APPLICABLE",
                        "NOT_EXECUTED"
                    )));
                }
            }
        }
        try {
            Path reportPath = Path.of("target", "p0-06-runtime-authorization-report.csv");
            Files.createDirectories(reportPath.getParent());
            Files.write(reportPath, report, StandardCharsets.UTF_8);
            System.out.println("RUNTIME_AUTHORIZATION_REPORT|path=" + reportPath
                + "|endpointRows=" + (report.size() - 1) + "|viewDeniedOperations="
                + viewDeniedOperations.size() + "|otherActorMatrix=PARTIAL");
        } catch (java.io.IOException failure) {
            throw new AssertionError("Could not write runtime authorization report", failure);
        }
    }

    private boolean isAuthorizationDenial(Throwable error) {
        if (error == null) return false;
        Throwable current = error;
        while (current.getCause() != null && (current instanceof InvocationTargetException
            || current instanceof UndeclaredThrowableException)) current = current.getCause();
        if (current instanceof AccessDeniedException) return true;
        return current instanceof ResponseStatusException response
            && response.getStatusCode().value() == HttpStatus.FORBIDDEN.value();
    }

    private Object defaultArgument(Class<?> type) {
        if (type == String.class) return "runtime-matrix-key-0001";
        if (type == Principal.class) return (Principal) () -> USER;
        if (Authentication.class.isAssignableFrom(type)) return SecurityContextHolder.getContext().getAuthentication();
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 1;
        if (type == Short.class || type == short.class) return (short) 1;
        if (type == Boolean.class || type == boolean.class) return false;
        if (type == BigDecimal.class) return BigDecimal.ONE;
        if (type == LocalDate.class) return LocalDate.of(2026, 11, 20);
        if (type == Instant.class) return Instant.parse("2026-11-20T00:00:00Z");
        if (type == UUID.class) return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        if (type.isEnum()) return type.getEnumConstants().length == 0 ? null : type.getEnumConstants()[0];
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
        if (type.isRecord()) {
            try {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] parameterTypes = Arrays.stream(components)
                    .map(RecordComponent::getType).toArray(Class<?>[]::new);
                Object[] values = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    String name = components[i].getName().toLowerCase(Locale.ROOT);
                    Class<?> componentType = components[i].getType();
                    if (componentType == String.class && (name.contains("username") || name.contains("email")
                        || name.equals("actor") || name.equals("cashier"))) {
                        values[i] = USER;
                    } else if (componentType == String.class && name.contains("reason")) {
                        values[i] = "Runtime matrix authorization test";
                    } else if (componentType == String.class && name.contains("method")) {
                        values[i] = "CASH";
                    } else {
                        values[i] = defaultRecordComponent(components[i]);
                    }
                }
                Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                throw new AssertionError("Cannot construct runtime authorization probe for " + type.getName(), failure);
            }
        }
        return null;
    }

    @Test
    void persistedActiveNoPermissionActorGetsRuntimeDenialForEveryProtectedHttpEndpoint() throws Exception {
        db.update("DELETE FROM vendor_role_permissions WHERE role_id IN (SELECT id FROM vendor_roles WHERE role_key='runtime-matrix-viewer')");
        String token = bearerToken();

        List<String> report = new ArrayList<>();
        report.add("method,route,controller,operation,expected_status,actual_status,result,actor");
        int denied = 0;
        int identitySelf = 0;
        int publicNotApplicable = 0;

        for (var entry : requestMappings.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> controllerType = handler.getBeanType();
            if (!controllerType.getName().startsWith("com.saumya.pos.")
                || !AnnotatedElementUtils.hasAnnotation(controllerType, RestController.class)) continue;

            var methodPolicy = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                org.springframework.security.access.prepost.PreAuthorize.class);
            var classPolicy = AnnotatedElementUtils.findMergedAnnotation(controllerType,
                org.springframework.security.access.prepost.PreAuthorize.class);
            var policy = methodPolicy != null ? methodPolicy : classPolicy;
            assertThat(policy).as("method security policy for %s#%s",
                controllerType.getSimpleName(), handler.getMethod().getName()).isNotNull();

            String expression = policy.value();
            String operation = controllerType.getSimpleName() + "#" + handler.getMethod().getName();
            boolean publicEndpoint = expression.contains("permitAll()");
            boolean authenticatedIdentityOnly = expression.trim().equals("isAuthenticated()");
            Set<RequestMethod> mappingMethods = entry.getKey().getMethodsCondition().getMethods();
            List<RequestMethod> methods = mappingMethods.isEmpty()
                ? List.of(RequestMethod.GET)
                : mappingMethods.stream().sorted().toList();

            for (String route : entry.getKey().getPatternValues()) {
                String concretePath = route.replaceAll("\\{[^/]+\\}", "1");
                for (RequestMethod method : methods) {
                    if (publicEndpoint) {
                        report.add(String.join(",", method.name(), "\"" + route.replace("\"", "\"\"") + "\"",
                            controllerType.getSimpleName(), handler.getMethod().getName(),
                            "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", "NO_PERMISSION"));
                        publicNotApplicable++;
                        continue;
                    }

                    MockHttpServletRequestBuilder request =
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            org.springframework.http.HttpMethod.valueOf(method.name()),
                            java.net.URI.create("http://localhost" + concretePath))
                        .header("Authorization", "Bearer " + token)
                        .with(csrf());

                    for (Parameter parameter : handler.getMethod().getParameters()) {
                        var requestBody = parameter.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
                        if (requestBody != null) {
                            Object payload = defaultArgument(parameter.getType(), parameter.getName(), parameter.getParameterizedType());
                            request.contentType(APPLICATION_JSON).content(json.writeValueAsString(payload));
                        }
                        var requestParam = parameter.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
                        if (requestParam != null && requestParam.required()
                            && org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())) {
                            String name = !requestParam.name().isBlank() ? requestParam.name() : requestParam.value();
                            if (name.isBlank()) name = parameter.getName();
                            if (name.isBlank()) name = "runtimeParam";
                            request.param(name, String.valueOf(defaultArgument(parameter.getType(), name, parameter.getParameterizedType())));
                        }
                        var requestHeader = parameter.getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
                        if (requestHeader != null && requestHeader.required()
                            && org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue())) {
                            String name = !requestHeader.name().isBlank() ? requestHeader.name() : requestHeader.value();
                            if (name.isBlank()) name = parameter.getName();
                            if (name.isBlank()) name = "X-Runtime-Header";
                            request.header(name, String.valueOf(defaultArgument(parameter.getType(), name, parameter.getParameterizedType())));
                        }
                        var cookieValue = parameter.getAnnotation(org.springframework.web.bind.annotation.CookieValue.class);
                        if (cookieValue != null && cookieValue.required()) {
                            String name = !cookieValue.name().isBlank() ? cookieValue.name() : cookieValue.value();
                            if (name.isBlank()) name = parameter.getName();
                            if (name.isBlank()) name = "runtimeCookie";
                            request.cookie(new Cookie(name, "A".repeat(43)));
                        }
                    }

                    int expected = authenticatedIdentityOnly ? 200 : 403;
                    int actual = mvc.perform(request).andReturn().getResponse().getStatus();
                    assertThat(actual).as("persisted no-effective-permission actor %s on %s %s (%s)",
                        USER, method.name(), route, expression).isEqualTo(expected);
                    String result = "PASS";
                    if (expected == 403) denied++; else identitySelf++;
                    report.add(String.join(",", method.name(), "\"" + route.replace("\"", "\"\"") + "\"",
                        controllerType.getSimpleName(), handler.getMethod().getName(),
                        Integer.toString(expected), Integer.toString(actual), result, "NO_PERMISSION"));
                    System.out.println("RUNTIME_AUTHORIZATION_NO_PERMISSION_HTTP|"
                        + method.name() + "|" + route + "|" + operation + "|"
                        + expected + "|" + actual + "|PASS");
                }
            }
        }

        assertThat(denied).as("every permission-protected mapped endpoint denies the persisted no-permission actor")
            .isGreaterThan(100);
        Path reportPath = Path.of("target", "p0-06-runtime-no-permission-http.csv");
        try {
            Files.createDirectories(reportPath.getParent());
            Files.write(reportPath, report, StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError("Could not write no-permission HTTP report", failure);
        }
        System.out.println("RUNTIME_AUTHORIZATION_NO_PERMISSION_SUMMARY|denied403=" + denied
            + "|identitySelf200=" + identitySelf + "|publicNotApplicable=" + publicNotApplicable + "|PASS");
    }

    private Object defaultArgument(Class<?> type, String fieldName, Type genericType) {
        String name = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (type == String.class) {
            if (name.contains("email") || name.contains("username") || name.contains("actor")
                || name.contains("cashier")) return "runtime.matrix.viewer@example.test";
            if (name.contains("ticket")) return "P006-TICKET-REF";
            if (name.contains("token")) return "A".repeat(43);
            if (name.contains("code") || name.contains("otp")) return "123456";
            if (name.contains("reason") || name.contains("purpose")) return "Runtime authorization matrix";
            if (name.contains("currency")) return "INR";
            if (name.contains("rolekey")) return "runtime-role";
            if (name.contains("slug")) return "runtime-slug";
            if (name.contains("sku")) return "RUNTIME-SKU";
            if (name.contains("barcode")) return "RUNTIME-BARCODE";
            if (name.contains("phone")) return "9876543210";
            if (name.contains("method") || name.contains("channel") || name.contains("tender")) return "CASH";
            return "runtime-matrix-key-0001";
        }
        if ((type == boolean.class || type == Boolean.class)
            && (name.contains("requested") || name.contains("consent") || name.contains("accepted")
                || name.contains("confirmed") || name.contains("enabled") || name.contains("active"))) return true;
        if (Collection.class.isAssignableFrom(type)) {
            Class<?> elementType = String.class;
            if (genericType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length > 0
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> rawElement) {
                elementType = rawElement;
            }
            Object item = defaultArgument(elementType);
            if (Set.class.isAssignableFrom(type)) return Set.of(item);
            return List.of(item);
        }
        if (Map.class.isAssignableFrom(type)) return Map.of();
        return defaultArgument(type);
    }

    private Object defaultRecordComponent(RecordComponent component) {
        return defaultArgument(component.getType(), component.getName(), component.getGenericType());
    }

}

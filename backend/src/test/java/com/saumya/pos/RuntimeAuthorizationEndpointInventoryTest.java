package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves the live Spring MVC route table and checks that every controller
 * represented by a mapped endpoint has tenant/actor/assignment metadata in
 * the runtime matrix seed. This deliberately complements (and does not
 * replace) AuthorizationPolicyMatrixTest: it verifies actual registered
 * routes, not just annotation declarations.
 *
 * Persisted-principal execution is a separate acceptance gate; this class
 * must never be reported as proof of runtime authorization by itself.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:runtime-authorization-inventory;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@ActiveProfiles("test")
class RuntimeAuthorizationEndpointInventoryTest {
    @Autowired
    RequestMappingHandlerMapping requestMappings;

    @Autowired
    ObjectMapper objectMapper;

    record Endpoint(String method, String route, String controller, String operation)
        implements Comparable<Endpoint> {
        @Override public int compareTo(Endpoint other) {
            int routeOrder = route.compareTo(other.route);
            if (routeOrder != 0) return routeOrder;
            int methodOrder = method.compareTo(other.method);
            if (methodOrder != 0) return methodOrder;
            int controllerOrder = controller.compareTo(other.controller);
            return controllerOrder != 0 ? controllerOrder : operation.compareTo(other.operation);
        }
    }

    @Test
    void everyLiveControllerEndpointHasCanonicalActorScopeAndAssignmentMetadata() throws Exception {
        JsonNode matrix;
        try (InputStream stream = getClass().getResourceAsStream("/runtime-authorization-matrix.json")) {
            assertThat(stream).as("runtime-authorization-matrix.json is present").isNotNull();
            matrix = objectMapper.readTree(stream);
        }
        JsonNode policies = matrix.path("controllerPolicies");
        assertThat(policies.isObject()).as("controllerPolicies object exists").isTrue();

        Set<Endpoint> endpoints = new TreeSet<>();
        Set<String> liveControllers = new TreeSet<>();
        for (var entry : requestMappings.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> controllerType = handler.getBeanType();
            if (!controllerType.getName().startsWith("com.saumya.pos.")
                || !AnnotatedElementUtils.hasAnnotation(controllerType, RestController.class)) {
                continue;
            }

            String controller = controllerType.getSimpleName();
            liveControllers.add(controller);
            assertThat(policies.has(controller))
                .as("canonical actor/scope/assignment metadata for live controller %s", controller)
                .isTrue();

            RequestMappingInfo mapping = entry.getKey();
            Set<String> routes = mapping.getPatternValues();
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            List<String> methodNames = methods.isEmpty()
                ? List.of("ANY")
                : methods.stream().map(Enum::name).sorted().toList();
            for (String route : routes) {
                for (String method : methodNames) {
                    endpoints.add(new Endpoint(method, route, controller, handler.getMethod().getName()));
                }
            }
        }

        assertThat(liveControllers).isNotEmpty();
        assertThat(endpoints).as("live endpoint inventory is non-empty").hasSizeGreaterThan(50);
        assertThat(liveControllers).as("all controller policy metadata is referenced by a live endpoint")
            .containsAll(java.util.stream.StreamSupport.stream(policies.fieldNames().spliterator(), false).toList());

        // Stable report payload makes the exact live route inventory available
        // in CI failure output without maintaining a second handwritten route list.
        List<String> report = new ArrayList<>();
        endpoints.forEach(endpoint -> report.add(
            endpoint.method() + " " + endpoint.route() + " -> "
                + endpoint.controller() + "#" + endpoint.operation()));
        report.sort(Comparator.naturalOrder());
        System.out.println("RUNTIME_AUTHORIZATION_ENDPOINT_INVENTORY count="
            + report.size() + " controllers=" + liveControllers.size());
        report.forEach(System.out::println);
    }
}

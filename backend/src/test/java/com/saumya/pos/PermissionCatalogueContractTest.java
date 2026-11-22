package com.saumya.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.membership.StandardRoleTemplates;
import com.saumya.pos.membership.VendorPermissionRegistry;
import com.saumya.pos.platform.PlatformPermissionRegistry;
import com.saumya.pos.security.PermissionCatalogue;
import java.io.InputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PermissionCatalogueContractTest {
    private JsonNode catalogue() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/authorization-policy-registry.json")) {
            assertThat(input).isNotNull();
            return new ObjectMapper().readTree(input);
        }
    }

    @Test void canonicalAssignableCataloguesDriveBothRuntimeRegistriesAndTemplates() {
        assertThat(PermissionCatalogue.version()).isEqualTo(2);
        assertThat(VendorPermissionRegistry.all()).containsExactlyInAnyOrderElementsOf(PermissionCatalogue.vendorPermissions());
        assertThat(PlatformPermissionRegistry.all()).containsExactlyInAnyOrderElementsOf(PermissionCatalogue.platformPermissions());
        assertThat(StandardRoleTemplates.implemented()).containsExactlyInAnyOrderElementsOf(PermissionCatalogue.vendorPermissions());
        assertThat(PermissionCatalogue.customerPermissions()).containsExactly("CUSTOMER");
        assertThat(VendorPermissionRegistry.all()).noneMatch(p -> p.startsWith("PLATFORM_") || p.equals("CUSTOMER"));
        assertThat(PlatformPermissionRegistry.all()).allMatch(p -> p.startsWith("PLATFORM_"));
    }

    @Test void everyAssignablePermissionHasOneCompleteImplementedTrace() throws Exception {
        JsonNode root = catalogue();
        Set<String> expected = new TreeSet<>();
        expected.addAll(VendorPermissionRegistry.all());
        expected.addAll(PlatformPermissionRegistry.all());
        Set<String> found = new TreeSet<>();
        for (JsonNode item : root.path("permissionTraceability")) {
            String permission = item.path("permission").asText();
            assertThat(found.add(permission)).as("one trace row for %s", permission).isTrue();
            assertThat(item.path("status").asText()).isEqualTo("IMPLEMENTED");
            for (String field : List.of("actor", "endpoint", "service", "scope", "ui", "test")) {
                assertThat(item.path(field).asText()).as("%s.%s", permission, field).isNotBlank();
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test void reservedOrphanedConflictingUnknownAndPlatformGrantsCannotEnterVendorRoles() throws Exception {
        JsonNode root = catalogue();
        for (String field : List.of("reservedVendorPermissions", "orphanedPermissions", "unknownPermissions")) {
            for (JsonNode item : root.path(field)) {
                String permission = item.isTextual() ? item.asText() : item.path("id").asText();
                assertThatThrownBy(() -> VendorPermissionRegistry.requireVendorPermission(permission))
                    .as("non-assignable %s from %s", permission, field)
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            }
        }
        for (JsonNode item : root.path("conflictingPermissions")) {
            String permission = item.path("id").asText();
            assertThatThrownBy(() -> VendorPermissionRegistry.requireVendorPermission(permission))
                .as("conflicting permission %s", permission)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        assertThatThrownBy(() -> VendorPermissionRegistry.requireVendorPermission("PLATFORM_TENANT_CREATE"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> VendorPermissionRegistry.requireVendorPermission("NOT_A_PERMISSION"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(VendorPermissionRegistry.requireVendorPermission("PRODUCT_UPDATE")).isEqualTo("PRODUCT_UPDATE");
    }

    @Test void catalogClassificationsAreDisjointAndReservedPermissionsAreNotStandardRoleGrants() {
        Set<String> all = new HashSet<>();
        List<Set<String>> groups = List.of(PermissionCatalogue.vendorPermissions(), PermissionCatalogue.platformPermissions(),
            PermissionCatalogue.customerPermissions(), PermissionCatalogue.reservedVendorPermissions(),
            PermissionCatalogue.orphanedPermissions(), PermissionCatalogue.conflictingPermissions());
        for (Set<String> group : groups) for (String permission : group) assertThat(all.add(permission)).isTrue();
        Set<String> templateGrants = new HashSet<>();
        StandardRoleTemplates.ALL.stream().filter(StandardRoleTemplates.Definition::supported)
            .forEach(template -> templateGrants.addAll(template.permissions()));
        assertThat(PermissionCatalogue.vendorPermissions()).containsAll(templateGrants);
        assertThat(templateGrants).doesNotContainAnyElementsOf(PermissionCatalogue.reservedVendorPermissions());
    }
}

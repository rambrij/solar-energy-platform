package com.saumya.pos.membership;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StandardRoleTemplatesTest {
    @Test
    void catalogueContainsEveryApprovedStandardRoleAndNoImplicitPlatformRole() {
        assertThat(StandardRoleTemplates.ALL)
            .extracting(StandardRoleTemplates.Definition::key)
            .containsExactly("VENDOR_ADMIN", "POS_CASHIER", "SALES_MANAGER",
                "INVENTORY_MANAGER", "ACCOUNTANT", "SALES_EXECUTIVE",
                "OPERATIONS_MANAGER", "SERVICE_MANAGER", "SERVICE_ENGINEER",
                "CONTENT_MANAGER");
        assertThat(StandardRoleTemplates.ALL)
            .extracting(StandardRoleTemplates.Definition::key)
            .doesNotContain("PLATFORM_SUPER_ADMIN", "PLATFORM_SUPPORT");
    }

    @Test
    void everySupportedTemplateUsesOnlyUniqueApprovedImplementedVendorPermissions() {
        for (var template : StandardRoleTemplates.ALL) {
            if (!template.supported()) {
                assertThat(template.permissions()).as(template.key()).isEmpty();
                continue;
            }
            assertThat(template.permissions()).as(template.key()).isNotEmpty();
            assertThat(template.permissions()).as(template.key())
                .doesNotHaveDuplicates()
                .allMatch(permission -> !permission.startsWith("PLATFORM_"));
            assertThat(StandardRoleTemplates.implemented().containsAll(template.permissions())).isTrue();
            assertThat(VendorPermissionRegistry.all().containsAll(template.permissions())).isTrue();
        }
    }

    @Test
    void vendorAdminSnapshotCoversEveryImplementedAssignableVendorPermission() {
        var vendorAdmin = StandardRoleTemplates.ALL.stream()
            .filter(template -> template.key().equals("VENDOR_ADMIN"))
            .findFirst().orElseThrow();
        assertThat(vendorAdmin.supported()).isTrue();
        assertThat(vendorAdmin.permissions())
            .containsExactlyInAnyOrderElementsOf(VendorPermissionRegistry.all());
    }

    @Test
    void deferredTemplatesHaveExplicitReasonsAndAreNotMaterializedDefinitions() {
        var deferred = StandardRoleTemplates.ALL.stream()
            .filter(template -> !template.supported())
            .collect(Collectors.toMap(StandardRoleTemplates.Definition::key,
                StandardRoleTemplates.Definition::deferredReason));
        assertThat(deferred.keySet()).containsExactlyInAnyOrder(
            "OPERATIONS_MANAGER", "SERVICE_MANAGER",
            "SERVICE_ENGINEER", "CONTENT_MANAGER");
        assertThat(StandardRoleTemplates.ALL.stream().filter(t -> t.key().equals("SALES_EXECUTIVE")).findFirst().orElseThrow().permissions())
            .containsExactlyInAnyOrder("LEAD_VIEW", "LEAD_UPDATE");
        assertThat(deferred.values()).allMatch(reason -> reason != null && !reason.isBlank());
    }

    @Test
    void templateRoleKeysAreVersionedWithoutChangingTheV1Identity() {
        assertThat(StandardRoleTemplates.standardRoleKey("POS_CASHIER", 1))
            .isEqualTo("standard:POS_CASHIER");
        assertThat(StandardRoleTemplates.standardRoleKey("POS_CASHIER", 2))
            .isEqualTo("standard:POS_CASHIER:v2");
        assertThatThrownBy(() -> StandardRoleTemplates.standardRoleKey("bad key", 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

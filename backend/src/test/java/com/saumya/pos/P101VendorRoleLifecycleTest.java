package com.saumya.pos;

import com.saumya.pos.isolation.TenantIsolationFixture;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.membership.VendorRoleService;
import com.saumya.pos.membership.TenantEntitlementPolicy;
import com.saumya.pos.security.ActorSessionDetails;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.saumya.pos.isolation.TenantIsolationFixture.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:p101-role-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "checkout.cleanup-enabled=false"
})
@ActiveProfiles("test")
@TestPropertySource(properties = "payments.mock-enabled=true")
@Transactional
@DisplayName("P1-01 — Custom role lifecycle and delegation")
class P101VendorRoleLifecycleTest {
    private static final long TARGET_USER_ID = 9_872_501L;
    private static final long SECOND_BRANCH_ID = 9_871_111L;
    private static final String TARGET = "p101-role-target@vendor-a.example.test";

    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired VendorRoleService roles;
    @Autowired MembershipService memberships;
    @Autowired TenantEntitlementPolicy entitlementPolicy;

    private TenantIsolationFixture.Seed seed;

    @BeforeEach
    void seedTenantActorsAndRolePermissions() {
        seed = new TenantIsolationFixture(db, passwords).seed();
        assertThat(db.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE LOWER(table_name)='employee_permissions'
            """,Integer.class)).isZero();
        assertThat(db.queryForObject("""
            SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'legacy-%'
            """,Integer.class)).isZero();
        grant(VENDOR_A_TENANT, seed.vendorARoleId(), "ROLE_CREATE", VENDOR_A);
        grant(VENDOR_A_TENANT, seed.vendorARoleId(), "PRODUCT_VIEW", VENDOR_A);
        grant(VENDOR_A_TENANT, seed.vendorARoleId(), "POS_VIEW", VENDOR_A);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id=? AND status='ACTIVE'",
            Integer.class,VENDOR_A_TENANT)).as("P1-01 tenant has a persisted active subscription").isEqualTo(1);
        Object rawEntitlements=db.queryForObject("""
            SELECT v.entitlements
            FROM tenant_subscriptions s
            JOIN subscription_plan_versions v ON v.plan_id=s.plan_id AND v.version=s.plan_version
            WHERE s.tenant_id=?
            """,Object.class,VENDOR_A_TENANT);
        String jdbcEntitlements=rawEntitlements instanceof byte[] jsonBytes
            ? new String(jsonBytes,java.nio.charset.StandardCharsets.UTF_8)
            : String.valueOf(rawEntitlements);
        assertThat(jdbcEntitlements)
            .as("P1-01 JDBC entitlement JSON text: %s",jdbcEntitlements)
            .startsWith("{")
            .contains("\\"ADMINISTRATION\\":true","\\"POS\\":true");
        assertThat(db.queryForList("""
            SELECT rp.permission
            FROM employee_role_assignments a
            JOIN vendor_role_permissions rp
              ON rp.tenant_id=a.tenant_id AND rp.role_id=a.role_id
            WHERE a.tenant_id=? AND a.username=? AND a.active=TRUE
            ORDER BY rp.permission
            """,String.class,VENDOR_A_TENANT,VENDOR_A))
            .as("P1-01 persisted active assignment exposes its stored grants")
            .contains("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW");
        assertThat(entitlementPolicy.permissions(VENDOR_A_TENANT,
            List.of("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW")))
            .as("P1-01 subscription policy retains entitled feature permissions")
            .contains("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW");
        List<String> effectiveRoleRows=db.queryForList("""
            SELECT DISTINCT rp.permission
            FROM employee_role_assignments a
            JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
            JOIN vendor_role_permissions rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id
            WHERE a.tenant_id=? AND a.username=? AND a.active=TRUE AND r.active=TRUE
            ORDER BY rp.permission
            """,String.class,VENDOR_A_TENANT,VENDOR_A);
        assertThat(effectiveRoleRows)
            .as("P1-01 exact MembershipService persisted-role SQL")
            .contains("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW");
        assertThat(com.saumya.pos.membership.VendorPermissionRegistry.all())
            .as("P1-01 persisted role grants are in the vendor allow-list")
            .contains("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW");
        assertThat(memberships.access(VENDOR_A).orElseThrow().permissions())
            .as("P1-01 actor permissions include persisted role grants after subscription filtering")
            .contains("ROLE_CREATE","ROLE_VIEW","PRODUCT_VIEW","POS_VIEW");
        db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",
            TARGET_USER_ID, TARGET, passwords.encode("P101-test-employee-password"));
        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,?,?,TRUE)",
            TARGET, VENDOR_A_TENANT, VENDOR_A_BRANCH);
        db.update("INSERT INTO branches(id,tenant_id,name) VALUES(?,?,?)",
            SECOND_BRANCH_ID, VENDOR_A_TENANT, "P101 Branch A2");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create, read, replace permissions, increment version and deactivate immediately")
    void roleCrudPermissionReplacementAndDeactivationAreImmediatelyEffective() {
        VendorRoleService.Role created = asVendorA(() ->
            roles.create("p1-lifecycle", "Lifecycle role", List.of("PRODUCT_VIEW"), "P1-01 create"));
        assertThat(created.active()).isTrue();
        assertThat(created.version()).isZero();
        assertThat(created.permissions()).containsExactly("PRODUCT_VIEW");
        assertThat(asVendorA(() -> roles.role(created.id()))).isEqualTo(created);

        asVendorA(() -> roles.assignRoles(TARGET, List.of(created.id()), "P1-01 assign"));
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::permissions).asList().contains("PRODUCT_VIEW");

        VendorRoleService.Role updated = asVendorA(() ->
            roles.update(created.id(), created.version(), "Lifecycle role updated",
                List.of("POS_VIEW"), "P1-01 replace permissions"));
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.displayName()).isEqualTo("Lifecycle role updated");
        assertThat(updated.permissions()).containsExactly("POS_VIEW");
        assertThat(permissionRows(created.id())).containsExactly("POS_VIEW");

        var afterUpdate = memberships.access(TARGET).orElseThrow();
        assertThat(afterUpdate.permissions()).contains("POS_VIEW").doesNotContain("PRODUCT_VIEW");

        asVendorA(() -> { roles.deactivate(created.id(), "P1-01 deactivate"); return null; });
        VendorRoleService.Role deactivated = asVendorA(() -> roles.role(created.id()));
        assertThat(deactivated.active()).isFalse();
        assertThat(deactivated.version()).isEqualTo(2L);
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::permissions).asList().isEmpty();

        VendorRoleService.Employee target = asVendorA(() -> roles.employees().stream()
            .filter(e -> e.username().equals(TARGET)).findFirst().orElseThrow());
        assertThat(target.roleIds()).doesNotContain(created.id());

        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_CREATED", Long.toString(created.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_UPDATED", Long.toString(created.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_PERMISSIONS_CHANGED", Long.toString(created.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_DEACTIVATED", Long.toString(created.id()))).isEqualTo(1);
    }

    @Test
    @DisplayName("Stale role version returns 409 without role, permission or success-audit changes")
    void staleRoleUpdateReturnsConflictWithoutPersistenceOrAuditSideEffects() {
        VendorRoleService.Role created = asVendorA(() ->
            roles.create("p1-stale-role", "Original role", List.of("PRODUCT_VIEW"), "P1-01 create"));
        int auditBefore = tenantAuditCount(VENDOR_A_TENANT);

        assertThatThrownBy(() -> asVendorA(() ->
            roles.update(created.id(), 99L, "Stale replacement", List.of("POS_VIEW"), "P1-01 stale")))
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        VendorRoleService.Role persisted = asVendorA(() -> roles.role(created.id()));
        assertThat(persisted.displayName()).isEqualTo("Original role");
        assertThat(persisted.version()).isZero();
        assertThat(persisted.permissions()).containsExactly("PRODUCT_VIEW");
        assertThat(permissionRows(created.id())).containsExactly("PRODUCT_VIEW");
        assertThat(tenantAuditCount(VENDOR_A_TENANT)).isEqualTo(auditBefore);
    }

    @Test
    @DisplayName("Multiple roles union permissions; replacement and final revocation take effect immediately")
    void roleAssignmentReplacementAndRevocationUpdateEffectivePermissionUnion() {
        var productRole = asVendorA(() ->
            roles.create("p1-product-reader", "Product reader", List.of("PRODUCT_VIEW"), "P1-01 create"));
        var posRole = asVendorA(() ->
            roles.create("p1-pos-reader", "POS reader", List.of("POS_VIEW"), "P1-01 create"));

        VendorRoleService.Employee assigned = asVendorA(() ->
            roles.assignRoles(TARGET, List.of(productRole.id(), posRole.id()), "P1-01 assign two roles"));
        assertThat(assigned.roleIds()).containsExactlyInAnyOrder(productRole.id(), posRole.id());
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::permissions).asList()
            .contains("PRODUCT_VIEW", "POS_VIEW");

        VendorRoleService.Employee replaced = asVendorA(() ->
            roles.assignRoles(TARGET, List.of(posRole.id()), "P1-01 replace with one role"));
        assertThat(replaced.roleIds()).containsExactly(posRole.id());
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::permissions).asList()
            .contains("POS_VIEW").doesNotContain("PRODUCT_VIEW");

        VendorRoleService.Employee revoked = asVendorA(() ->
            roles.assignRoles(TARGET, List.of(), "P1-01 revoke final role"));
        assertThat(revoked.roleIds()).isEmpty();
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::permissions).asList().isEmpty();
        assertThat(activeAssignments(TARGET)).isZero();
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_ASSIGNED", Long.toString(productRole.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_ASSIGNED", Long.toString(posRole.id()))).isEqualTo(2);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_REVOKED", Long.toString(productRole.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_REVOKED", Long.toString(posRole.id()))).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_ASSIGNMENTS_REPLACED", TARGET)).isEqualTo(3);
    }

    @Test
    @DisplayName("Disabling an employee fails closed and audits assignment revocation")
    void disabledEmployeeReceivesNoEffectiveVendorPermissions() {
        var role = asVendorA(() ->
            roles.create("p1-disabled-employee", "Disabled employee role",
                List.of("PRODUCT_VIEW"), "P1-01 create"));
        asVendorA(() -> roles.assignRoles(TARGET, List.of(role.id()), "P1-01 assign"));
        asVendorA(() -> { roles.disableEmployee(TARGET, "P1-01 disable"); return null; });

        assertThat(memberships.access(TARGET)).isEmpty();
        assertThat(db.queryForObject("SELECT enabled FROM employees WHERE tenant_id=? AND username=?",
            Boolean.class, VENDOR_A_TENANT, TARGET)).isFalse();
        assertThat(activeAssignments(TARGET)).isZero();
        assertThat(auditCount(VENDOR_A_TENANT, "EMPLOYEE_DISABLED", TARGET)).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_REVOKED_ON_EMPLOYEE_DISABLE",
            Long.toString(role.id()))).isEqualTo(1);
    }

    @Test
    @DisplayName("Vendor A and Vendor B cannot read, update, deactivate or assign each other's roles")
    void vendorRoleLifecycleAndAssignmentsAreIsolatedInBothDirections() {
        int auditsA = tenantAuditCount(VENDOR_A_TENANT);
        int auditsB = tenantAuditCount(VENDOR_B_TENANT);

        notFound(() -> asVendorA(() -> roles.role(seed.vendorBRoleId())));
        notFound(() -> asVendorA(() ->
            roles.update(seed.vendorBRoleId(), 0L, "Foreign update", List.of("ROLE_VIEW"), "probe")));
        notFound(() -> asVendorA(() -> { roles.deactivate(seed.vendorBRoleId(), "probe"); return null; }));
        notFound(() -> asVendorA(() ->
            roles.assignRoles(TARGET, List.of(seed.vendorBRoleId()), "foreign assignment")));

        notFound(() -> asVendorB(() -> roles.role(seed.vendorARoleId())));
        notFound(() -> asVendorB(() ->
            roles.update(seed.vendorARoleId(), 0L, "Foreign update", List.of("ROLE_VIEW"), "probe")));
        notFound(() -> asVendorB(() -> { roles.deactivate(seed.vendorARoleId(), "probe"); return null; }));
        notFound(() -> asVendorB(() ->
            roles.assignRoles(VENDOR_B, List.of(seed.vendorARoleId()), "foreign assignment")));

        assertThat(tenantAuditCount(VENDOR_A_TENANT)).isEqualTo(auditsA);
        assertThat(tenantAuditCount(VENDOR_B_TENANT)).isEqualTo(auditsB);
        assertThat(db.queryForObject("SELECT active FROM vendor_roles WHERE tenant_id=? AND id=?",
            Boolean.class, VENDOR_A_TENANT, seed.vendorARoleId())).isTrue();
        assertThat(db.queryForObject("SELECT active FROM vendor_roles WHERE tenant_id=? AND id=?",
            Boolean.class, VENDOR_B_TENANT, seed.vendorBRoleId())).isTrue();
        assertThat(db.queryForObject("""
            SELECT COUNT(*) FROM employee_role_assignments
            WHERE tenant_id=? AND username=? AND role_id=? AND active=TRUE
            """, Integer.class, VENDOR_B_TENANT, VENDOR_B, seed.vendorBRoleId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ROLE_CREATE, ROLE_UPDATE and ROLE_ASSIGN do not allow privilege escalation")
    void delegationRejectsUnheldUnknownAndPlatformPermissionsWithoutSideEffects() {
        int rolesBefore = tenantRoleCount(VENDOR_A_TENANT);
        int auditsBefore = tenantAuditCount(VENDOR_A_TENANT);
        int targetAssignmentsBefore = activeAssignments(TARGET);

        forbidden(() -> asVendor(VENDOR_A, VENDOR_A_TENANT, VENDOR_A_BRANCH,
            List.of("ROLE_CREATE"), () -> roles.create(
                "p1-escalation-create", "Escalation", List.of("PRODUCT_VIEW"), "P1-01 escalation")));
        forbidden(() -> asVendor(VENDOR_A, VENDOR_A_TENANT, VENDOR_A_BRANCH,
            List.of("ROLE_UPDATE"), () -> roles.update(
                seed.vendorARoleId(), 0L, "Escalation", List.of("POS_VIEW"), "P1-01 escalation")));
        forbidden(() -> asVendor(VENDOR_A, VENDOR_A_TENANT, VENDOR_A_BRANCH,
            List.of("ROLE_ASSIGN"), () -> roles.assignRoles(
                TARGET, List.of(seed.vendorARoleId()), "P1-01 escalation")));

        badRequest(() -> asVendorA(() -> roles.create(
            "p1-unknown-permission", "Unknown permission", List.of("UNKNOWN_PERMISSION"), "P1-01 reject unknown")));
        badRequest(() -> asVendorA(() -> roles.create(
            "p1-platform-permission", "Platform permission", List.of("PLATFORM_TENANT_CREATE"), "P1-01 reject platform")));

        assertThat(tenantRoleCount(VENDOR_A_TENANT)).isEqualTo(rolesBefore);
        assertThat(tenantAuditCount(VENDOR_A_TENANT)).isEqualTo(auditsBefore);
        assertThat(activeAssignments(TARGET)).isEqualTo(targetAssignmentsBefore);
        assertThat(permissionRows(seed.vendorARoleId())).contains("PRODUCT_VIEW", "POS_VIEW");
    }

    @Test
    @DisplayName("Branch changes rebind active role assignments to the new tenant branch")
    void branchChangeRebindsRoleAssignmentsToTheNewTenantBranch() {
        var role = asVendorA(() ->
            roles.create("p1-branch-role", "Branch role", List.of("PRODUCT_VIEW"), "P1-01 create"));
        asVendorA(() -> roles.assignRoles(TARGET, List.of(role.id()), "P1-01 assign"));

        VendorRoleService.Employee moved = asVendorA(() ->
            roles.updateBranch(TARGET, SECOND_BRANCH_ID, "P1-01 branch change"));

        assertThat(moved.branchId()).isEqualTo(SECOND_BRANCH_ID);
        assertThat(moved.roleIds()).containsExactly(role.id());
        assertThat(memberships.access(TARGET)).get()
            .extracting(MembershipService.Access::branch).isEqualTo(SECOND_BRANCH_ID);
        assertThat(db.queryForObject("""
            SELECT branch_id FROM employee_role_assignments
            WHERE tenant_id=? AND username=? AND role_id=? AND active=TRUE
            """, Long.class, VENDOR_A_TENANT, TARGET, role.id())).isEqualTo(SECOND_BRANCH_ID);
        assertThat(auditCount(VENDOR_A_TENANT, "EMPLOYEE_BRANCH_CHANGED", TARGET)).isEqualTo(1);
        assertThat(auditCount(VENDOR_A_TENANT, "ROLE_ASSIGNMENT_REVOKED_ON_BRANCH_CHANGE",
            Long.toString(role.id()))).isEqualTo(1);
    }

    private void grant(long tenant, long roleId, String permission, String actor) {
        db.update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",
            tenant, roleId, permission, actor);
    }

    private List<String> permissionRows(long roleId) {
        return db.queryForList("""
            SELECT permission FROM vendor_role_permissions
            WHERE tenant_id=? AND role_id=? ORDER BY permission
            """, String.class, VENDOR_A_TENANT, roleId);
    }

    private int auditCount(long tenant, String action, String resourceId) {
        return db.queryForObject("""
            SELECT COUNT(*) FROM vendor_access_audit_events
            WHERE tenant_id=? AND action=? AND resource_id=?
            """, Integer.class, tenant, action, resourceId);
    }

    private int tenantAuditCount(long tenant) {
        return db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=?",
            Integer.class, tenant);
    }

    private int tenantRoleCount(long tenant) {
        return db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=?",
            Integer.class, tenant);
    }

    private int activeAssignments(String username) {
        return db.queryForObject("""
            SELECT COUNT(*) FROM employee_role_assignments
            WHERE tenant_id=? AND username=? AND active=TRUE
            """, Integer.class, VENDOR_A_TENANT, username);
    }

    private <T> T asVendorA(Supplier<T> action) {
        return asVendorAWithPermissions(memberships.access(VENDOR_A).orElseThrow().permissions(), action);
    }

    private <T> T asVendorB(Supplier<T> action) {
        return asVendor(VENDOR_B, VENDOR_B_TENANT, VENDOR_B_BRANCH,
            memberships.access(VENDOR_B).orElseThrow().permissions(), action);
    }

    private <T> T asVendor(String username, long tenant, long branch,
            List<String> permissions, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext current = SecurityContextHolder.createEmptyContext();
        var authorities = java.util.stream.Stream.concat(
            permissions.stream(), java.util.stream.Stream.of(
                com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))
            .map(SimpleGrantedAuthority::new).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(username, "P101", authorities);
        authentication.setDetails(new ActorSessionDetails("VENDOR", tenant, tenant + ":" + username, "p101-test-family"));
        current.setAuthentication(authentication);
        SecurityContextHolder.setContext(current);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private <T> T asVendorAWithPermissions(List<String> permissions, Supplier<T> action) {
        return asVendor(VENDOR_A, VENDOR_A_TENANT, VENDOR_A_BRANCH, permissions, action);
    }

    private static void notFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void badRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

package com.saumya.pos;

import com.saumya.pos.customer.internal.VendorCustomerRepository;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.identity.AuthenticationService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.security.ActorSessionDetails;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.isolation.TenantIsolationFixture;
import com.saumya.pos.isolation.TenantIsolationFixture.Seed;
import com.saumya.pos.membership.VendorRoleService;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.saumya.pos.isolation.TenantIsolationFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused P0-04 runtime proof. The wider POS integration suite complements
 * this fixture with HTTP, checkout, payment, receipt, return and export cases.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "checkout.cleanup-enabled=false"
})
@ActiveProfiles("test")
@TestPropertySource(properties = "payments.mock-enabled=true")
@Transactional
@DisplayName("P0-04 — Vendor and global customer isolation")
class P004TenantIsolationTest {
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired VendorRoleService roles;
    @Autowired CustomerAccess customers;
    @Autowired VendorCustomerRepository vendorCustomers;
    @Autowired InventoryService inventory;

    private Seed seed;

    @BeforeEach
    void seedTwoVendorsAndTwoCustomers() {
        seed = new TenantIsolationFixture(db, passwords).seed();
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Vendor A and Vendor B cannot list, read, update or deactivate each other's roles or employees")
    void vendorEmployeeAndRoleOperationsAreTenantScopedInBothDirections() {
        var aRoles = asVendorA(() -> roles.roles());
        var bRoles = asVendorB(() -> roles.roles());

        assertThat(aRoles).extracting(VendorRoleService.Role::id).containsExactly(seed.vendorARoleId());
        assertThat(bRoles).extracting(VendorRoleService.Role::id).containsExactly(seed.vendorBRoleId());
        assertThat(asVendorA(() -> roles.employees()))
            .extracting(VendorRoleService.Employee::username).containsExactly(VENDOR_A);
        assertThat(asVendorB(() -> roles.employees()))
            .extracting(VendorRoleService.Employee::username).containsExactly(VENDOR_B);

        notFound(() -> asVendorA(() -> roles.role(seed.vendorBRoleId())));
        notFound(() -> asVendorB(() -> roles.role(seed.vendorARoleId())));
        notFound(() -> asVendorA(() -> roles.update(
            seed.vendorBRoleId(), 0, "Forged Vendor B Role", List.of("ROLE_VIEW"), "P004 cross-tenant probe")));
        notFound(() -> asVendorB(() -> roles.update(
            seed.vendorARoleId(), 0, "Forged Vendor A Role", List.of("ROLE_VIEW"), "P004 cross-tenant probe")));
        notFound(() -> asVendorA(() -> {
            roles.deactivate(seed.vendorBRoleId(), "P004 cross-tenant probe");
            return null;
        }));
        notFound(() -> asVendorB(() -> {
            roles.deactivate(seed.vendorARoleId(), "P004 cross-tenant probe");
            return null;
        }));

        // Employee targets are tenant-scoped independently of the supplied
        // username, role ID, branch ID or register ID.
        notFound(() -> asVendorA(() -> roles.effectivePermissions(VENDOR_B)));
        notFound(() -> asVendorB(() -> roles.effectivePermissions(VENDOR_A)));
        notFound(() -> asVendorA(() -> roles.assignRoles(
            VENDOR_B, List.of(seed.vendorARoleId()), "P004 cross-tenant probe")));
        notFound(() -> asVendorB(() -> roles.assignRoles(
            VENDOR_A, List.of(seed.vendorBRoleId()), "P004 cross-tenant probe")));
        notFound(() -> asVendorA(() -> roles.updateBranch(
            VENDOR_B, VENDOR_A_BRANCH, "P004 cross-tenant probe")));
        notFound(() -> asVendorB(() -> roles.updateBranch(
            VENDOR_A, VENDOR_B_BRANCH, "P004 cross-tenant probe")));
        notFound(() -> asVendorA(() -> roles.assignRegisters(
            VENDOR_B, List.of(), "P004 cross-tenant probe")));
        notFound(() -> asVendorB(() -> roles.assignRegisters(
            VENDOR_A, List.of(), "P004 cross-tenant probe")));
        notFound(() -> asVendorA(() -> {
            roles.disableEmployee(VENDOR_B, "P004 cross-tenant probe");
            return null;
        }));
        notFound(() -> asVendorB(() -> {
            roles.disableEmployee(VENDOR_A, "P004 cross-tenant probe");
            return null;
        }));

        assertThat(db.queryForObject(
            "SELECT display_name FROM vendor_roles WHERE tenant_id=? AND id=?",
            String.class, VENDOR_A_TENANT, seed.vendorARoleId())).isEqualTo("P004 Vendor A Admin");
        assertThat(db.queryForObject(
            "SELECT display_name FROM vendor_roles WHERE tenant_id=? AND id=?",
            String.class, VENDOR_B_TENANT, seed.vendorBRoleId())).isEqualTo("P004 Vendor B Admin");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=? AND resource_id=?",
            Integer.class, VENDOR_A_TENANT, Long.toString(seed.vendorARoleId()))).isZero();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=? AND resource_id=?",
            Integer.class, VENDOR_B_TENANT, Long.toString(seed.vendorBRoleId()))).isZero();
    }

    @Test
    @DisplayName("Customer identity and profile ownership come only from the authenticated customer")
    void customerIdentityCannotBeSubstitutedWithAnotherUsernameOrProfileId() {
        assertThat(asCustomerA(() -> customers.resolve(CUSTOMER_A))).isEqualTo(CUSTOMER_A_ID);
        assertThat(asCustomerB(() -> customers.resolve(CUSTOMER_B))).isEqualTo(CUSTOMER_B_ID);

        forbidden(() -> asCustomerA(() -> customers.resolve(CUSTOMER_B)));
        forbidden(() -> asCustomerB(() -> customers.resolve(CUSTOMER_A)));
        notFound(() -> asCustomerA(() -> {
            customers.lock(CUSTOMER_B_ID);
            return null;
        }));
        notFound(() -> asCustomerB(() -> {
            customers.lock(CUSTOMER_A_ID);
            return null;
        }));

        asCustomerA(() -> { customers.lock(CUSTOMER_A_ID); return null; });
        asCustomerB(() -> { customers.lock(CUSTOMER_B_ID); return null; });

        forbidden(() -> asVendorA(() -> customers.resolve(CUSTOMER_A)));
        forbidden(() -> asVendorB(() -> customers.resolve(CUSTOMER_B)));
        forbidden(() -> asCustomerA(() -> roles.roles()));

        // Customer A is one global identity with two independent vendor links;
        // Vendor A cannot substitute Customer B's Vendor B-only relationship.
        assertThat(vendorCustomers.existsByTenantAndCustomer(VENDOR_A_TENANT, CUSTOMER_A_ID)).isTrue();
        assertThat(vendorCustomers.existsByTenantAndCustomer(VENDOR_B_TENANT, CUSTOMER_A_ID)).isTrue();
        assertThat(vendorCustomers.existsByTenantAndCustomer(VENDOR_A_TENANT, CUSTOMER_B_ID)).isFalse();
        assertThat(vendorCustomers.existsByTenantAndCustomer(VENDOR_B_TENANT, CUSTOMER_B_ID)).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("Forged tenant and resource identifiers cannot read or mutate another vendor's inventory")
    void inventoryScopeAndCompositeDatabaseOwnershipRejectCrossTenantProbesWithoutSideEffects() {
        assertThat(asVendorA(() -> inventory.atBranch(
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_PRODUCT_ID))).isEqualTo(5);
        assertThat(asVendorB(() -> inventory.atBranch(
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_PRODUCT_ID))).isEqualTo(7);

        forbidden(() -> asVendorA(() -> inventory.atBranch(
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_PRODUCT_ID)));
        forbidden(() -> asVendorB(() -> inventory.atBranch(
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_PRODUCT_ID)));
        forbidden(() -> asVendorA(() -> {
            inventory.issue(VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_PRODUCT_ID, 1);
            return null;
        }));
        forbidden(() -> asVendorB(() -> {
            inventory.issue(VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_PRODUCT_ID, 1);
            return null;
        }));

        // A foreign product ID under the caller's authenticated tenant returns
        // the same zero aggregate as any absent stock row, not foreign metadata.
        assertThat(asVendorA(() -> inventory.atBranch(
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_B_PRODUCT_ID))).isZero();
        assertThat(asVendorB(() -> inventory.atBranch(
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_A_PRODUCT_ID))).isZero();

        assertThatThrownBy(() -> db.update(
            "INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(?,?,?,99)",
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_B_PRODUCT_ID))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(
            "INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(?,?,?,99)",
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_A_PRODUCT_ID))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("""
            INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
            VALUES(?,?,?, ?,TRUE,?)
            """, VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A,
            seed.vendorBRoleId(), VENDOR_A))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(db.queryForObject(
            "SELECT quantity FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",
            Integer.class, VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_PRODUCT_ID)).isEqualTo(5);
        assertThat(db.queryForObject(
            "SELECT quantity FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",
            Integer.class, VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_PRODUCT_ID)).isEqualTo(7);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM inventory WHERE quantity=99 AND tenant_id IN (?,?)",
            Integer.class, VENDOR_A_TENANT, VENDOR_B_TENANT)).isZero();
    }

    private static final List<String> VENDOR_A_GRANTS = List.of(
        "ROLE_VIEW", "ROLE_UPDATE", "USER_VIEW", "ROLE_ASSIGN", "USER_UPDATE",
        "USER_DISABLE", "INVENTORY_VIEW", "INVENTORY_ADJUST");
    private static final List<String> VENDOR_B_GRANTS = VENDOR_A_GRANTS;

    private <T> T asVendorA(Supplier<T> operation) {
        return as(VENDOR_A, AuthenticationService.ActorType.VENDOR,
            VENDOR_A_TENANT, VENDOR_A_BRANCH, VENDOR_A_GRANTS, operation);
    }

    private <T> T asVendorB(Supplier<T> operation) {
        return as(VENDOR_B, AuthenticationService.ActorType.VENDOR,
            VENDOR_B_TENANT, VENDOR_B_BRANCH, VENDOR_B_GRANTS, operation);
    }

    private <T> T asCustomerA(Supplier<T> operation) {
        return as(CUSTOMER_A, AuthenticationService.ActorType.CUSTOMER,
            null, null, List.of("CUSTOMER"), operation);
    }

    private <T> T asCustomerB(Supplier<T> operation) {
        return as(CUSTOMER_B, AuthenticationService.ActorType.CUSTOMER,
            null, null, List.of("CUSTOMER"), operation);
    }

    private <T> T as(String username, AuthenticationService.ActorType actorType,
            Long tenantId, Long branchId, List<String> permissions, Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var current = SecurityContextHolder.createEmptyContext();
        String marker = switch (actorType) {
            case PLATFORM -> AuthorizationContext.ACTOR_CONTEXT_PLATFORM;
            case VENDOR -> AuthorizationContext.ACTOR_CONTEXT_VENDOR;
            case CUSTOMER -> AuthorizationContext.ACTOR_CONTEXT_CUSTOMER;
        };
        var authorities = java.util.stream.Stream.concat(
            permissions.stream(), java.util.stream.Stream.of(marker))
            .map(SimpleGrantedAuthority::new).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            username, "P004", authorities);
        String membershipId = tenantId == null ? null : tenantId + ":" + username;
        authentication.setDetails(new ActorSessionDetails(
            actorType.name(), tenantId, membershipId, "p004-test-family"));
        current.setAuthentication(authentication);
        SecurityContextHolder.setContext(current);
        try {
            return operation.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void notFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}

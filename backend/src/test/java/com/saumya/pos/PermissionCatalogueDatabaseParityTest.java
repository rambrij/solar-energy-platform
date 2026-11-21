package com.saumya.pos;

import com.saumya.pos.membership.VendorPermissionRegistry;
import com.saumya.pos.platform.PlatformPermissionRegistry;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "checkout.cleanup-enabled=false"
})
@ActiveProfiles("test")
@TestPropertySource(properties="payments.mock-enabled=true")
class PermissionCatalogueDatabaseParityTest {
    @Autowired JdbcTemplate jdbc;

    @Test void databaseGrantTablesMatchTheCanonicalAssignableCatalogues() {
        Set<String> vendor = new TreeSet<>(jdbc.queryForList(
            "SELECT permission FROM vendor_permission_catalogue", String.class));
        Set<String> platform = new TreeSet<>(jdbc.queryForList(
            "SELECT permission FROM platform_permission_catalogue", String.class));
        assertThat(vendor).containsExactlyInAnyOrderElementsOf(VendorPermissionRegistry.all());
        assertThat(platform).containsExactlyInAnyOrderElementsOf(PlatformPermissionRegistry.all());
        Integer vendorDrift = jdbc.queryForObject("""
            SELECT COUNT(*) FROM vendor_role_permissions rp
            LEFT JOIN vendor_permission_catalogue c ON c.permission=rp.permission
            WHERE c.permission IS NULL
            """, Integer.class);
        Integer platformDrift = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_user_permissions p
            LEFT JOIN platform_permission_catalogue c ON c.permission=p.permission
            WHERE c.permission IS NULL
            """, Integer.class);
        assertThat(vendorDrift).isZero();
        assertThat(platformDrift).isZero();
        assertForeignKey("FK_VRP_PERMISSION_CATALOG");
        assertForeignKey("FK_PUP_PERMISSION_CATALOG");
    }

    private void assertForeignKey(String name) {
        String table = switch (name) {
            case "FK_VRP_PERMISSION_CATALOG" -> "VENDOR_ROLE_PERMISSIONS";
            case "FK_PUP_PERMISSION_CATALOG" -> "PLATFORM_USER_PERMISSIONS";
            default -> throw new IllegalArgumentException("Unexpected permission catalogue FK: " + name);
        };
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE UPPER(CONSTRAINT_NAME)=?
              AND UPPER(CONSTRAINT_SCHEMA)=UPPER(CURRENT_SCHEMA())
              AND UPPER(TABLE_NAME)=?
              AND CONSTRAINT_TYPE='FOREIGN KEY'
            """, Integer.class, name, table);
        assertThat(count).as("database foreign key %s on %s in current schema", name, table).isEqualTo(1);
    }
}

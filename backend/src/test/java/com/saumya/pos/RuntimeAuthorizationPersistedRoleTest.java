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

    @BeforeEach
    void seedPersistedViewOnlyActor() {
        db.update("DELETE FROM identity_refresh_tokens WHERE username=?", USER);
        db.update("DELETE FROM employee_register_assignments WHERE username=?", USER);
        db.update("DELETE FROM employee_role_assignments WHERE username=?", USER);
        db.update("DELETE FROM vendor_role_permissions WHERE role_id IN (SELECT id FROM vendor_roles WHERE role_key='runtime-matrix-viewer')");
        db.update("DELETE FROM vendor_roles WHERE role_key='runtime-matrix-viewer'");
        db.update("DELETE FROM employees WHERE username=?", USER);
        db.update("DELETE FROM users WHERE username=?", USER);

        db.update("DELETE FROM branches WHERE id=91001");
        db.update("DELETE FROM tenants WHERE id=91001");
        db.update("INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status) VALUES(91001,'Runtime Matrix Vendor','runtime-matrix-vendor','Runtime Matrix Vendor','ACTIVE')");
        db.update("INSERT INTO branches(id,tenant_id,name) VALUES(91001,91001,'Runtime Matrix Branch')");
        db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(91001,?,?,TRUE)",
            USER, passwords.encode(PASSWORD));
        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,91001,91001,TRUE)", USER);
        db.update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(91001,'runtime-matrix-viewer','Runtime Matrix VIEW only',TRUE,?,?)", USER, USER);
        Long roleId = db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=91001 AND role_key='runtime-matrix-viewer'", Long.class);
        db.update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(91001,?,'ROLE_VIEW',?)", roleId, USER);
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
    void anonymousProtectedEndpointReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/vendor/roles"))
            .andExpect(status().isUnauthorized());
    }
}

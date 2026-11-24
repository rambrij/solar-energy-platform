package com.saumya.pos.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Explicitly invoked by CI after the ordinary full suite. Support grant/revoke
 * writes immutable platform audit events; this acceptance fixture therefore
 * uses fresh globally unique identities and is not part of the ordinary test
 * discovery pass that repeatedly clears tenant fixtures.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:platform-support-acceptance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "payments.mock-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSupportRuntimeAuthorizationAcceptance {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;

    @Test
    void persistedPlatformSupportCanGrantAndRevokeAuditedTimeBoundAccess() throws Exception {
        long tenantId = db.queryForObject("SELECT COALESCE(MAX(id),0)+1000 FROM tenants", Long.class);
        long userId = db.queryForObject("SELECT COALESCE(MAX(id),0)+1000 FROM users", Long.class);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "p006.support." + suffix + "@platform.test";
        String slug = "p006-support-" + suffix;
        String ticket = "P0-06-" + suffix.toUpperCase();

        db.update("INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status) VALUES(?,?,?,?,'ACTIVE')",
            tenantId, "P0-06 Support Acceptance", slug, "P0-06 Support Acceptance Ltd");
        db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",
            userId, username, passwords.encode("PlatformSupportAcceptance2026!"));
        db.update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,'PLATFORM_SUPPORT_ACCESS')",
            username);

        String loginBody = json.writeValueAsString(Map.of(
            "username", username, "password", "PlatformSupportAcceptance2026!"));
        String loginResponse = mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token = json.readTree(loginResponse).path("accessToken").asText();
        assertThat(token).isNotBlank();

        mvc.perform(get("/api/v1/platform/support-access").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        Instant expiresAt = Instant.now().plusSeconds(1800);
        String grantBody = json.writeValueAsString(Map.of(
            "tenantId", tenantId,
            "purpose", "Verify restricted vendor login as part of P0-06",
            "ticketReference", ticket,
            "expiresAt", expiresAt.toString()
        ));
        String grantResponse = mvc.perform(post("/api/v1/platform/support-access")
                .header("Authorization", "Bearer " + token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(grantBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode grant = json.readTree(grantResponse);
        long grantId = grant.path("id").asLong();
        assertThat(grantId).isPositive();

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_support_access WHERE id=? AND tenant_id=? AND actor_username=? AND revoked_at IS NULL",
            Integer.class, grantId, tenantId, username)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_audit_events WHERE actor_username=? AND action='SUPPORT_ACCESS_GRANTED' AND resource_id=? AND outcome='SUCCESS'",
            Integer.class, username, Long.toString(grantId))).isEqualTo(1);

        String revokeBody = json.writeValueAsString(Map.of("reason", "P0-06 verification completed"));
        mvc.perform(post("/api/v1/platform/support-access/" + grantId + "/revoke")
                .header("Authorization", "Bearer " + token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(revokeBody))
            .andExpect(status().isOk());

        var repeat = mvc.perform(post("/api/v1/platform/support-access/" + grantId + "/revoke")
                .header("Authorization", "Bearer " + token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(revokeBody))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(repeat.getContentAsString()).path("revokedBy").asText()).isEqualTo(username);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_support_access WHERE id=? AND revoked_at IS NOT NULL AND revoked_by=?",
            Integer.class, grantId, username)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_audit_events WHERE actor_username=? AND action IN ('SUPPORT_ACCESS_GRANTED','SUPPORT_ACCESS_REVOKED') AND resource_id=? AND outcome='SUCCESS'",
            Integer.class, username, Long.toString(grantId))).isEqualTo(2);

        // The same persisted global support identity must not obtain unrelated
        // platform administration or vendor privileges.
        mvc.perform(get("/api/v1/platform/dashboard").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vendor/products").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}

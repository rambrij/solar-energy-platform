package com.saumya.pos.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class VendorPaymentAccountControllerAuthorizationTest {
    private static String policy(String method) {
        return java.util.Arrays.stream(VendorPaymentAccountController.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals(method))
            .findFirst().map(candidate->candidate.getAnnotation(PreAuthorize.class))
            .map(PreAuthorize::value).orElse("");
    }

    @Test void listRequiresTenantProfileView() {
        assertThat(policy("list")).isEqualTo("hasAuthority('TENANT_PROFILE_VIEW')");
    }

    @Test void submitRequiresTenantProfileUpdate() {
        assertThat(policy("submit")).isEqualTo("hasAuthority('TENANT_PROFILE_UPDATE')");
    }
}

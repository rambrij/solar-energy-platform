package com.saumya.pos.marketplace;

import com.saumya.pos.marketplace.internal.Quotation;
import com.saumya.pos.marketplace.internal.QuotationRevision;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Focused guard and domain-state tests for the assisted-sales quotation boundary. */
class QuotationControllerAuthorizationTest {
    private static String policy(String method) throws Exception {
        PreAuthorize annotation=QuotationController.class.getDeclaredMethod(method,
            switch(method) {
                case "create" -> QuotationController.CreateRequest.class;
                case "revise" -> long.class;
                case "decide" -> java.security.Principal.class;
                default -> java.security.Principal.class;
            }).getAnnotation(PreAuthorize.class);
        return annotation==null?"":annotation.value();
    }

    @Test
    void vendorQuotationListRequiresLeadViewAndTenantAssignmentScope() throws Exception {
        assertThat(QuotationController.class.getDeclaredMethod("vendorList")
            .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('LEAD_VIEW')");
    }

    @Test
    void createQuotationRequiresAssignedLeadAndTenantOwnedProducts() throws Exception {
        assertThat(policy("create")).isEqualTo("hasAuthority('LEAD_UPDATE')");
    }

    @Test
    void reviseQuotationRequiresTenantAndAssignedLeadScope() throws Exception {
        assertThat(policy("revise")).isEqualTo("hasAuthority('LEAD_UPDATE')");
    }

    @Test
    void customerQuotationListReturnsOnlyAuthenticatedCustomerRows() throws Exception {
        assertThat(policy("customerList")).isEqualTo("hasAuthority('CUSTOMER')");
    }

    @Test
    void customerCannotReadAnotherCustomersQuotation() throws Exception {
        assertThat(policy("customerGet")).isEqualTo("hasAuthority('CUSTOMER')");
    }

    @Test
    void customerDecisionIsOwnerBoundAndConvertsOnce() {
        Instant now=Instant.parse("2026-11-27T12:00:00Z");
        Quotation quotation=new Quotation(12,34,56,"vendor@example.test",now);
        int revisionNumber=quotation.addRevision("vendor@example.test",now);
        assertThat(revisionNumber).isEqualTo(1);
        quotation.accept(revisionNumber,56,now);
        assertThat(quotation.status()).isEqualTo("ACCEPTED");
        assertThat(quotation.acceptedByCustomerId()).isEqualTo(56L);
        assertThat(quotation.acceptedRevisionNumber()).isEqualTo(1);
        quotation.converted("order-123",now);
        assertThat(quotation.status()).isEqualTo("CONVERTED");
        assertThat(quotation.convertedOrderId()).isEqualTo("order-123");
        assertThatThrownBy(()->quotation.converted("order-456",now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supersededQuotationRevisionCannotBeDecided() {
        Instant now=Instant.parse("2026-11-27T12:00:00Z");
        Quotation quotation=new Quotation(12,34,56,"vendor@example.test",now);
        int first=quotation.addRevision("vendor@example.test",now);
        int second=quotation.addRevision("vendor@example.test",now.plusSeconds(1));
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(quotation.latestRevisionNumber()).isEqualTo(2);
        QuotationRevision old=new QuotationRevision(12,quotation.id()==null?1:quotation.id(),first,
            now.plusSeconds(3600),"INR",java.math.BigDecimal.ONE,java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ONE,null,"vendor@example.test",now);
        old.send(now);
        old.supersede();
        assertThat(old.status()).isEqualTo("SUPERSEDED");
        assertThatThrownBy(()->old.decide("ACCEPTED",56,null,now))
            .isInstanceOf(IllegalStateException.class);
    }
}

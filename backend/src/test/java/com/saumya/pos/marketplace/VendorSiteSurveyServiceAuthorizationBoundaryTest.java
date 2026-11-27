package com.saumya.pos.marketplace;

import com.saumya.pos.customer.VendorAccessAuditService;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiry;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiryRepository;
import com.saumya.pos.marketplace.internal.SiteSurveyRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorSiteSurveyServiceAuthorizationBoundaryTest {
    private static final Clock CLOCK=Clock.fixed(
        Instant.parse("2026-11-27T10:00:00Z"),ZoneOffset.UTC);

    private VendorSiteSurveyService service(AuthorizationContext authorization,
            SiteSurveyRepository surveys,MarketplaceEnquiryRepository enquiries,
            VendorAccessAuditService audit) {
        return new VendorSiteSurveyService(surveys,enquiries,authorization,audit,CLOCK);
    }

    @Test void customerCannotReadVendorSurveysBeforeAnyTenantQuery() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "customer@example.test",null,null,null,Set.of("CUSTOMER"),false,true,false));
        var surveys=mock(SiteSurveyRepository.class);
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var audit=mock(VendorAccessAuditService.class);
        assertThatThrownBy(()->service(authorization,surveys,enquiries,audit).list())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(surveys,enquiries,audit);
    }

    @Test void surveyReadRequiresDedicatedPermissionBeforeQuerying() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "sales@vendor.test",71L,5L,"71:sales@vendor.test",Set.of("CRM_NOTE_VIEW"),false,false,true));
        var surveys=mock(SiteSurveyRepository.class);
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var audit=mock(VendorAccessAuditService.class);
        assertThatThrownBy(()->service(authorization,surveys,enquiries,audit).list())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(surveys,enquiries,audit);
    }

    @Test void employeeListUsesOnlyAuthenticatedTenantAndOwnAssignment() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "sales@vendor.test",71L,5L,"71:sales@vendor.test",Set.of("LEAD_VIEW"),false,false,true));
        var surveys=mock(SiteSurveyRepository.class);
        when(surveys.findTop100ByTenantIdAndAssignedToOrderByScheduledAtDescIdDesc(71L,"sales@vendor.test"))
            .thenReturn(List.of());
        var audit=mock(VendorAccessAuditService.class);
        var result=service(authorization,surveys,mock(MarketplaceEnquiryRepository.class),audit).list();
        assertThat(result).isEmpty();
        verify(surveys).findTop100ByTenantIdAndAssignedToOrderByScheduledAtDescIdDesc(71L,"sales@vendor.test");
        verify(audit).recordSiteSurveyRead();
        verifyNoMoreInteractions(surveys);
    }

    @Test void employeeCannotScheduleSurveyAgainstAnotherEmployeesLead() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "sales@vendor.test",71L,5L,"71:sales@vendor.test",
            Set.of("LEAD_UPDATE"),false,false,true));
        var surveys=mock(SiteSurveyRepository.class);
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var lead=mock(MarketplaceEnquiry.class);
        when(lead.assignedTo()).thenReturn("other@vendor.test");
        when(enquiries.findByTenantIdAndIdForUpdate(71L,44L)).thenReturn(Optional.of(lead));
        var audit=mock(VendorAccessAuditService.class);
        var request=new VendorSiteSurveyService.Create("Site address",
            Instant.parse("2026-12-01T10:00:00Z"),null,null,null,null,"Schedule requested");
        assertThatThrownBy(()->service(authorization,surveys,enquiries,audit).create(44L,request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(surveys,audit);
    }

    @Test void managerCanScheduleSurveyOnlyForLeadInItsAuthenticatedTenant() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "manager@vendor.test",71L,5L,"71:manager@vendor.test",
            Set.of("LEAD_UPDATE","LEAD_ASSIGN"),false,false,true));
        var surveys=mock(SiteSurveyRepository.class);
        var enquiries=mock(MarketplaceEnquiryRepository.class);
        var lead=mock(MarketplaceEnquiry.class);
        when(lead.assignedTo()).thenReturn("sales@vendor.test");
        when(lead.status()).thenReturn("QUALIFIED");
        when(enquiries.findByTenantIdAndIdForUpdate(71L,44L)).thenReturn(Optional.of(lead));
        when(surveys.saveAndFlush(any())).thenAnswer(call->{ var saved=(com.saumya.pos.marketplace.internal.SiteSurvey)call.getArgument(0); org.springframework.test.util.ReflectionTestUtils.setField(saved,"id",9L); return saved; });
        var audit=mock(VendorAccessAuditService.class);
        var request=new VendorSiteSurveyService.Create("Factory roof",
            Instant.parse("2026-12-01T10:00:00Z"),"METAL",BigDecimal.valueOf(250),BigDecimal.valueOf(5),
            "Access via east gate","Customer requested a site visit");
        var view=service(authorization,surveys,enquiries,audit).create(44L,request);
        assertThat(view.enquiryId()).isEqualTo(44L);
        assertThat(view.status()).isEqualTo("SCHEDULED");
        assertThat(view.assignedTo()).isEqualTo("sales@vendor.test");
        verify(audit).recordSiteSurveyMutation(9L,"SITE_SURVEY_CREATED","Customer requested a site visit");
    }

    @Test void employeeCannotUpdateAnotherEmployeesSurvey() {
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "sales@vendor.test",71L,5L,"71:sales@vendor.test",
            Set.of("LEAD_UPDATE"),false,false,true));
        var surveys=mock(SiteSurveyRepository.class);
        var survey=mock(com.saumya.pos.marketplace.internal.SiteSurvey.class);
        when(survey.assignedTo()).thenReturn("other@vendor.test");
        when(surveys.findByTenantIdAndIdForUpdate(71L,9L)).thenReturn(Optional.of(survey));
        var audit=mock(VendorAccessAuditService.class);
        var request=new VendorSiteSurveyService.Update(0,"COMPLETED",null,null,null,null,
            "Panels measured","Completion report");
        assertThatThrownBy(()->service(authorization,surveys,mock(MarketplaceEnquiryRepository.class),audit).update(9L,request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(audit);
    }
}

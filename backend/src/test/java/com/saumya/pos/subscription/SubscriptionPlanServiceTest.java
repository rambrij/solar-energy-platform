package com.saumya.pos.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.test.util.ReflectionTestUtils;
import com.saumya.pos.subscription.internal.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {
    @Mock SubscriptionPlanRepository plans;@Mock SubscriptionPlanVersionRepository versions;
    private SubscriptionPlanService.PlanInput input(String price){return new SubscriptionPlanService.PlanInput("starter","Starter",new BigDecimal(price),"INR","MONTHLY",5,1024,1,0,"{\"features\":{\"POS\":true}}",7,"{\"gstRatePercent\":0}","Starter SaaS plan");}
    @Test void revisedPriceCreatesNewVersionWithoutChangingHistoricTerms(){
        var now=Instant.parse("2026-11-16T00:00:00Z");var plan=mock(SubscriptionPlan.class);
        when(plans.findById(1L)).thenReturn(Optional.of(plan));when(plan.id()).thenReturn(1L);when(plan.planKey()).thenReturn("starter");
        when(plan.currentVersion()).thenReturn(1);when(plan.displayName()).thenReturn("Starter");when(plan.active()).thenReturn(true);when(plan.createdAt()).thenReturn(now);when(plan.updatedAt()).thenReturn(now);
        var old=new SubscriptionPlanVersion(1,1,new BigDecimal("799"),"INR","MONTHLY",5,1024,1,0,"{\"pos\":true}",7,"{}","Starter plan","admin",now);
        var saved=new ArrayList<SubscriptionPlanVersion>();
        when(versions.saveAndFlush(any())).thenAnswer(inv->{var v=(SubscriptionPlanVersion)inv.getArgument(0);saved.add(v);return v;});
        when(versions.findByPlanIdOrderByVersionDesc(1L)).thenAnswer(inv->{var all=new ArrayList<SubscriptionPlanVersion>();all.addAll(saved);all.add(old);return all;});
        var service=new SubscriptionPlanServiceImpl(plans,versions,Clock.fixed(now,ZoneOffset.UTC),new ObjectMapper());
        var auth=mock(AuthorizationContext.class);when(auth.current()).thenReturn(new AuthorizationContext.Context("platform-admin",null,null,Set.of("PLATFORM_PLAN_MANAGE","PLATFORM_SUBSCRIPTION_VIEW"),true,false,false));
        ReflectionTestUtils.setField(service,"authorization",auth);
        var result=service.revise(1,input("999"),"platform-admin","corr");
        assertThat(saved).hasSize(1);assertThat(saved.getFirst().version()).isEqualTo(2);assertThat(saved.getFirst().price()).isEqualByComparingTo("999");
        assertThat(old.version()).isEqualTo(1);assertThat(old.price()).isEqualByComparingTo("799");
        assertThat(result.versions()).extracting(SubscriptionPlanService.PlanVersionView::version).contains(1,2);
    }
    @Test void rejectsUnknownOrNonBooleanFeatureEntitlements(){
        var service=new SubscriptionPlanServiceImpl(plans,versions,Clock.systemUTC(),new ObjectMapper());
        var unknown=new SubscriptionPlanService.PlanInput("starter","Starter",BigDecimal.ONE,"INR","MONTHLY",5,100,1,0,"{\"features\":{\"UNKNOWN\":true}}",7,"{}","plan");
        assertThatThrownBy(()->service.create(unknown,"admin",null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var nonBoolean=new SubscriptionPlanService.PlanInput("starter","Starter",BigDecimal.ONE,"INR","MONTHLY",5,100,1,0,"{\"features\":{\"POS\":\"yes\"}}",7,"{}","plan");
        assertThatThrownBy(()->service.create(nonBoolean,"admin",null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void rejectsMalformedEntitlementOrTaxSnapshotsBeforePersisting(){
        var plan=mock(SubscriptionPlan.class);
        when(plans.findByPlanKey("starter")).thenReturn(Optional.empty());
        when(plans.saveAndFlush(any())).thenReturn(plan);
        when(plan.id()).thenReturn(1L);
        var service=new SubscriptionPlanServiceImpl(plans,versions,Clock.systemUTC(),new ObjectMapper());
        var auth=mock(AuthorizationContext.class);when(auth.current()).thenReturn(new AuthorizationContext.Context("admin",null,null,Set.of("PLATFORM_PLAN_MANAGE"),true,false,false));
        ReflectionTestUtils.setField(service,"authorization",auth);
        var invalid=new SubscriptionPlanService.PlanInput("starter","Starter",BigDecimal.ONE,"INR","MONTHLY",5,100,1,0,"[]",7,"{}","plan");
        assertThatThrownBy(()->service.create(invalid,"admin",null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(versions,never()).saveAndFlush(any());
    }

    @Test void planCreateReviseActivateAndListDenyBeforeRepositoryAccessForVendorActor(){
        var service=new SubscriptionPlanServiceImpl(plans,versions,Clock.systemUTC(),new ObjectMapper());
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "vendor",7L,12L,Set.of("POS_VIEW"),false,false,true));
        ReflectionTestUtils.setField(service,"authorization",auth);

        assertThatThrownBy(()->service.create(input("799"),"vendor",null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.revise(1,input("999"),"vendor",null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.setActive(1,false,"vendor",null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(service::list)
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verifyNoInteractions(plans,versions);
    }

}

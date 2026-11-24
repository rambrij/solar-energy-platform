package com.saumya.pos.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.subscription.internal.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {
    private static final Pattern KEY=Pattern.compile("[a-z0-9][a-z0-9_-]{1,79}");
    private static final Set<String> INTERVALS=Set.of("MONTHLY","QUARTERLY","YEARLY");
    private final SubscriptionPlanRepository plans;private final SubscriptionPlanVersionRepository versions;private final Clock clock;private final ObjectMapper json;
    @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.security.AuthorizationContext authorization;
    private void requirePlatform(String actor,String permission){
        if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");
        var context=authorization.current();
        if(!context.isPlatformUser()||!context.username().equals(actor))throw new ResponseStatusException(FORBIDDEN,"Platform actor does not match authenticated identity");
        context.requirePermission(permission);
    }
    private void requireRead(){
        if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");
        var context=authorization.current();
        if(!context.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        if(!context.hasAnyPermission("PLATFORM_SUBSCRIPTION_VIEW","PLATFORM_PLAN_MANAGE"))throw new ResponseStatusException(FORBIDDEN,"Platform plan view permission required");
    }
    public SubscriptionPlanServiceImpl(SubscriptionPlanRepository plans,SubscriptionPlanVersionRepository versions,Clock clock,ObjectMapper json){this.plans=plans;this.versions=versions;this.clock=clock;this.json=json;}
    @Override public PlanView create(PlanInput input,String actor,String correlation){
        requirePlatform(actor,"PLATFORM_PLAN_MANAGE");
        String key=required(input.planKey(),80,"Plan key").toLowerCase(Locale.ROOT);
        if(!KEY.matcher(key).matches())throw new ResponseStatusException(BAD_REQUEST,"Plan key must use lowercase letters, numbers, underscore or hyphen");
        if(plans.findByPlanKey(key).isPresent())throw new ResponseStatusException(CONFLICT,"Plan key already exists");
        var now=clock.instant();var plan=plans.saveAndFlush(new SubscriptionPlan(key,required(input.displayName(),120,"Display name"),now));
        versions.saveAndFlush(version(plan.id(),1,input,actor,now));plan.currentVersion(1,input.displayName().strip(),now);
        return view(plan);
    }
    @Override public PlanView revise(long id,PlanInput input,String actor,String correlation){
        requirePlatform(actor,"PLATFORM_PLAN_MANAGE");
        var plan=plans.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Subscription plan unavailable"));
        if(input.planKey()==null||!plan.planKey().equalsIgnoreCase(input.planKey().strip()))throw new ResponseStatusException(BAD_REQUEST,"Plan key is immutable; create another plan for a different key");
        int next=plan.currentVersion()+1;var now=clock.instant();
        versions.saveAndFlush(version(id,next,input,actor,now));plan.currentVersion(next,required(input.displayName(),120,"Display name"),now);
        return view(plan);
    }
    @Override public PlanView setActive(long id,boolean active,String actor,String correlation){
        requirePlatform(actor,"PLATFORM_PLAN_MANAGE");
        var plan=plans.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Subscription plan unavailable"));
        plan.active(active,clock.instant());return view(plan);
    }
    @Override @Transactional(readOnly=true) public List<PlanView> list(){requireRead();return plans.findAll().stream().sorted(Comparator.comparing(SubscriptionPlan::planKey)).map(this::view).toList();}
    private SubscriptionPlanVersion version(long id,int version,PlanInput p,String actor,java.time.Instant now){
        validate(p);
        return new SubscriptionPlanVersion(id,version,p.price(),p.currency().toUpperCase(Locale.ROOT),p.billingInterval().toUpperCase(Locale.ROOT),
            p.maxUsers(),p.maxStorageMb(),p.maxBranches(),p.maxWarehouses(),normalizeJson(p.entitlements()),p.trialEligible(),p.graceDays(),
            normalizeJson(p.taxMetadata()),required(p.invoiceDescription(),500,"Invoice description"),actor,now);
    }
    private void validate(PlanInput p){
        if(p.price()==null||p.price().compareTo(BigDecimal.ZERO)<0||p.price().scale()>4)throw new ResponseStatusException(BAD_REQUEST,"Plan price must be non-negative with at most four decimal places");
        try{if(p.currency()==null||!Currency.getInstance(p.currency().toUpperCase(Locale.ROOT)).getCurrencyCode().equals(p.currency().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException();}catch(Exception e){throw new ResponseStatusException(BAD_REQUEST,"Valid ISO currency code required");}
        if(p.billingInterval()==null||!INTERVALS.contains(p.billingInterval().toUpperCase(Locale.ROOT)))throw new ResponseStatusException(BAD_REQUEST,"Billing interval must be MONTHLY, QUARTERLY or YEARLY");
        if(p.maxUsers()<1||p.maxStorageMb()<0||p.maxBranches()<1||p.maxWarehouses()<0||p.graceDays()<0||p.graceDays()>90)throw new ResponseStatusException(BAD_REQUEST,"Plan limits are outside supported bounds");
        validateEntitlements(normalizeJson(p.entitlements()));
        normalizeJson(p.taxMetadata());
    }
    private void validateEntitlements(String value){
        try{
            var root=json.readTree(value);
            var features=root.path("features");
            if(!features.isObject())throw new IllegalArgumentException("features object required");
            Set<String> known=Set.of("POS","CATALOG","INVENTORY","ADMINISTRATION","CRM","LEADS","QUOTATIONS",
                "ORDERS","RETURNS","FINANCE","SHIPMENTS","SERVICE","REPORTING");
            var names=features.fieldNames();
            while(names.hasNext()){
                String key=names.next();
                if(!known.contains(key)||!features.path(key).isBoolean())throw new IllegalArgumentException("Unknown or non-boolean feature: "+key);
            }
        }catch(Exception e){throw new ResponseStatusException(BAD_REQUEST,"Entitlements must contain a features object with supported boolean flags");}
    }
    private String normalizeJson(String value){
        try{var node=json.readTree(value);if(node==null||!node.isObject())throw new IllegalArgumentException();return json.writeValueAsString(node);}
        catch(Exception e){throw new ResponseStatusException(BAD_REQUEST,"Entitlements and tax metadata must be valid JSON objects");}
    }
    private PlanView view(SubscriptionPlan p){return new PlanView(p.id(),p.planKey(),p.displayName(),p.active(),p.currentVersion(),p.createdAt(),p.updatedAt(),
        versions.findByPlanIdOrderByVersionDesc(p.id()).stream().map(v->new PlanVersionView(v.version(),v.price(),v.currency(),v.billingInterval(),v.maxUsers(),v.maxStorageMb(),v.maxBranches(),v.maxWarehouses(),v.entitlements(),v.trialEligible(),v.graceDays(),v.taxMetadata(),v.invoiceDescription(),v.createdBy(),v.createdAt())).toList());}
    private static String required(String v,int max,String label){if(v==null||v.isBlank()||v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,label+" is required");return v.strip();}
}

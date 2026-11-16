package com.saumya.pos.platform;

import com.saumya.pos.platform.InvoiceMath.Interval;
import com.saumya.pos.platform.internal.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global subscription plans with immutable, versioned price and entitlements (SUB-001..003). */
@Service @Transactional
public class PlanService {
    public record Terms(Interval interval,BigDecimal price,String currency,BigDecimal taxRate,boolean taxInclusive,int graceDays,Integer maxUsers,Integer maxBranches,Integer maxProducts,List<String> features) {}
    public record VersionView(String id,String planCode,int version,Interval interval,BigDecimal price,String currency,BigDecimal taxRate,boolean taxInclusive,int graceDays,Integer maxUsers,Integer maxBranches,Integer maxProducts,List<String> features) {}
    public record PlanView(String code,String name,boolean active,List<VersionView> versions) {}
    private static final Pattern CODE=Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");
    private static final Pattern FEATURE=Pattern.compile("^[a-z0-9_.-]{1,40}$");
    private final PlanRepository plans;private final PlanVersionRepository versions;private final PlatformAuditService audit;private final Clock clock;
    public PlanService(PlanRepository plans,PlanVersionRepository versions,PlatformAuditService audit,Clock clock){this.plans=plans;this.versions=versions;this.audit=audit;this.clock=clock;}

    static VersionView view(PlanVersion v){
        return new VersionView(v.id(),v.planCode(),v.version(),Interval.valueOf(v.interval()),v.price(),v.currency(),v.taxRate(),v.taxInclusive(),v.graceDays(),v.maxUsers(),v.maxBranches(),v.maxProducts(),v.features().isBlank()?List.of():List.of(v.features().split(",")));
    }
    private static void check(Terms t){
        if(t==null||t.interval()==null)throw Problems.invalid("Billing interval is required");
        if(t.price()==null||t.price().signum()<0||t.price().scale()>2)throw Problems.invalid("Price must be non-negative with at most two decimals");
        if(t.currency()==null||!t.currency().matches("^[A-Z]{3}$"))throw Problems.invalid("Currency must be a three-letter upper-case code");
        if(t.taxRate()==null||t.taxRate().signum()<0||t.taxRate().compareTo(new BigDecimal("100"))>0||t.taxRate().scale()>2)throw Problems.invalid("Tax rate must be between 0 and 100");
        if(t.graceDays()<0||t.graceDays()>60)throw Problems.invalid("Grace period must be 0-60 days");
        for(Integer limit:new Integer[]{t.maxUsers(),t.maxBranches(),t.maxProducts()})if(limit!=null&&limit<1)throw Problems.invalid("Entitlement limits must be at least 1 or unlimited");
        var features=t.features()==null?List.<String>of():t.features();
        if(features.size()>30||features.stream().anyMatch(f->f==null||!FEATURE.matcher(f).matches()))throw Problems.invalid("Features must be up to 30 lower-case identifiers");
    }
    private PlanVersion newVersion(String code,int number,Terms t,String actor){
        return new PlanVersion(UUID.randomUUID().toString(),code,number,t.interval().name(),t.price(),t.currency(),t.taxRate(),t.taxInclusive(),t.graceDays(),t.maxUsers(),t.maxBranches(),t.maxProducts(),String.join(",",t.features()==null?List.of():t.features()),clock.instant(),actor);
    }
    public PlanView create(String actor,String code,String name,Terms terms){
        if(code==null||!CODE.matcher(code).matches())throw Problems.invalid("Plan code must be 3-40 lower-case letters, digits or hyphens");
        if(name==null||name.isBlank()||name.strip().length()>120)throw Problems.invalid("Plan name is required (max 120 characters)");
        check(terms);
        if(plans.existsById(code))throw Problems.conflict("Plan code already exists");
        plans.saveAndFlush(new SubscriptionPlan(code,name.strip(),clock.instant()));
        versions.saveAndFlush(newVersion(code,1,terms,actor));
        audit.record(actor,"PLAN_CREATED","PLAN",code,null,null,"version=1 price="+terms.price()+" "+terms.currency());
        return get(code);
    }
    /** Price/entitlement changes always create a new version; existing subscriptions keep the version they agreed to. */
    public PlanView addVersion(String actor,String code,Terms terms){
        check(terms);
        if(!plans.existsById(code))throw Problems.missing("Plan not found");
        int next=versions.findByPlanCodeOrderByVersionDesc(code).stream().findFirst().map(v->v.version()+1).orElse(1);
        versions.saveAndFlush(newVersion(code,next,terms,actor));
        audit.record(actor,"PLAN_VERSION_ADDED","PLAN",code,null,null,"version="+next+" price="+terms.price()+" "+terms.currency());
        return get(code);
    }
    public PlanView setActive(String actor,String code,boolean active){
        var plan=plans.findById(code).orElseThrow(()->Problems.missing("Plan not found"));plan.active(active);plans.saveAndFlush(plan);
        audit.record(actor,active?"PLAN_ACTIVATED":"PLAN_RETIRED","PLAN",code,null,null,null);
        return get(code);
    }
    @Transactional(readOnly=true) public PlanView get(String code){
        var plan=plans.findById(code).orElseThrow(()->Problems.missing("Plan not found"));
        return new PlanView(plan.code(),plan.name(),plan.active(),versions.findByPlanCodeOrderByVersionDesc(code).stream().map(PlanService::view).toList());
    }
    @Transactional(readOnly=true) public List<PlanView> list(){
        var out=new ArrayList<PlanView>();for(var p:plans.findAllByOrderByCodeAsc())out.add(get(p.code()));return out;
    }
}

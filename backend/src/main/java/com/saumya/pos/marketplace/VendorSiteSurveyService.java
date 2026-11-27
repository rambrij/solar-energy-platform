package com.saumya.pos.marketplace;

import com.saumya.pos.customer.VendorAccessAuditService;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiry;
import com.saumya.pos.marketplace.internal.MarketplaceEnquiryRepository;
import com.saumya.pos.marketplace.internal.SiteSurvey;
import com.saumya.pos.marketplace.internal.SiteSurveyRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class VendorSiteSurveyService {
    private final SiteSurveyRepository surveys;
    private final MarketplaceEnquiryRepository enquiries;
    private final AuthorizationContext authorization;
    private final VendorAccessAuditService audit;
    private final Clock clock;

    public VendorSiteSurveyService(SiteSurveyRepository surveys,
            MarketplaceEnquiryRepository enquiries, AuthorizationContext authorization,
            VendorAccessAuditService audit, Clock clock) {
        this.surveys=surveys;
        this.enquiries=enquiries;
        this.authorization=authorization;
        this.audit=audit;
        this.clock=clock;
    }

    public record Create(String siteAddress,Instant scheduledAt,String roofType,
            BigDecimal monthlyConsumptionKwh,BigDecimal proposedCapacityKw,String notes,String reason) {}
    public record Update(long expectedVersion,String status,Instant scheduledAt,String roofType,
            BigDecimal monthlyConsumptionKwh,BigDecimal proposedCapacityKw,String findings,String reason) {}
    public record View(Long id,long enquiryId,String siteAddress,Instant scheduledAt,String status,
            String roofType,BigDecimal monthlyConsumptionKwh,BigDecimal proposedCapacityKw,
            String notes,String findings,String assignedTo,String createdBy,String updatedBy,
            Instant createdAt,Instant updatedAt,long version) {}

    public List<View> list() {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_VIEW");
        List<SiteSurvey> rows=actor.hasPermission("LEAD_ASSIGN")
            ?surveys.findTop100ByTenantIdOrderByScheduledAtDescIdDesc(actor.tenantId())
            :surveys.findTop100ByTenantIdAndAssignedToOrderByScheduledAtDescIdDesc(
                actor.tenantId(),actor.username());
        audit.recordSiteSurveyRead();
        return rows.stream().map(this::view).toList();
    }

    public View create(long enquiryId,Create request) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_UPDATE");
        if(enquiryId<=0||request==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lead and survey details are required");
        String address=required(request.siteAddress(),500,"Site address");
        if(request.scheduledAt()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Scheduled date and time are required");
        String reason=required(request.reason(),500,"Change reason");
        String roofType=optional(request.roofType(),40,"Roof type");
        String notes=optional(request.notes(),2000,"Survey notes");
        validateMeasurement(request.monthlyConsumptionKwh(),10,"Monthly consumption");
        validateMeasurement(request.proposedCapacityKw(),8,"Proposed capacity");

        MarketplaceEnquiry lead=enquiries.findByTenantIdAndIdForUpdate(actor.tenantId(),enquiryId)
            .orElseThrow(this::missing);
        requireLeadScope(actor,lead);
        if("LOST".equals(lead.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A lost lead cannot receive a site survey");

        String assignee=lead.assignedTo()==null?actor.username():lead.assignedTo();
        Instant now=clock.instant();
        SiteSurvey saved=surveys.saveAndFlush(new SiteSurvey(actor.tenantId(),enquiryId,address,
            request.scheduledAt(),"SCHEDULED",roofType,request.monthlyConsumptionKwh(),
            request.proposedCapacityKw(),notes,assignee,actor.username(),now));
        audit.recordSiteSurveyMutation(saved.id(),"SITE_SURVEY_CREATED",reason);
        return view(saved);
    }

    public View update(long id,Update request) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("LEAD_UPDATE");
        if(id<=0||request==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Survey ID and update details are required");
        String status=request.status()==null?"":request.status().strip().toUpperCase(Locale.ROOT);
        if(!List.of("SCHEDULED","COMPLETED","CANCELLED").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported site survey status");
        String reason=required(request.reason(),500,"Change reason");
        String roofType=optional(request.roofType(),40,"Roof type");
        String findings=optional(request.findings(),4000,"Survey findings");
        validateMeasurement(request.monthlyConsumptionKwh(),10,"Monthly consumption");
        validateMeasurement(request.proposedCapacityKw(),8,"Proposed capacity");
        if("COMPLETED".equals(status)&&(findings==null||findings.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Survey findings are required to complete a survey");

        SiteSurvey survey=surveys.findByTenantIdAndIdForUpdate(actor.tenantId(),id)
            .orElseThrow(this::missing);
        requireSurveyScope(actor,survey);
        if(request.expectedVersion()<0||survey.version()!=request.expectedVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Site survey changed; reload before saving");
        if(!"SCHEDULED".equals(survey.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Completed or cancelled surveys are read-only");
        if("CANCELLED".equals(status)&&request.scheduledAt()!=null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A cancelled survey cannot be rescheduled");

        survey.update(status,request.scheduledAt(),roofType,request.monthlyConsumptionKwh(),
            request.proposedCapacityKw(),findings,actor.username(),clock.instant());
        SiteSurvey saved=surveys.saveAndFlush(survey);
        audit.recordSiteSurveyMutation(saved.id(),"SITE_SURVEY_UPDATED",reason);
        return view(saved);
    }

    private void requireLeadScope(AuthorizationContext.Context actor,MarketplaceEnquiry lead) {
        if(!actor.hasPermission("LEAD_ASSIGN")&&!Objects.equals(lead.assignedTo(),actor.username()))
            throw missing();
    }
    private void requireSurveyScope(AuthorizationContext.Context actor,SiteSurvey survey) {
        if(!actor.hasPermission("LEAD_ASSIGN")&&!actor.username().equals(survey.assignedTo()))
            throw missing();
    }
    private String required(String value,int max,String label) {
        String normalized=value==null?"":value.strip();
        if(normalized.isBlank()||normalized.length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" must contain 1 to "+max+" characters");
        return normalized;
    }
    private String optional(String value,int max,String label) {
        if(value==null)return null;
        String normalized=value.strip();
        if(normalized.length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" is too long");
        return normalized.isBlank()?null:normalized;
    }
    private void validateMeasurement(BigDecimal value,int maxIntegerDigits,String label) {
        if(value!=null&&(value.signum()<=0||value.scale()>2||value.precision()-value.scale()>maxIntegerDigits))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                label+" must be positive with at most "+maxIntegerDigits+" integer and two fractional digits");
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Site survey unavailable");
    }
    private View view(SiteSurvey row) {
        return new View(row.id(),row.enquiryId(),row.siteAddress(),row.scheduledAt(),row.status(),
            row.roofType(),row.monthlyConsumptionKwh(),row.proposedCapacityKw(),row.notes(),
            row.findings(),row.assignedTo(),row.createdBy(),row.updatedBy(),row.createdAt(),
            row.updatedAt(),row.version());
    }
}

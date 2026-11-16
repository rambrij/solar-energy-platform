package com.saumya.pos.retail;
import com.saumya.pos.retail.internal.*;
import java.time.Clock;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service
public class BranchPolicyService {
 private final BranchConfigurationRepository branches;private final PosService retail;private final BranchSettingsEventRepository events;private final RetailSequenceRepository sequence;private final RegisterDirectoryRepository registers;private final Clock clock;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 public BranchPolicyService(BranchConfigurationRepository branches,PosService retail,BranchSettingsEventRepository events,RetailSequenceRepository sequence,RegisterDirectoryRepository registers,Clock clock){this.branches=branches;this.retail=retail;this.events=events;this.sequence=sequence;this.registers=registers;this.clock=clock;}
 public record Policy(long branch,String name,long version,boolean enabled,String upi,String card,boolean split,int days,boolean cashRefunds,boolean invoiceRequired,boolean buyerDetailsRequired,boolean creditNotesEnabled,String currency){public String merchant(String method){return "UPI".equals(method)?upi:card;}}
 public record Settings(@NotBlank @Size(max=160) String name,long expectedVersion,boolean enabled,@Size(max=80) String upi,@Size(max=80) String card,boolean split,@Min(0) @Max(365) int days,boolean cashRefunds,boolean invoiceRequired,boolean buyerDetailsRequired,boolean creditNotesEnabled,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotBlank @Size(max=250) String reason){}
 private Policy view(BranchConfiguration b){return new Policy(b.id(),b.name(),b.version(),b.terminal(),b.upi(),b.card(),b.split(),b.returnDays(),b.cashRefunds(),b.invoice(),b.buyerDetails(),b.creditNotes(),b.currency());}
    
    @Transactional(readOnly = true)
    public Optional<Policy> find(long tenant, long branch) {
        var scope=retail.scope();if(scope.tenant()!=tenant||scope.branch()!=branch)throw new ResponseStatusException(NOT_FOUND,"Outlet unavailable");
        retail.authorize(scope,scope.cashier(),"POS_VIEW");
        return branches.findByTenantAndId(tenant, branch).map(this::view);
    }
    
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Policy> lock(long tenant, long branch) {
        var scope=retail.scope();if(scope.tenant()!=tenant||scope.branch()!=branch)throw new ResponseStatusException(NOT_FOUND,"Outlet unavailable");
        retail.authorize(scope,scope.cashier(),"POS_VIEW");
        return branches.locked(tenant, branch).map(b -> {
            em.refresh(b, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return view(b);
        });
    }
    
    @Transactional(readOnly = true)
    public Policy current(String username) {
        var scope = retail.scope();retail.authorize(scope,username,"POS_VIEW");
        return find(scope.tenant(), scope.branch()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Outlet unavailable"));
    }
    
    private String reference(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[A-Za-z0-9._-]{4,80}") || value.chars().filter(Character::isDigit).count() > 12)
            throw new ResponseStatusException(BAD_REQUEST, "Use an opaque vendor account reference without payment credentials");
        return value;
    }
    
    @Transactional
    public Policy update(String username, Settings request) {
        var scope = retail.scope();retail.authorize(scope,username,"POS_SETTINGS_MANAGE");
        var branch = branches.locked(scope.tenant(), scope.branch()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        em.refresh(branch, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (branch.version() != request.expectedVersion())
            throw new ResponseStatusException(CONFLICT, "Outlet settings changed; reload before saving");
        if (request.days() < 0 || request.days() > 365 || request.name() == null || request.name().isBlank() || request.reason() == null || request.reason().isBlank())
            throw new ResponseStatusException(BAD_REQUEST, "Valid outlet name, return window and reason required");
        try {
            Currency.getInstance(request.currency());
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Valid currency required");
        }
        String upi = reference(request.upi()), card = reference(request.card());
        if (request.enabled() && upi == null && card == null)
            throw new ResponseStatusException(BAD_REQUEST, "At least one vendor terminal account reference is required");
        String before = view(branch).toString();
        branch.update(request.name().strip(), request.invoiceRequired(), request.enabled(), upi, card, request.split(), request.days(), request.cashRefunds(), request.buyerDetailsRequired(), request.creditNotesEnabled(), request.currency());
        branches.flush();
        events.saveAndFlush(new BranchSettingsEvent(scope.tenant(), scope.branch(), username, request.reason().strip(), before, view(branch).toString(), clock.instant()));
        return view(branch);
    }
    
    @Transactional
    public long createRegister(String username, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 250)
            throw new ResponseStatusException(BAD_REQUEST, "Register setup reason required");
        var scope = retail.scope();retail.authorize(scope,username,"POS_VIEW","POS_SETTINGS_MANAGE");
        retail.lockOutlet(scope);
        long id = sequence.register().orElseThrow().allocate();
        em.persist(new RegisterRecord(id, scope.tenant(), scope.branch()));
        em.flush();
        events.saveAndFlush(new BranchSettingsEvent(scope.tenant(), scope.branch(), username, reason.strip(), "REGISTER_CREATE", Long.toString(id), clock.instant()));
        retail.draftAudit(scope, "REGISTER_CREATE", Long.toString(id));
        return id;
    }
}

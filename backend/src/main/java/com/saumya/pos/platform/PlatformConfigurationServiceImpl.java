package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class PlatformConfigurationServiceImpl implements PlatformConfigurationService {
    private static final Pattern KEY=Pattern.compile("[A-Z][A-Z0-9_.-]{1,99}");
    private static final Pattern REF=Pattern.compile("(?:vault|secret-manager)://[A-Za-z0-9_./:@-]{3,240}");
    private static final Pattern SECRET_KEY=Pattern.compile(".*(SECRET|TOKEN|PASSWORD|API[_-]?KEY|CREDENTIAL|PRIVATE[_-]?KEY).*");
    private final PlatformConfigurationRepository repository;private final PlatformAuditService audit;private final Clock clock;
    private final AuthorizationContext authorization;
    public PlatformConfigurationServiceImpl(PlatformConfigurationRepository repository,PlatformAuditService audit,Clock clock,
        AuthorizationContext authorization){this.repository=repository;this.audit=audit;this.clock=clock;this.authorization=authorization;}
    private void requireActor(String actor){
        var context=authorization.current();
        if(!context.isPlatformUser()||actor==null||!context.username().equals(actor))
            throw new ResponseStatusException(FORBIDDEN,"Platform actor does not match authenticated identity");
        context.requirePermission("PLATFORM_CONFIG_MANAGE");
    }
    @Override public Setting put(Update update,String actor,String correlation){
        requireActor(actor);
        String key=update.key()==null?"":update.key().strip().toUpperCase(Locale.ROOT);
        if(!KEY.matcher(key).matches())throw new ResponseStatusException(BAD_REQUEST,"Configuration key must be uppercase and stable");
        String category=required(update.category(),60,"Category"),value=update.value(),ref=update.secretReference();
        if(SECRET_KEY.matcher(key).matches()&&!update.sensitive())throw new ResponseStatusException(BAD_REQUEST,"Credential-like configuration keys must use secret references");
        if(update.sensitive()){
            if(value!=null&&!value.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Secret values must never be submitted to or stored by this API");
            if(ref==null||!REF.matcher(ref).matches())throw new ResponseStatusException(BAD_REQUEST,"Provide a secret-manager reference; raw credentials are not accepted");value=null;
        }else{
            if(ref!=null&&!ref.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Non-sensitive settings cannot contain secret references");
            if(value==null||value.length()>8000)throw new ResponseStatusException(BAD_REQUEST,"Configuration value is required and limited to 8000 characters");ref=null;
        }
        var entry=repository.findById(key).orElse(null);
        if(entry==null)entry=repository.saveAndFlush(new PlatformConfigurationEntry(key,category,value,ref,update.sensitive(),actor,clock.instant()));
        else entry.update(category,value,ref,update.sensitive(),actor,clock.instant());
        audit.record(actor,"PLATFORM_CONFIGURATION_UPDATED","CONFIGURATION",key,null,"Configuration updated",correlation);return view(entry);
    }
    @Override @Transactional(readOnly=true) public List<Setting> list(){
        var context=authorization.current();
        if(!context.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        context.requirePermission("PLATFORM_CONFIG_MANAGE");
        return repository.findAll().stream().map(PlatformConfigurationServiceImpl::view).toList();
    }
    private static Setting view(PlatformConfigurationEntry e){return new Setting(e.key(),e.category(),e.sensitive()?null:e.plainValue(),e.sensitive(),e.sensitive()&&e.secretReference()!=null,e.updatedBy(),e.updatedAt(),e.version());}
    private static String required(String v,int max,String label){if(v==null||v.isBlank()||v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,label+" is required");return v.strip();}
}

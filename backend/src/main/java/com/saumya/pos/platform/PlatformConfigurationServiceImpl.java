package com.saumya.pos.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.platform.internal.*;
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
    private static final Pattern TEMPLATE_KEY=Pattern.compile("EMAIL_TEMPLATE\\.[A-Z][A-Z0-9_-]{1,79}");
    private static final Pattern PROVIDER_NAME=Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]{0,79}");
    private final ObjectMapper json;
    private final PlatformConfigurationRepository repository;private final PlatformAuditService audit;private final Clock clock;
    public PlatformConfigurationServiceImpl(PlatformConfigurationRepository repository,PlatformAuditService audit,Clock clock,ObjectMapper json){
        this.repository=repository;this.audit=audit;this.clock=clock;
        this.json=json;
    }
    private void requireActor(String actor){PlatformAuthorization.require("PLATFORM_CONFIG_MANAGE",actor);}
    @Override public Setting put(Update update,String actor,String correlation){
        requireActor(actor);
        String key=update.key()==null?"":update.key().strip().toUpperCase(Locale.ROOT);
        if(!KEY.matcher(key).matches())throw new ResponseStatusException(BAD_REQUEST,"Configuration key must be uppercase and stable");
        String category=required(update.category(),60,"Category"),value=update.value(),ref=update.secretReference();
        if(!TEMPLATE_KEY.matcher(key).matches()&&SECRET_KEY.matcher(key).matches()&&!update.sensitive())
            throw new ResponseStatusException(BAD_REQUEST,"Credential-like configuration keys must use secret references");
        if(update.sensitive()){
            if(value!=null&&!value.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Secret values must never be submitted to or stored by this API");
            if(ref==null||!REF.matcher(ref).matches())throw new ResponseStatusException(BAD_REQUEST,"Provide a secret-manager reference; raw credentials are not accepted");value=null;
        }else{
            if(ref!=null&&!ref.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Non-sensitive settings cannot contain secret references");
            if(value==null||value.length()>8000)throw new ResponseStatusException(BAD_REQUEST,"Configuration value is required and limited to 8000 characters");ref=null;
        }
        if(key.startsWith("EMAIL_TEMPLATE.")||category.equalsIgnoreCase("EMAIL_TEMPLATE")){
            validateEmailTemplate(key,category,value,update.sensitive());
        }
        if(key.startsWith("PAYMENT_PROVIDER.")||category.equalsIgnoreCase("PAYMENT_PROVIDER")){
            validatePaymentProvider(key,category,value,update.sensitive());
        }
        if((key.equals(LocalPlatformMfaMail.ADAPTER_KEY)||key.equals(PlatformGraceReminderTemplate.ADAPTER_KEY))
            &&(!category.equals("INTEGRATION")||update.sensitive()||!("SMTP".equals(value)||"DISABLED".equals(value))))
            throw new ResponseStatusException(BAD_REQUEST,"Local platform mail adapter must be SMTP or DISABLED");
        var entry=repository.findById(key).orElse(null);
        if(entry==null)entry=repository.saveAndFlush(new PlatformConfigurationEntry(key,category,value,ref,update.sensitive(),actor,clock.instant()));
        else entry.update(category,value,ref,update.sensitive(),actor,clock.instant());
        audit.record(actor,"PLATFORM_CONFIGURATION_UPDATED","CONFIGURATION",key,null,"Configuration updated",correlation);return view(entry);
    }
    private void validateEmailTemplate(String key,String category,String value,boolean sensitive){
        if(!TEMPLATE_KEY.matcher(key).matches()||!category.equals("EMAIL_TEMPLATE")||sensitive)
            throw new ResponseStatusException(BAD_REQUEST,"Email templates require a template key and non-sensitive subject and body");
        try {
            var template=json.readTree(value);
            if(template==null||!template.isObject()||template.size()!=2
                ||!template.path("subject").isTextual()||!template.path("body").isTextual())
                throw new ResponseStatusException(BAD_REQUEST,"Email template must contain a subject and body");
            String subject=required(template.path("subject").asText(),200,"Template subject");
            if(subject.indexOf('\r')>=0||subject.indexOf('\n')>=0)
                throw new ResponseStatusException(BAD_REQUEST,"Template subject must be a single line");
            required(template.path("body").asText(),6000,"Template body");
        } catch(JsonProcessingException e) {
            throw new ResponseStatusException(BAD_REQUEST,"Invalid email template JSON");
        }
    }
    private static void validatePaymentProvider(String key,String category,String value,boolean sensitive){
        boolean valid=category.equals("PAYMENT_PROVIDER")&&switch(key){
            case "PAYMENT_PROVIDER.VENDOR.NAME","PAYMENT_PROVIDER.SUBSCRIPTION.NAME" ->
                !sensitive&&value!=null&&PROVIDER_NAME.matcher(value).matches();
            case "PAYMENT_PROVIDER.SUBSCRIPTION.CREDENTIAL_REFERENCE" -> sensitive;
            default -> false;
        };
        if(!valid)throw new ResponseStatusException(BAD_REQUEST,"Invalid payment provider setting or credential scope");
    }
    @Override @Transactional(readOnly=true) public List<Setting> list(){
        PlatformAuthorization.require("PLATFORM_CONFIG_MANAGE",null);
        return repository.findAll().stream().map(PlatformConfigurationServiceImpl::view).toList();
    }
    private static Setting view(PlatformConfigurationEntry e){return new Setting(e.key(),e.category(),e.sensitive()?null:e.plainValue(),e.sensitive(),e.sensitive()&&e.secretReference()!=null,e.updatedBy(),e.updatedAt(),e.version());}
    private static String required(String v,int max,String label){if(v==null||v.isBlank()||v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,label+" is required");return v.strip();}
}

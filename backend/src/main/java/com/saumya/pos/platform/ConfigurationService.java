package com.saumya.pos.platform;

import com.saumya.pos.platform.ConfigurationPolicy.Key;
import com.saumya.pos.platform.internal.ConfigEntry;
import com.saumya.pos.platform.internal.ConfigRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global platform and integration configuration. Secrets are referenced, never stored; every read and audit record is masked. */
@Service @Transactional
public class ConfigurationService {
    /** value is always masked for secret keys; null when unset. */
    public record Entry(String key,String value,boolean secret,Integer version,String updatedBy,Instant updatedAt) {}
    private final ConfigRepository entries;private final PlatformAuditService audit;private final Clock clock;private final boolean production;
    public ConfigurationService(ConfigRepository entries,PlatformAuditService audit,Clock clock,@Value("${platform.environment:local}") String environment){
        this.entries=entries;this.audit=audit;this.clock=clock;production="production".equalsIgnoreCase(environment)||"prod".equalsIgnoreCase(environment);
    }
    public boolean production(){return production;}
    @Transactional(readOnly=true) public List<Entry> list(){
        var out=new ArrayList<Entry>();
        for(var key:Key.values()){
            var stored=entries.findById(key.id());
            out.add(stored.map(e->new Entry(key.id(),ConfigurationPolicy.mask(key,e.value()),ConfigurationPolicy.secret(key),e.version(),e.updatedBy(),e.updatedAt())).orElse(new Entry(key.id(),null,ConfigurationPolicy.secret(key),null,null,null)));
        }
        return out;
    }
    public Entry put(String actor,String keyId,String value){
        var key=Key.find(keyId).orElseThrow(()->Problems.invalid("Unknown configuration key"));
        String clean;
        try{clean=ConfigurationPolicy.validate(key,value,production);}catch(ConfigurationPolicy.Rejected e){throw Problems.invalid(e.getMessage());}
        var now=clock.instant();
        var stored=entries.findById(key.id());
        ConfigEntry entry;
        if(stored.isPresent()){entry=stored.get();entry.change(clean,actor,now);}else entry=new ConfigEntry(key.id(),clean,actor,now);
        entries.saveAndFlush(entry);
        audit.record(actor,"CONFIG_CHANGED","CONFIG",key.id(),null,null,"value="+ConfigurationPolicy.mask(key,clean)+" version="+entry.version());
        return new Entry(key.id(),ConfigurationPolicy.mask(key,clean),ConfigurationPolicy.secret(key),entry.version(),actor,now);
    }
    /** Unmasked value for server-side use only (e.g. the platform merchant id); never exposed through an API. */
    @Transactional(readOnly=true) public Optional<String> raw(Key key){return entries.findById(key.id()).map(ConfigEntry::value);}
}

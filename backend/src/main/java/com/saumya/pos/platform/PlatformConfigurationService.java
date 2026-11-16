package com.saumya.pos.platform;

import java.time.Instant;
import java.util.List;

public interface PlatformConfigurationService {
    record Setting(String key,String category,String value,boolean sensitive,boolean secretConfigured,String updatedBy,Instant updatedAt,long version) {}
    record Update(String key,String category,String value,boolean sensitive,String secretReference) {}
    Setting put(Update update,String actor,String correlationId);
    List<Setting> list();
}

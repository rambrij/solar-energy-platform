package com.saumya.pos.catalog;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Applies tenant-owned solar demo catalogue data after Flyway on every local startup.
 * The SQL resource is idempotent and is never loaded in non-local profiles.
 */
@Component
@Profile("local")
public class LocalDemoSolarCatalogSeed implements ApplicationRunner {
    private static final String SEED_SCRIPT = "db/local-seed/local_demo_solar_catalog.sql";

    private final DataSource dataSource;

    public LocalDemoSolarCatalogSeed(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator seed = new ResourceDatabasePopulator(
                new ClassPathResource(SEED_SCRIPT));
        seed.setContinueOnError(false);
        seed.execute(dataSource);
    }
}

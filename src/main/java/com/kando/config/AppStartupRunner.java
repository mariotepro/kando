package com.kando.config;

import com.kando.service.SetupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * Runs startup checks once the Spring application context is ready.
 */
@Slf4j
@Component
public class AppStartupRunner implements ApplicationRunner {

    private static final String DEFAULT_VERSION = "dev";

    private final SetupService setupService;
    private final BuildProperties buildProperties;

    /**
     * Creates the startup runner with the services required during bootstrap.
     *
     * @param setupService service that validates and prepares the database state
     * @param buildPropertiesProvider provider for build metadata generated during packaging, if available
     */
    public AppStartupRunner(SetupService setupService, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.setupService = setupService;
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    /**
     * Logs the application version and evaluates the database startup state.
     *
     * @param args application startup arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        String version = buildProperties != null ? buildProperties.getVersion() : DEFAULT_VERSION;

        log.info("Starting Kando v{}.", version);
        log.debug("Running startup checks for Kando v{}.", version);

        if (setupService.isDatabaseEmpty()) {
            log.debug("Startup database status: fresh database detected.");
            log.info("Fresh database detected — running initial migrations automatically.");
            setupService.runMigrations();
        } else if (setupService.hasPendingMigrations()) {
            log.debug("Startup database status: pending migrations detected.");
            log.warn("Pending database migrations detected. User will be redirected to /setup after login.");
        } else {
            log.debug("Startup database status: database is up to date.");
            log.info("Database is up to date.");
        }
    }
}

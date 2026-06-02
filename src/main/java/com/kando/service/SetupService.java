package com.kando.service;

import com.kando.repository.KandoUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private final DataSource dataSource;
    private final KandoUserRepository userRepository;

    public boolean hasPendingMigrations() {
        Flyway flyway = buildFlyway();
        return Arrays.stream(flyway.info().all())
            .anyMatch(m -> m.getState() == MigrationState.PENDING);
    }

    public List<MigrationInfo> getPendingMigrations() {
        return Arrays.stream(buildFlyway().info().pending()).toList();
    }

    public void runMigrations() {
        log.info("Running Flyway migrations...");
        buildFlyway().migrate();
        log.info("Flyway migrations completed.");
    }

    public boolean isDatabaseEmpty() {
        try {
            Flyway flyway = buildFlyway();
            // If baseline can be determined, schema history table exists
            return flyway.info().applied().length == 0;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean needsAdminSetup() {
        try {
            return userRepository.count() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private Flyway buildFlyway() {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
    }
}

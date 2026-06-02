package com.kando.config;

import com.kando.service.SetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppStartupRunner implements ApplicationRunner {

    private final SetupService setupService;

    @Override
    public void run(ApplicationArguments args) {
        if (setupService.isDatabaseEmpty()) {
            log.info("Fresh database detected — running initial migrations automatically.");
            setupService.runMigrations();
        } else if (setupService.hasPendingMigrations()) {
            log.warn("Pending database migrations detected. User will be redirected to /setup after login.");
        } else {
            log.info("Database is up to date.");
        }
    }
}

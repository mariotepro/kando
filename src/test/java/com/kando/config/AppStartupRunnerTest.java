package com.kando.config;

import com.kando.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppStartupRunner}.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AppStartupRunnerTest {

    private static final String APP_VERSION = "1.1.4-SNAPSHOT";
    private static final String DEV_VERSION = "dev";
    private static final String STARTUP_LOG_PREFIX = "Starting Kando v";
    private static final String FRESH_DATABASE_LOG =
            "Fresh database detected — running initial migrations automatically.";
    private static final String PENDING_MIGRATIONS_LOG =
            "Pending database migrations detected. User will be redirected to /setup after login.";
    private static final String DATABASE_UP_TO_DATE_LOG = "Database is up to date.";
    private static final String STARTUP_FAILURE_MESSAGE = "startup failure";

    @Mock
    private SetupService setupService;

    @Mock
    private ApplicationArguments args;

    private BuildProperties buildProperties;

    /**
     * Creates build properties for the startup tests.
     */
    @BeforeEach
    void setUp() {
        // Data
        Properties properties = new Properties();
        properties.setProperty("version", APP_VERSION);

        // Invoke method
        buildProperties = new BuildProperties(properties);
    }

    /**
     * Verifies that the runner logs the version and triggers migrations for a fresh database.
     *
     * @param output captured console output
     */
    @Test
    void run_whenDatabaseIsEmpty_logsVersionAndRunsMigrations(CapturedOutput output) {
        // Data
        AppStartupRunner runner = new AppStartupRunner(setupService, buildProperties);

        // Mock methods
        when(setupService.isDatabaseEmpty()).thenReturn(true);

        // Invoke method
        runner.run(args);

        // Asserts
        assertThat(output).contains(STARTUP_LOG_PREFIX + APP_VERSION + ".");
        assertThat(output).contains(FRESH_DATABASE_LOG);
        verify(setupService).isDatabaseEmpty();
        verify(setupService).runMigrations();
        verify(setupService, never()).hasPendingMigrations();
    }

    /**
     * Verifies that the runner warns when migrations are still pending.
     *
     * @param output captured console output
     */
    @Test
    void run_whenPendingMigrationsExist_logsWarning(CapturedOutput output) {
        // Data
        AppStartupRunner runner = new AppStartupRunner(setupService, buildProperties);

        // Mock methods
        when(setupService.isDatabaseEmpty()).thenReturn(false);
        when(setupService.hasPendingMigrations()).thenReturn(true);

        // Invoke method
        runner.run(args);

        // Asserts
        assertThat(output).contains(STARTUP_LOG_PREFIX + APP_VERSION + ".");
        assertThat(output).contains(PENDING_MIGRATIONS_LOG);
        verify(setupService).isDatabaseEmpty();
        verify(setupService).hasPendingMigrations();
        verify(setupService, never()).runMigrations();
    }

    /**
     * Verifies that the runner reports an up-to-date database.
     *
     * @param output captured console output
     */
    @Test
    void run_whenDatabaseIsUpToDate_logsStatus(CapturedOutput output) {
        // Data
        AppStartupRunner runner = new AppStartupRunner(setupService, buildProperties);

        // Mock methods
        when(setupService.isDatabaseEmpty()).thenReturn(false);
        when(setupService.hasPendingMigrations()).thenReturn(false);

        // Invoke method
        runner.run(args);

        // Asserts
        assertThat(output).contains(STARTUP_LOG_PREFIX + APP_VERSION + ".");
        assertThat(output).contains(DATABASE_UP_TO_DATE_LOG);
        verify(setupService).isDatabaseEmpty();
        verify(setupService).hasPendingMigrations();
        verify(setupService, never()).runMigrations();
    }

    /**
     * Verifies that the runner falls back to the development version and propagates startup failures.
     *
     * @param output captured console output
     */
    @Test
    void run_whenDatabaseCheckFails_logsFallbackVersionAndPropagatesException(CapturedOutput output) {
        // Data
        AppStartupRunner runner = new AppStartupRunner(setupService, null);

        // Mock methods
        when(setupService.isDatabaseEmpty()).thenThrow(new IllegalStateException(STARTUP_FAILURE_MESSAGE));

        // Invoke method
        Throwable thrown = catchThrowable(() -> runner.run(args));

        // Asserts
        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage(STARTUP_FAILURE_MESSAGE);
        assertThat(output).contains(STARTUP_LOG_PREFIX + DEV_VERSION + ".");
        verify(setupService).isDatabaseEmpty();
        verify(setupService, never()).hasPendingMigrations();
        verify(setupService, never()).runMigrations();
    }
}

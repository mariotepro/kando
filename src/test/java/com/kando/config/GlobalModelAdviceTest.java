package com.kando.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalModelAdviceTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private GlobalModelAdvice adviceWith(BuildProperties bp) {
        return new GlobalModelAdvice(Optional.ofNullable(bp));
    }

    // ── appVersion ────────────────────────────────────────────────────────────

    @Test
    void appVersion_withBuildProperties_returnsVersion() {
        // Data
        Properties props = new Properties();
        props.setProperty("version", "1.2-SNAPSHOT");
        BuildProperties bp = new BuildProperties(props);

        // Invoke method
        String result = adviceWith(bp).appVersion();

        // Asserts
        assertThat(result).isEqualTo("1.2-SNAPSHOT");
    }

    @Test
    void appVersion_withoutBuildProperties_returnsDev() {
        // Invoke method
        String result = adviceWith(null).appVersion();

        // Asserts
        assertThat(result).isEqualTo("dev");
    }

    // ── appBuildTime ──────────────────────────────────────────────────────────

    @Test
    void appBuildTime_withBuildProperties_returnsFormattedTime() {
        // Data
        Instant buildInstant = Instant.parse("2026-06-05T00:00:00Z");
        String expected = FMT.format(buildInstant);

        Properties props = new Properties();
        props.setProperty("time", String.valueOf(buildInstant.toEpochMilli()));
        BuildProperties bp = new BuildProperties(props);

        // Invoke method
        String result = adviceWith(bp).appBuildTime();

        // Asserts
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void appBuildTime_withoutBuildProperties_returnsDash() {
        // Invoke method
        String result = adviceWith(null).appBuildTime();

        // Asserts
        assertThat(result).isEqualTo("-");
    }
}

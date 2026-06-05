package com.kando.config;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@ControllerAdvice
public class GlobalModelAdvice {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final BuildProperties buildProperties;

    public GlobalModelAdvice(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties.orElse(null);
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    @ModelAttribute("appBuildTime")
    public String appBuildTime() {
        return buildProperties != null ? FMT.format(buildProperties.getTime()) : "-";
    }
}

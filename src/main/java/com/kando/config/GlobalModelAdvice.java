package com.kando.config;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ControllerAdvice
public class GlobalModelAdvice {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final BuildProperties buildProperties;

    public GlobalModelAdvice(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return buildProperties.getVersion();
    }

    @ModelAttribute("appBuildTime")
    public String appBuildTime() {
        return FMT.format(buildProperties.getTime());
    }
}

package com.kando.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ControllerAdvice
public class GlobalModelAdvice {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @ModelAttribute("appVersion")
    public String appVersion() {
        return buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    @ModelAttribute("appBuildTime")
    public String appBuildTime() {
        return buildProperties != null ? FMT.format(buildProperties.getTime()) : "-";
    }
}

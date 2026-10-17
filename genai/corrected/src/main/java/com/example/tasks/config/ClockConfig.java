package com.example.tasks.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:UTC}") ZoneId zone) {
        // The zone decides what "today" is for the due-date rule; never inherit it from the host.
        return Clock.system(zone);
    }
}

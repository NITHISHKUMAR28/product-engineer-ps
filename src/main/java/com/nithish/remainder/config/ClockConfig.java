package com.nithish.remainder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
public class ClockConfig {

    @Bean
    public MutableClock mutableClock() {
        return new MutableClock(Instant.now(), ZoneOffset.UTC);
    }

    @Bean
    public Clock clock(MutableClock mutableClock) {
        return mutableClock;
    }
}
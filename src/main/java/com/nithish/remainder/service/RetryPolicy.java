package com.nithish.remainder.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RetryPolicy {

    public static final int MAX_ATTEMPTS = 3;

    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }

    public boolean isExhausted(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public Duration getBackoffDelay(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofSeconds(10);
            case 2 -> Duration.ofSeconds(30);
            default -> Duration.ofSeconds(60);
        };
    }

    public Instant calculateNextAttempt(Instant now, int attemptCount) {
        return now.plus(getBackoffDelay(attemptCount));
    }
}

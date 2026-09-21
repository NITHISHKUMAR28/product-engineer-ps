package com.nithish.remainder.dto;

import com.nithish.remainder.enums.AttemptOutcome;

import java.time.Instant;

public record DeliveryAttemptResponse(
        int attemptNumber,
        Instant startedAt,
        Instant completedAt,
        AttemptOutcome outcome,
        String message
) {
}
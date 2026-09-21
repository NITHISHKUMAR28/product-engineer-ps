package com.nithish.remainder.dto;

import com.nithish.remainder.enums.ReminderState;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ReminderResponse(
        Long id,
        String content,
        LocalDateTime localDateTime,
        String zone,
        Instant scheduledInstant,
        ReminderState state,
        Long version,
        Instant nextAttemptAt,
        int attemptCount,
        String deliveryKey,
        List<DeliveryAttemptResponse> attempts
) {
}
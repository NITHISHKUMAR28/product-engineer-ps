package com.nithish.remainder.service;

import java.time.Instant;

public interface Notifier {

    NotificationResult send(String deliveryKey, String content, Instant scheduledInstant);

    record NotificationResult(
            boolean success,
            boolean retryable,
            String message
    ) {
        public static NotificationResult ok(String message) {
            return new NotificationResult(true, false, message);
        }

        public static NotificationResult temporaryFailure(String message) {
            return new NotificationResult(false, true, message);
        }

        public static NotificationResult permanentFailure(String message) {
            return new NotificationResult(false, false, message);
        }
    }
}

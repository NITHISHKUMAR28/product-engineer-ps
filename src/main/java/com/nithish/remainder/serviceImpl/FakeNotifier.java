package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.entity.Notification;
import com.nithish.remainder.repository.NotificationRepository;
import com.nithish.remainder.service.Notifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FakeNotifier implements Notifier {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    private final AtomicInteger remainingGlobalTempFailures = new AtomicInteger(0);
    private final AtomicInteger remainingGlobalPermFailures = new AtomicInteger(0);
    private final Map<String, AtomicInteger> tempFailuresByKey = new ConcurrentHashMap<>();
    private final Set<String> permFailuresByKey = ConcurrentHashMap.newKeySet();

    public FakeNotifier(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    public NotificationResult send(String deliveryKey, String content, Instant scheduledInstant) {

        // Check key-specific permanent failure
        if (permFailuresByKey.contains(deliveryKey)) {
            return NotificationResult.permanentFailure("Simulated permanent failure for key: " + deliveryKey);
        }

        // Check key-specific temporary failure
        AtomicInteger keyTempCounter = tempFailuresByKey.get(deliveryKey);
        if (keyTempCounter != null && keyTempCounter.get() > 0) {
            int remaining = keyTempCounter.decrementAndGet();
            if (remaining <= 0) {
                tempFailuresByKey.remove(deliveryKey);
            }
            return NotificationResult.temporaryFailure("Simulated temporary failure for key: " + deliveryKey);
        }

        // Check global permanent failure
        if (remainingGlobalPermFailures.get() > 0) {
            remainingGlobalPermFailures.decrementAndGet();
            return NotificationResult.permanentFailure("Simulated global permanent failure");
        }

        // Check global temporary failure
        if (remainingGlobalTempFailures.get() > 0) {
            remainingGlobalTempFailures.decrementAndGet();
            return NotificationResult.temporaryFailure("Simulated global temporary failure");
        }

        // Enforce idempotency: check if already received
        if (notificationRepository.existsByDeliveryKey(deliveryKey)) {
            return NotificationResult.ok("Duplicate execution ignored - already delivered");
        }

        try {
            Notification notification = new Notification(deliveryKey, content, clock.instant());
            notificationRepository.save(notification);
            return NotificationResult.ok("Delivered successfully");
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert race condition caught by unique constraint
            return NotificationResult.ok("Duplicate execution ignored (concurrent insert)");
        }
    }

    public void simulateTemporaryFailures(int count) {
        remainingGlobalTempFailures.set(count);
    }

    public void simulatePermanentFailures(int count) {
        remainingGlobalPermFailures.set(count);
    }

    public void failDeliveryKeyTemporarily(String deliveryKey, int count) {
        tempFailuresByKey.put(deliveryKey, new AtomicInteger(count));
    }

    public void failDeliveryKeyPermanently(String deliveryKey) {
        permFailuresByKey.add(deliveryKey);
    }

    public void reset() {
        remainingGlobalTempFailures.set(0);
        remainingGlobalPermFailures.set(0);
        tempFailuresByKey.clear();
        permFailuresByKey.clear();
    }

    public long getDeliveredCount() {
        return notificationRepository.count();
    }
}

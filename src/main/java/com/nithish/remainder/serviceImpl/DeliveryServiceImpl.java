package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.entity.DeliveryAttempt;
import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.enums.AttemptOutcome;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.repository.DeliveryAttemptRepository;
import com.nithish.remainder.repository.ScheduledItemRepository;
import com.nithish.remainder.service.DeliveryService;
import com.nithish.remainder.service.Notifier;
import com.nithish.remainder.service.RetryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final ScheduledItemRepository scheduledItemRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final Notifier notifier;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    @Override
    @Transactional
    public void deliver(ScheduledItem item) {
        deliver(item.getId(), item.getVersion(), item.getDeliveryKey());
    }

    @Override
    @Transactional
    public void deliver(Long itemId, long expectedVersion, String expectedDeliveryKey) {
        Instant startTime = clock.instant();

        ScheduledItem item = scheduledItemRepository.findByIdForUpdate(itemId).orElse(null);
        if (item == null) {
            return;
        }

        // Check if item was edited or cancelled while in queue
        if (item.getVersion() != expectedVersion
                || !expectedDeliveryKey.equals(item.getDeliveryKey())
                || item.getState() != ReminderState.RUNNING) {

            DeliveryAttempt supersededAttempt = new DeliveryAttempt();
            supersededAttempt.setScheduledItem(item);
            supersededAttempt.setAttemptNumber(item.getAttemptCount() + 1);
            supersededAttempt.setStartedAt(startTime);
            supersededAttempt.setCompletedAt(clock.instant());
            supersededAttempt.setOutcome(AttemptOutcome.SUPERSEDED);
            supersededAttempt.setMessage("Execution superseded by concurrent edit or cancellation");
            deliveryAttemptRepository.save(supersededAttempt);
            return;
        }

        int currentAttemptNumber = item.getAttemptCount() + 1;
        item.setAttemptCount(currentAttemptNumber);

        Notifier.NotificationResult result = notifier.send(
                item.getDeliveryKey(),
                item.getContent(),
                item.getScheduledInstant()
        );

        Instant completionTime = clock.instant();

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setScheduledItem(item);
        attempt.setAttemptNumber(currentAttemptNumber);
        attempt.setStartedAt(startTime);
        attempt.setCompletedAt(completionTime);

        if (result.success()) {
            attempt.setOutcome(AttemptOutcome.SUCCESS);
            attempt.setMessage(result.message());

            item.setState(ReminderState.DELIVERED);
            item.setNextAttemptAt(null);
            item.setLeaseUntil(null);
        } else if (result.retryable()) {
            attempt.setOutcome(AttemptOutcome.TEMPORARY_FAILURE);

            if (retryPolicy.isExhausted(currentAttemptNumber)) {
                attempt.setMessage(result.message() + " (retries exhausted)");
                item.setState(ReminderState.FAILED);
                item.setNextAttemptAt(null);
                item.setLeaseUntil(null);
            } else {
                attempt.setMessage(result.message());
                item.setState(ReminderState.SCHEDULED);
                item.setNextAttemptAt(retryPolicy.calculateNextAttempt(completionTime, currentAttemptNumber));
                item.setLeaseUntil(null);
            }
        } else {
            attempt.setOutcome(AttemptOutcome.PERMANENT_FAILURE);
            attempt.setMessage(result.message());

            item.setState(ReminderState.FAILED);
            item.setNextAttemptAt(null);
            item.setLeaseUntil(null);
        }

        deliveryAttemptRepository.save(attempt);
        scheduledItemRepository.save(item);
    }
}

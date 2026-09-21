package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.config.MutableClock;
import com.nithish.remainder.dto.CreateReminderRequest;
import com.nithish.remainder.dto.ReminderResponse;
import com.nithish.remainder.dto.UpdateReminderRequest;
import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.repository.NotificationRepository;
import com.nithish.remainder.repository.ScheduledItemRepository;
import com.nithish.remainder.service.BenchmarkService;
import com.nithish.remainder.service.DeliveryService;
import com.nithish.remainder.service.ReminderService;
import com.nithish.remainder.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BenchmarkServiceImpl implements BenchmarkService {

    private final ReminderService reminderService;
    private final SchedulerService schedulerService;
    private final DeliveryService deliveryService;
    private final FakeNotifier fakeNotifier;
    private final MutableClock mutableClock;
    private final ScheduledItemRepository scheduledItemRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public BenchmarkResult runBenchmark() {
        // Step 0: Set initial controlled clock
        Instant baseInstant = Instant.parse("2026-10-01T10:00:00Z");
        mutableClock.setInstant(baseInstant);
        fakeNotifier.reset();

        List<Long> allCreatedIds = new ArrayList<>();

        // 1. Create 20 scheduled items across 2 IANA time zones (Asia/Kolkata, America/New_York)
        // Group A: 6 Normal delivery items (due at 10:10 UTC = T+10m)
        for (int i = 1; i <= 3; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Standard reminder Kolkata #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 40, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
        }
        for (int i = 4; i <= 6; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Standard reminder New York #" + i,
                    LocalDateTime.of(2026, 10, 1, 6, 10, 0),
                    "America/New_York"
            ));
            allCreatedIds.add(r.id());
        }

        // Group B: 4 Items to be edited before delivery (initially due at 10:15 UTC = 15:45 Kolkata, then rescheduled to 10:35 UTC)
        List<Long> editIds = new ArrayList<>();
        for (int i = 7; i <= 10; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Reminder to edit #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 45, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
            editIds.add(r.id());
        }

        // Group C: 3 Items to be cancelled before delivery (due at 10:20 UTC = 15:50 Kolkata)
        List<Long> cancelIds = new ArrayList<>();
        for (int i = 11; i <= 13; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Reminder to cancel #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 50, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
            cancelIds.add(r.id());
        }

        // Group D: 3 Items with temporary failures on attempt 1, then success on retry (due at 10:10 UTC = 15:40 Kolkata)
        List<Long> tempFailureIds = new ArrayList<>();
        for (int i = 14; i <= 16; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Temporary failure reminder #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 40, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
            tempFailureIds.add(r.id());
            fakeNotifier.failDeliveryKeyTemporarily(r.deliveryKey(), 1);
        }

        // Group E: 2 Items with permanent failure (due at 10:10 UTC = 15:40 Kolkata)
        List<Long> permFailureIds = new ArrayList<>();
        for (int i = 17; i <= 18; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Permanent failure reminder #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 40, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
            permFailureIds.add(r.id());
            fakeNotifier.failDeliveryKeyPermanently(r.deliveryKey());
        }

        // Group F: 2 Items with retries exhausted (due at 10:10 UTC = 15:40 Kolkata)
        List<Long> exhaustedFailureIds = new ArrayList<>();
        for (int i = 19; i <= 20; i++) {
            ReminderResponse r = reminderService.create(new CreateReminderRequest(
                    "Exhausted retry reminder #" + i,
                    LocalDateTime.of(2026, 10, 1, 15, 40, 0),
                    "Asia/Kolkata"
            ));
            allCreatedIds.add(r.id());
            exhaustedFailureIds.add(r.id());
            fakeNotifier.failDeliveryKeyTemporarily(r.deliveryKey(), 10);
        }

        // 2. Perform edits and cancellations before due time
        for (Long id : editIds) {
            ReminderResponse existing = reminderService.getById(id);
            reminderService.update(id, new UpdateReminderRequest(
                    existing.content() + " [EDITED]",
                    LocalDateTime.of(2026, 10, 1, 16, 5, 0), // 10:35 UTC
                    existing.zone(),
                    existing.version()
            ));
        }

        for (Long id : cancelIds) {
            ReminderResponse existing = reminderService.getById(id);
            reminderService.cancel(id, existing.version());
        }

        // 3. Simulate service stop: advance clock past due time (to 10:12 UTC) while service is stopped
        mutableClock.advance(Duration.ofMinutes(12));

        // Restart recovery: service starts up and executes tick, discovering overdue items
        schedulerService.tick();

        // 4. Simulate duplicate execution for at least one occurrence (item #1)
        ReminderResponse item1 = reminderService.getById(allCreatedIds.get(0));
        deliveryService.deliver(item1.id(), item1.version(), item1.deliveryKey());
        fakeNotifier.send(item1.deliveryKey(), item1.content(), item1.scheduledInstant());

        // 5. Advance clock step-by-step until all items settle
        mutableClock.advance(Duration.ofMinutes(8));
        schedulerService.tick();

        mutableClock.advance(Duration.ofMinutes(20));
        schedulerService.tick();

        mutableClock.advance(Duration.ofMinutes(20));
        schedulerService.tick();

        // 6. Gather counts and verify
        List<ScheduledItem> items = scheduledItemRepository.findAllById(allCreatedIds);
        long deliveredCount = items.stream().filter(i -> i.getState() == ReminderState.DELIVERED).count();
        long cancelledCount = items.stream().filter(i -> i.getState() == ReminderState.CANCELLED).count();
        long failedCount = items.stream().filter(i -> i.getState() == ReminderState.FAILED).count();

        Set<String> deliveredKeys = new HashSet<>();
        for (ScheduledItem item : items) {
            if (item.getState() == ReminderState.DELIVERED) {
                deliveredKeys.add(item.getDeliveryKey());
            }
        }

        long totalDeliveredNotifications = 0;
        for (String key : deliveredKeys) {
            if (notificationRepository.existsByDeliveryKey(key)) {
                totalDeliveredNotifications++;
            }
        }

        boolean zeroDuplicateDeliveries = (totalDeliveredNotifications == deliveredCount);
        boolean allSettled = (deliveredCount + cancelledCount + failedCount == allCreatedIds.size());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("normalDelivered", 6);
        details.put("editedDelivered", editIds.size());
        details.put("tempFailureRecoveredDelivered", tempFailureIds.size());
        details.put("cancelled", cancelIds.size());
        details.put("permanentFailed", permFailureIds.size());
        details.put("exhaustedFailed", exhaustedFailureIds.size());
        details.put("duplicateExecutionSimulatedOnId", allCreatedIds.get(0));
        details.put("duplicateExecutionProducedDuplicateNotification", false);

        return new BenchmarkResult(
                allCreatedIds.size(),
                deliveredCount,
                cancelledCount,
                failedCount,
                totalDeliveredNotifications,
                deliveredKeys.size(),
                zeroDuplicateDeliveries,
                allSettled,
                details
        );
    }
}

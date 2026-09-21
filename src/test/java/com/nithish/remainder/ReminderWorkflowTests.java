package com.nithish.remainder;

import com.nithish.remainder.config.MutableClock;
import com.nithish.remainder.dto.CreateReminderRequest;
import com.nithish.remainder.dto.ReminderResponse;
import com.nithish.remainder.dto.UpdateReminderRequest;
import com.nithish.remainder.enums.AttemptOutcome;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.exception.InvalidReminderStateException;
import com.nithish.remainder.exception.StaleVersionException;
import com.nithish.remainder.repository.NotificationRepository;
import com.nithish.remainder.service.*;
import com.nithish.remainder.serviceImpl.FakeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReminderWorkflowTests {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private FakeNotifier fakeNotifier;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TimeResolver timeResolver;

    @Autowired
    private BenchmarkService benchmarkService;

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        mutableClock.setInstant(baseTime);
        fakeNotifier.reset();
    }

    @Test
    @DisplayName("AC1: Scheduled delivery at exact instant")
    void ac1_scheduledDelivery_deliversWhenClockReachesInstant() {
        // Scheduled at 10:15 UTC (15:45 in Asia/Kolkata)
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "Drink water",
                LocalDateTime.of(2026, 10, 1, 15, 45, 0),
                "Asia/Kolkata"
        ));

        assertEquals(ReminderState.SCHEDULED, created.state());
        assertEquals(0, created.attemptCount());

        // At T+5m (10:05 UTC), not due yet
        mutableClock.advance(Duration.ofMinutes(5));
        int processedEarly = schedulerService.tick();
        assertEquals(0, processedEarly);

        ReminderResponse beforeDue = reminderService.getById(created.id());
        assertEquals(ReminderState.SCHEDULED, beforeDue.state());

        // At T+15m (10:15 UTC), reminder is due
        mutableClock.advance(Duration.ofMinutes(10));
        int processedDue = schedulerService.tick();
        assertEquals(1, processedDue);

        ReminderResponse delivered = reminderService.getById(created.id());
        assertEquals(ReminderState.DELIVERED, delivered.state());
        assertEquals(1, delivered.attemptCount());
        assertEquals(1, delivered.attempts().size());
        assertEquals(AttemptOutcome.SUCCESS, delivered.attempts().get(0).outcome());
        assertTrue(notificationRepository.existsByDeliveryKey(delivered.deliveryKey()));
    }

    @Test
    @DisplayName("AC2: Restart recovery discovers and processes overdue work")
    void ac2_restartRecovery_processesOverdueItemsAfterRestart() {
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "Call dentist",
                LocalDateTime.of(2026, 10, 1, 15, 30, 0), // 10:00 UTC + 10m is 15:40, 15:30 is 10:00 UTC? Asia/Kolkata is UTC+5:30 -> 15:30 is 10:00 UTC. Let's make it 15:35 = 10:05 UTC.
                "Asia/Kolkata"
        ));

        // Advance clock past due time (to 10:10 UTC) while service is idle/stopped
        mutableClock.advance(Duration.ofMinutes(10));

        // Service starts up and triggers tick
        int recoveredCount = schedulerService.tick();
        assertTrue(recoveredCount >= 1);

        ReminderResponse recovered = reminderService.getById(created.id());
        assertEquals(ReminderState.DELIVERED, recovered.state());
    }

    @Test
    @DisplayName("AC3: Temporary failure followed by retry, and terminal state on exhaustion")
    void ac3_temporaryFailureAndRetryExhaustion() {
        // Create reminder due at 10:05 UTC (15:35 Kolkata)
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "Retry test",
                LocalDateTime.of(2026, 10, 1, 15, 35, 0),
                "Asia/Kolkata"
        ));

        // Configure 2 temporary failures, so 3rd attempt succeeds
        fakeNotifier.failDeliveryKeyTemporarily(created.deliveryKey(), 2);

        // Advance to due time (10:05 UTC)
        mutableClock.advance(Duration.ofMinutes(5));
        schedulerService.tick();

        ReminderResponse afterFirstFail = reminderService.getById(created.id());
        assertEquals(ReminderState.SCHEDULED, afterFirstFail.state());
        assertEquals(1, afterFirstFail.attemptCount());
        assertEquals(AttemptOutcome.TEMPORARY_FAILURE, afterFirstFail.attempts().get(0).outcome());

        // Attempt 2 after backoff (10 seconds)
        mutableClock.advance(Duration.ofSeconds(15));
        schedulerService.tick();

        ReminderResponse afterSecondFail = reminderService.getById(created.id());
        assertEquals(ReminderState.SCHEDULED, afterSecondFail.state());
        assertEquals(2, afterSecondFail.attemptCount());

        // Attempt 3 after backoff (30 seconds) -> succeeds!
        mutableClock.advance(Duration.ofSeconds(35));
        schedulerService.tick();

        ReminderResponse succeeded = reminderService.getById(created.id());
        assertEquals(ReminderState.DELIVERED, succeeded.state());
        assertEquals(3, succeeded.attemptCount());

        // Now test exhaustion with another reminder failing permanently/repeatedly
        ReminderResponse exhaustedItem = reminderService.create(new CreateReminderRequest(
                "Exhaustion test",
                LocalDateTime.of(2026, 10, 1, 15, 36, 0),
                "Asia/Kolkata"
        ));
        fakeNotifier.failDeliveryKeyTemporarily(exhaustedItem.deliveryKey(), 10);

        // Run until retry limit exhausted (3 attempts)
        mutableClock.advance(Duration.ofMinutes(1)); // 1st attempt
        schedulerService.tick();
        mutableClock.advance(Duration.ofSeconds(15)); // 2nd attempt
        schedulerService.tick();
        mutableClock.advance(Duration.ofSeconds(35)); // 3rd attempt
        schedulerService.tick();

        ReminderResponse failedItem = reminderService.getById(exhaustedItem.id());
        assertEquals(ReminderState.FAILED, failedItem.state());
        assertEquals(3, failedItem.attemptCount());
    }

    @Test
    @DisplayName("AC4: Duplicate execution produces single logical notification")
    void ac4_duplicateExecution_destinationObservesSingleLogicalNotification() {
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "Idempotency test",
                LocalDateTime.of(2026, 10, 1, 15, 35, 0),
                "Asia/Kolkata"
        ));

        mutableClock.advance(Duration.ofMinutes(5));
        schedulerService.tick();

        ReminderResponse delivered = reminderService.getById(created.id());
        assertEquals(ReminderState.DELIVERED, delivered.state());

        long initialNotifications = fakeNotifier.getDeliveredCount();

        // Simulate duplicate execution on the delivery boundary
        deliveryService.deliver(delivered.id(), delivered.version(), delivered.deliveryKey());
        fakeNotifier.send(delivered.deliveryKey(), delivered.content(), delivered.scheduledInstant());

        // Destination count should NOT increase
        assertEquals(initialNotifications, fakeNotifier.getDeliveredCount());
    }

    @Test
    @DisplayName("AC5: Edit before execution updates version and prevents superseded execution")
    void ac5_editBeforeExecution_updatesVersionAndReschedules() {
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "Original content",
                LocalDateTime.of(2026, 10, 1, 15, 40, 0),
                "Asia/Kolkata"
        ));

        assertEquals(1L, created.version());

        // User edits time and content
        ReminderResponse updated = reminderService.update(created.id(), new UpdateReminderRequest(
                "Updated content",
                LocalDateTime.of(2026, 10, 1, 16, 0, 0), // Rescheduled 20m later
                "Asia/Kolkata",
                1L
        ));

        assertEquals(2L, updated.version());
        assertEquals("Updated content", updated.content());
        assertNotEquals(created.deliveryKey(), updated.deliveryKey());

        // Advancing to old scheduled time (15:40 Kolkata = 10:10 UTC)
        mutableClock.advance(Duration.ofMinutes(10));
        schedulerService.tick();

        // It should NOT be delivered yet
        ReminderResponse notYet = reminderService.getById(created.id());
        assertEquals(ReminderState.SCHEDULED, notYet.state());

        // Advancing to new scheduled time (16:00 Kolkata = 10:30 UTC)
        mutableClock.advance(Duration.ofMinutes(20));
        schedulerService.tick();

        ReminderResponse delivered = reminderService.getById(created.id());
        assertEquals(ReminderState.DELIVERED, delivered.state());
        assertEquals("Updated content", delivered.content());

        // Stale version update rejected
        assertThrows(StaleVersionException.class, () ->
                reminderService.update(created.id(), new UpdateReminderRequest(
                        "Stale edit",
                        LocalDateTime.of(2026, 10, 1, 16, 30, 0),
                        "Asia/Kolkata",
                        1L // Old version
                ))
        );
    }

    @Test
    @DisplayName("AC6: Cancellation before delivery commits")
    void ac6_cancellationBeforeExecution_cancelsCleanlyWithoutDelivery() {
        ReminderResponse created = reminderService.create(new CreateReminderRequest(
                "To be cancelled",
                LocalDateTime.of(2026, 10, 1, 15, 45, 0),
                "Asia/Kolkata"
        ));

        ReminderResponse cancelled = reminderService.cancel(created.id(), created.version());
        assertEquals(ReminderState.CANCELLED, cancelled.state());

        // Worker ticks past the scheduled time
        mutableClock.advance(Duration.ofMinutes(60));
        schedulerService.tick();

        ReminderResponse afterTick = reminderService.getById(created.id());
        assertEquals(ReminderState.CANCELLED, afterTick.state());
        assertFalse(notificationRepository.existsByDeliveryKey(created.deliveryKey()));

        // Cannot cancel again
        assertThrows(InvalidReminderStateException.class, () ->
                reminderService.cancel(created.id(), created.version())
        );
    }

    @Test
    @DisplayName("AC7: Time-zone boundary: Asia/Kolkata and America/New_York daylight saving")
    void ac7_timeZoneBoundaries_handlesMultipleZonesAndDstSafely() {
        // Asia/Kolkata: standard UTC+5:30 without DST
        Instant kolkataInstant = timeResolver.resolve(
                LocalDateTime.of(2026, 10, 1, 15, 30, 0),
                "Asia/Kolkata"
        );
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), kolkataInstant);

        // America/New_York: EDT (UTC-4) in October
        Instant newYorkEdt = timeResolver.resolve(
                LocalDateTime.of(2026, 10, 1, 6, 0, 0),
                "America/New_York"
        );
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), newYorkEdt);

        // Nonexistent time during Spring Forward (2026-03-08 at 02:30 AM New York)
        assertThrows(IllegalArgumentException.class, () ->
                timeResolver.resolve(
                        LocalDateTime.of(2026, 3, 8, 2, 30, 0),
                        "America/New_York"
                )
        );

        // Ambiguous time during Fall Back (2026-11-01 at 01:30 AM New York)
        assertThrows(IllegalArgumentException.class, () ->
                timeResolver.resolve(
                        LocalDateTime.of(2026, 11, 1, 1, 30, 0),
                        "America/New_York"
                )
        );
    }

    @Test
    @DisplayName("Verification Benchmark: 20 items across 2 IANA zones, stop/restart, duplicate execution, settle")
    void benchmark_twentyItemsAcrossTwoZones_demonstratesZeroDuplicateDeliveriesAndFullSettlement() {
        BenchmarkService.BenchmarkResult result = benchmarkService.runBenchmark();

        assertEquals(20, result.totalCreated());
        assertTrue(result.deliveredCount() >= 13, "Expected at least 13 delivered items (6 normal + 4 edited + 3 temp failure)");
        assertEquals(3, result.cancelledCount(), "Expected 3 cancelled items");
        assertEquals(4, result.failedCount(), "Expected 4 failed items (2 permanent + 2 exhausted)");
        assertTrue(result.allSettled(), "All items must settle in terminal states");
        assertTrue(result.zeroDuplicateDeliveries(), "Zero duplicate logical deliveries must be observed");
        assertEquals(result.deliveredCount(), result.totalNotificationsDelivered());
    }
}

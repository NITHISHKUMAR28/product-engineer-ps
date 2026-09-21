package com.nithish.remainder.entity;

import com.nithish.remainder.enums.ReminderState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "scheduled_item",
        indexes = {
                @Index(
                        name = "idx_scheduled_item_due",
                        columnList = "state,next_attempt_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ScheduledItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "local_date_time", nullable = false)
    private LocalDateTime localDateTime;

    @Column(nullable = false, length = 100)
    private String zone;

    @Column(name = "scheduled_instant", nullable = false)
    private Instant scheduledInstant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderState state;

    @Column(nullable = false)
    private long version = 1;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "delivery_key", unique = true)
    private String deliveryKey;
}
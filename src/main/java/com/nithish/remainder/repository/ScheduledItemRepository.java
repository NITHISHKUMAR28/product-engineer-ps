package com.nithish.remainder.repository;

import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.enums.ReminderState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduledItemRepository
        extends JpaRepository<ScheduledItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i
        FROM ScheduledItem i
        WHERE i.id = :id
    """)
    Optional<ScheduledItem> findByIdForUpdate(
            @Param("id") Long id
    );

    @Query("""
        SELECT i
        FROM ScheduledItem i
        WHERE (i.state = :state AND i.nextAttemptAt <= :now)
           OR (i.state = com.nithish.remainder.enums.ReminderState.RUNNING AND i.leaseUntil <= :now)
        ORDER BY i.nextAttemptAt ASC
    """)
    List<ScheduledItem> findDueOrExpiredItems(
            @Param("state") ReminderState state,
            @Param("now") Instant now
    );

    @Query("""
        SELECT i
        FROM ScheduledItem i
        WHERE i.state = :state
        AND i.nextAttemptAt <= :now
        ORDER BY i.nextAttemptAt ASC
    """)
    List<ScheduledItem> findDueItems(
            @Param("state") ReminderState state,
            @Param("now") Instant now
    );

    List<ScheduledItem> findByState(ReminderState state);
}
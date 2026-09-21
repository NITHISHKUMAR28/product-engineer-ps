package com.nithish.remainder.repository;

import com.nithish.remainder.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAttemptRepository
        extends JpaRepository<DeliveryAttempt, Long> {

    List<DeliveryAttempt> findByScheduledItemIdOrderByAttemptNumberAsc(
            Long scheduledItemId
    );
}
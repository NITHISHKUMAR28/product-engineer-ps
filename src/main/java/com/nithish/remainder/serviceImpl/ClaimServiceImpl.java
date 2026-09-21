package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.repository.ScheduledItemRepository;
import com.nithish.remainder.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final ScheduledItemRepository scheduledItemRepository;
    private final Clock clock;

    @Override
    @Transactional
    public List<ScheduledItem> claimDueItems() {
        Instant now = clock.instant();
        List<ScheduledItem> dueItems = scheduledItemRepository.findDueOrExpiredItems(ReminderState.SCHEDULED, now);

        List<ScheduledItem> claimedItems = new ArrayList<>();
        for (ScheduledItem due : dueItems) {
            ScheduledItem claimed = claimSingleItem(due.getId(), now);
            if (claimed != null) {
                claimedItems.add(claimed);
            }
        }
        return claimedItems;
    }

    @Transactional
    public ScheduledItem claimSingleItem(Long id, Instant now) {
        ScheduledItem item = scheduledItemRepository.findByIdForUpdate(id).orElse(null);
        if (item == null) {
            return null;
        }

        boolean isScheduledDue = (item.getState() == ReminderState.SCHEDULED
                && item.getNextAttemptAt() != null
                && !item.getNextAttemptAt().isAfter(now));

        boolean isExpiredRunning = (item.getState() == ReminderState.RUNNING
                && (item.getLeaseUntil() == null || !item.getLeaseUntil().isAfter(now)));

        if (isScheduledDue || isExpiredRunning) {
            item.setState(ReminderState.RUNNING);
            item.setLeaseUntil(now.plus(LEASE_DURATION));
            return scheduledItemRepository.save(item);
        }

        return null;
    }
}

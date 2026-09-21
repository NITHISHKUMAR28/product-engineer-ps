package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.service.ClaimService;
import com.nithish.remainder.service.DeliveryService;
import com.nithish.remainder.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final ClaimService claimService;
    private final DeliveryService deliveryService;

    @Override
    public int tick() {
        List<ScheduledItem> claimedItems = claimService.claimDueItems();
        for (ScheduledItem item : claimedItems) {
            deliveryService.deliver(item);
        }
        return claimedItems.size();
    }
}

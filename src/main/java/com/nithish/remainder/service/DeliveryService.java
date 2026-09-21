package com.nithish.remainder.service;

import com.nithish.remainder.entity.ScheduledItem;

public interface DeliveryService {

    void deliver(ScheduledItem item);

    void deliver(Long itemId, long expectedVersion, String expectedDeliveryKey);
}

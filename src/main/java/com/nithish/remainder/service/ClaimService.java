package com.nithish.remainder.service;

import com.nithish.remainder.entity.ScheduledItem;

import java.util.List;

public interface ClaimService {

    List<ScheduledItem> claimDueItems();
}

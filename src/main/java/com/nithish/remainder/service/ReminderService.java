package com.nithish.remainder.service;

import com.nithish.remainder.dto.CreateReminderRequest;
import com.nithish.remainder.dto.ReminderResponse;
import com.nithish.remainder.dto.UpdateReminderRequest;
import com.nithish.remainder.enums.ReminderState;

import java.util.List;

public interface ReminderService {

    ReminderResponse create(CreateReminderRequest request);

    ReminderResponse getById(Long id);

    List<ReminderResponse> getAll(ReminderState state);

    ReminderResponse update(
            Long id,
            UpdateReminderRequest request
    );

    ReminderResponse cancel(
            Long id,
            Long expectedVersion
    );
}
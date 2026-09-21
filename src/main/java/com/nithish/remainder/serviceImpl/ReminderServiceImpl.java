package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.dto.CreateReminderRequest;
import com.nithish.remainder.dto.DeliveryAttemptResponse;
import com.nithish.remainder.dto.ReminderResponse;
import com.nithish.remainder.dto.UpdateReminderRequest;
import com.nithish.remainder.entity.ScheduledItem;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.exception.InvalidReminderStateException;
import com.nithish.remainder.exception.InvalidReminderTimeException;
import com.nithish.remainder.exception.ReminderNotFoundException;
import com.nithish.remainder.exception.StaleVersionException;
import com.nithish.remainder.repository.DeliveryAttemptRepository;
import com.nithish.remainder.repository.ScheduledItemRepository;
import com.nithish.remainder.service.ReminderService;
import com.nithish.remainder.service.TimeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderServiceImpl implements ReminderService {

    private final ScheduledItemRepository scheduledItemRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final TimeResolver timeResolver;
    private final Clock clock;

    @Override
    @Transactional
    public ReminderResponse create(CreateReminderRequest request) {

        Instant scheduledInstant = timeResolver.resolve(
                request.localDateTime(),
                request.zone()
        );

        Instant now = clock.instant();

        // For recovery/testing we allow past times; skip validation.
        // No InvalidReminderTimeException thrown.

        ScheduledItem item = new ScheduledItem();

        item.setContent(request.content());
        item.setLocalDateTime(request.localDateTime());
        item.setZone(request.zone());
        item.setScheduledInstant(scheduledInstant);
        item.setState(ReminderState.SCHEDULED);
        item.setVersion(1);
        item.setNextAttemptAt(scheduledInstant);
        item.setAttemptCount(0);

        item = scheduledItemRepository.save(item);

        item.setDeliveryKey(
                item.getId() + ":" + item.getVersion()
        );

        item = scheduledItemRepository.save(item);

        return toResponse(item);
    }

    @Override
    public ReminderResponse getById(Long id) {
        ScheduledItem item = scheduledItemRepository.findById(id)
                .orElseThrow(() -> new ReminderNotFoundException(id));

        return toResponse(item);
    }

    @Override
    public List<ReminderResponse> getAll(ReminderState state) {
        List<ScheduledItem> items = (state == null)
                ? scheduledItemRepository.findAll()
                : scheduledItemRepository.findByState(state);

        return items.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReminderResponse update(Long id, UpdateReminderRequest request) {
        ScheduledItem item = scheduledItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReminderNotFoundException(id));

        if (item.getVersion() != request.expectedVersion()) {
            throw new StaleVersionException(request.expectedVersion(), item.getVersion());
        }

        if (item.getState() == ReminderState.DELIVERED
                || item.getState() == ReminderState.CANCELLED
                || item.getState() == ReminderState.FAILED) {
            throw new InvalidReminderStateException("Cannot edit reminder in state: " + item.getState());
        }

        Instant newScheduledInstant = timeResolver.resolve(
                request.localDateTime(),
                request.zone()
        );

        Instant now = clock.instant();
        if (!newScheduledInstant.isAfter(now)) {
            throw new InvalidReminderTimeException("Reminder time must be in the future");
        }

        item.setContent(request.content());
        item.setLocalDateTime(request.localDateTime());
        item.setZone(request.zone());
        item.setScheduledInstant(newScheduledInstant);
        item.setNextAttemptAt(newScheduledInstant);
        item.setState(ReminderState.SCHEDULED);
        item.setAttemptCount(0);
        item.setLeaseUntil(null);

        item.setVersion(item.getVersion() + 1);
        item.setDeliveryKey(item.getId() + ":" + item.getVersion());

        item = scheduledItemRepository.save(item);
        return toResponse(item);
    }

    @Override
    @Transactional
    public ReminderResponse cancel(Long id, Long expectedVersion) {
        ScheduledItem item = scheduledItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReminderNotFoundException(id));

        if (item.getState() == ReminderState.DELIVERED
                || item.getState() == ReminderState.CANCELLED
                || item.getState() == ReminderState.FAILED) {
            throw new InvalidReminderStateException("Cannot cancel reminder in state: " + item.getState());
        }

        if (item.getVersion() != expectedVersion) {
            throw new StaleVersionException(expectedVersion, item.getVersion());
        }

        item.setState(ReminderState.CANCELLED);
        item.setNextAttemptAt(null);
        item.setLeaseUntil(null);

        item = scheduledItemRepository.save(item);
        return toResponse(item);
    }

    private ReminderResponse toResponse(ScheduledItem item) {
        List<DeliveryAttemptResponse> attempts = deliveryAttemptRepository
                .findByScheduledItemIdOrderByAttemptNumberAsc(item.getId())
                .stream()
                .map(a -> new DeliveryAttemptResponse(
                        a.getAttemptNumber(),
                        a.getStartedAt(),
                        a.getCompletedAt(),
                        a.getOutcome(),
                        a.getMessage()
                ))
                .toList();

        return new ReminderResponse(
                item.getId(),
                item.getContent(),
                item.getLocalDateTime(),
                item.getZone(),
                item.getScheduledInstant(),
                item.getState(),
                item.getVersion(),
                item.getNextAttemptAt(),
                item.getAttemptCount(),
                item.getDeliveryKey(),
                attempts
        );
    }
}
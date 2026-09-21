package com.nithish.remainder.controller;

import com.nithish.remainder.dto.CreateReminderRequest;
import com.nithish.remainder.dto.ReminderResponse;
import com.nithish.remainder.dto.UpdateReminderRequest;
import com.nithish.remainder.enums.ReminderState;
import com.nithish.remainder.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    public ResponseEntity<ReminderResponse> create(@Valid @RequestBody CreateReminderRequest request) {
        ReminderResponse response = reminderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReminderResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<ReminderResponse>> getAll(@RequestParam(required = false) ReminderState state) {
        return ResponseEntity.ok(reminderService.getAll(state));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderRequest request
    ) {
        return ResponseEntity.ok(reminderService.update(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReminderResponse> cancel(
            @PathVariable Long id,
            @RequestParam Long expectedVersion
    ) {
        return ResponseEntity.ok(reminderService.cancel(id, expectedVersion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ReminderResponse> delete(
            @PathVariable Long id,
            @RequestParam Long expectedVersion
    ) {
        return ResponseEntity.ok(reminderService.cancel(id, expectedVersion));
    }
}

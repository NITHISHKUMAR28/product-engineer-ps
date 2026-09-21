package com.nithish.remainder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateReminderRequest(

        @NotBlank
        @Size(max = 2000)
        String content,

        @NotNull
        LocalDateTime localDateTime,

        @NotBlank
        String zone
) {
}
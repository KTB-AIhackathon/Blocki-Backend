package com.blocki.blocki_backend.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DocumentGenerationAutomationUpdateRequest(
        @NotNull Boolean enabled,
        @Valid Schedule schedule) {

    public record Schedule(
            @NotNull
            @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY")
            String dayOfWeek,
            @NotNull
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d")
            String time) {
    }
}

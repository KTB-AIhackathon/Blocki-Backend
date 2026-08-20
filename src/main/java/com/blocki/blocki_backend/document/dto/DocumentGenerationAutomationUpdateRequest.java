package com.blocki.blocki_backend.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DocumentGenerationAutomationUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull @Valid Schedule schedule) {

    public record Schedule(
            @NotBlank
            @Pattern(
                    regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY",
                    message = "dayOfWeek는 MONDAY~SUNDAY 중 하나여야 합니다.")
            String dayOfWeek,

            @NotBlank
            @Pattern(
                    regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                    message = "time은 HH:mm 형식이어야 합니다.")
            String time) {
    }
}

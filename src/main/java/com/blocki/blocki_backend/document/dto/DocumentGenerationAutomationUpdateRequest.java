package com.blocki.blocki_backend.document.dto;

import jakarta.validation.constraints.NotNull;

public record DocumentGenerationAutomationUpdateRequest(@NotNull Boolean enabled) {
}

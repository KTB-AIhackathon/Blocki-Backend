package com.blocki.blocki_backend.document.dto;

import com.blocki.blocki_backend.document.entity.DocumentType;
import jakarta.validation.constraints.NotNull;

public record DocumentGenerationRequest(@NotNull DocumentType type) {
}

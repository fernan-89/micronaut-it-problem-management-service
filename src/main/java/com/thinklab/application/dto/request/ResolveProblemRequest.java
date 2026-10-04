package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/resolve}: the permanent fix. */
@Serdeable
public record ResolveProblemRequest(
        @NotBlank(message = "Resolution notes are required")
        @Size(max = 4000, message = "Resolution notes must not exceed 4000 characters")
        String resolution
) {}

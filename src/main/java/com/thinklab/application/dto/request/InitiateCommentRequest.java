package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code comment/initiate}. Every comment on a problem is staff-only, because the whole service is. */
@Serdeable
public record InitiateCommentRequest(
        @NotBlank(message = "Text is required")
        @Size(max = 4000, message = "Text must not exceed 4000 characters")
        String text
) {}

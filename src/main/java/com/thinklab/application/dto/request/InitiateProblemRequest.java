package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Problem.Priority;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** DTO for Problem creation (BIAN Behavior Qualifier: {@code initiate}). The priority is the analyst's choice (ADR-032). */
@Serdeable
public record InitiateProblemRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,
        @NotBlank(message = "Description is required")
        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,
        @NotNull(message = "Priority is required")
        Priority priority,
        @Nullable
        @Size(max = 200, message = "At most 200 related incidents")
        Set<UUID> relatedIncidentIds,
        @Nullable
        @Size(max = 200, message = "At most 200 related changes")
        Set<UUID> relatedChangeIds,
        @Nullable
        @Size(max = 200, message = "At most 200 affected assets")
        Set<UUID> affectedAssetIds
) {}

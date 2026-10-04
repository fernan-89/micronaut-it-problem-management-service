package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Serdeable
public record ProblemResponse(
        UUID id,
        UUID organisationId,
        String title,
        String description,
        String priority,
        String status,
        UUID assigneeId,
        Set<UUID> relatedIncidentIds,
        Set<UUID> relatedChangeIds,
        Set<UUID> affectedAssetIds,
        String rootCause,
        String workaround,
        String resolution,
        int reopenCount,
        List<CommentResponse> comments,
        Instant createdAt,
        Instant updatedAt
) {}

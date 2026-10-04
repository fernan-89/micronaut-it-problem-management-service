package com.thinklab.application.dto.request;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;

/** DTO for {@code analysis/update}: what the investigation found. A field left out is kept as it was; at least one is needed. */
@Serdeable
public record RecordAnalysisRequest(
        @Nullable
        @Size(max = 4000, message = "Root cause must not exceed 4000 characters")
        String rootCause,
        @Nullable
        @Size(max = 4000, message = "Workaround must not exceed 4000 characters")
        String workaround
) {}

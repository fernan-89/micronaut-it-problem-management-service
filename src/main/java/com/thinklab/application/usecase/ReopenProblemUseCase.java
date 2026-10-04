package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReopenProblemRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reopening a resolved Problem whose fix did not hold (BIAN Behavior Qualifier: {@code control/reopen}). */
@Singleton
public class ReopenProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReopenProblemUseCase.class);

    private final ProblemWorkflow workflow;

    public ReopenProblemUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, ReopenProblemRequest request, String executor, String role) {
        log.info("[USE CASE] Reopening Problem ID: {}", id);

        return workflow.apply(id, organisationId, role, "reopen a problem", problem -> problem.reopen(request.reason(), executor));
    }
}

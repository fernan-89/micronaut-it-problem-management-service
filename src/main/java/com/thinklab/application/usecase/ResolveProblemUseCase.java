package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ResolveProblemRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for resolving a Problem with its permanent fix (BIAN Behavior Qualifier: {@code control/resolve}). */
@Singleton
public class ResolveProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResolveProblemUseCase.class);

    private final ProblemWorkflow workflow;

    public ResolveProblemUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, ResolveProblemRequest request, String executor, String role) {
        log.info("[USE CASE] Resolving Problem ID: {}", id);

        return workflow.apply(id, organisationId, role, "resolve a problem", problem -> problem.resolve(request.resolution(), executor));
    }
}

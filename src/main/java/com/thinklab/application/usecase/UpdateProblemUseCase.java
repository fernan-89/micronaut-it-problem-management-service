package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateProblemRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for editing a Problem while it is open (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateProblemUseCase.class);

    private final ProblemWorkflow workflow;

    public UpdateProblemUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateProblemRequest request, String executor, String role) {
        log.info("[USE CASE] Updating Problem ID: {}", id);

        return workflow.apply(id, organisationId, role, "update a problem",
                problem -> problem.updateDetails(request.title(), request.description(), request.priority(), request.relatedIncidentIds(),
                        request.relatedChangeIds(), request.affectedAssetIds(), executor));
    }
}

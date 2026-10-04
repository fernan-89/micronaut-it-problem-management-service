package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssignProblemRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for naming who investigates a Problem (BIAN Behavior Qualifier: {@code assignment/update}). */
@Singleton
public class AssignProblemUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignProblemUseCase.class);

    private final ProblemWorkflow workflow;

    public AssignProblemUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, AssignProblemRequest request, String executor, String role) {
        log.info("[USE CASE] Assigning Problem ID: {} to {}", id, request.assigneeId());

        return workflow.apply(id, organisationId, role, "assign a problem", problem -> problem.assign(request.assigneeId(), executor));
    }
}

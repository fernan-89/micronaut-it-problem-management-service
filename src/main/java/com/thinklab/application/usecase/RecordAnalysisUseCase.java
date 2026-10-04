package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.RecordAnalysisRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for recording what the investigation found (BIAN Behavior Qualifier: {@code analysis/update}): the root cause and/or the workaround. */
@Singleton
public class RecordAnalysisUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordAnalysisUseCase.class);

    private final ProblemWorkflow workflow;

    public RecordAnalysisUseCase(ProblemWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, RecordAnalysisRequest request, String executor, String role) {
        log.info("[USE CASE] Recording the analysis of Problem ID: {}", id);

        return workflow.apply(id, organisationId, role, "record an analysis", problem -> problem.recordAnalysis(request.rootCause(), request.workaround(), executor));
    }
}
